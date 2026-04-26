package com.gpuoj.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.gpuoj.dto.JobMessage
import com.gpuoj.dto.ResultMessage
import com.gpuoj.repository.SubmissionRepository
import jakarta.annotation.PostConstruct
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.kafka.receiver.KafkaReceiver
import reactor.kafka.sender.KafkaSender
import reactor.kafka.sender.SenderRecord
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class KafkaPipeline(
    private val sender: KafkaSender<String, String>,
    private val receiver: KafkaReceiver<String, String>,
    private val objectMapper: ObjectMapper,
    private val submissionRepo: SubmissionRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${app.kafka.jobs-topic}")
    private lateinit var jobsTopic: String

    private val pendingSinks = ConcurrentHashMap<String, Sinks.One<ResultMessage>>()

    @PostConstruct
    fun startConsuming() {
        receiver.receive()
            .flatMap { record ->
                val result = try {
                    objectMapper.readValue(record.value(), ResultMessage::class.java)
                } catch (e: Exception) {
                    log.error("Failed to parse result message", e)
                    record.receiverOffset().acknowledge()
                    return@flatMap Mono.empty()
                }

                persistResult(result)
                    .doOnSuccess {
                        pendingSinks.remove(result.submissionId)?.tryEmitValue(result)
                        record.receiverOffset().acknowledge()
                    }
                    .doOnError { err -> log.error("Failed to persist result for ${result.submissionId}", err) }
                    .onErrorResume { Mono.empty() }
            }
            .subscribe(
                {},
                { err -> log.error("Kafka consumer error", err) }
            )
    }

    private fun persistResult(result: ResultMessage): Mono<Void> {
        val id = try { UUID.fromString(result.submissionId) } catch (e: Exception) { return Mono.empty() }
        return submissionRepo.findById(id).flatMap { sub ->
            sub.verdict = result.verdict
            sub.status = if (result.verdict != null) "COMPLETED" else sub.status
            sub.stdout = result.stdout
            sub.stderr = result.stderr?.takeIf { it.isNotBlank() } ?: result.compileError
            sub.wallTimeMs = result.wallTimeMs
            sub.peakVramMb = result.peakVramMb
            sub.speedup = result.speedup
            submissionRepo.save(sub)
        }.then()
    }

    fun publishJob(job: JobMessage): Mono<Void> {
        return try {
            val json = objectMapper.writeValueAsString(job)
            val producerRecord = ProducerRecord<String, String>(jobsTopic, job.submissionId, json)
            sender.send(Mono.just(SenderRecord.create(producerRecord, job.submissionId))).then()
        } catch (e: Exception) {
            Mono.error(e)
        }
    }

    fun registerPending(submissionId: UUID): Sinks.One<ResultMessage> {
        val sink = Sinks.one<ResultMessage>()
        pendingSinks[submissionId.toString()] = sink
        return sink
    }

    fun removePending(submissionId: UUID) {
        pendingSinks.remove(submissionId.toString())
    }
}

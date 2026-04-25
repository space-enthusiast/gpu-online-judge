package com.gpuoj.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.gpuoj.dto.JobMessage
import com.gpuoj.dto.ResultMessage
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
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${app.kafka.jobs-topic}")
    private lateinit var jobsTopic: String

    private val pendingSinks = ConcurrentHashMap<String, Sinks.One<ResultMessage>>()

    @PostConstruct
    fun startConsuming() {
        receiver.receive()
            .doOnNext { record ->
                try {
                    val result = objectMapper.readValue(record.value(), ResultMessage::class.java)
                    pendingSinks.remove(result.submissionId)?.tryEmitValue(result)
                } catch (e: Exception) {
                    log.error("Failed to parse result message", e)
                }
                record.receiverOffset().acknowledge()
            }
            .subscribe(
                {},
                { err -> log.error("Kafka consumer error", err) }
            )
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

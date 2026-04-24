package com.gpuoj.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gpuoj.dto.JobMessage;
import com.gpuoj.dto.ResultMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaPipeline {

    private final KafkaSender<String, String> sender;
    private final KafkaReceiver<String, String> receiver;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.jobs-topic}")
    private String jobsTopic;

    // One sink per pending submission
    private final Map<String, Sinks.One<ResultMessage>> pendingSinks = new ConcurrentHashMap<>();

    @PostConstruct
    public void startConsuming() {
        receiver.receive()
                .doOnNext(record -> {
                    try {
                        ResultMessage result = objectMapper.readValue(record.value(), ResultMessage.class);
                        Sinks.One<ResultMessage> sink = pendingSinks.remove(result.getSubmissionId());
                        if (sink != null) {
                            sink.tryEmitValue(result);
                        }
                    } catch (JsonProcessingException e) {
                        log.error("Failed to parse result message", e);
                    }
                    record.receiverOffset().acknowledge();
                })
                .subscribe(
                        record -> {},
                        err -> log.error("Kafka consumer error", err)
                );
    }

    public Mono<Void> publishJob(JobMessage job) {
        try {
            String json = objectMapper.writeValueAsString(job);
            ProducerRecord<String, String> producerRecord =
                    new ProducerRecord<>(jobsTopic, job.getSubmissionId(), json);
            return sender.send(Mono.just(SenderRecord.create(producerRecord, job.getSubmissionId())))
                    .then();
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    public Sinks.One<ResultMessage> registerPending(UUID submissionId) {
        Sinks.One<ResultMessage> sink = Sinks.one();
        pendingSinks.put(submissionId.toString(), sink);
        return sink;
    }

    public void removePending(UUID submissionId) {
        pendingSinks.remove(submissionId.toString());
    }
}

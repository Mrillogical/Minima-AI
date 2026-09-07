package com.minima-AI.url.dlq;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/* Consumes whatever DeadLetterPublishingRecoverer sends to <topic>.DLT after
/ classifiedKafkaListenerContainerFactory exhausts its 3 retries.
*/
@Component
@Slf4j
public class DeadLetterMonitor {

    private final Counter dlqCounter;

    public DeadLetterMonitor(MeterRegistry meterRegistry) {

        this.dlqCounter = Counter.builder("minima-AI_kafka_dlq_messages_total")
                .description("Messages that landed on a Kafka dead-letter topic")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${spring.kafka.topics.url-classified}.DLT",
            groupId = "url-service-dlq-monitor"
    )
    public void onDeadLetter(
            @Payload(required = false) Object payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String originalTopic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage
    ) {

        dlqCounter.increment();

        log.error(
                "DEAD LETTER QUEUE - topic: {} error: {} payload: {}",
                originalTopic,
                exceptionMessage,
                payload
        );
    }
}


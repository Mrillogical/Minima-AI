package com.minima-AI.ai.classification;

import com.minima-AI.ai.events.dto.UrlClassifiedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UrlClassifiedEventPublisher {

    private final String topic;

    private final KafkaTemplate<String, UrlClassifiedEvent> kafkaTemplate;

    public UrlClassifiedEventPublisher(
            @Value("${spring.kafka.topics.url-classified}")
            String topic,
            KafkaTemplate<String, UrlClassifiedEvent> kafkaTemplate
    ) {
        this.topic = topic;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(UrlClassifiedEvent event) {

        try {

            kafkaTemplate.send(topic, String.valueOf(event.urlId()), event)

                    .whenComplete((result, ex) -> {

                        if (ex != null) {

                            log.error("Failed to publish url.classified urlId={}", event.urlId(), ex);

                        } else {

                            log.info("Published url.classified urlId= {}, category= {}",
                                    event.urlId(), event.category());
                        }
                    });

        } catch (Exception ex) {

            // catches SYNCHRONOUS failures (serialization, broker unreachable at send-call time)
            log.error("Synchronous failure publishing url.classified urlId= {}", event.urlId(), ex);
        }
    }
}


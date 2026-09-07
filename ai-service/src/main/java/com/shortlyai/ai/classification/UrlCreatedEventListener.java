package com.minima-AI.ai.classification;

import com.minima-AI.ai.classification.dto.ClassificationRequest;
import com.minima-AI.ai.classification.dto.ClassificationResponse;
import com.minima-AI.ai.events.dto.UrlClassifiedEvent;
import com.minima-AI.ai.events.dto.UrlCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class UrlCreatedEventListener {

    private final ClassificationService classificationService;

    private final UrlClassifiedEventPublisher publisher;

    // no containerFactory specified -> uses default "kafkaListenerContainerFactory"
    @KafkaListener(topics = "${spring.kafka.topics.url-created}")
    public void onUrlCreated(UrlCreatedEvent event) {

        log.info("Received url.created event urlId: {}, originalUrl: {}",
                event.urlId(), event.originalUrl()
        );

        try {

            ClassificationResponse classification =
                    classificationService
                            .classify(
                                    new ClassificationRequest(event.originalUrl())
                            )
                            .join();

            if (classification == null) {

                // classify()'s own CircuitBreaker fallback returns null when Groq
                // is unreachable/degraded - don't publish a fake "Unknown" verdict
                // that would get permanently persisted with no way to retry once
                // Groq recovers. URL just stays unclassified for now.
                log.warn(
                        "Classification unavailable (degraded/circuit open) for urlId: {} - skipping publish, url stays unclassified",
                        event.urlId()
                );

                return;
            }

            UrlClassifiedEvent classifiedEvent = new UrlClassifiedEvent(
                    event.urlId(),
                    classification.title(),
                    classification.category(),
                    classification.confidence(),
                    classification.tags()
            );

            publisher.publish(classifiedEvent);

        } catch (Exception ex) {

            log.error("Failed to classify urlId: {}", event.urlId(), ex);
            // Belt-and-suspenders only - classify()'s own Retry/CircuitBreaker/
            // TimeLimiter plus the null-fallback above absorb virtually every
            // failure before it gets here, and this catch doesn't rethrow, so
            // KafkaConfig's DefaultErrorHandler retry never actually engages
            // for classification failures.
        }
    }
}


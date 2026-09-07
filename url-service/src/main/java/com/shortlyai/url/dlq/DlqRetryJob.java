package com.minima-AI.url.dlq;

import com.minima-AI.url.events.UrlClickedEvent;
import com.minima-AI.url.events.UrlCreatedEvent;
import com.minima-AI.url.events.UrlDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor
public class DlqRetryJob {

    private static final int MAX_RETRIES = 5;

    private final FailedEventRepository failedEventRepository;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final JsonMapper jsonMapper;

    @Scheduled(fixedDelay = 15 * 60 * 1000)
    @SchedulerLock(name = "dlq_retry_job", lockAtLeastFor = "5s", lockAtMostFor = "1m")
    public void retryFailedEvents() {

        log.info("DLQ retry fired");

        int pageSize = 100;
        int totalProcessed = 0;
        Page<FailedEvent> page;

        do {
            // always page 0 - processed/maxed-out rows fall out of the WHERE
            // clause each time, so offset 0 always holds the next unhandled batch
            page = failedEventRepository
                    .findRetryable(
                            MAX_RETRIES,
                            PageRequest.of(0, pageSize)
                    );

            if (page.isEmpty()) {

                log.debug("DLQ retry: no pending events");

                break;
            }

            for (FailedEvent event : page.getContent()) {

                retryEvent(event);
            }

            totalProcessed += page.getNumberOfElements();

        } while (page.getNumberOfElements() == pageSize);

        if (totalProcessed > 0) {

            log.info("DLQ retry complete: attempted {} events", totalProcessed);
        }
    }

    private void retryEvent(FailedEvent failedEvent) {

        failedEvent.setLastAttemptAt(Instant.now());

        failedEvent.setRetryCount(failedEvent.getRetryCount() + 1);

        failedEventRepository.save(failedEvent);

        try {

            // Resolve correct event class from topic name.
            Class<?> eventClass = resolveEventClass(failedEvent.getTopic());

            Object payload = jsonMapper.readValue(failedEvent.getPayload(), eventClass);

            kafkaTemplate.send(failedEvent.getTopic(), failedEvent.getEventKey(), payload)
                    .whenComplete((result, ex) -> {

                        if (ex != null) {

                            log.warn("DLQ retry failed: topic={} key={} attempt={} error={}",
                                    failedEvent.getTopic(), failedEvent.getEventKey(),
                                    failedEvent.getRetryCount(), ex.getMessage());

                        } else {

                            failedEvent.setProcessed(true);
                            failedEventRepository.save(failedEvent);

                            log.info("DLQ retry success: topic={} key={} attempt={}",
                                    failedEvent.getTopic(), failedEvent.getEventKey(),
                                    failedEvent.getRetryCount());
                        }
                    });

        } catch (Exception e) {

            log.warn("DLQ retry immediate failure: topic={} key={} attempt={} error={}",
                    failedEvent.getTopic(), failedEvent.getEventKey(),
                    failedEvent.getRetryCount(), e.getMessage());
        }
    }

    // Maps Kafka topic -> event record class so Jackson deserializes correctly.
    // If a new topic is added to url-service, add it here or retry falls back to Object.
    private Class<?> resolveEventClass(String topic) {

        return switch (topic) {
            case "url.created" -> UrlCreatedEvent.class;
            case "url.clicks" -> UrlClickedEvent.class;
            case "url.deleted" -> UrlDeletedEvent.class;
            default -> {
                log.warn("DLQ: unknown topic '{}', deserializing as Object - retry may fail", topic);
                yield Object.class;
            }
        };
    }
}


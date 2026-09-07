package com.minima-AI.url.shortening;

import com.minima-AI.url.dlq.FailedEventService;
import com.minima-AI.url.events.UrlClickedEvent;
import com.minima-AI.url.events.UrlCreatedEvent;
import com.minima-AI.url.events.UrlDeletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

@Component
@Slf4j
public class UrlEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final FailedEventService failedEventService;

    private final String baseDomain;

    private final String urlCreatedTopic;

    private final String urlClickedTopic;

    private final String urlDeletedTopic;

    public UrlEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            FailedEventService failedEventService,
            @Value("${url.base-domain}") String baseDomain,
            @Value("${spring.kafka.topics.url-created}") String urlCreatedTopic,
            @Value("${spring.kafka.topics.url-clicked}") String urlClickedTopic,
            @Value("${spring.kafka.topics.url-deleted}") String urlDeletedTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.failedEventService = failedEventService;
        this.baseDomain = baseDomain;
        this.urlCreatedTopic = urlCreatedTopic;
        this.urlClickedTopic = urlClickedTopic;
        this.urlDeletedTopic = urlDeletedTopic;
    }

    // Must be called from inside the caller's open @Transactional method -
    // recordPending() is Propagation.MANDATORY and throws otherwise.
    public void publishCreated(Url savedUrl) {

        UrlCreatedEvent event = new UrlCreatedEvent(
                savedUrl.getId(), savedUrl.getSlug(), savedUrl.getOriginalUrl(),
                baseDomain + "/" + savedUrl.getSlug(), savedUrl.getUserId(),
                savedUrl.getExpiresAt(), savedUrl.getCreatedAt());

        Long outboxId = failedEventService.recordPending(urlCreatedTopic, savedUrl.getSlug(), event);

        registerAfterCommit(urlCreatedTopic, savedUrl.getSlug(), event, outboxId);

        log.info("Queued url.created (outbox #{}) for slug: {}", outboxId, savedUrl.getSlug());
    }

    public void publishDeleted(Url url) {

        UrlDeletedEvent event = new UrlDeletedEvent(
                url.getId(), url.getSlug(), url.getUserId(), Instant.now());

        Long outboxId = failedEventService.recordPending(urlDeletedTopic, url.getSlug(), event);

        registerAfterCommit(urlDeletedTopic, url.getSlug(), event, outboxId);

        log.info("Queued url.deleted (outbox #{}) for slug: {}", outboxId, url.getSlug());
    }

    // Best-effort, intentionally NOT outbox-backed: called from resolve(),
    // which is not @Transactional (redirect hot path, runs off
    // clickTrackingExecutor). There is no open transaction here
    public void publishClick(
            Long urlId, String slug, UUID ownerId,
            String ipHash, String userAgent, String referer
    ) {

        UrlClickedEvent event = new UrlClickedEvent(
                urlId, slug, userAgent, ipHash, referer,
                null, null, Instant.now(), ownerId);

        try {

            kafkaTemplate.send(urlClickedTopic, slug, event).whenComplete((result, ex) -> {

                if (ex != null) {

                    log.error("Kafka publish failed: topic: {} slug: {} error: {}",
                            urlClickedTopic, slug, ex.getMessage());

                    failedEventService.save(urlClickedTopic, slug, event, ex.getMessage());

                } else {

                    log.debug("Published url.clicked: topic: {} slug: {} urlId: {} partition: {}",
                            urlClickedTopic, slug, urlId, result.getRecordMetadata().partition());
                }
            });

        } catch (Exception e) {

            log.error("Failed to publish " + urlClickedTopic + " event: ", e);

            failedEventService.save(urlClickedTopic, slug, event, e.getMessage());
        }
    }

    private void registerAfterCommit(String topic, String key, Object event, Long outboxId) {

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                // fires only once the transaction is durably committed -
                // Kafka never sees an event for a row that might still roll back
                sendAndMarkProcessed(topic, key, event, outboxId);
            }
        });
    }

    private void sendAndMarkProcessed(String topic, String key, Object event, Long outboxId) {

        try {

            kafkaTemplate.send(topic, key, event).whenComplete((result, ex) -> {

                if (ex == null) {

                    failedEventService.markProcessed(outboxId);

                    log.debug("Published {} key: {} (outbox #{}) partition: {}",
                            topic, key, outboxId, result.getRecordMetadata().partition());

                } else {

                    log.warn("Immediate publish failed topic: {} key: {} (outbox #{}) - " +
                            "DlqRetryJob will deliver it: {}", topic, key, outboxId, ex.getMessage());
                }
            });

        } catch (Exception ex) {

            log.warn("Synchronous publish failure topic: {} key: {} (outbox #{}) - " +
                    "DlqRetryJob will deliver it: {}", topic, key, outboxId, ex.getMessage());
        }
    }
}


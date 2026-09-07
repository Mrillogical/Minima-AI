package com.minima-AI.url.dlq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class FailedEventService {

    private final FailedEventRepository failedEventRepository;

    private final JsonMapper jsonMapper;

    // Called from Kafka whenComplete callback - that runs on Kafka I/O thread,
    // outside any existing transaction, so @Transactional opens a fresh one
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(String topic, String eventKey, Object event, String errorMessage) {

        try {
            String payload = jsonMapper.writeValueAsString(event); // serialize event -> JSON

            FailedEvent failed = FailedEvent.builder()
                    .topic(topic)
                    .eventKey(eventKey)
                    .payload(payload)
                    .errorMessage(errorMessage)
                    .build();

            failedEventRepository.save(failed);

            log.warn("Saved failed event to DLQ: topic= {} key= {}", topic, eventKey);

        } catch (JacksonException e) {

            // If we can't even serialize the event, log and skip â€” don't throw
            // Throwing here would crash the Kafka callback thread
            log.error("Failed to serialize event for DLQ: topic= {} key= {} error= {}",
                    topic, eventKey, e.getMessage());
        }
    }

    // Runs INSIDE the caller's existing @Transactional method (e.g. shorten()),
    // so this row commits atomically with the business write. If crashes, then this delivers the event
    @Transactional(propagation = Propagation.MANDATORY)
    public Long recordPending(String topic, String eventKey, Object event) {

        try {

            String payload = jsonMapper.writeValueAsString(event);

            FailedEvent pending = FailedEvent.builder()
                    .topic(topic)
                    .eventKey(eventKey)
                    .payload(payload)
                    .errorMessage("PENDING_INITIAL_PUBLISH")
                    .build();

            return failedEventRepository.save(pending).getId();

        } catch (JacksonException e) {
            // Can't serialize the DTO. Fail loud instead of silently losing the event.
            throw new IllegalStateException("Could not serialize event for outbox: topic= " + topic, e);
        }
    }

    // Called from the post-commit Kafka callback, same reasoning as save() above:
    // own transaction since there's no ambient one on that thread.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(Long outboxId) {
        failedEventRepository.markProcessed(outboxId);
    }
}


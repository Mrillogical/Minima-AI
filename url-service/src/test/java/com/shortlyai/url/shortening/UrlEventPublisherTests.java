package com.minima-AI.url.shortening;

import com.minima-AI.url.dlq.FailedEventService;
import com.minima-AI.url.events.UrlClickedEvent;
import com.minima-AI.url.events.UrlCreatedEvent;
import com.minima-AI.url.events.UrlDeletedEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UrlEventPublisherTests {

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    FailedEventService failedEventService;

    UrlEventPublisher publisher;

    private static final String BASE_DOMAIN = "http://localhost:8082";
    private static final String CREATED_TOPIC = "url.created";
    private static final String CLICKED_TOPIC = "url.clicks";
    private static final String DELETED_TOPIC = "url.deleted";
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {

        publisher = new UrlEventPublisher(
                kafkaTemplate, failedEventService, BASE_DOMAIN,
                CREATED_TOPIC, CLICKED_TOPIC, DELETED_TOPIC);
    }

    private Url testUrl() {

        return Url.builder()
                .id(1L)
                .slug("abc123")
                .originalUrl("https://example.com")
                .userId(USER_ID)
                .createdAt(Instant.now())
                .build();
    }

    @SuppressWarnings("unchecked")
    private SendResult<String, Object> mockSuccessResult() {
        SendResult<String, Object> result = mock(SendResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(result.getRecordMetadata()).thenReturn(metadata);
        return result;
    }

    // ---------- publishCreated() â€” outbox path ----------

    @Test
    void publishCreated_registersOutboxRow_beforeRegisteringSync() {

        when(failedEventService.recordPending(eq(CREATED_TOPIC), eq("abc123"), any(UrlCreatedEvent.class)))
                .thenReturn(42L);

        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {

            publisher.publishCreated(testUrl());

            // outbox row must exist before the sync is even registered -
            // this is the crash-safety guarantee itself
            verify(failedEventService).recordPending(eq(CREATED_TOPIC), eq("abc123"), any(UrlCreatedEvent.class));

            tsm.verify(() -> TransactionSynchronizationManager.registerSynchronization(any()));
        }

        // Kafka must NOT have been touched yet - only queued for after commit
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void publishCreated_sendsAndMarksProcessed_onlyAfterCommitFires() {

        when(failedEventService.recordPending(anyString(), anyString(), any())).thenReturn(42L);

        SendResult<String, Object> success = mockSuccessResult();
        when(kafkaTemplate.send(eq(CREATED_TOPIC), eq("abc123"), any(UrlCreatedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(success));

        ArgumentCaptor<TransactionSynchronization> syncCaptor =
                ArgumentCaptor.forClass(TransactionSynchronization.class);

        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {

            publisher.publishCreated(testUrl());

            tsm.verify(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));
        }

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());

        syncCaptor.getValue().afterCommit();

        verify(kafkaTemplate).send(eq(CREATED_TOPIC), eq("abc123"), any(UrlCreatedEvent.class));
        verify(failedEventService).markProcessed(42L);
    }

    @Test
    void publishCreated_asyncSendFails_doesNotMarkProcessed_leavesRowForRetryJob() {

        when(failedEventService.recordPending(anyString(), anyString(), any())).thenReturn(42L);

        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unreachable"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);

        ArgumentCaptor<TransactionSynchronization> syncCaptor =
                ArgumentCaptor.forClass(TransactionSynchronization.class);

        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {

            publisher.publishCreated(testUrl());
            tsm.verify(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));
        }

        syncCaptor.getValue().afterCommit();

        // row stays unprocessed - DlqRetryJob is the only thing allowed to mark it done
        verify(failedEventService, never()).markProcessed(anyLong());
    }

    @Test
    void publishCreated_synchronousSendThrow_doesNotPropagateOutOfAfterCommit() {

        when(failedEventService.recordPending(anyString(), anyString(), any())).thenReturn(42L);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("serializer blew up"));

        ArgumentCaptor<TransactionSynchronization> syncCaptor =
                ArgumentCaptor.forClass(TransactionSynchronization.class);

        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {

            publisher.publishCreated(testUrl());
            tsm.verify(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));
        }

        // must not throw - a Spring TransactionSynchronization callback throwing
        // would corrupt commit-phase processing for anything registered after it
        syncCaptor.getValue().afterCommit();

        verify(failedEventService, never()).markProcessed(anyLong());
    }

    // ---------- publishDeleted() â€” THE regression test for the fix ----------

    @Test
    void publishDeleted_isOutboxBacked_notSentBeforeCommit() {

        when(failedEventService.recordPending(eq(DELETED_TOPIC), eq("abc123"), any(UrlDeletedEvent.class)))
                .thenReturn(7L);

        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {

            publisher.publishDeleted(testUrl());

            // this is the exact guarantee that was missing before the fix:
            // an outbox row must exist and no send must have happened yet
            verify(failedEventService).recordPending(eq(DELETED_TOPIC), eq("abc123"), any(UrlDeletedEvent.class));
            tsm.verify(() -> TransactionSynchronizationManager.registerSynchronization(any()));
        }

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void publishDeleted_afterCommit_sendsCorrectEventFields() {

        when(failedEventService.recordPending(anyString(), anyString(), any())).thenReturn(7L);

        SendResult<String, Object> success = mockSuccessResult();
        when(kafkaTemplate.send(eq(DELETED_TOPIC), eq("abc123"), any(UrlDeletedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(success));

        Url url = testUrl();

        ArgumentCaptor<TransactionSynchronization> syncCaptor =
                ArgumentCaptor.forClass(TransactionSynchronization.class);
        ArgumentCaptor<UrlDeletedEvent> eventCaptor = ArgumentCaptor.forClass(UrlDeletedEvent.class);

        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {

            publisher.publishDeleted(url);
            tsm.verify(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));
        }

        syncCaptor.getValue().afterCommit();

        verify(kafkaTemplate).send(eq(DELETED_TOPIC), eq("abc123"), eventCaptor.capture());
        verify(failedEventService).markProcessed(7L);

        UrlDeletedEvent sent = eventCaptor.getValue();
        assertThat(sent.id()).isEqualTo(url.getId());
        assertThat(sent.slug()).isEqualTo(url.getSlug());
        assertThat(sent.userId()).isEqualTo(url.getUserId());
    }

    // ---------- publishClick() â€” intentionally NOT outbox-backed ----------

    @Test
    void publishClick_sendsImmediately_neverTouchesOutbox() {

        SendResult<String, Object> success = mockSuccessResult();
        when(kafkaTemplate.send(eq(CLICKED_TOPIC), eq("abc123"), any(UrlClickedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(success));

        publisher.publishClick(1L, "abc123", USER_ID, "iphash", "ua", "ref");

        verify(kafkaTemplate).send(eq(CLICKED_TOPIC), eq("abc123"), any(UrlClickedEvent.class));
        verify(failedEventService, never()).recordPending(anyString(), anyString(), any());
        verify(failedEventService, never()).markProcessed(anyLong());
    }

    @Test
    void publishClick_asyncFailure_fallsBackToSave_notRecordPending() {

        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);

        publisher.publishClick(1L, "abc123", USER_ID, "iphash", "ua", "ref");

        verify(failedEventService).save(eq(CLICKED_TOPIC), eq("abc123"), any(UrlClickedEvent.class), anyString());
        verify(failedEventService, never()).recordPending(anyString(), anyString(), any());
    }

    @Test
    void publishClick_synchronousThrow_savesToDlqInsteadOfPropagating() {

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("serializer blew up"));

        publisher.publishClick(1L, "abc123", USER_ID, "iphash", "ua", "ref");

        verify(failedEventService).save(eq(CLICKED_TOPIC), eq("abc123"), any(UrlClickedEvent.class), anyString());
    }
}


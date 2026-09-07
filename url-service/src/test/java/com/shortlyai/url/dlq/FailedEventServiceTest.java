package com.minima-AI.url.dlq;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FailedEventServiceTest {

    @Mock
    FailedEventRepository failedEventRepository;

    @Mock
    JsonMapper jsonMapper;

    FailedEventService failedEventService;

    record DummyEvent(Long id, String name) {}

    @BeforeEach
    void setUp() {
        failedEventService = new FailedEventService(failedEventRepository, jsonMapper);
    }

    @Test
    void save_serializesEventAndPersistsFailedEvent() {

        DummyEvent event = new DummyEvent(1L, "test");

        when(jsonMapper.writeValueAsString(event)).thenReturn("{\"id\":1,\"name\":\"test\"}");

        failedEventService.save("url.created", "slug-1", event, "kafka broker down");

        ArgumentCaptor<FailedEvent> captor = ArgumentCaptor.forClass(FailedEvent.class);
        verify(failedEventRepository).save(captor.capture());

        FailedEvent saved = captor.getValue();

        assertThat(saved.getTopic()).isEqualTo("url.created");
        assertThat(saved.getEventKey()).isEqualTo("slug-1");
        assertThat(saved.getPayload()).isEqualTo("{\"id\":1,\"name\":\"test\"}");
        assertThat(saved.getErrorMessage()).isEqualTo("kafka broker down");
        assertThat(saved.getRetryCount()).isEqualTo(0);
        assertThat(saved.isProcessed()).isFalse();
    }

    @Test
    void save_serializationFails_logsAndSkipsWithoutThrowing() {

        DummyEvent event = new DummyEvent(2L, "unserializable");

        when(jsonMapper.writeValueAsString(event))
                .thenThrow(mock(JacksonException.class));

        // must not propagate - this runs on the Kafka callback thread
        failedEventService.save("url.deleted", "slug-2", event, "boom");

        verify(failedEventRepository, never()).save(any());
    }
}


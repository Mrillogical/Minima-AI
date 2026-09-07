package com.minima-AI.analytics.consumers;

import com.minima-AI.analytics.clicks.ClickService;
import com.minima-AI.analytics.events.UrlDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteConsumer {

    private final ClickService clickService;

    @KafkaListener(
            topics = "${spring.kafka.topics.url-deleted}",
            groupId = "analytics-service-group",
            containerFactory = "deletedKafkaListenerContainerFactory"
    )
    public void onUrlDeleted(
            @Payload
            UrlDeletedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION)
            int partition,
            @Header(KafkaHeaders.OFFSET)
            long offset
    ) {

        log.debug("Received url.deleted event - slug: {} partition: {} offset: {}",
                event.slug(), partition, offset);

        clickService.deleteClickData(event);
    }
}


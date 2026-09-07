package com.minima-AI.analytics.consumers;

import com.minima-AI.analytics.clicks.ClickService;
import com.minima-AI.analytics.events.UrlClickedEvent;
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
public class ClickConsumer {

    private final ClickService clickService;

    @KafkaListener(
            topics = "${spring.kafka.topics.url-clicked}",
            groupId = "analytics-service-group"
    )
    public void onUrlClicked(
            @Payload UrlClickedEvent event,                       // deserialized JSON â†’ Record
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition, // which Kafka partition
            @Header(KafkaHeaders.OFFSET) long offset              // position in partition
    ) {

        log.debug("Received url.clicks event - slug: {} partition: {} offset: {}",
                event.slug(), partition, offset);

        // Delegate to service - consumer stays thin
        clickService.processClick(event);
    }
}


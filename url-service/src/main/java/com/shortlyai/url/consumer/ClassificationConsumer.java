package com.minima-AI.url.consumer;

import com.minima-AI.url.events.UrlClassifiedEvent;
import com.minima-AI.url.shortening.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class ClassificationConsumer {

    private final UrlRepository urlRepository;

    @KafkaListener(
            topics = "${spring.kafka.topics.url-classified}",
            containerFactory = "classifiedKafkaListenerContainerFactory"
    )
    @Transactional
    public void onUrlClassified(UrlClassifiedEvent event) {

        log.info("Received url.classified event urlId: {}, category: {}, confidence: {}",
                event.urlId(), event.category(), event.confidence());

        // MALICIOUS category -> mark unsafe. Adjust if you want ADULT etc. flagged too.
        boolean isSafe = !"MALICIOUS".equals(event.category());

        urlRepository.updateClassification(event.urlId(), event.title(), event.category(), isSafe);
    }
}


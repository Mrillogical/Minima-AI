package com.minima-AI.url.common.config;

import com.minima-AI.url.events.UrlClassifiedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Configuration
public class KafkaConsumerConfig {

    private final String bootstrapServers;

    // reuses same bootstrap-servers property the producer already uses
    public KafkaConsumerConfig(
            @Value("${spring.kafka.bootstrap-servers}")
            String bootstrapServers
    ) {
        this.bootstrapServers = bootstrapServers;
    }

    @Bean
    public ConsumerFactory<String, UrlClassifiedEvent> classifiedConsumerFactory() {

        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "url-service-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        JacksonJsonDeserializer<UrlClassifiedEvent> deser =
                new JacksonJsonDeserializer<>(UrlClassifiedEvent.class);

        deser.addTrustedPackages("com.minima-AI.*");

        deser.setUseTypeHeaders(false); // ai-service producer has add.type.headers: false

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(deser)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UrlClassifiedEvent>
    classifiedKafkaListenerContainerFactory(KafkaTemplate<Object, Object> template) {

        ConcurrentKafkaListenerContainerFactory<String, UrlClassifiedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(classifiedConsumerFactory());

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(
                        recoverer,
                        new FixedBackOff(1000L, 3L)
                );

        // Poison pills go straight to the DLT on attempt 1
        errorHandler.addNotRetryableExceptions(
                DeserializationException.class,
                NullPointerException.class,
                IllegalArgumentException.class
        );

        // Transient - DB hiccup on the updateClassification() write - worth the retry
        errorHandler.addRetryableExceptions(
                TimeoutException.class,
                TransientDataAccessException.class
        );

        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}


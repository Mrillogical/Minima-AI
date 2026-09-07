package com.minima-AI.ai.events.config;

import com.minima-AI.ai.events.dto.UrlCreatedEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@EnableKafka
@Configuration
public class KafkaConfig {

    // consumer factory - deserializes incoming url.created JSON into UrlCreatedEvent
    @Bean
    public ConsumerFactory<String, UrlCreatedEvent> urlCreatedConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());

        JacksonJsonDeserializer<UrlCreatedEvent> deserializer = new JacksonJsonDeserializer<>(UrlCreatedEvent.class);
        deserializer.setUseTypeHeaders(false); // producer sends no type headers - trust target type
        deserializer.addTrustedPackages("com.minima-AI.*");

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(deserializer)
        );
    }

    // poison-pill handling: retry 3x (1s apart) ONLY for exceptions worth retrying
    @Bean
    public DefaultErrorHandler errorHandler() {

        DefaultErrorHandler handler =
                new DefaultErrorHandler(new FixedBackOff(1000L, 3));

        // Poison pills - retrying can never fix these
        handler.addNotRetryableExceptions(
                DeserializationException.class,
                NullPointerException.class,
                IllegalArgumentException.class
        );

        // Transient - Groq API blip, DB hiccup
        handler.addRetryableExceptions(
                TimeoutException.class,
                TransientDataAccessException.class
        );

        return handler;
    }

    // name MUST be "kafkaListenerContainerFactory" - default factory, auto-wires
    // to @KafkaListener with no containerFactory specified
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UrlCreatedEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, UrlCreatedEvent> urlCreatedConsumerFactory,
            DefaultErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, UrlCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(urlCreatedConsumerFactory);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    // ensures url.classified topic exists even if broker auto-create is off
    @Bean
    public NewTopic urlClassifiedTopic(@Value("${spring.kafka.topics.url-classified}") String topic) {

        return TopicBuilder.name(topic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}


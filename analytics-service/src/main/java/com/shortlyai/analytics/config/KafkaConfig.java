package com.minima-AI.analytics.config;

import com.minima-AI.analytics.events.UrlClickedEvent;
import com.minima-AI.analytics.events.UrlCreatedEvent;
import com.minima-AI.analytics.events.UrlDeletedEvent;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class KafkaConfig {

    private final String bootstrapServers;

    public KafkaConfig(
            @Value("${spring.kafka.bootstrap-servers}")
            String bootstrapServers
    ) {
        this.bootstrapServers = bootstrapServers;
    }

    // Shared base props - both factories use these
    private Map<String, Object> baseProps() {

        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "analytics-service-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        return props;
    }

    // url.clicks factory
    @Bean
    public ConsumerFactory<String, UrlClickedEvent> clickedConsumerFactory() {

        JacksonJsonDeserializer<UrlClickedEvent> deser =
                new JacksonJsonDeserializer<>(UrlClickedEvent.class);

        deser.addTrustedPackages("com.minima-AI.*");
        deser.setUseTypeHeaders(false);

        // Wrapped so raw deserialization failures surface as DeserializationException
        // required for addNotRetryableExceptions below to actually catch poison-pill messages
        return new DefaultKafkaConsumerFactory<>(
                baseProps(),
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(deser)
        );
    }

    // Default factory name - picked up automatically by ClickConsumer
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UrlClickedEvent>
    kafkaListenerContainerFactory(KafkaTemplate<Object, Object> template) {

        ConcurrentKafkaListenerContainerFactory<String, UrlClickedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(clickedConsumerFactory());
        factory.setCommonErrorHandler(analyticsErrorHandler(template));

        return factory;
    }

    // url.deleted factory
    @Bean
    public ConsumerFactory<String, UrlDeletedEvent> deletedConsumerFactory() {

        JacksonJsonDeserializer<UrlDeletedEvent> deser =
                new JacksonJsonDeserializer<>(UrlDeletedEvent.class);

        deser.addTrustedPackages("com.minima-AI.*");
        deser.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
                baseProps(),
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(deser)
        );
    }

    // Named - referenced explicitly in DeleteConsumer's containerFactory
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UrlDeletedEvent>
    deletedKafkaListenerContainerFactory(KafkaTemplate<Object, Object> template) {

        ConcurrentKafkaListenerContainerFactory<String, UrlDeletedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(deletedConsumerFactory());
        factory.setCommonErrorHandler(analyticsErrorHandler(template));

        return factory;
    }

    // url.created factory
    @Bean
    public ConsumerFactory<String, UrlCreatedEvent> createdConsumerFactory() {

        JacksonJsonDeserializer<UrlCreatedEvent> deser =
                new JacksonJsonDeserializer<>(UrlCreatedEvent.class);

        deser.addTrustedPackages("com.minima-AI.*");
        deser.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
                baseProps(),
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(deser)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UrlCreatedEvent>
    createdKafkaListenerContainerFactory(KafkaTemplate<Object, Object> template) {

        ConcurrentKafkaListenerContainerFactory<String, UrlCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(createdConsumerFactory());
        factory.setCommonErrorHandler(analyticsErrorHandler(template));

        return factory;
    }

    @Bean
    public ConsumerFactory<String, String> dlqConsumerFactory() {

        Map<String, Object> props = baseProps();
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "analytics-service-dlq-monitor");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> dlqKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(dlqConsumerFactory());
        // deliberately NO setCommonErrorHandler(analyticsErrorHandler(...)) here -
        // this factory must never be able to dead-letter its own dead-letter topic

        return factory;
    }

    // shared across all 3 factories now - takes the template so it can
    // publish to <topic>.DLT instead of just logging and dropping
    @Bean
    public DefaultErrorHandler analyticsErrorHandler(KafkaTemplate<Object, Object> template) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));

        handler.addNotRetryableExceptions(
                DeserializationException.class,
                NullPointerException.class,
                IllegalArgumentException.class
        );

        handler.addRetryableExceptions(
                TimeoutException.class,
                TransientDataAccessException.class
        );

        return handler;
    }
}


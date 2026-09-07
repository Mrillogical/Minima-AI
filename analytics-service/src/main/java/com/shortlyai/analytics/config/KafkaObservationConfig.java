package com.minima-AI.analytics.config;

import io.micrometer.common.KeyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.micrometer.KafkaRecordSenderContext;
import org.springframework.kafka.support.micrometer.KafkaTemplateObservation;
import org.springframework.kafka.support.micrometer.KafkaTemplateObservationConvention;

// 4.1 auto-applies these to KafkaTemplate/listener container with zero extra wiring.
// Adds the topic as a low-cardinality tag so Tempo spans are filterable by topic.
@Configuration
public class KafkaObservationConfig {

    @Bean
    KafkaTemplateObservationConvention kafkaTemplateObservationConvention() {

        return new KafkaTemplateObservation.DefaultKafkaTemplateObservationConvention() {

            @Override
            public KeyValues getLowCardinalityKeyValues(KafkaRecordSenderContext context) {

                return super.getLowCardinalityKeyValues(context)
                        .and(
                                "minima-AI.topic",
                                context.getDestination()
                        );
            }
        };
    }
}


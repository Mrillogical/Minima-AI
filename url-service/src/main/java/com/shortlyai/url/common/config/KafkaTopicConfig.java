package com.minima-AI.url.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic urlCreatedTopic(@Value("${spring.kafka.topics.url-created}") String topic) {

        return TopicBuilder
                .name(topic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic urlClickedTopic(@Value("${spring.kafka.topics.url-clicked}") String topic) {

        return TopicBuilder
                .name(topic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic urlDeletedTopic(@Value("${spring.kafka.topics.url-deleted}") String topic) {

        return TopicBuilder
                .name(topic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}


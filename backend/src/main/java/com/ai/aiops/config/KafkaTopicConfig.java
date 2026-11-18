package com.ai.aiops.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import com.ai.aiops.kafka.KafkaTopics;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic logEventsTopic() {
        return TopicBuilder.name(KafkaTopics.LOG_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic logEventsDltTopic() {
        return TopicBuilder.name(KafkaTopics.LOG_EVENTS_DLT).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic anomalyEventsTopic() {
        return TopicBuilder.name(KafkaTopics.ANOMALY_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic incidentEventsTopic() {
        return TopicBuilder.name(KafkaTopics.INCIDENT_EVENTS).partitions(3).replicas(1).build();
    }
}

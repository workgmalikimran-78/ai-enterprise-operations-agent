package com.ai.aiops.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DomainEventProducer {

    private static final Logger log = LoggerFactory.getLogger(DomainEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DomainEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishAnomaly(AnomalyEventMessage message) {
        send(KafkaTopics.ANOMALY_EVENTS, message.applicationId().toString(), message);
    }

    public void publishIncident(IncidentEventMessage message) {
        send(KafkaTopics.INCIDENT_EVENTS, message.applicationId().toString(), message);
    }

    private void send(String topic, String key, Object payload) {
        kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event to {}", topic, ex);
                    }
                });
    }
}

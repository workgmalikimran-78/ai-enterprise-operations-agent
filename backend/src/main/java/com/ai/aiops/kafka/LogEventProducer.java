package com.ai.aiops.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class LogEventProducer {

    private static final Logger log = LoggerFactory.getLogger(LogEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public LogEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(LogEventMessage message) {
        String key = message.applicationId().toString();
        kafkaTemplate.send(KafkaTopics.LOG_EVENTS, key, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish log event {} to {}", message.logEventId(),
                                KafkaTopics.LOG_EVENTS, ex);
                    }
                });
    }
}

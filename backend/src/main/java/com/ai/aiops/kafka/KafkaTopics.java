package com.ai.aiops.kafka;

public final class KafkaTopics {

    public static final String LOG_EVENTS = "log-events";
    public static final String LOG_EVENTS_DLT = "log-events.dlt";
    public static final String ANOMALY_EVENTS = "anomaly-events";
    public static final String INCIDENT_EVENTS = "incident-events";

    private KafkaTopics() {
    }
}

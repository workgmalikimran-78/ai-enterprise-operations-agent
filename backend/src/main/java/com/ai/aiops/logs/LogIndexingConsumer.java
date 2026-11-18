package com.ai.aiops.logs;

import java.io.UncheckedIOException;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.ai.aiops.kafka.KafkaTopics;
import com.ai.aiops.kafka.LogEventMessage;
import com.ai.aiops.opensearch.OpenSearchIndexingService;

/**
 * Decouples OpenSearch availability from the ingest path: this consumer, not the REST controller,
 * performs the search-index write, so a slow/down OpenSearch cluster never blocks log ingestion.
 * Failures here are retried and ultimately routed to log-events.dlt by the container's
 * DefaultErrorHandler (see KafkaConsumerConfig).
 */
@Component
public class LogIndexingConsumer {

    private final OpenSearchIndexingService indexingService;

    public LogIndexingConsumer(OpenSearchIndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @KafkaListener(topics = KafkaTopics.LOG_EVENTS, groupId = "log-indexer")
    public void onLogEvent(LogEventMessage message) {
        try {
            indexingService.index(message);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

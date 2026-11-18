package com.ai.aiops.opensearch;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.stereotype.Service;

import com.ai.aiops.kafka.LogEventMessage;

@Service
public class OpenSearchIndexingService {

    private static final DateTimeFormatter MONTHLY_INDEX_SUFFIX = DateTimeFormatter.ofPattern("yyyy.MM")
            .withZone(ZoneOffset.UTC);

    private final OpenSearchClient client;

    public OpenSearchIndexingService(OpenSearchClient client) {
        this.client = client;
    }

    public void index(LogEventMessage message) throws IOException {
        String indexName = "log-events-" + MONTHLY_INDEX_SUFFIX.format(message.timestamp());
        client.index(i -> i
                .index(indexName)
                .id(message.logEventId().toString())
                .document(LogSearchDocument.from(message)));
    }
}

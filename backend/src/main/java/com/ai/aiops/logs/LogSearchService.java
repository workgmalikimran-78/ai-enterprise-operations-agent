package com.ai.aiops.logs;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.aiops.api.dto.LogEventResponse;
import com.ai.aiops.api.dto.PagedResponse;
import com.ai.aiops.common.exception.ResourceNotFoundException;
import com.ai.aiops.domain.LogEvent;
import com.ai.aiops.opensearch.LogSearchDocument;
import com.ai.aiops.repository.LogEventRepository;

@Service
public class LogSearchService {

    private final OpenSearchClient client;
    private final LogEventRepository logEventRepository;

    public LogSearchService(OpenSearchClient client, LogEventRepository logEventRepository) {
        this.client = client;
        this.logEventRepository = logEventRepository;
    }

    @Transactional(readOnly = true)
    public LogEventResponse getById(UUID id) {
        LogEvent event = logEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Log event not found: " + id));
        return LogEventResponse.from(event);
    }

    public PagedResponse<LogEventResponse> search(String service, String environment, String level,
                                                    String traceId, Instant from, Instant to, String q,
                                                    int page, int size) throws IOException {
        List<Query> filters = new ArrayList<>();
        if (service != null) {
            filters.add(Query.of(b -> b.term(t -> t.field("service").value(v -> v.stringValue(service)))));
        }
        if (environment != null) {
            filters.add(Query.of(b -> b.term(t -> t.field("environment").value(v -> v.stringValue(environment)))));
        }
        if (level != null) {
            filters.add(Query.of(b -> b.term(t -> t.field("level").value(v -> v.stringValue(level)))));
        }
        if (traceId != null) {
            filters.add(Query.of(b -> b.term(t -> t.field("traceId").value(v -> v.stringValue(traceId)))));
        }
        if (from != null || to != null) {
            filters.add(Query.of(b -> b.range(r -> {
                r.field("timestamp");
                if (from != null) {
                    r.gte(org.opensearch.client.json.JsonData.of(from.toString()));
                }
                if (to != null) {
                    r.lte(org.opensearch.client.json.JsonData.of(to.toString()));
                }
                return r;
            })));
        }
        if (q != null && !q.isBlank()) {
            filters.add(Query.of(b -> b.match(m -> m.field("message").query(v -> v.stringValue(q)))));
        }

        BoolQuery boolQuery = new BoolQuery.Builder().filter(filters).build();

        SearchResponse<LogSearchDocument> response = client.search(s -> s
                        .index("log-events-*")
                        .query(Query.of(qb -> qb.bool(boolQuery)))
                        .from(page * size)
                        .size(size)
                        .sort(sort -> sort.field(f -> f.field("timestamp").order(
                                org.opensearch.client.opensearch._types.SortOrder.Desc))),
                LogSearchDocument.class);

        List<LogEventResponse> content = response.hits().hits().stream()
                .map(hit -> hit.source())
                .filter(java.util.Objects::nonNull)
                .map(doc -> new LogEventResponse(
                        doc.logEventId(), doc.timestamp(), doc.service(), doc.environment(), doc.level(),
                        doc.traceId(), doc.message(), doc.exceptionType(), doc.exceptionMessage(), doc.timestamp()))
                .toList();

        long total = response.hits().total() != null ? response.hits().total().value() : content.size();
        return new PagedResponse<>(content, page, size, total);
    }
}

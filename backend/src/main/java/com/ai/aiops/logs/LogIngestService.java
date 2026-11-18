package com.ai.aiops.logs;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.aiops.api.dto.LogEventResponse;
import com.ai.aiops.api.dto.LogIngestRequest;
import com.ai.aiops.domain.Environment;
import com.ai.aiops.domain.LogEvent;
import com.ai.aiops.kafka.LogEventMessage;
import com.ai.aiops.kafka.LogEventProducer;
import com.ai.aiops.repository.LogEventRepository;

/**
 * Writes the log row to Postgres synchronously (it's the system of record, needed immediately by
 * the anomaly detectors), then publishes to Kafka for asynchronous OpenSearch indexing. This way
 * a slow/unavailable search cluster never blocks ingestion.
 */
@Service
public class LogIngestService {

    private final LogNormalizer normalizer;
    private final ApplicationResolver applicationResolver;
    private final LogEventRepository logEventRepository;
    private final LogEventProducer logEventProducer;

    public LogIngestService(LogNormalizer normalizer, ApplicationResolver applicationResolver,
                             LogEventRepository logEventRepository, LogEventProducer logEventProducer) {
        this.normalizer = normalizer;
        this.applicationResolver = applicationResolver;
        this.logEventRepository = logEventRepository;
        this.logEventProducer = logEventProducer;
    }

    @Transactional
    public LogEventResponse ingest(LogIngestRequest request) {
        NormalizedLogEvent normalized = normalizer.normalize(request);
        Environment environment = applicationResolver.resolve(normalized.service(), normalized.environment());

        LogEvent event = new LogEvent();
        event.setTenant(environment.getApplication().getTenant());
        event.setApplication(environment.getApplication());
        event.setEnvironment(environment);
        event.setTimestamp(normalized.timestamp());
        event.setService(normalized.service());
        event.setLevel(normalized.level());
        event.setTraceId(normalized.traceId());
        event.setMessage(normalized.message());
        event.setExceptionType(normalized.exceptionType());
        event.setExceptionMessage(normalized.exceptionMessage());

        LogEvent saved = logEventRepository.saveAndFlush(event);

        logEventProducer.publish(new LogEventMessage(
                saved.getId(),
                saved.getApplication().getId(),
                saved.getEnvironment().getId(),
                saved.getTimestamp(),
                saved.getService(),
                environment.getName(),
                saved.getLevel(),
                saved.getTraceId(),
                saved.getMessage(),
                saved.getExceptionType(),
                saved.getExceptionMessage()
        ));

        return LogEventResponse.from(saved);
    }
}

package com.ai.aiops.logs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ai.aiops.api.dto.LogEventResponse;
import com.ai.aiops.api.dto.LogIngestRequest;
import com.ai.aiops.domain.Application;
import com.ai.aiops.domain.Environment;
import com.ai.aiops.domain.LogEvent;
import com.ai.aiops.domain.Tenant;
import com.ai.aiops.kafka.LogEventProducer;
import com.ai.aiops.repository.LogEventRepository;

@ExtendWith(MockitoExtension.class)
class LogIngestServiceTest {

    @Mock
    private ApplicationResolver applicationResolver;
    @Mock
    private LogEventRepository logEventRepository;
    @Mock
    private LogEventProducer logEventProducer;

    private final LogNormalizer normalizer = new LogNormalizer();

    @Test
    void savesToPostgresAndPublishesToKafka() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        Application application = new Application();
        application.setId(UUID.randomUUID());
        application.setTenant(tenant);
        application.setName("order-service");
        Environment environment = new Environment();
        environment.setId(UUID.randomUUID());
        environment.setApplication(application);
        environment.setName("production");

        when(applicationResolver.resolve("order-service", "production")).thenReturn(environment);
        when(logEventRepository.saveAndFlush(any(LogEvent.class))).thenAnswer(invocation -> {
            LogEvent event = invocation.getArgument(0);
            event.setId(UUID.randomUUID());
            return event;
        });

        LogIngestService service = new LogIngestService(
                normalizer, applicationResolver, logEventRepository, logEventProducer);

        LogIngestRequest request = new LogIngestRequest(
                Instant.now(), "order-service", "production", "ERROR", "trace-1",
                "Database connection timeout",
                new LogIngestRequest.ExceptionInfo("SQLTransientConnectionException", "timed out", null));

        LogEventResponse response = service.ingest(request);

        assertThat(response.service()).isEqualTo("order-service");
        assertThat(response.level()).isEqualTo("ERROR");
        verify(logEventRepository).saveAndFlush(any(LogEvent.class));
        verify(logEventProducer).publish(any());
    }
}

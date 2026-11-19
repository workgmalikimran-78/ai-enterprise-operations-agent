package com.ai.aiops.logs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.ai.aiops.api.dto.LogEventResponse;
import com.ai.aiops.api.dto.LogFileUploadResponse;
import com.ai.aiops.api.dto.LogIngestRequest;
import com.ai.aiops.common.exception.ValidationException;

@ExtendWith(MockitoExtension.class)
class LogFileIngestServiceTest {

    private final LogFileParser parser = new LogFileParser();

    @Mock
    private LogIngestService logIngestService;

    @Test
    void parsesAndIngestsEveryEntryThroughTheSameIngestPipeline() {
        LogFileIngestService service = new LogFileIngestService(parser, logIngestService);
        MockMultipartFile file = new MockMultipartFile("file", "app.log", "text/plain",
                ("2026-11-18T10:30:00Z INFO Service started\n2026-11-18T10:30:05Z ERROR DB timeout\n")
                        .getBytes(StandardCharsets.UTF_8));
        when(logIngestService.ingest(org.mockito.ArgumentMatchers.any(LogIngestRequest.class)))
                .thenReturn(new LogEventResponse(null, Instant.now(), "order-service", "production", "INFO",
                        null, null, null, null, Instant.now()));

        LogFileUploadResponse response = service.upload(file, "order-service", "production");

        assertThat(response.entriesParsed()).isEqualTo(2);
        assertThat(response.entriesIngested()).isEqualTo(2);
        assertThat(response.entriesFailed()).isZero();
        assertThat(response.errors()).isEmpty();
    }

    @Test
    void collectsPerEntryFailuresWithoutAbortingTheRestOfTheUpload() {
        LogFileIngestService service = new LogFileIngestService(parser, logIngestService);
        MockMultipartFile file = new MockMultipartFile("file", "app.log", "text/plain",
                "INFO ok entry\nERROR bad entry\n".getBytes(StandardCharsets.UTF_8));
        when(logIngestService.ingest(org.mockito.ArgumentMatchers.any(LogIngestRequest.class)))
                .thenReturn(new LogEventResponse(null, Instant.now(), "order-service", "production", "INFO",
                        null, null, null, null, Instant.now()))
                .thenThrow(new ValidationException("boom"));

        LogFileUploadResponse response = service.upload(file, "order-service", "production");

        assertThat(response.entriesParsed()).isEqualTo(2);
        assertThat(response.entriesIngested()).isEqualTo(1);
        assertThat(response.entriesFailed()).isEqualTo(1);
        assertThat(response.errors()).containsExactly("boom");
    }

    @Test
    void rejectsEmptyFile() {
        LogFileIngestService service = new LogFileIngestService(parser, logIngestService);
        MockMultipartFile emptyFile = new MockMultipartFile("file", "app.log", "text/plain", new byte[0]);

        assertThatThrownBy(() -> service.upload(emptyFile, "order-service", "production"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsMissingServiceOrEnvironment() {
        LogFileIngestService service = new LogFileIngestService(parser, logIngestService);
        MockMultipartFile file = new MockMultipartFile("file", "app.log", "text/plain",
                "INFO hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.upload(file, "", "production"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.upload(file, "order-service", " "))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsFilesPastTheLineLimit() {
        LogFileIngestService service = new LogFileIngestService(parser, logIngestService);
        String content = "INFO line\n".repeat(20_001);
        MockMultipartFile file = new MockMultipartFile("file", "app.log", "text/plain",
                content.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.upload(file, "order-service", "production"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("20001");
    }
}

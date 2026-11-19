package com.ai.aiops.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.ai.aiops.api.dto.LogEventResponse;
import com.ai.aiops.api.dto.LogFileUploadResponse;
import com.ai.aiops.logs.LogFileIngestService;
import com.ai.aiops.logs.LogIngestService;
import com.ai.aiops.logs.LogSearchService;
import com.ai.aiops.security.SecurityConfig;

@WebMvcTest(LogController.class)
@org.springframework.context.annotation.Import(SecurityConfig.class)
class LogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LogIngestService logIngestService;
    @MockBean
    private LogSearchService logSearchService;
    @MockBean
    private LogFileIngestService logFileIngestService;

    @Test
    void ingestReturns201OnValidPayload() throws Exception {
        LogEventResponse response = new LogEventResponse(
                UUID.randomUUID(), Instant.now(), "order-service", "production", "ERROR",
                "trace-1", "Database connection timeout", "SQLTransientConnectionException", "timed out",
                Instant.now());
        when(logIngestService.ingest(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/logs/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "service": "order-service",
                                  "environment": "production",
                                  "level": "ERROR",
                                  "traceId": "trace-1",
                                  "message": "Database connection timeout",
                                  "exception": { "type": "SQLTransientConnectionException" }
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void ingestReturns400WhenRequiredFieldMissing() throws Exception {
        mockMvc.perform(post("/api/v1/logs/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "environment": "production",
                                  "level": "ERROR",
                                  "message": "missing service field"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadReturns200WithIngestSummaryForValidFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "app.log", "text/plain",
                "ERROR something broke".getBytes(StandardCharsets.UTF_8));
        when(logFileIngestService.upload(any(), anyString(), anyString()))
                .thenReturn(new LogFileUploadResponse(1, 1, 0, List.of()));

        mockMvc.perform(multipart("/api/v1/logs/upload")
                        .file(file)
                        .param("service", "order-service")
                        .param("environment", "production"))
                .andExpect(status().isOk());
    }
}

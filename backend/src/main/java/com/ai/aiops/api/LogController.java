package com.ai.aiops.api;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ai.aiops.api.dto.LogEventResponse;
import com.ai.aiops.api.dto.LogIngestRequest;
import com.ai.aiops.api.dto.PagedResponse;
import com.ai.aiops.logs.LogIngestService;
import com.ai.aiops.logs.LogSearchService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/logs")
public class LogController {

    private final LogIngestService logIngestService;
    private final LogSearchService logSearchService;

    public LogController(LogIngestService logIngestService, LogSearchService logSearchService) {
        this.logIngestService = logIngestService;
        this.logSearchService = logSearchService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<LogEventResponse> ingest(@Valid @RequestBody LogIngestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(logIngestService.ingest(request));
    }

    @GetMapping("/search")
    public PagedResponse<LogEventResponse> search(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws IOException {
        return logSearchService.search(service, environment, level, traceId, from, to, q, page, size);
    }

    @GetMapping("/{id}")
    public LogEventResponse getById(@PathVariable UUID id) {
        return logSearchService.getById(id);
    }
}

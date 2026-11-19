package com.ai.aiops.logs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.ai.aiops.api.dto.LogFileUploadResponse;
import com.ai.aiops.api.dto.LogIngestRequest;
import com.ai.aiops.common.exception.ValidationException;

/**
 * Bulk-ingests an uploaded application log file by parsing it into individual
 * {@link LogIngestRequest}s and running each through the same {@link LogIngestService#ingest}
 * path a single API-submitted log event takes - same validation, same Postgres write, same Kafka
 * publish that feeds the anomaly detectors. One bad entry doesn't abort the rest: each line group
 * is ingested independently and failures are collected for the response.
 */
@Service
public class LogFileIngestService {

    /** Bounds memory/ingestion time for a single upload; a file past this should be split. */
    private static final int MAX_LINES = 20_000;
    private static final int MAX_REPORTED_ERRORS = 20;

    private final LogFileParser parser;
    private final LogIngestService logIngestService;

    public LogFileIngestService(LogFileParser parser, LogIngestService logIngestService) {
        this.parser = parser;
        this.logIngestService = logIngestService;
    }

    public LogFileUploadResponse upload(MultipartFile file, String service, String environment) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Uploaded log file is empty");
        }
        if (service == null || service.isBlank() || environment == null || environment.isBlank()) {
            throw new ValidationException("service and environment are required");
        }

        List<String> lines = readLines(file);
        if (lines.size() > MAX_LINES) {
            throw new ValidationException("Log file has " + lines.size() + " lines, exceeding the "
                    + MAX_LINES + "-line limit per upload - please split it into smaller files");
        }

        List<LogIngestRequest> entries = parser.parse(lines, service, environment);

        int ingested = 0;
        List<String> errors = new ArrayList<>();
        for (LogIngestRequest entry : entries) {
            try {
                logIngestService.ingest(entry);
                ingested++;
            } catch (RuntimeException e) {
                if (errors.size() < MAX_REPORTED_ERRORS) {
                    errors.add(e.getMessage());
                }
            }
        }

        return new LogFileUploadResponse(entries.size(), ingested, entries.size() - ingested, errors);
    }

    private List<String> readLines(MultipartFile file) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            throw new ValidationException("Failed to read uploaded log file: " + e.getMessage());
        }
        return lines;
    }
}

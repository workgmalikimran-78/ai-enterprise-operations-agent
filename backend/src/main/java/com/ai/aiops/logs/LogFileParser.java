package com.ai.aiops.logs;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.ai.aiops.api.dto.LogIngestRequest;

/**
 * Parses a raw application log file into a list of {@link LogIngestRequest}s, reusing the exact
 * same ingest path (validation, normalization, persistence, anomaly detection) as the single-event
 * API. Deliberately Spring/persistence-free beyond the {@code @Component} annotation, so the
 * parsing rules can be unit tested without any infrastructure.
 *
 * <p>Format assumed: one log entry starts per line matching an optional timestamp followed by a
 * level token (e.g. {@code 2024-01-01T10:15:30.123Z ERROR Connection timeout}, or just
 * {@code ERROR Connection timeout}). Any line that doesn't match a new entry - most commonly a
 * Java stack trace frame ("at ...", "Caused by: ...") - is treated as a continuation of the
 * previous entry rather than a new one, since that's how real application logs are formatted.
 */
@Component
public class LogFileParser {

    private static final Map<String, String> LEVEL_ALIASES = Map.of(
            "WARNING", "WARN",
            "SEVERE", "ERROR"
    );

    private static final Pattern NEW_ENTRY_PATTERN = Pattern.compile(
            "^(?:\\[?(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?(?:Z|[+-]\\d{2}:?\\d{2})?)\\]?\\s+)?"
                    + "\\[?(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|SEVERE)\\]?\\s*[:-]?\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EXCEPTION_TYPE_PATTERN = Pattern.compile(
            "([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+(?:Exception|Error))(?::\\s*(.*))?");

    public List<LogIngestRequest> parse(List<String> lines, String service, String environment) {
        List<LogIngestRequest> entries = new ArrayList<>();
        PendingEntry current = null;

        for (String rawLine : lines) {
            if (rawLine.isBlank()) {
                continue;
            }
            Matcher matcher = NEW_ENTRY_PATTERN.matcher(rawLine);
            if (matcher.matches()) {
                if (current != null) {
                    entries.add(current.toRequest(service, environment));
                }
                current = new PendingEntry(parseTimestamp(matcher.group(1)),
                        normalizeLevel(matcher.group(2)), matcher.group(3));
            } else if (current != null) {
                current.continuationLines.add(rawLine.strip());
            } else {
                current = new PendingEntry(null, "INFO", rawLine.strip());
            }
        }
        if (current != null) {
            entries.add(current.toRequest(service, environment));
        }
        return entries;
    }

    private String normalizeLevel(String level) {
        String upper = level.toUpperCase();
        return LEVEL_ALIASES.getOrDefault(upper, upper);
    }

    private Instant parseTimestamp(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
            // Falls through to the space-separated, offset-less formats below.
        }
        String normalized = raw.replace(',', '.').replace(' ', 'T');
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))) {
            try {
                return LocalDateTime.parse(normalized, formatter).toInstant(ZoneOffset.UTC);
            } catch (DateTimeException ignored) {
                // Try the next formatter; falling back to "now" (via LogNormalizer) is acceptable.
            }
        }
        return null;
    }

    private static final class PendingEntry {
        private final Instant timestamp;
        private final String level;
        private final String firstLine;
        private final List<String> continuationLines = new ArrayList<>();

        private PendingEntry(Instant timestamp, String level, String firstLine) {
            this.timestamp = timestamp;
            this.level = level;
            this.firstLine = firstLine;
        }

        private LogIngestRequest toRequest(String service, String environment) {
            String message = continuationLines.isEmpty() ? firstLine
                    : firstLine + "\n" + String.join("\n", continuationLines);

            LogIngestRequest.ExceptionInfo exception = null;
            if ("ERROR".equals(level) || "FATAL".equals(level)) {
                exception = extractException();
            }

            return new LogIngestRequest(timestamp, service, environment, level, null, message, exception);
        }

        private LogIngestRequest.ExceptionInfo extractException() {
            Matcher matcher = findExceptionMatch(firstLine);
            String source = firstLine;
            if (matcher == null && !continuationLines.isEmpty()) {
                source = continuationLines.get(0);
                matcher = findExceptionMatch(source);
            }
            if (matcher == null) {
                return null;
            }
            String type = matcher.group(1);
            String message = matcher.group(2) != null ? matcher.group(2) : source;
            String stackTrace = continuationLines.isEmpty() ? null : String.join("\n", continuationLines);
            return new LogIngestRequest.ExceptionInfo(type, message, stackTrace);
        }

        private Matcher findExceptionMatch(String source) {
            Matcher matcher = EXCEPTION_TYPE_PATTERN.matcher(source);
            return matcher.find() ? matcher : null;
        }
    }
}

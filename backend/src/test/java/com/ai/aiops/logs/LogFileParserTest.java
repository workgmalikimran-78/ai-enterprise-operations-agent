package com.ai.aiops.logs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ai.aiops.api.dto.LogIngestRequest;

class LogFileParserTest {

    private final LogFileParser parser = new LogFileParser();

    @Test
    void parsesOneEntryPerTimestampedLevelLine() {
        List<String> lines = List.of(
                "2026-11-18T10:30:00.123Z INFO Service started",
                "2026-11-18T10:30:05.000Z WARN Slow response from downstream");

        List<LogIngestRequest> entries = parser.parse(lines, "order-service", "production");

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).timestamp()).isEqualTo(Instant.parse("2026-11-18T10:30:00.123Z"));
        assertThat(entries.get(0).level()).isEqualTo("INFO");
        assertThat(entries.get(0).message()).isEqualTo("Service started");
        assertThat(entries.get(1).level()).isEqualTo("WARN");
    }

    @Test
    void appendsNonMatchingLinesAsStackTraceContinuationOfPreviousEntry() {
        List<String> lines = List.of(
                "2026-11-18T10:30:00Z ERROR Database connection timeout",
                "java.sql.SQLTransientConnectionException: timed out waiting for connection",
                "\tat com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:200)",
                "\tat com.ai.aiops.OrderService.save(OrderService.java:42)");

        List<LogIngestRequest> entries = parser.parse(lines, "order-service", "production");

        assertThat(entries).hasSize(1);
        LogIngestRequest entry = entries.get(0);
        assertThat(entry.level()).isEqualTo("ERROR");
        assertThat(entry.message()).contains("Database connection timeout").contains("HikariPool");
        assertThat(entry.exception()).isNotNull();
        assertThat(entry.exception().type()).isEqualTo("java.sql.SQLTransientConnectionException");
        assertThat(entry.exception().message()).isEqualTo("timed out waiting for connection");
        assertThat(entry.exception().stackTrace()).contains("HikariPool.getConnection");
    }

    @Test
    void mapsCommonLevelAliasesToCanonicalLevels() {
        List<String> lines = List.of("WARNING disk usage high", "SEVERE out of memory");

        List<LogIngestRequest> entries = parser.parse(lines, "order-service", "production");

        assertThat(entries).extracting(LogIngestRequest::level).containsExactly("WARN", "ERROR");
    }

    @Test
    void handlesPlainLevelMessageLineWithNoTimestamp() {
        List<String> lines = List.of("ERROR Something broke");

        List<LogIngestRequest> entries = parser.parse(lines, "order-service", "production");

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).timestamp()).isNull();
        assertThat(entries.get(0).level()).isEqualTo("ERROR");
        assertThat(entries.get(0).message()).isEqualTo("Something broke");
    }

    @Test
    void treatsLeadingUnrecognizedLineAsAnInfoEntryRatherThanDroppingIt() {
        List<String> lines = List.of("some free-form line with no level token at all");

        List<LogIngestRequest> entries = parser.parse(lines, "order-service", "production");

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).level()).isEqualTo("INFO");
        assertThat(entries.get(0).message()).isEqualTo("some free-form line with no level token at all");
    }

    @Test
    void skipsBlankLines() {
        List<String> lines = List.of("INFO first", "", "   ", "INFO second");

        List<LogIngestRequest> entries = parser.parse(lines, "order-service", "production");

        assertThat(entries).hasSize(2);
    }

    @Test
    void appliesSuppliedServiceAndEnvironmentToEveryEntry() {
        List<String> lines = List.of("INFO a", "ERROR b");

        List<LogIngestRequest> entries = parser.parse(lines, "payments-service", "staging");

        assertThat(entries).allSatisfy(e -> {
            assertThat(e.service()).isEqualTo("payments-service");
            assertThat(e.environment()).isEqualTo("staging");
        });
    }
}

package com.ai.aiops.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.LogEvent;

public interface LogEventRepository extends JpaRepository<LogEvent, UUID> {
    List<LogEvent> findTop20ByApplicationIdAndLevelInAndTimestampGreaterThanEqualOrderByTimestampDesc(
            UUID applicationId, List<String> levels, Instant since);

    long countByApplicationIdAndLevelInAndTimestampBetween(
            UUID applicationId, List<String> levels, Instant from, Instant to);
}

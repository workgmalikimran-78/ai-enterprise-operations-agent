package com.ai.aiops.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.LogEvent;

public interface LogEventRepository extends JpaRepository<LogEvent, UUID> {
}

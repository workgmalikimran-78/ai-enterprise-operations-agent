package com.ai.aiops.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.RemediationExecution;

public interface RemediationExecutionRepository extends JpaRepository<RemediationExecution, UUID> {
}

package com.ai.aiops.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.RemediationExecution;

public interface RemediationExecutionRepository extends JpaRepository<RemediationExecution, UUID> {
    Optional<RemediationExecution> findByIdempotencyKey(String idempotencyKey);

    Optional<RemediationExecution> findFirstByRemediationRecommendationIdOrderByCreatedAtDesc(UUID recommendationId);

    List<RemediationExecution> findByStatusAndVerifiedAtIsNullAndCompletedAtBefore(String status, Instant cutoff);
}

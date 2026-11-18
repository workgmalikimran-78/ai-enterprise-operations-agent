package com.ai.aiops.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.RemediationRecommendation;

public interface RemediationRecommendationRepository extends JpaRepository<RemediationRecommendation, UUID> {
    List<RemediationRecommendation> findByRootCauseAnalysisIncidentIdOrderByCreatedAtDesc(UUID incidentId);
}

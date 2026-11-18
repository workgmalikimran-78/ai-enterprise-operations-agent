package com.ai.aiops.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.RootCauseAnalysis;

public interface RootCauseAnalysisRepository extends JpaRepository<RootCauseAnalysis, UUID> {
}

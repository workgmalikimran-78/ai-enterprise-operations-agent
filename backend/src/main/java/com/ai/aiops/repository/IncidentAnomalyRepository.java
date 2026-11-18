package com.ai.aiops.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.IncidentAnomaly;
import com.ai.aiops.domain.IncidentAnomalyId;

public interface IncidentAnomalyRepository extends JpaRepository<IncidentAnomaly, IncidentAnomalyId> {
}

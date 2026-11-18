package com.ai.aiops.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.IncidentAnomaly;
import com.ai.aiops.domain.IncidentAnomalyId;

public interface IncidentAnomalyRepository extends JpaRepository<IncidentAnomaly, IncidentAnomalyId> {
    List<IncidentAnomaly> findByIncidentId(UUID incidentId);
}

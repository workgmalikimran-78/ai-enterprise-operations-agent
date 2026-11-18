package com.ai.aiops.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.Anomaly;

public interface AnomalyRepository extends JpaRepository<Anomaly, UUID> {
    List<Anomaly> findByApplicationIdAndStatus(UUID applicationId, String status);
}

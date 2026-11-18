package com.ai.aiops.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ai.aiops.domain.Anomaly;

public interface AnomalyRepository extends JpaRepository<Anomaly, UUID> {
    List<Anomaly> findByApplicationIdAndStatus(UUID applicationId, String status);

    long countByStatus(String status);

    @Query("select a.detectionType, count(a) from Anomaly a group by a.detectionType")
    List<Object[]> countGroupedByDetectionType();

    // Mean time to detect: how long after the anomaly's window started was it actually recorded.
    @Query(value = "select avg(extract(epoch from (created_at - window_start))) from anomalies",
            nativeQuery = true)
    Double averageDetectionSeconds();
}

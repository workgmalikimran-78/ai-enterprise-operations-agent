package com.ai.aiops.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ai.aiops.domain.Incident;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    List<Incident> findByApplicationIdAndStatusOrderByOpenedAtDesc(UUID applicationId, String status);

    Page<Incident> findByStatus(String status, Pageable pageable);

    List<Incident> findByApplicationIdAndStatusInOrderByOpenedAtDesc(UUID applicationId, List<String> statuses);

    long countByStatus(String status);

    long countBySeverity(String severity);

    long countBySeverityAndStatusNot(String severity, String status);

    @Query("select i.severity, count(i) from Incident i group by i.severity")
    List<Object[]> countGroupedBySeverity();

    @Query("select i.status, count(i) from Incident i group by i.status")
    List<Object[]> countGroupedByStatus();

    @Query(value = "select avg(extract(epoch from (resolved_at - opened_at))) from incidents "
            + "where resolved_at is not null", nativeQuery = true)
    Double averageResolutionSeconds();
}

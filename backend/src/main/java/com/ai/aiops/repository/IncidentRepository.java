package com.ai.aiops.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.Incident;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    List<Incident> findByApplicationIdAndStatusOrderByOpenedAtDesc(UUID applicationId, String status);

    Page<Incident> findByStatus(String status, Pageable pageable);

    List<Incident> findByApplicationIdAndStatusInOrderByOpenedAtDesc(UUID applicationId, List<String> statuses);
}

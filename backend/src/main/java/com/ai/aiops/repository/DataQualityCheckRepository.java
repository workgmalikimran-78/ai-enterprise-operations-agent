package com.ai.aiops.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.DataQualityCheck;

public interface DataQualityCheckRepository extends JpaRepository<DataQualityCheck, UUID> {
    Page<DataQualityCheck> findAllByOrderByRunAtDesc(Pageable pageable);

    List<DataQualityCheck> findByEtlRunId(UUID etlRunId);

    long countByEtlRunIdAndStatus(UUID etlRunId, String status);
}

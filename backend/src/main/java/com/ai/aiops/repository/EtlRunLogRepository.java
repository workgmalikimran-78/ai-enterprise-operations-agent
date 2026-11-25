package com.ai.aiops.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.EtlRunLog;

public interface EtlRunLogRepository extends JpaRepository<EtlRunLog, UUID> {
    Optional<EtlRunLog> findFirstByJobNameAndStatusOrderByWatermarkToDesc(String jobName, String status);

    Page<EtlRunLog> findAllByOrderByStartedAtDesc(Pageable pageable);
}

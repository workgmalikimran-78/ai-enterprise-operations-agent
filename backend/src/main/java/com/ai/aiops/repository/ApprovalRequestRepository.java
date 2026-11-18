package com.ai.aiops.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.ApprovalRequest;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {
    Page<ApprovalRequest> findByStatus(String status, Pageable pageable);

    List<ApprovalRequest> findByStatusAndExpiresAtBefore(String status, Instant cutoff);
}

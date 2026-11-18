package com.ai.aiops.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.Runbook;

public interface RunbookRepository extends JpaRepository<Runbook, UUID> {
    List<Runbook> findByTenantIdAndActiveTrue(UUID tenantId);
}

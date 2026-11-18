package com.ai.aiops.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.OperationalAudit;

public interface OperationalAuditRepository extends JpaRepository<OperationalAudit, UUID> {
}

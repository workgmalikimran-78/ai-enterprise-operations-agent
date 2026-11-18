package com.ai.aiops.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.Application;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    Optional<Application> findByTenantIdAndSlug(UUID tenantId, String slug);
}

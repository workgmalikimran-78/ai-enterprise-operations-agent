package com.ai.aiops.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.Environment;

public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {
    Optional<Environment> findByApplicationIdAndName(UUID applicationId, String name);
}

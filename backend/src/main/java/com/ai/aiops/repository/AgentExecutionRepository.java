package com.ai.aiops.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ai.aiops.domain.AgentExecution;

public interface AgentExecutionRepository extends JpaRepository<AgentExecution, UUID> {
}

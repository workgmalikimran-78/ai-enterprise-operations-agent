package com.ai.aiops.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ai.aiops.domain.AgentExecution;

public interface AgentExecutionRepository extends JpaRepository<AgentExecution, UUID> {
    long countByAgentType(String agentType);

    long countByAgentTypeAndStatus(String agentType, String status);

    @Query("select e.agentType, count(e) from AgentExecution e group by e.agentType")
    List<Object[]> countGroupedByAgentType();

    Page<AgentExecution> findByIncidentIdOrderByCreatedAtDesc(UUID incidentId, Pageable pageable);
}

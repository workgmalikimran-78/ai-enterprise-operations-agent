package com.ai.aiops.dashboard;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.aiops.api.dto.IncidentResponse;
import com.ai.aiops.common.exception.ResourceNotFoundException;
import com.ai.aiops.repository.IncidentRepository;

/**
 * Wraps incident reads in a transaction so IncidentResponse.from() can safely traverse the lazy
 * Application association - without this, mapping to the DTO outside a Hibernate session throws
 * LazyInitializationException.
 */
@Service
public class IncidentQueryService {

    private final IncidentRepository incidentRepository;

    public IncidentQueryService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    @Transactional(readOnly = true)
    public Page<IncidentResponse> list(String status, Pageable pageable) {
        Page<com.ai.aiops.domain.Incident> incidents = status != null
                ? incidentRepository.findByStatus(status, pageable)
                : incidentRepository.findAll(pageable);
        return incidents.map(IncidentResponse::from);
    }

    @Transactional(readOnly = true)
    public IncidentResponse getById(UUID id) {
        return incidentRepository.findById(id)
                .map(IncidentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + id));
    }
}

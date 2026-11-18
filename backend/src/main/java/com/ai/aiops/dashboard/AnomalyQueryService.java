package com.ai.aiops.dashboard;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.aiops.api.dto.AnomalyResponse;
import com.ai.aiops.repository.AnomalyRepository;

/** Wraps anomaly reads in a transaction - see IncidentQueryService for why this is necessary. */
@Service
public class AnomalyQueryService {

    private final AnomalyRepository anomalyRepository;

    public AnomalyQueryService(AnomalyRepository anomalyRepository) {
        this.anomalyRepository = anomalyRepository;
    }

    @Transactional(readOnly = true)
    public Page<AnomalyResponse> list(String status, Pageable pageable) {
        Page<com.ai.aiops.domain.Anomaly> anomalies = status != null
                ? anomalyRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : anomalyRepository.findAllByOrderByCreatedAtDesc(pageable);
        return anomalies.map(AnomalyResponse::from);
    }
}

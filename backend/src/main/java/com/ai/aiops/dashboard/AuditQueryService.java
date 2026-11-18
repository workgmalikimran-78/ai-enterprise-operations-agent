package com.ai.aiops.dashboard;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.aiops.api.dto.OperationalAuditResponse;
import com.ai.aiops.repository.OperationalAuditRepository;

/** Wraps audit reads in a transaction - OperationalAuditResponse.from() reads the lazy actorUser association. */
@Service
public class AuditQueryService {

    private final OperationalAuditRepository operationalAuditRepository;

    public AuditQueryService(OperationalAuditRepository operationalAuditRepository) {
        this.operationalAuditRepository = operationalAuditRepository;
    }

    @Transactional(readOnly = true)
    public Page<OperationalAuditResponse> list(Pageable pageable) {
        return operationalAuditRepository.findAll(pageable).map(OperationalAuditResponse::from);
    }
}

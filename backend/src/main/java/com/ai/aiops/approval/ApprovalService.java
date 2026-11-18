package com.ai.aiops.approval;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.aiops.api.dto.ApprovalResponse;
import com.ai.aiops.common.exception.ResourceNotFoundException;
import com.ai.aiops.common.exception.ValidationException;
import com.ai.aiops.domain.ApprovalRequest;
import com.ai.aiops.domain.OperationalAudit;
import com.ai.aiops.domain.RemediationRecommendation;
import com.ai.aiops.domain.User;
import com.ai.aiops.repository.ApprovalRequestRepository;
import com.ai.aiops.repository.OperationalAuditRepository;
import com.ai.aiops.repository.RemediationRecommendationRepository;

/**
 * Human-in-the-loop approval lifecycle: AI proposal -> PENDING -> APPROVED/REJECTED/EXPIRED/
 * CANCELLED. Every decision transition requires the request to currently be PENDING and not
 * expired, which is what prevents double-approval/duplicate-execution at this layer (Phase 6's
 * execution step adds its own idempotency key on top of this).
 */
@Service
public class ApprovalService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final RemediationRecommendationRepository recommendationRepository;
    private final UserResolver userResolver;
    private final OperationalAuditRepository auditRepository;
    private final Duration approvalTtl;

    public ApprovalService(ApprovalRequestRepository approvalRequestRepository,
                            RemediationRecommendationRepository recommendationRepository,
                            UserResolver userResolver, OperationalAuditRepository auditRepository,
                            @Value("${aiops.approval.ttl-hours:24}") long ttlHours) {
        this.approvalRequestRepository = approvalRequestRepository;
        this.recommendationRepository = recommendationRepository;
        this.userResolver = userResolver;
        this.auditRepository = auditRepository;
        this.approvalTtl = Duration.ofHours(ttlHours);
    }

    @Transactional
    public ApprovalRequest createApprovalRequest(RemediationRecommendation recommendation) {
        ApprovalRequest request = new ApprovalRequest();
        request.setRemediationRecommendation(recommendation);
        request.setStatus("PENDING");
        request.setExpiresAt(Instant.now().plus(approvalTtl));
        return approvalRequestRepository.save(request);
    }

    @Transactional
    public ApprovalResponse approve(UUID approvalId, String approverEmail) {
        ApprovalRequest request = requirePending(approvalId);
        User approver = userResolver.resolve(approverEmail);

        request.setStatus("APPROVED");
        request.setDecidedBy(approver);
        request.setDecidedAt(Instant.now());
        ApprovalRequest saved = approvalRequestRepository.save(request);

        transitionRecommendation(request.getRemediationRecommendation(), "APPROVED");
        audit(approver, "APPROVAL_GRANTED", saved);

        return ApprovalResponse.from(saved);
    }

    @Transactional
    public ApprovalResponse reject(UUID approvalId, String approverEmail, String reason) {
        ApprovalRequest request = requirePending(approvalId);
        User approver = userResolver.resolve(approverEmail);

        request.setStatus("REJECTED");
        request.setDecidedBy(approver);
        request.setDecidedAt(Instant.now());
        request.setRejectionReason(reason);
        ApprovalRequest saved = approvalRequestRepository.save(request);

        transitionRecommendation(request.getRemediationRecommendation(), "REJECTED");
        audit(approver, "APPROVAL_REJECTED", saved);

        return ApprovalResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<ApprovalResponse> list(String status, Pageable pageable) {
        Page<ApprovalRequest> page = status != null
                ? approvalRequestRepository.findByStatus(status, pageable)
                : approvalRequestRepository.findAll(pageable);
        return page.map(ApprovalResponse::from);
    }

    /** Runs hourly - PENDING requests past their expiry are moved to EXPIRED so they can no longer be decided. */
    @Scheduled(fixedRateString = "${aiops.approval.expiry-check-minutes:60}", timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    @Transactional
    public void expireStalePendingApprovals() {
        approvalRequestRepository.findByStatusAndExpiresAtBefore("PENDING", Instant.now())
                .forEach(request -> {
                    request.setStatus("EXPIRED");
                    approvalRequestRepository.save(request);
                    transitionRecommendation(request.getRemediationRecommendation(), "REJECTED");
                });
    }

    private ApprovalRequest requirePending(UUID approvalId) {
        ApprovalRequest request = approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval request not found: " + approvalId));

        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(Instant.now())
                && "PENDING".equals(request.getStatus())) {
            request.setStatus("EXPIRED");
            approvalRequestRepository.save(request);
        }

        if (!"PENDING".equals(request.getStatus())) {
            throw new ValidationException("Approval request " + approvalId
                    + " is not pending (current status: " + request.getStatus() + ")");
        }
        return request;
    }

    private void transitionRecommendation(RemediationRecommendation recommendation, String status) {
        recommendation.setStatus(status);
        recommendationRepository.save(recommendation);
    }

    private void audit(User actor, String action, ApprovalRequest request) {
        OperationalAudit auditEntry = new OperationalAudit();
        auditEntry.setTenant(actor.getTenant());
        auditEntry.setActorUser(actor);
        auditEntry.setAction(action);
        auditEntry.setEntityType("APPROVAL_REQUEST");
        auditEntry.setEntityId(request.getId());
        auditEntry.setDetails(Map.of(
                "remediationRecommendationId", request.getRemediationRecommendation().getId().toString(),
                "status", request.getStatus()
        ));
        auditRepository.save(auditEntry);
    }
}

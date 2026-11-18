package com.ai.aiops.domain;

import java.time.Instant;

import com.ai.aiops.common.BaseCreatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "remediation_executions")
@Getter
@Setter
@NoArgsConstructor
public class RemediationExecution extends BaseCreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "remediation_recommendation_id")
    private RemediationRecommendation remediationRecommendation;

    /** Null for read-only/diagnostic actions (required_approval_level = NONE), which never need approval. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_request_id")
    private ApprovalRequest approvalRequest;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "k8s_job_ref")
    private String k8sJobRef;

    @Column(name = "result_log", columnDefinition = "text")
    private String resultLog;

    @Column(name = "verified_at")
    private Instant verifiedAt;
}

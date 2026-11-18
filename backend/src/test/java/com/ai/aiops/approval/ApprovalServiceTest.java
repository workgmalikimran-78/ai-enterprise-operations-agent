package com.ai.aiops.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ai.aiops.api.dto.ApprovalResponse;
import com.ai.aiops.common.exception.ValidationException;
import com.ai.aiops.domain.ApprovalRequest;
import com.ai.aiops.domain.OperationalAudit;
import com.ai.aiops.domain.RemediationRecommendation;
import com.ai.aiops.domain.Tenant;
import com.ai.aiops.domain.User;
import com.ai.aiops.repository.ApprovalRequestRepository;
import com.ai.aiops.repository.OperationalAuditRepository;
import com.ai.aiops.repository.RemediationRecommendationRepository;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;
    @Mock
    private RemediationRecommendationRepository recommendationRepository;
    @Mock
    private UserResolver userResolver;
    @Mock
    private OperationalAuditRepository auditRepository;

    private ApprovalService service() {
        return new ApprovalService(approvalRequestRepository, recommendationRepository, userResolver,
                auditRepository, 24);
    }

    private ApprovalRequest pendingRequest(Instant expiresAt) {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        RemediationRecommendation recommendation = new RemediationRecommendation();
        recommendation.setId(UUID.randomUUID());

        ApprovalRequest request = new ApprovalRequest();
        request.setId(UUID.randomUUID());
        request.setRemediationRecommendation(recommendation);
        request.setStatus("PENDING");
        request.setExpiresAt(expiresAt);
        return request;
    }

    private User user() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenant(tenant);
        user.setEmail("ops@example.com");
        return user;
    }

    @Test
    void approveTransitionsRequestAndRecommendationAndWritesAudit() {
        ApprovalRequest request = pendingRequest(Instant.now().plusSeconds(3600));
        when(approvalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(approvalRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(recommendationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userResolver.resolve("ops@example.com")).thenReturn(user());

        ApprovalResponse response = service().approve(request.getId(), "ops@example.com");

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(request.getRemediationRecommendation().getStatus()).isEqualTo("APPROVED");
        org.mockito.Mockito.verify(auditRepository).save(any(OperationalAudit.class));
    }

    @Test
    void rejectStoresReasonAndTransitionsRecommendation() {
        ApprovalRequest request = pendingRequest(Instant.now().plusSeconds(3600));
        when(approvalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(approvalRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(recommendationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userResolver.resolve(anyString())).thenReturn(user());

        ApprovalResponse response = service().reject(request.getId(), "ops@example.com", "too risky right now");

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.rejectionReason()).isEqualTo("too risky right now");
        assertThat(request.getRemediationRecommendation().getStatus()).isEqualTo("REJECTED");
    }

    @Test
    void cannotApproveTwice() {
        ApprovalRequest request = pendingRequest(Instant.now().plusSeconds(3600));
        request.setStatus("APPROVED");
        when(approvalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service().approve(request.getId(), "ops@example.com"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void expiredRequestCannotBeApproved() {
        ApprovalRequest request = pendingRequest(Instant.now().minusSeconds(60));
        when(approvalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(approvalRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service().approve(request.getId(), "ops@example.com"))
                .isInstanceOf(ValidationException.class);
        assertThat(request.getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void scheduledExpiryJobMarksStaleRequestsAndRejectsTheirRecommendation() {
        ApprovalRequest request = pendingRequest(Instant.now().minusSeconds(60));
        when(approvalRequestRepository.findByStatusAndExpiresAtBefore(org.mockito.ArgumentMatchers.eq("PENDING"),
                any())).thenReturn(List.of(request));
        when(approvalRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(recommendationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().expireStalePendingApprovals();

        assertThat(request.getStatus()).isEqualTo("EXPIRED");
        assertThat(request.getRemediationRecommendation().getStatus()).isEqualTo("REJECTED");
    }
}

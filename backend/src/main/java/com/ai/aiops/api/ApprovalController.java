package com.ai.aiops.api;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ai.aiops.api.dto.ApprovalDecisionRequest;
import com.ai.aiops.api.dto.ApprovalResponse;
import com.ai.aiops.approval.ApprovalService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    public Page<ApprovalResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return approvalService.list(status, PageRequest.of(page, size));
    }

    @PostMapping("/{id}/approve")
    public ApprovalResponse approve(@PathVariable UUID id, @Valid @RequestBody ApprovalDecisionRequest request) {
        return approvalService.approve(id, request.approverEmail());
    }

    @PostMapping("/{id}/reject")
    public ApprovalResponse reject(@PathVariable UUID id, @Valid @RequestBody ApprovalDecisionRequest request) {
        return approvalService.reject(id, request.approverEmail(), request.reason());
    }
}

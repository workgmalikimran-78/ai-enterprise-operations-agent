package com.ai.aiops.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ai.aiops.api.dto.AnomalyResponse;
import com.ai.aiops.dashboard.AnomalyQueryService;

@RestController
@RequestMapping("/api/v1/anomalies")
public class AnomalyController {

    private final AnomalyQueryService anomalyQueryService;

    public AnomalyController(AnomalyQueryService anomalyQueryService) {
        this.anomalyQueryService = anomalyQueryService;
    }

    @GetMapping
    public Page<AnomalyResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return anomalyQueryService.list(status, PageRequest.of(page, size));
    }
}

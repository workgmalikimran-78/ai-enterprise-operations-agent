package com.ai.aiops.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ai.aiops.api.dto.DataCatalogEntryResponse;
import com.ai.aiops.api.dto.DataQualityCheckResponse;
import com.ai.aiops.api.dto.EtlRunLogResponse;
import com.ai.aiops.api.dto.FactIncidentDailyResponse;
import com.ai.aiops.api.dto.FactLogVolumeHourlyResponse;
import com.ai.aiops.api.dto.FactRemediationDailyResponse;
import com.ai.aiops.warehouse.WarehouseEtlJob;
import com.ai.aiops.warehouse.WarehouseQueryService;

/**
 * Data platform API: the governance catalog, ETL run history, data quality check results, and the
 * warehouse's three fact tables. See ARCHITECTURE.md section 13 for the medallion-layer design
 * this sits on top of.
 */
@RestController
@RequestMapping("/api/v1/warehouse")
public class WarehouseController {

    private final WarehouseQueryService warehouseQueryService;
    private final WarehouseEtlJob warehouseEtlJob;

    public WarehouseController(WarehouseQueryService warehouseQueryService, WarehouseEtlJob warehouseEtlJob) {
        this.warehouseQueryService = warehouseQueryService;
        this.warehouseEtlJob = warehouseEtlJob;
    }

    @GetMapping("/catalog")
    public List<DataCatalogEntryResponse> catalog() {
        return warehouseQueryService.catalog();
    }

    @GetMapping("/quality-checks")
    public Page<DataQualityCheckResponse> qualityChecks(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return warehouseQueryService.qualityChecks(PageRequest.of(page, size));
    }

    @GetMapping("/etl-runs")
    public Page<EtlRunLogResponse> etlRuns(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return warehouseQueryService.etlRuns(PageRequest.of(page, size));
    }

    @PostMapping("/etl/run")
    public EtlRunLogResponse runEtlNow() {
        return EtlRunLogResponse.from(warehouseEtlJob.runNow());
    }

    @GetMapping("/metrics/incidents-daily")
    public List<FactIncidentDailyResponse> incidentsDaily(
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return warehouseQueryService.incidentsDaily(applicationId, defaultFrom(from), defaultTo(to));
    }

    @GetMapping("/metrics/remediation-daily")
    public List<FactRemediationDailyResponse> remediationDaily(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return warehouseQueryService.remediationDaily(defaultFrom(from), defaultTo(to));
    }

    @GetMapping("/metrics/log-volume-hourly")
    public List<FactLogVolumeHourlyResponse> logVolumeHourly(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return warehouseQueryService.logVolumeHourly(defaultFrom(from), defaultTo(to));
    }

    private LocalDate defaultFrom(LocalDate from) {
        return from != null ? from : LocalDate.now().minusDays(30);
    }

    private LocalDate defaultTo(LocalDate to) {
        return to != null ? to : LocalDate.now();
    }
}

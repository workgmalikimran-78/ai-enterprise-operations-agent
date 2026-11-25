package com.ai.aiops.warehouse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.aiops.api.dto.DataCatalogEntryResponse;
import com.ai.aiops.api.dto.DataQualityCheckResponse;
import com.ai.aiops.api.dto.EtlRunLogResponse;
import com.ai.aiops.api.dto.FactIncidentDailyResponse;
import com.ai.aiops.api.dto.FactLogVolumeHourlyResponse;
import com.ai.aiops.api.dto.FactRemediationDailyResponse;
import com.ai.aiops.domain.Application;
import com.ai.aiops.domain.FactIncidentDaily;
import com.ai.aiops.domain.FactLogVolumeHourly;
import com.ai.aiops.domain.FactRemediationDaily;
import com.ai.aiops.repository.ApplicationRepository;
import com.ai.aiops.repository.DataQualityCheckRepository;
import com.ai.aiops.repository.EtlRunLogRepository;
import com.ai.aiops.repository.FactIncidentDailyRepository;
import com.ai.aiops.repository.FactLogVolumeHourlyRepository;
import com.ai.aiops.repository.FactRemediationDailyRepository;

/** Read side for the warehouse/governance API: resolves application names and maps entities to DTOs. */
@Service
public class WarehouseQueryService {

    private final FactIncidentDailyRepository factIncidentDailyRepository;
    private final FactRemediationDailyRepository factRemediationDailyRepository;
    private final FactLogVolumeHourlyRepository factLogVolumeHourlyRepository;
    private final DataQualityCheckRepository dataQualityCheckRepository;
    private final EtlRunLogRepository etlRunLogRepository;
    private final DataCatalogService dataCatalogService;
    private final ApplicationRepository applicationRepository;

    public WarehouseQueryService(FactIncidentDailyRepository factIncidentDailyRepository,
                                  FactRemediationDailyRepository factRemediationDailyRepository,
                                  FactLogVolumeHourlyRepository factLogVolumeHourlyRepository,
                                  DataQualityCheckRepository dataQualityCheckRepository,
                                  EtlRunLogRepository etlRunLogRepository, DataCatalogService dataCatalogService,
                                  ApplicationRepository applicationRepository) {
        this.factIncidentDailyRepository = factIncidentDailyRepository;
        this.factRemediationDailyRepository = factRemediationDailyRepository;
        this.factLogVolumeHourlyRepository = factLogVolumeHourlyRepository;
        this.dataQualityCheckRepository = dataQualityCheckRepository;
        this.etlRunLogRepository = etlRunLogRepository;
        this.dataCatalogService = dataCatalogService;
        this.applicationRepository = applicationRepository;
    }

    @Transactional(readOnly = true)
    public List<DataCatalogEntryResponse> catalog() {
        return dataCatalogService.list().stream().map(DataCatalogEntryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<DataQualityCheckResponse> qualityChecks(Pageable pageable) {
        return dataQualityCheckRepository.findAllByOrderByRunAtDesc(pageable).map(DataQualityCheckResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<EtlRunLogResponse> etlRuns(Pageable pageable) {
        return etlRunLogRepository.findAllByOrderByStartedAtDesc(pageable).map(EtlRunLogResponse::from);
    }

    @Transactional(readOnly = true)
    public List<FactIncidentDailyResponse> incidentsDaily(UUID applicationId, LocalDate from, LocalDate to) {
        List<FactIncidentDaily> facts = applicationId != null
                ? factIncidentDailyRepository.findByApplicationIdAndDateIdBetweenOrderByDateIdAsc(applicationId, from, to)
                : factIncidentDailyRepository.findByDateIdBetweenOrderByDateIdAsc(from, to);
        Map<UUID, String> names = applicationNames(facts.stream().map(FactIncidentDaily::getApplicationId).toList());
        return facts.stream().map(f -> FactIncidentDailyResponse.from(f, names.get(f.getApplicationId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<FactRemediationDailyResponse> remediationDaily(LocalDate from, LocalDate to) {
        List<FactRemediationDaily> facts = factRemediationDailyRepository.findByDateIdBetweenOrderByDateIdAsc(from, to);
        Map<UUID, String> names = applicationNames(facts.stream().map(FactRemediationDaily::getApplicationId).toList());
        return facts.stream().map(f -> FactRemediationDailyResponse.from(f, names.get(f.getApplicationId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<FactLogVolumeHourlyResponse> logVolumeHourly(LocalDate from, LocalDate to) {
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<FactLogVolumeHourly> facts =
                factLogVolumeHourlyRepository.findByHourBucketBetweenOrderByHourBucketAsc(fromInstant, toInstant);
        Map<UUID, String> names = applicationNames(facts.stream().map(FactLogVolumeHourly::getApplicationId).toList());
        return facts.stream().map(f -> FactLogVolumeHourlyResponse.from(f, names.get(f.getApplicationId()))).toList();
    }

    private Map<UUID, String> applicationNames(List<UUID> applicationIds) {
        return applicationRepository.findAllById(applicationIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(Application::getId, Application::getName, (a, b) -> a));
    }
}

package com.ai.aiops.warehouse;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.aiops.domain.DataCatalogEntry;
import com.ai.aiops.repository.DataCatalogEntryRepository;

@Service
public class DataCatalogService {

    private final DataCatalogEntryRepository repository;

    public DataCatalogService(DataCatalogEntryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DataCatalogEntry> list() {
        return repository.findAllByOrderByDatasetNameAsc();
    }
}

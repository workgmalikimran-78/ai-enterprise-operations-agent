package com.ai.aiops.opensearch;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.opensearch.client.opensearch._types.mapping.DateProperty;
import org.opensearch.client.opensearch._types.mapping.KeywordProperty;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TextProperty;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.PutIndexTemplateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Bootstraps the index template for the monthly rolling log-events-yyyy.MM indices. */
@Component
public class OpenSearchIndexTemplateInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchIndexTemplateInitializer.class);
    private static final String TEMPLATE_NAME = "log-events-template";

    private final OpenSearchClient client;

    public OpenSearchIndexTemplateInitializer(OpenSearchClient client) {
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            TypeMapping mapping = new TypeMapping.Builder()
                    .properties("timestamp", Property.of(p -> p.date(DateProperty.of(d -> d))))
                    .properties("service", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))))
                    .properties("environment", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))))
                    .properties("level", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))))
                    .properties("traceId", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))))
                    .properties("message", Property.of(p -> p.text(TextProperty.of(t -> t))))
                    .properties("exceptionType", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))))
                    .properties("exceptionMessage", Property.of(p -> p.text(TextProperty.of(t -> t))))
                    .build();

            PutIndexTemplateRequest request = new PutIndexTemplateRequest.Builder()
                    .name(TEMPLATE_NAME)
                    .indexPatterns(List.of("log-events-*"))
                    .template(t -> t.mappings(mapping))
                    .build();

            client.indices().putIndexTemplate(request);
            log.info("OpenSearch index template '{}' ready", TEMPLATE_NAME);
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not bootstrap OpenSearch index template (will retry on next log index write): {}",
                    ex.getMessage());
        }
    }
}

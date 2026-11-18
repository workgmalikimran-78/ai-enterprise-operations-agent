package com.ai.aiops.config;

import org.apache.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.opensearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class OpenSearchConfig {

    @Value("${aiops.opensearch.host}")
    private String host;

    @Value("${aiops.opensearch.port}")
    private int port;

    @Value("${aiops.opensearch.scheme:http}")
    private String scheme;

    @Bean
    public OpenSearchClient openSearchClient() {
        RestClient restClient = RestClient.builder(new HttpHost(host, port, scheme)).build();
        // The OpenSearch client's JacksonJsonpMapper uses its own ObjectMapper, separate from
        // Spring's auto-configured one - it needs JavaTimeModule registered explicitly to
        // serialize java.time.Instant fields, and WRITE_DATES_AS_TIMESTAMPS disabled so Instant
        // serializes as an ISO-8601 string (what the "date" field mapping expects) rather than a
        // numeric epoch-with-fractional-seconds value OpenSearch's date parser rejects.
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
        return new OpenSearchClient(transport);
    }
}

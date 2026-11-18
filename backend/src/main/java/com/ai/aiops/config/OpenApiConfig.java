package com.ai.aiops.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI aiopsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AI Operations Agent API")
                .description("Enterprise AIOps platform: log monitoring, anomaly detection, "
                        + "AI-driven incident investigation and remediation")
                .version("v1"));
    }
}

package com.ai.aiops.warehouse;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3-compatible client for the bronze data lake. Talks to whatever S3-compatible endpoint
 * aiops.lake.endpoint points at - locally that's LocalStack (docker-compose.yml's "lake" service;
 * MinIO's own image stopped being pullable anonymously from Docker Hub/Quay/GHCR partway through
 * this build, a licensing change by MinIO Inc, so LocalStack's S3 emulation stands in instead).
 * The same S3Client works unchanged against real AWS S3, GCS's S3 interop, or a self-hosted MinIO
 * - only the endpoint override and credentials differ, which is the whole point of the lake's
 * object-storage layer being S3-API-shaped from day one.
 */
@Configuration
public class DataLakeConfig {

    @Bean
    public S3Client s3Client(@Value("${aiops.lake.endpoint}") String endpoint,
                              @Value("${aiops.lake.access-key}") String accessKey,
                              @Value("${aiops.lake.secret-key}") String secretKey) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                // MinIO (and most S3-compatible stores) need path-style addressing; only real AWS
                // S3 defaults to virtual-hosted-style.
                .forcePathStyle(true)
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }
}

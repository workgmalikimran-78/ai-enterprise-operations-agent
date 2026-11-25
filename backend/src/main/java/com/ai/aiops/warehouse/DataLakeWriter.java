package com.ai.aiops.warehouse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketLifecycleConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.LifecycleExpiration;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.s3.model.LifecycleRuleFilter;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Writes the bronze (raw, full-fidelity) layer of the data lake: one gzip-NDJSON object per
 * dataset per ETL run, under {@code bronze/{dataset}/dt={yyyy-MM-dd}/...}. Retention is enforced
 * by an S3 bucket lifecycle rule set once at startup, not a custom delete loop - the native way
 * object stores expire old data.
 */
@Component
public class DataLakeWriter {

    private static final Logger log = LoggerFactory.getLogger(DataLakeWriter.class);

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final String bucket;
    private final int retentionDays;

    public DataLakeWriter(S3Client s3Client, ObjectMapper objectMapper,
                           @Value("${aiops.lake.bucket}") String bucket,
                           @Value("${aiops.lake.retention-days:90}") int retentionDays) {
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
        this.bucket = bucket;
        this.retentionDays = retentionDays;
    }

    /**
     * Creates the bucket and sets its retention lifecycle rule if not already present. Failures
     * here (e.g. MinIO not reachable yet) are logged, not fatal - mirrors how config.LlmConfig
     * tolerates a missing LLM key at startup: the app still starts, the ETL job just fails clearly
     * the first time it actually tries to write.
     */
    @PostConstruct
    void ensureBucketAndLifecycle() {
        try {
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            } catch (NoSuchBucketException e) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("Created data lake bucket '{}'", bucket);
            }
            s3Client.putBucketLifecycleConfiguration(PutBucketLifecycleConfigurationRequest.builder()
                    .bucket(bucket)
                    .lifecycleConfiguration(BucketLifecycleConfiguration.builder()
                            .rules(LifecycleRule.builder()
                                    .id("expire-bronze")
                                    .status(ExpirationStatus.ENABLED)
                                    .filter(LifecycleRuleFilter.builder().prefix("bronze/").build())
                                    .expiration(LifecycleExpiration.builder().days(retentionDays).build())
                                    .build())
                            .build())
                    .build());
            log.info("Data lake bucket '{}' ready, bronze/ retention {} days", bucket, retentionDays);
        } catch (RuntimeException e) {
            log.warn("Could not initialize data lake bucket '{}' at startup ({}); the warehouse ETL "
                    + "job will fail clearly when it next tries to write to it", bucket, e.getMessage());
        }
    }

    /**
     * Writes {@code rows} as a single gzip-compressed NDJSON object. Returns the object key, or
     * null if there were no rows to write.
     */
    public String writeBronze(String dataset, List<Map<String, Object>> rows, Instant from, Instant to) {
        if (rows.isEmpty()) {
            return null;
        }
        String key = "bronze/%s/dt=%s/%s-%d-%d.ndjson.gz".formatted(
                dataset, to.toString().substring(0, 10), dataset, from.toEpochMilli(), to.toEpochMilli());

        byte[] gzipped = toGzippedNdjson(rows);
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/x-ndjson")
                .contentEncoding("gzip")
                .build(), RequestBody.fromBytes(gzipped));
        log.debug("Wrote {} rows ({} bytes gzipped) to s3://{}/{}", rows.size(), gzipped.length, bucket, key);
        return key;
    }

    private byte[] toGzippedNdjson(List<Map<String, Object>> rows) {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {
            for (Map<String, Object> row : rows) {
                gzipStream.write(objectMapper.writeValueAsBytes(row));
                gzipStream.write('\n');
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize bronze rows for dataset export", e);
        }
        return byteStream.toByteArray();
    }
}

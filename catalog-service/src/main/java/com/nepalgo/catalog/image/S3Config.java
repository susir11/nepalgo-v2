package com.nepalgo.catalog.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
public class S3Config {

    @Value("${nepalgo.storage.endpoint}")
    private String endpoint;

    @Value("${nepalgo.storage.access-key}")
    private String accessKey;

    @Value("${nepalgo.storage.secret-key}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                // Real AWS S3 uses virtual-hosted-style URLs by default;
                // MinIO (and most S3-compatible servers) need path-style
                // instead. This is the one real behavioral difference our
                // code has to account for between dev and real S3 later —
                // everything else is identical.
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .region(Region.US_EAST_1) // MinIO ignores region, but the SDK requires one be set
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }
}
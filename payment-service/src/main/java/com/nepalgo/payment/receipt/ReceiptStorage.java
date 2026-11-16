package com.nepalgo.payment.receipt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

// Receipt screenshots live in a PRIVATE bucket (unlike catalog's public
// destination images): they show a customer's payment details. Nobody gets a
// permanent link — admin views go through short-lived presigned URLs, like a
// bank statement download link that expires after a few minutes.
@Component
public class ReceiptStorage implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReceiptStorage.class);
    private static final Duration VIEW_LINK_TTL = Duration.ofMinutes(5);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public ReceiptStorage(@Value("${nepalgo.storage.endpoint}") String endpoint,
                          @Value("${nepalgo.storage.access-key}") String accessKey,
                          @Value("${nepalgo.storage.secret-key}") String secretKey,
                          @Value("${nepalgo.storage.receipts-bucket}") String bucket) {
        var creds = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        // Path-style addressing: RustFS (like MinIO) serves http://host:9000/bucket/key
        var pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        this.s3 = S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.US_EAST_1)
                .serviceConfiguration(pathStyle).credentialsProvider(creds).build();
        this.presigner = S3Presigner.builder().endpointOverride(URI.create(endpoint)).region(Region.US_EAST_1)
                .serviceConfiguration(pathStyle).credentialsProvider(creds).build();
        this.bucket = bucket;
    }

    // Create the bucket on startup if missing. Deliberately NO bucket policy:
    // the default is private, which is exactly what we want here.
    @Override
    public void run(ApplicationArguments args) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created private bucket {}", bucket);
        }
    }

    public void put(String key, byte[] bytes, String contentType) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(bytes));
    }

    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    public String presignedViewUrl(String key) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(VIEW_LINK_TTL)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build()).url().toString();
    }

    public String bucket() {
        return bucket;
    }
}

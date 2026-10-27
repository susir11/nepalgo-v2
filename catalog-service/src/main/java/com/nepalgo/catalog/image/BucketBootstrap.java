package com.nepalgo.catalog.image;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;

// Public-read is a deliberate v1 simplification: image URLs work directly
// in a browser/Flutter Image.network() with no signing step. Real S3 in
// production would more likely sit behind a CDN with its own access
// pattern — this bucket policy is a dev convenience, not a production
// security decision.
@Component
@RequiredArgsConstructor
public class BucketBootstrap implements ApplicationRunner {

    private final S3Client s3Client;

    @Value("${nepalgo.storage.bucket}")
    private String bucket;

    @Override
    public void run(ApplicationArguments args) {
        boolean exists = true;
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            exists = false;
        }

        if (!exists) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }

        String policy = """
            {
              "Version": "2012-10-17",
              "Statement": [{
                "Effect": "Allow",
                "Principal": "*",
                "Action": "s3:GetObject",
                "Resource": "arn:aws:s3:::%s/*"
              }]
            }
            """.formatted(bucket);
        s3Client.putBucketPolicy(PutBucketPolicyRequest.builder().bucket(bucket).policy(policy).build());
    }
}
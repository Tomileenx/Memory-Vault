package com.example.video_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageService {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    // Generate the Upload Url
    public String generateUploadUrl(String key) {
        // Generate the S3 upload request
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        // Configure the presigned URL
        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(
                                Duration.ofMinutes(15)
                        )
                        .putObjectRequest(putObjectRequest)
                        .build();

        // Generate the presigned request
        PresignedPutObjectRequest presignedRequest =
                s3Presigner.presignPutObject(presignRequest);

        // Return the presigned upload url
        return presignedRequest.url().toString();
    }

    // Verify the metadata of an upload request
    public HeadObjectResponse getObjectMetadata(String key) {
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        return s3Client.headObject(request);
    }
}

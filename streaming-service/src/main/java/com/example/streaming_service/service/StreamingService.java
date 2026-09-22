package com.example.streaming_service.service;

import com.example.streaming_service.dto.StreamingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-expiry}")
    private long presignedUrlExpiry;

    private final static String STREAMING_URL_CACHE_PREFIX = "streaming_url_cache:";

    /**
     * Get streamingUrl for a movie
     *
     * FLOW:
     * 1. Check redis cache for existing presigned Url
     * 2. If Cached - return immediately
     * 3. If not Cached = generated new presigned Url from S3
     * 4. Cache the Url in Redis
     * 5. Return streaming Url
     *
     * Why presigned Url?
     * - S3 bucket is private locker room - videos are not publicly accessible
     * - Presigned Url gives temporary access (x minutes)
     * - Prevent unauthorized video download
     */

    public StreamingResponse getStreamingUrl(UUID userId, String videoId, String playlistKey) {
        log.info("Getting streaming url for video: {}", videoId);

        String cacheKey = STREAMING_URL_CACHE_PREFIX + videoId;

        // Check redis Cache first
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);

        if (cachedUrl != null) {
            log.info("Streaming URL for video: {}", videoId);

            return new StreamingResponse(
                    videoId,
                    cachedUrl,
                    "1080p, 720p, 480p, 360p",
                    presignedUrlExpiry
            );
        }

        // Generate presigned URL from S3
        log.info("Generating new presigned URL for video: {}", videoId);
        String presignedUrl = generatePresignedUrl(playlistKey);

        // Cache in redis for 55 minutes
        // (5 minutes less than actual expiry to avoid edge cases);
        redisTemplate.opsForValue().set(
                cacheKey,
                presignedUrl,
                55,
                TimeUnit.MINUTES
        );

        log.info("Streaming URL generated and cached for vide: {}", videoId);

        return new StreamingResponse(
                videoId,
                presignedUrl,
                "1080p, 720p, 480p, 360p",
                presignedUrlExpiry
        );
    }

    public String generatePresignedUrl(String playlistKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(playlistKey)
                .build();

        GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpiry))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(getObjectPresignRequest)
                .url()
                .toString();
    }

    public String getSignedPlaylist(UUID userId, String videoId, String playlistPath) {
        log.info("Getting signed playlist for video: {} path: {}",
                videoId, playlistPath);

        // Get base path for this playlist
        String basePath = playlistPath.substring(
                0,
                playlistPath.lastIndexOf('/') + 1
        );

        // Read m3u8 content from S3
        String m3u8Content = readFromS3(playlistPath);

        // Rewrite each line that is a segment or playlist reference
        return rewriteM3u8SignedUrls(
                m3u8Content,
                basePath
        );
    }

    public String readFromS3(String s3Key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        ResponseInputStream<GetObjectResponse> response =
                s3Client.getObject(request);

        return new BufferedReader(new InputStreamReader(response))
                .lines()
                .collect(Collectors.joining("\n"));
    }


    /**
     * Modifying every file and replacing it with a pre-signed url
     * @param m3u8Content
     * @param basePath
     * @return
     */
    public String rewriteM3u8SignedUrls(String m3u8Content, String basePath) {
        StringBuilder rewritten = new StringBuilder();

        for (String line : m3u8Content.split("\n")) {
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                rewritten.append(line).append("\n");
                continue;
            }

            // This is a segment or playlist reference
            // Build full S3 key and Sign it
            String fullKey = basePath + trimmed;
            String signedUrl = generatePresignedUrl(fullKey);

            rewritten.append(signedUrl).append("\n");
        }

        return rewritten.toString();
    }

    public void invalidateCache(UUID userId, String videoId) {
        String cacheKey = STREAMING_URL_CACHE_PREFIX + videoId;
        Boolean deleted = redisTemplate.delete(cacheKey);
        log.info("Cache deleted for {}: {}", videoId, deleted);
    }
}

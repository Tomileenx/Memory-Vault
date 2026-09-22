package com.example.streaming_service;

import com.example.streaming_service.dto.StreamingResponse;
import com.example.streaming_service.service.StreamingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class StreamingUrlTests {
    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private PresignedGetObjectRequest presignedRequest;

    @InjectMocks
    private StreamingService streamingService;

    private final String bucketName = "memory-vault";
    private final long presignedUrlExpiry = 60;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                streamingService,
                "bucketName",
                bucketName
        );

        ReflectionTestUtils.setField(
                streamingService,
                "presignedUrlExpiry",
                presignedUrlExpiry
        );
    }

    @Test
    void shouldGenerateAndCacheStreamingUrlWhenCacheMisses() throws Exception {

        String videoId = "video-123";
        String playlistKey = "encoded/video-123/master.m3u8";
        String presignedUrl =
                "https://example.com/signed/master.m3u8";

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.get(
                "streaming_url_cache:" + videoId
        )).thenReturn(null);

        when(presignedRequest.url())
                .thenReturn(new URL(presignedUrl));

        when(s3Presigner.presignGetObject(
                any(GetObjectPresignRequest.class)
        )).thenReturn(presignedRequest);

        StreamingResponse response =
                streamingService.getStreamingUrl(
                        UUID.randomUUID(),
                        videoId,
                        playlistKey
                );

        assertEquals(videoId, response.videoId());
        assertEquals(
                presignedUrl,
                response.streamingUrl()
        );

        verify(valueOperations).set(
                eq("streaming_url_cache:" + videoId),
                eq(presignedUrl),
                eq(55L),
                eq(TimeUnit.MINUTES)
        );

        verify(s3Presigner).presignGetObject(
                any(GetObjectPresignRequest.class)
        );
    }

    @Test
    void shouldInvalidateStreamingUrlCache() {

        String videoId = "video-123";

        when(redisTemplate.delete(
                "streaming_url_cache:" + videoId
        )).thenReturn(true);

        streamingService.invalidateCache(
                UUID.randomUUID(),
                videoId
        );

        verify(redisTemplate).delete(
                "streaming_url_cache:" + videoId
        );
    }
}

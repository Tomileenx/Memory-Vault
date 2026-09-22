package com.example.streaming_service;

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
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class ReadFromS3Test {
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
    void shouldReadPlaylistFromS3() throws Exception {

        String s3Key = "encoded/video-123/master.m3u8";

        String playlistContent = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXTINF:10,
            1080p/segment0.ts
            """;

        // Create an actual ResponseInputStream containing the playlist
        GetObjectResponse getObjectResponse =
                GetObjectResponse.builder().build();

        ResponseInputStream<GetObjectResponse> response =
                new ResponseInputStream<>(
                        getObjectResponse,
                        AbortableInputStream.create(
                                new ByteArrayInputStream(
                                        playlistContent.getBytes(
                                                StandardCharsets.UTF_8
                                        )
                                )
                        )
                );

        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(response);

        String result =
                streamingService.readFromS3(s3Key);

        assertEquals(playlistContent.trim(), result);

        ArgumentCaptor<GetObjectRequest> captor =
                ArgumentCaptor.forClass(GetObjectRequest.class);

        verify(s3Client).getObject(captor.capture());

        GetObjectRequest request = captor.getValue();

        assertEquals(bucketName, request.bucket());
        assertEquals(s3Key, request.key());
    }
}

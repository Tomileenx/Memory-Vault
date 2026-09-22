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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PresignedUrlTests {
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
    void shouldNotGeneratePresignedUrlWhenUrlIsCached() {

        String videoId = "video-123";
        String cachedUrl =
                "https://example.com/cached/master.m3u8";

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.get(
                "streaming_url_cache:" + videoId
        )).thenReturn(cachedUrl);

        streamingService.getStreamingUrl(
                UUID.randomUUID(),
                videoId,
                "encoded/video-123/master.m3u8"
        );

        verify(valueOperations).get(
                "streaming_url_cache:" + videoId
        );

        verify(s3Presigner, never()).presignGetObject(
                any(GetObjectPresignRequest.class)
        );

        verify(valueOperations, never()).set(
                anyString(),
                anyString(),
                anyLong(),
                any(TimeUnit.class)
        );
    }

    @Test
    void shouldGeneratePresignedUrl()
            throws Exception {

        String playlistKey =
                "encoded/video-123/master.m3u8";

        URL expectedUrl =
                new URL("https://example.com/signed/master.m3u8");

        when(presignedRequest.url())
                .thenReturn(expectedUrl);

        when(s3Presigner.presignGetObject(
                any(GetObjectPresignRequest.class)
        )).thenReturn(presignedRequest);

        String result =
                streamingService.generatePresignedUrl(
                        playlistKey
                );

        assertEquals(
                expectedUrl.toString(),
                result
        );

        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(
                        GetObjectPresignRequest.class
                );

        verify(s3Presigner).presignGetObject(
                captor.capture()
        );

        GetObjectPresignRequest request =
                captor.getValue();

        assertEquals(
                bucketName,
                request.getObjectRequest().bucket()
        );

        assertEquals(
                playlistKey,
                request.getObjectRequest().key()
        );

        assertEquals(
                presignedUrlExpiry,
                request.signatureDuration().toMinutes()
        );
    }

    @Test
    void shouldReplaceSegmentWithSignedUrl()
            throws Exception {

        String playlist = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXTINF:10,
                segment0.ts
                """;

        URL signedUrl =
                new URL(
                        "https://example.com/signed/segment0.ts"
                );

        when(presignedRequest.url())
                .thenReturn(signedUrl);

        when(s3Presigner.presignGetObject(
                any(GetObjectPresignRequest.class)
        )).thenReturn(presignedRequest);

        String result =
                streamingService.rewriteM3u8SignedUrls(
                        playlist,
                        "encoded/video-123/1080p/"
                );

        assertTrue(
                result.contains(
                        "https://example.com/signed/segment0.ts"
                )
        );

        assertTrue(
                result.contains("#EXTM3U")
        );

        assertTrue(
                result.contains("#EXT-X-VERSION:3")
        );

        assertTrue(
                result.contains("#EXTINF:10,")
        );
    }

    @Test
    void shouldGeneratePresignedUrlForCorrectS3Key()
            throws Exception {

        String playlist = """
                #EXTM3U
                #EXTINF:10,
                segment0.ts
                """;

        when(presignedRequest.url())
                .thenReturn(
                        new URL(
                                "https://example.com/signed/segment0.ts"
                        )
                );

        when(s3Presigner.presignGetObject(
                any(GetObjectPresignRequest.class)
        )).thenReturn(presignedRequest);

        streamingService.rewriteM3u8SignedUrls(
                playlist,
                "encoded/video-123/1080p/"
        );

        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(
                        GetObjectPresignRequest.class
                );

        verify(s3Presigner).presignGetObject(
                captor.capture()
        );

        GetObjectPresignRequest request =
                captor.getValue();

        assertEquals(
                "encoded/video-123/1080p/segment0.ts",
                request.getObjectRequest().key()
        );

        assertEquals(
                bucketName,
                request.getObjectRequest().bucket()
        );
    }

    @Test
    void shouldPreservePlaylistComments() {

        String playlist = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXTINF:10,
                """;

        String result =
                streamingService.rewriteM3u8SignedUrls(
                        playlist,
                        "encoded/video-123/1080p/"
                );

        assertTrue(result.contains("#EXTM3U"));
        assertTrue(result.contains("#EXT-X-VERSION:3"));
        assertTrue(result.contains("#EXTINF:10,"));

        verifyNoInteractions(s3Presigner);
    }
}

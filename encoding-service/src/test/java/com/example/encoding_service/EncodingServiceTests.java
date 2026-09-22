package com.example.encoding_service;

import com.example.encoding_service.event.VideoEncodedEvent;
import com.example.encoding_service.event.VideoUploadedEvent;
import com.example.encoding_service.service.EncodingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EncodingServiceTests {
    @Mock
    private S3Client s3Client;

    @Mock
    private KafkaTemplate<String, VideoEncodedEvent> kafkaTemplate;

    @InjectMocks
    private EncodingService encodingService;

    @TempDir
    Path tempDirectory;

    private VideoUploadedEvent event;

    @BeforeEach
    void setUp() {

        ReflectionTestUtils.setField(
                encodingService,
                "bucketName",
                "test-bucket"
        );

        ReflectionTestUtils.setField(
                encodingService,
                "tempDir",
                tempDirectory.toString()
        );

        // Change this to your actual FFmpeg path
        ReflectionTestUtils.setField(
                encodingService,
                "ffmpegPath",
                "C:/Users/USER/Documents/ffmpeg-8.1.2-essentials_build/bin/ffmpeg.exe"
        );

        event = new VideoUploadedEvent(
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                "videos/user/video/original/video.mp4",
                "test-bucket",
                1000L
        );
    }

    @Test
    void shouldPublishSuccessEventWhenEncodingSucceeds() throws Exception {

        // given

        doAnswer(invocation -> {

            Path destination = invocation.getArgument(1);

            Path source = Paths.get(
                    Objects.requireNonNull(
                            getClass()
                                    .getClassLoader()
                                    .getResource("test-video.mp4")
                    ).toURI()
            );

            Files.copy(
                    source,
                    destination
            );

            return null;

        }).when(s3Client).getObject(
                any(GetObjectRequest.class),
                any(Path.class)
        );

        // when
        encodingService.encodeVideo(event);

        // then

        ArgumentCaptor<VideoEncodedEvent> captor =
                ArgumentCaptor.forClass(VideoEncodedEvent.class);

        verify(kafkaTemplate).send(
                eq("video.encoded"),
                eq(event.videoId()),
                captor.capture()
        );

        VideoEncodedEvent encodedEvent =
                captor.getValue();

        assertEquals(
                event.videoId(),
                encodedEvent.videoId()
        );

        assertTrue(
                encodedEvent.success()
        );

        assertNotNull(
                encodedEvent.hlsUrl()
        );

        assertNotNull(
                encodedEvent.masterPlaylistKey()
        );

        assertNull(
                encodedEvent.errorMessage()
        );
    }
}

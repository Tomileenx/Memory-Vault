package com.example.video_service;

import com.example.video_service.entity.Video;
import com.example.video_service.enumFolder.VideoStatus;
import com.example.video_service.event.VideoUploadedEvent;
import com.example.video_service.exception.BadRequest;
import com.example.video_service.exception.NotFound;
import com.example.video_service.exception.Unauthorized;
import com.example.video_service.idempotency.Idempotency;
import com.example.video_service.idempotency.IdempotencyOperation;
import com.example.video_service.idempotency.IdempotencyRepo;
import com.example.video_service.idempotency.IdempotencyStatus;
import com.example.video_service.repo.VideoRepo;
import com.example.video_service.service.S3StorageService;
import com.example.video_service.service.VideoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CompleteVideoTests {

    @InjectMocks
    private VideoService videoService;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private VideoRepo videoRepo;

    @Mock
    private IdempotencyRepo idempotencyRepo;

    @Mock
    private KafkaTemplate<String, VideoUploadedEvent> kafkaTemplate;

    @Test
    void shouldCompleteUpload() {

        UUID userId = UUID.randomUUID();
        String videoId = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();

        String videoKey =
                "videos/" + userId + "/" + videoId + "/original/video";

        Video existingVideo = Video.builder()
                .id(videoId)
                .userId(userId)
                .videoKey(videoKey)
                .videoStatus(VideoStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(videoRepo.findById(videoId))
                .thenReturn(Optional.of(existingVideo));

        when(videoRepo.save(any(Video.class)))
                .thenAnswer(invocation -> {
                    Video video = invocation.getArgument(0);
                    video.setId(videoId);
                    return video;
                });

        HeadObjectResponse metadata =
                mock(HeadObjectResponse.class);

        when(metadata.contentLength())
                .thenReturn(5000L);

        when(metadata.contentType())
                .thenReturn("video/mp4");


        when(s3StorageService.getObjectMetadata(videoKey))
                .thenReturn(metadata);

        // Act
        videoService.completeUpload(
                userId,
                videoId,
                idempotencyKey
        );


        // Assert
        assertEquals(
                5000L,
                existingVideo.getFileSize()
        );

        assertEquals(
                "video/mp4",
                existingVideo.getContentType()
        );

        assertEquals(
                VideoStatus.UPLOADED,
                existingVideo.getVideoStatus()
        );


        verify(videoRepo)
                .save(existingVideo);


        verify(idempotencyRepo, times(2))
                .saveAndFlush(any(Idempotency.class));


        verify(s3StorageService)
                .getObjectMetadata(videoKey);


        verify(idempotencyRepo, never())
                .findByUserIdAndKeyAndOperation(
                        any(),
                        anyString(),
                        eq(IdempotencyOperation.COMPLETE_UPLOAD)
                );


        verify(kafkaTemplate)
                .send(
                        eq("video.uploaded"),
                        eq(videoId),
                        any(VideoUploadedEvent.class)
                );
    }

    @Test
    void shouldThrowWhenVideoDoesNotExist() {

        UUID userId = UUID.randomUUID();
        String videoId = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();

        when(videoRepo.findById(videoId))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFound.class,
                () -> videoService.completeUpload(
                        userId,
                        videoId,
                        idempotencyKey
                )
        );

        verify(videoRepo)
                .findById(videoId);

        verify(idempotencyRepo, never())
                .saveAndFlush(any(Idempotency.class));

        verify(s3StorageService, never())
                .getObjectMetadata(anyString());

        verify(kafkaTemplate, never())
                .send(anyString(), anyString(), any());
    }

    @Test
    void shouldRejectWhenUserDoesNotOwnVideo() {

        UUID userId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        String videoId = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();

        Video video = Video.builder()
                .id(videoId)
                .userId(ownerId)
                .videoStatus(VideoStatus.PENDING)
                .videoKey("videos/test/video")
                .build();

        when(videoRepo.findById(videoId))
                .thenReturn(Optional.of(video));

        assertThrows(
                Unauthorized.class,
                () -> videoService.completeUpload(
                        userId,
                        videoId,
                        idempotencyKey
                )
        );

        verify(idempotencyRepo, never())
                .saveAndFlush(any(Idempotency.class));

        verify(s3StorageService, never())
                .getObjectMetadata(anyString());

        verify(kafkaTemplate, never())
                .send(anyString(), anyString(), any());
    }

    @Test
    void shouldReturnWhenVideoAlreadyUploaded() {

        UUID userId = UUID.randomUUID();

        String videoId = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();

        Video video = Video.builder()
                .id(videoId)
                .userId(userId)
                .videoStatus(VideoStatus.UPLOADED)
                .videoKey("videos/test/video")
                .build();

        when(videoRepo.findById(videoId))
                .thenReturn(Optional.of(video));


        videoService.completeUpload(
                userId,
                videoId,
                idempotencyKey
        );


        verify(idempotencyRepo, times(2))
                .saveAndFlush(any(Idempotency.class));

        verify(videoRepo, never())
                .save(any(Video.class));

        verify(s3StorageService, never())
                .getObjectMetadata(anyString());

        verify(kafkaTemplate, never())
                .send(anyString(), anyString(), any());
    }

    @Test
    void shouldRejectWhenVideoHasNotBeenUploadedToS3() {

        UUID userId = UUID.randomUUID();

        String videoId = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();

        String videoKey = "videos/test/video";

        Video video = Video.builder()
                .id(videoId)
                .userId(userId)
                .videoKey(videoKey)
                .videoStatus(VideoStatus.PENDING)
                .build();

        when(videoRepo.findById(videoId))
                .thenReturn(Optional.of(video));

        when(s3StorageService.getObjectMetadata(videoKey))
                .thenThrow(
                        S3Exception.builder()
                                .statusCode(404)
                                .message("Not Found")
                                .build()
                );

        assertThrows(
                BadRequest.class,
                () -> videoService.completeUpload(
                        userId,
                        videoId,
                        idempotencyKey
                )
        );


        verify(s3StorageService)
                .getObjectMetadata(videoKey);

        verify(videoRepo, never())
                .save(any(Video.class));

        verify(kafkaTemplate, never())
                .send(anyString(), anyString(), any());

        // Important:
        // Your current service does NOT mark the idempotency
        // record COMPLETED when S3 verification fails.
        verify(idempotencyRepo)
                .saveAndFlush(any(Idempotency.class));
    }

    @Test
    void shouldReturnWhenCompleteUploadIsDuplicate() {

        UUID userId = UUID.randomUUID();

        String videoId = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();

        Video video = Video.builder()
                .id(videoId)
                .userId(userId)
                .videoStatus(VideoStatus.PENDING)
                .videoKey("videos/test/video")
                .build();

        Idempotency existing = Idempotency.builder()
                .userId(userId)
                .key(idempotencyKey)
                .resourceId(videoId)
                .operation(IdempotencyOperation.COMPLETE_UPLOAD)
                .status(IdempotencyStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .build();

        when(videoRepo.findById(videoId))
                .thenReturn(Optional.of(video));

        when(idempotencyRepo.saveAndFlush(any(Idempotency.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "Duplicate idempotency key"
                        )
                );

        when(idempotencyRepo.findByUserIdAndKeyAndOperation(
                userId,
                idempotencyKey,
                IdempotencyOperation.COMPLETE_UPLOAD
        )).thenReturn(Optional.of(existing));


        videoService.completeUpload(
                userId,
                videoId,
                idempotencyKey
        );


        verify(idempotencyRepo)
                .findByUserIdAndKeyAndOperation(
                        userId,
                        idempotencyKey,
                        IdempotencyOperation.COMPLETE_UPLOAD
                );

        verify(s3StorageService, never())
                .getObjectMetadata(anyString());

        verify(videoRepo, never())
                .save(any(Video.class));

        verify(kafkaTemplate, never())
                .send(anyString(), anyString(), any());
    }
}





package com.example.video_service;


import com.example.video_service.dto.UploadUrlResponse;
import com.example.video_service.dto.VideoUploadRequest;
import com.example.video_service.entity.Video;
import com.example.video_service.enumFolder.VideoStatus;
import com.example.video_service.exception.AlreadyExists;
import com.example.video_service.exception.BadRequest;
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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UploadUrlTests {

    @InjectMocks
    private VideoService videoService;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private VideoRepo videoRepo;

    @Mock
    private IdempotencyRepo idempotencyRepo;

    @Test
    void shouldUploadUrl() {
        VideoUploadRequest request = new VideoUploadRequest(
                "X-men 97-1",
                "Episode 1"
        );

        UUID userId = UUID.randomUUID();
        String videoId = UUID.randomUUID().toString();

        when(videoRepo.existsByUserIdAndTitle(userId, request.title()))
                .thenReturn(false);

        String idempotencyKey = UUID.randomUUID().toString();

        when(videoRepo.save(any(Video.class)))
                .thenAnswer(invocationOnMock -> {
                    Video video = invocationOnMock.getArgument(0);
                    video.setId(videoId);
                    return video;
                });

        when(s3StorageService.generateUploadUrl(anyString()))
                .thenReturn("https://s3-presigned-url");

        UploadUrlResponse response = videoService.createUploadUrl(
                userId,
                request,
                idempotencyKey
        );

        assertNotNull(response);
        assertNotNull(response.id());
        assertNotNull(response.videoKey());
        assertEquals(videoId, response.id());
        assertEquals("https://s3-presigned-url", response.uploadUrl());
        assertEquals(VideoStatus.PENDING, response.videoStatus());
        assertNotNull(response.createdAt());


        verify(videoRepo).existsByUserIdAndTitle(
                userId,
                request.title()
        );

        verify(idempotencyRepo, times(2))
                .saveAndFlush(any(Idempotency.class));

        verify(videoRepo, times(2)).save(any(Video.class));

        verify(s3StorageService)
                .generateUploadUrl(response.videoKey());

        verify(idempotencyRepo, never())
                .findByUserIdAndKeyAndOperation(
                        any(),
                        anyString(),
                        eq(IdempotencyOperation.CREATE_UPLOAD_URL)
                );
    }

    @Test
    void shouldThrowWhenTitleAlreadyExists() {
        VideoUploadRequest request = new VideoUploadRequest(
                "X-men 97-1",
                "Episode 1"
        );

        UUID userId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        when(videoRepo.existsByUserIdAndTitle(userId, request.title()))
                .thenReturn(true);

        assertThrows(AlreadyExists.class,
                () -> videoService.createUploadUrl(userId, request, idempotencyKey));

        verify(videoRepo, never()).save(any());
        verify(idempotencyRepo, never()).save(any());
        verify(s3StorageService, never()).generateUploadUrl(any());
    }

    @Test
    void shouldReturnExistingVideoForDuplicateIdempotencyKey() {
        VideoUploadRequest request = new VideoUploadRequest(
                "X-men 97-1",
                "Episode 1"
        );

        UUID userId = UUID.randomUUID();
        String videoId = UUID.randomUUID().toString();
        String idempotencyKey = UUID.randomUUID().toString();
        String videoKey = "videos/" + userId + "/" + videoId
                + "/original/video";

        Video existingVideo = Video.builder()
                .id(videoId)
                .userId(userId)
                .title(request.title())
                .description(request.description())
                .videoKey(videoKey)
                .videoStatus(VideoStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        Idempotency existingIdempotency = Idempotency.builder()
                .userId(userId)
                .key(idempotencyKey)
                .resourceId(videoId)
                .operation(IdempotencyOperation.CREATE_UPLOAD_URL)
                .status(IdempotencyStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .build();

        when(idempotencyRepo.saveAndFlush(any(Idempotency.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate idempotency key"
                ));

        when(idempotencyRepo.findByUserIdAndKeyAndOperation(
                userId,
                idempotencyKey,
                IdempotencyOperation.CREATE_UPLOAD_URL
        )).thenReturn(Optional.of(existingIdempotency));

        when(videoRepo.findById(videoId))
                .thenReturn(Optional.of(existingVideo));

        when(s3StorageService.generateUploadUrl(
                existingVideo.getVideoKey()
        )).thenReturn("https://s3-presigned-url");

        UploadUrlResponse response =
                videoService.createUploadUrl(
                        userId,
                        request,
                        idempotencyKey
                );

        assertNotNull(response);
        assertEquals(videoId, response.id());
        assertEquals(
                existingVideo.getVideoKey(),
                response.videoKey()
        );
        assertEquals(
                "https://s3-presigned-url",
                response.uploadUrl()
        );
        assertEquals(
                VideoStatus.PENDING,
                response.videoStatus()
        );

        verify(idempotencyRepo)
                .saveAndFlush(any(Idempotency.class));

        verify(idempotencyRepo)
                .findByUserIdAndKeyAndOperation(
                        userId,
                        idempotencyKey,
                        IdempotencyOperation.CREATE_UPLOAD_URL
                );

        verify(videoRepo)
                .findById(videoId);

        verify(s3StorageService)
                .generateUploadUrl(
                        existingVideo.getVideoKey()
                );

        // No new video should be created
        verify(videoRepo, never())
                .save(any(Video.class));
    }

    @Test
    void shouldRejectWhenIdempotencyRequestIsProcessing() {

        UUID userId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        Idempotency existingIdempotency = Idempotency.builder()
                .userId(userId)
                .key(idempotencyKey)
                .operation(IdempotencyOperation.CREATE_UPLOAD_URL)
                .status(IdempotencyStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .build();

        when(idempotencyRepo.saveAndFlush(any(Idempotency.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "Duplicate idempotency key"
                        )
                );

        when(idempotencyRepo.findByUserIdAndKeyAndOperation(
                userId,
                idempotencyKey,
                IdempotencyOperation.CREATE_UPLOAD_URL
        )).thenReturn(Optional.of(existingIdempotency));

        BadRequest exception = assertThrows(
                BadRequest.class,
                () -> videoService.createUploadUrl(
                        userId,
                        new VideoUploadRequest(
                                "X-men 97-1",
                                "Episode 1"
                        ),
                        idempotencyKey
                )
        );

        assertEquals(
                "Upload request is being processed",
                exception.getMessage()
        );

        verify(idempotencyRepo)
                .saveAndFlush(any(Idempotency.class));

        verify(idempotencyRepo)
                .findByUserIdAndKeyAndOperation(
                        userId,
                        idempotencyKey,
                        IdempotencyOperation.CREATE_UPLOAD_URL
                );

        verify(videoRepo, never())
                .existsByUserIdAndTitle(any(), anyString());

        verify(videoRepo, never())
                .save(any(Video.class));

        verify(s3StorageService, never())
                .generateUploadUrl(anyString());
    }
}

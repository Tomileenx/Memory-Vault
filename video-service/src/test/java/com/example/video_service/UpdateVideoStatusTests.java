package com.example.video_service;

import com.example.video_service.entity.Video;
import com.example.video_service.enumFolder.VideoStatus;
import com.example.video_service.exception.NotFound;
import com.example.video_service.repo.VideoRepo;
import com.example.video_service.service.VideoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UpdateVideoStatusTests {

    @InjectMocks
    private VideoService videoService;

    @Mock
    private VideoRepo videoRepo;

    @Test
    void shouldUpdateVideoStatus() {

        String videoId = UUID.randomUUID().toString();

        Video existingVideo = Video.builder()
                .id(videoId)
                .videoStatus(VideoStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(videoRepo.findById(videoId))
                .thenReturn(Optional.of(existingVideo));

        videoService.updateVideoStatus(videoId, VideoStatus.FAILED);

        assertEquals(VideoStatus.FAILED, existingVideo.getVideoStatus());

        verify(videoRepo).findById(videoId);
        verify(videoRepo).save(existingVideo);
    }

    @Test
    void shouldThrowWhenVideoNotFound() {
        String videoId = UUID.randomUUID().toString();

        when(videoRepo.findById(videoId))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFound.class,
                () -> videoService.updateVideoStatus(videoId, VideoStatus.FAILED)
        );

        verify(videoRepo).findById(videoId);
    }
}

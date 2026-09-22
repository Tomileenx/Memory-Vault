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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UpdateHlsUrlTests {

    @InjectMocks
    private VideoService videoService;

    @Mock
    private VideoRepo videoRepo;

    @Test
    void shouldUpdateHlsUrl() {

        String videoId = UUID.randomUUID().toString();
        String hlsUrl = "www.uploadVidoHlsUrl.com";

        Video existingVideo = Video.builder()
                .id(videoId)
                .hlsUrl(hlsUrl)
                .videoStatus(VideoStatus.UPLOADED)
                .createdAt(LocalDateTime.now())
                .build();

        when(videoRepo.findById(videoId))
                .thenReturn(Optional.of(existingVideo));

        when(videoRepo.save(any(Video.class)))
                .then(invocationOnMock -> {
                    Video video = invocationOnMock.getArgument(0);
                    video.setId(videoId);
                    video.setHlsUrl(hlsUrl);
                    video.setVideoStatus(VideoStatus.READY);
                    return video;
                });

        videoService.updateHlsUrl(videoId, hlsUrl);

        assertEquals(hlsUrl, existingVideo.getHlsUrl());
        assertEquals(VideoStatus.READY, existingVideo.getVideoStatus());

        verify(videoRepo).findById(videoId);
        verify(videoRepo).save(existingVideo);
    }

    @Test
    void shouldThrowWhenVideoNotFound() {
        String videoId = UUID.randomUUID().toString();
        String hlsUrl = "www.uploadVidoHlsUrl.com";

        when(videoRepo.findById(videoId))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFound.class,
                () -> videoService.updateHlsUrl(videoId, hlsUrl)
        );

        verify(videoRepo).findById(videoId);
    }
}

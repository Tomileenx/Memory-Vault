package com.example.video_service.dto;

import com.example.video_service.enumFolder.VideoStatus;

import java.time.LocalDateTime;

public record VideoResponse(
        String videoId,
        String title,
        String description,
        Long durationSeconds,
        String contentType,
        long fileSize,
        VideoStatus videoStatus,
        String videoKey,
        LocalDateTime createdAt
) {
}

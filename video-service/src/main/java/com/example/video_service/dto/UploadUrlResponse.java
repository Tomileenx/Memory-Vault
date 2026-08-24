package com.example.video_service.dto;

import com.example.video_service.enumFolder.VideoStatus;

import java.time.LocalDateTime;

public record UploadUrlResponse(
        String id,
        String videoKey,
        String uploadUrl,
        VideoStatus videoStatus,
        LocalDateTime createdAt
) {
}

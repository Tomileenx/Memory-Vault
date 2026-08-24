package com.example.video_service.event;

import java.util.UUID;

public record VideoUploadedEvent(
        String videoId,
        UUID userId,
        String videoKey,
        String bucketName,
        long fileSizeBytes
) {
}

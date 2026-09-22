package com.example.encoding_service.event;

import java.util.UUID;

public record VideoUploadedEvent(
        String videoId,
        UUID userId,
        String videoKey,
        String bucketName,
        long fileSizeBytes
) {
}

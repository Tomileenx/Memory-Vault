package com.example.video_service.event;

public record VideoEncodedEvent(
        String videoId,
        Long durationSeconds,
        String hlsUrl, // Master playlist
        String masterPlaylistKey, // S3 key if master.m3u8
        boolean success,
        String errorMessage // If encoding failed
) {
}

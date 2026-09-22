package com.example.streaming_service.dto;

public record StreamingResponse(
        String videoId,
        String streamingUrl,     //Presigned HLS master playlist URL
        String quality,
        long expiresInMinutes
) {
}

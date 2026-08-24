package com.example.video_service.controller;

import com.example.video_service.dto.UploadUrlResponse;
import com.example.video_service.dto.VideoUploadRequest;
import com.example.video_service.service.VideoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memVault")
@Slf4j
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    @PostMapping("/upload-url")
    public ResponseEntity<UploadUrlResponse> uploadVideo(
            Authentication authentication,
            @RequestHeader("Idempotency-key") String idempotencyKey,
            @RequestBody @Valid VideoUploadRequest request
    ) {
        UUID userId = UUID.fromString(authentication.getName());

        UploadUrlResponse response = videoService.createUploadUrl(
                userId,
                request,
                idempotencyKey
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{videoId}/upload/complete")
    public ResponseEntity<String> completeUpload(
            Authentication authentication,
            @RequestHeader("Idempotency-key") String idempotencyKey,
            @PathVariable String videoId
    ) {
        UUID userId = UUID.fromString(authentication.getName());

        videoService.completeUpload(userId, videoId, idempotencyKey);

        return ResponseEntity.ok("Video successfully uploaded to S3 - " + videoId);
    }
}

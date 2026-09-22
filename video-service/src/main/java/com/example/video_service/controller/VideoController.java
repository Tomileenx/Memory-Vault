package com.example.video_service.controller;

import com.example.video_service.dto.UploadUrlResponse;
import com.example.video_service.dto.VideoResponse;
import com.example.video_service.dto.VideoUploadRequest;
import com.example.video_service.service.VideoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @GetMapping("/{videoId}")
    public ResponseEntity<VideoResponse> getVideo(
            Authentication authentication,
            @PathVariable String videoId
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        VideoResponse response = videoService.getVideo(userId, videoId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/videos")
    public ResponseEntity<Page<VideoResponse>> getAllVideos(
            Authentication authentication,
            @PageableDefault(
                    page = 0
            )
            Pageable pageable
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        Page<VideoResponse> response = videoService.getAllVideos(userId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search-videos")
    public ResponseEntity<Page<VideoResponse>> searchVideos(
            Authentication authentication,

            @RequestParam(required = false)
            String title,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            @RequestParam(defaultValue = "newest")
            String sort
    ) {
        UUID userId = UUID.fromString(authentication.getName());

        Sort sorting = sort.equalsIgnoreCase("oldest")
                ? Sort.by("createdAt").ascending()
                : Sort.by("createdAt").descending();

        Pageable pageable = PageRequest.of(
                page,
                Math.min(size, 10),
                sorting
        );

        Page<VideoResponse> response = videoService.searchVideos(userId, title, pageable);

        return ResponseEntity.ok(response);
    }
}

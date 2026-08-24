package com.example.video_service.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record VideoUploadRequest(
        @NotBlank(message = "Title cannot be blank")
        String title,

        String description
) {
}

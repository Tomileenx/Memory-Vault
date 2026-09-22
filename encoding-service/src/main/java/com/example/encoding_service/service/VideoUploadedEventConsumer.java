package com.example.encoding_service.service;

import com.example.encoding_service.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoUploadedEventConsumer {
    private final EncodingService encodingService;

    @KafkaListener(
            topics = "video.uploaded",
            groupId = "encoding-service-group"
    )
    private void consumeVideoUploadedEvent(VideoUploadedEvent event) {
        log.info("Consumed VideoUploadedEvent for video: {}", event.videoId());

        try {
            encodingService.encodeVideo(event);
        } catch (Exception e) {
            log.error("Failed to process encoding for video: {} - {}",
                    event.videoId(), e.getMessage());
        }
    }
}

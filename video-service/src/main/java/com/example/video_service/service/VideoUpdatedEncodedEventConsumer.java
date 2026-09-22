package com.example.video_service.service;

import com.example.video_service.enumFolder.VideoStatus;
import com.example.video_service.event.VideoEncodedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoUpdatedEncodedEventConsumer {
    private final VideoService videoService;

   @KafkaListener(
           topics = "video.encoded",
           groupId = "video-service-group"
   )
   public void consumeVideoEncodedEvent(
           VideoEncodedEvent event
   ) {
       log.info("Received VideoEncodedEvent: {}", event);

       log.info(
               "Received event: videoId={}, durationSeconds={}, hlsUrl={}, masterPlaylistKey={}, success={}, error={}",
               event.videoId(),
               event.durationSeconds(),
               event.hlsUrl(),
               event.masterPlaylistKey(),
               event.success(),
               event.errorMessage()
       );

       if (event.videoId() == null) {
           throw new IllegalArgumentException("Received VideoEncodedEvent with null videoId");
       }

       if (event.success()) {
           videoService.updateHlsUrl(event.videoId(), event.hlsUrl());
           videoService.updateVideoDurationSeconds(event.videoId(), event.durationSeconds());
           videoService.updateVideoStatus(event.videoId(), VideoStatus.READY);
       } else {
           log.error(
                   "Video encoding failed: videoId={}, error={}",
                   event.videoId(),
                   event.errorMessage()
           );
           videoService.updateVideoStatus(event.videoId(), VideoStatus.FAILED);
       }
   }
}

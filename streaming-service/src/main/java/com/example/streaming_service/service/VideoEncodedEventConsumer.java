package com.example.streaming_service.service;

import com.example.streaming_service.event.VideoEncodedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoEncodedEventConsumer {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String MASTER_PLAYLIST_KEY_PREFIX = "streaming:playlist:";

    /**
     * Listens to video.encoded Kafka topic.
     * Stores master playlist key in Redis when encoding is complete.
     * This allows StreamingService to quickly find the playlist key by movieId
     */
    @KafkaListener(
            topics = "video.encoded",
            groupId = "streaming-service-group"
    )
    public void consumerVideoEncodedEvent(VideoEncodedEvent event) {
        log.info("Consumed VideoEncodedEvent for video: {} success: {}",
                event.videoId(), event.success());

        if (event.success()) {
            // Store master playlist key in redis
            String cacheKey = MASTER_PLAYLIST_KEY_PREFIX + event.videoId();
            redisTemplate.opsForValue().set(cacheKey, event.masterPlaylistKey());
            log.info("Master playlist key stored in Redis for video: {} - {}",
                    event.videoId(), event.errorMessage());
        } else {
            log.error("Encoding failed for video: {} — {}",
                    event.videoId(), event.errorMessage());
        }
    }
}

package com.example.streaming_service.controller;

import com.example.streaming_service.dto.StreamingResponse;
import com.example.streaming_service.service.StreamingService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memVault")
@Slf4j
@RequiredArgsConstructor
public class StreamingController {

    private final StreamingService streamingService;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String MASTER_PLAYLIST_PREFIX = "streaming:playlist:";

    @GetMapping("/stream/{videoId}")
    public ResponseEntity<StreamingResponse> getStreamingUrl(
        Authentication authentication,
        @PathVariable String videoId
    ) {
        log.info(
                "CONTROLLER REACHED - videoId: {}, userId: {}, authenticated: {}",
                videoId,
                authentication != null ? authentication.getName() : null,
                authentication != null && authentication.isAuthenticated()
        );

        UUID userId = UUID.fromString(authentication.getName());

        log.info("Streaming request for video {}", videoId);
        String playlistKey = redisTemplate.opsForValue()
                .get(MASTER_PLAYLIST_PREFIX + videoId);

        if (playlistKey == null) {
            log.warn("No playlist found for video {}", videoId);
            return ResponseEntity.notFound().build();
        }

        StreamingResponse response = streamingService.getStreamingUrl(userId, videoId,  playlistKey);

        return ResponseEntity.ok(response);
    }

    /**
     * Server signed m3u8 playlist content
     * Called by HLS Player for each quality playlist
     * @param movieId
     * @param path
     * @return
     */

    @GetMapping("/{videoId}/playlist")
    public ResponseEntity<String> getSignedPlaylist(
            Authentication authentication,
            @PathVariable String videoId,
            @RequestParam String path
    ) {
        UUID userId = UUID.fromString(authentication.getName());

        String signedPlaylist = streamingService.getSignedPlaylist(userId, videoId, path);

        return ResponseEntity.ok()
                .header(
                        "Content-Type",
                        "application/x-mpegURL"
                )
                .body(signedPlaylist);
    }

    @DeleteMapping("/cache/{videoId}")
    private ResponseEntity<Void> invalidateCache(
            Authentication authentication,
            @PathVariable String videoId
    ) {
        UUID userId = UUID.fromString(authentication.getName());

        streamingService.invalidateCache(userId, videoId);

        return ResponseEntity.noContent().build();
    }
}

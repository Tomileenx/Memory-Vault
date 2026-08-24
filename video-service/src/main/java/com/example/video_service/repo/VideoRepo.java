package com.example.video_service.repo;

import com.example.video_service.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VideoRepo extends JpaRepository<Video, String> {
    boolean existsByUserIdAndTitle(UUID userId, String title);
}

package com.example.video_service.repo;

import com.example.video_service.entity.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VideoRepo extends JpaRepository<Video, String> {
    boolean existsByUserIdAndTitle(UUID userId, String title);

    Optional<Video> findByUserIdAndId(UUID userId, String videoId);

    Page<Video> findAllByUserId(UUID userId, Pageable pageable);

    @Query("""
    SELECT v
    FROM Video v
    WHERE v.userId = :userId
    AND LOWER(v.title) LIKE LOWER(CONCAT('%', :title, '%'))
    """)
    Page<Video> searchVideos(
            @Param("userId") UUID userId,
            @Param("title") String title,
            Pageable pageable
    );
}

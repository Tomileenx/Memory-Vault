package com.example.video_service.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyRepo extends JpaRepository<Idempotency, UUID> {
    Optional<Idempotency> findByUserIdAndKeyAndOperation(
            UUID userId,
            String key,
            IdempotencyOperation operation
    );
}

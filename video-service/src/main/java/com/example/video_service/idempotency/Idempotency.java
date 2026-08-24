package com.example.video_service.idempotency;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Table(
        name = "idempotency",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_idempotency_key",
                        columnNames = {
                                "user_id",
                                "key",
                                "operation"
                        }
                )
        }
)
public class Idempotency {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    private String resourceId;

    @Column(nullable = false)
    private String key;

    @Enumerated(EnumType.STRING)
    private IdempotencyOperation operation;

    @Enumerated(EnumType.STRING)
    private IdempotencyStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;
}

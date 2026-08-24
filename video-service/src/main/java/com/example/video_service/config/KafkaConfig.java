package com.example.video_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    // Published when video is uploaded to s3
    @Bean
    public NewTopic VideoUploadedTopic() {
        return TopicBuilder.name("video.uploaded")
                .partitions(3)
                .replicas(1)
                .build();
    }
}

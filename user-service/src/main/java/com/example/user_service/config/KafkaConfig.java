package com.example.user_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic emailVerificationTopic() {
        return TopicBuilder.name("email.verification")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic emailVerificationSuccessfulTopic() {
        return TopicBuilder.name("email.verification.successful")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic passwordResetTokenTopic() {
        return TopicBuilder.name("password.reset.token")
                .partitions(3)
                .replicas(1)
                .build();
    }
}

package com.uade.mecanicosya.assistance.config;

import com.uade.mecanicosya.assistance.infrastructure.messaging.Topics;
import com.uade.mecanicosya.shared.messaging.MessagingConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Topics que usa este servicio y su dead-letter topic. Se crean al arrancar si no existen.
 */
@Configuration
public class MessagingTopicsConfiguration {

    @Bean
    KafkaAdmin.NewTopics assistanceTopics(
            @Value("${spring.application.name}") String applicationName,
            @Value("${messaging.topics.partitions:3}") int partitions
    ) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(Topics.ASSISTANCE_EVENTS).partitions(partitions).build(),
                TopicBuilder.name(Topics.DISPATCH_EVENTS).partitions(partitions).build(),
                TopicBuilder.name(MessagingConfiguration.deadLetterTopic(Topics.DISPATCH_EVENTS, applicationName))
                        .partitions(partitions)
                        .build()
        );
    }
}

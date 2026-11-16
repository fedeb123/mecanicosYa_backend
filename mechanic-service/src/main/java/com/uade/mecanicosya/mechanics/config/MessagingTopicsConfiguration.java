package com.uade.mecanicosya.mechanics.config;

import com.uade.mecanicosya.mechanics.infrastructure.messaging.AssistanceEventsListener;
import com.uade.mecanicosya.shared.messaging.MessagingConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * El topic que consume este servicio y su dead-letter topic. Se crean al arrancar si no existen.
 */
@Configuration
public class MessagingTopicsConfiguration {

    @Bean
    KafkaAdmin.NewTopics mechanicTopics(
            @Value("${spring.application.name}") String applicationName,
            @Value("${messaging.topics.partitions:3}") int partitions
    ) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(AssistanceEventsListener.TOPIC).partitions(partitions).build(),
                TopicBuilder.name(MessagingConfiguration.deadLetterTopic(AssistanceEventsListener.TOPIC, applicationName))
                        .partitions(partitions)
                        .build()
        );
    }
}

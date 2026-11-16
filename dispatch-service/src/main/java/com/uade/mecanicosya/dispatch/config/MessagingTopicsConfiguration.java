package com.uade.mecanicosya.dispatch.config;

import com.uade.mecanicosya.dispatch.infrastructure.messaging.Topics;
import com.uade.mecanicosya.shared.messaging.MessagingConfiguration;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Topics que publica este servicio y su dead-letter topic. Se crean al arrancar si no existen.
 */
@Configuration
public class MessagingTopicsConfiguration {

    @Bean
    KafkaAdmin.NewTopics dispatchTopics(
            @Value("${spring.application.name}") String applicationName,
            @Value("${messaging.topics.partitions:3}") int partitions
    ) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(Topics.DISPATCH_EVENTS).partitions(partitions).build(),
                TopicBuilder.name(Topics.ASSISTANCE_EVENTS).partitions(partitions).build(),
                deadLetter(Topics.ASSISTANCE_EVENTS, applicationName, partitions)
        );
    }

    private static NewTopic deadLetter(String topic, String applicationName, int partitions) {
        return TopicBuilder.name(MessagingConfiguration.deadLetterTopic(topic, applicationName))
                .partitions(partitions)
                .build();
    }
}

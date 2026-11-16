package com.uade.mecanicosya.shared.messaging;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.core.JacksonException;

import java.time.Duration;

/**
 * Lo que comparten los consumidores de Kafka de todos los servicios: reintentos, dead-letter topic e identificador
 * de correlación.
 */
@Configuration
@EnableKafka
public class MessagingConfiguration {

    /**
     * Sufijo del dead-letter topic de cada servicio: {@code assistance.events.dispatch-service.dlt}. Cada consumidor
     * tiene el suyo, porque un mensaje puede fallar en un servicio y no en otro.
     */
    public static String deadLetterTopic(String topic, String applicationName) {
        return topic + "." + applicationName + ".dlt";
    }

    @Bean
    CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${spring.application.name}") String applicationName,
            @Value("${messaging.consumer.retry-interval:PT2S}") Duration retryInterval,
            @Value("${messaging.consumer.max-retries:5}") long maxRetries
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(deadLetterTopic(record.topic(), applicationName), -1)
        );
        DefaultErrorHandler handler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(retryInterval.toMillis(), maxRetries)
        );
        // Un mensaje mal formado no se arregla reintentando: va directo al dead-letter topic.
        handler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class);
        return handler;
    }

    // Spring Boot aplica al contenedor de listeners el RecordInterceptor<Object, Object> que encuentre.
    @Bean
    RecordInterceptor<Object, Object> correlationIdRecordInterceptor() {
        return new CorrelationIdRecordInterceptor();
    }
}

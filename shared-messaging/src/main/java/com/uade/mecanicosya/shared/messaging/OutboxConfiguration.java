package com.uade.mecanicosya.shared.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;

/**
 * Outbox para los servicios que publican eventos. Registra el paquete de {@link OutboxMessage} para que JPA cree la
 * tabla {@code outbox_messages} en la base de cada servicio.
 */
@Configuration
@EnableScheduling
@AutoConfigurationPackage(basePackageClasses = OutboxMessage.class)
@Import(MessagingConfiguration.class)
public class OutboxConfiguration {

    @Bean
    Outbox outbox(OutboxMessageRepository repository, JsonMapper jsonMapper, Clock clock) {
        return new Outbox(repository, jsonMapper, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "messaging.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
    OutboxRelay outboxRelay(
            OutboxMessageRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${messaging.outbox.send-timeout:PT5S}") Duration sendTimeout
    ) {
        return new OutboxRelay(repository, kafkaTemplate, new TransactionTemplate(transactionManager), clock, sendTimeout);
    }
}

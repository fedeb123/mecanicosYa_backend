package com.uade.mecanicosya.assistance.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.OutputConfig;
import com.uade.mecanicosya.assistance.application.triage.KeywordProblemClassifier;
import com.uade.mecanicosya.assistance.application.triage.ProblemClassifier;
import com.uade.mecanicosya.assistance.application.triage.TriageService;
import com.uade.mecanicosya.assistance.infrastructure.triage.ClaudeProblemClassifier;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Arma la clasificación de pedidos. Con {@code ANTHROPIC_API_KEY} usa la API de Claude y cae a palabras clave si
 * falla; sin la clave usa sólo palabras clave. La clave se lee de la variable de entorno y nunca se guarda en el repo.
 */
@Configuration
@EnableConfigurationProperties(TriageConfiguration.AnthropicProperties.class)
public class TriageConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TriageConfiguration.class);

    static Optional<AnthropicClient> anthropicClient(AnthropicProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.info("ANTHROPIC_API_KEY is not set: assistance requests are classified with keywords only");
            return Optional.empty();
        }
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .timeout(properties.timeout())
                .maxRetries(properties.maxRetries());
        if (properties.baseUrl() != null && !properties.baseUrl().isBlank()) {
            builder.baseUrl(properties.baseUrl());
        }
        log.info("Assistance requests are classified with {} (effort {})", properties.model(), properties.effort());
        return Optional.of(builder.build());
    }

    /** Spring llama a {@link TriageService#close()} al apagar, que cierra el cliente HTTP de Anthropic. */
    @Bean
    TriageService triageService(AnthropicProperties properties, ObjectProvider<MeterRegistry> meterRegistry) {
        Optional<ProblemClassifier> ai = anthropicClient(properties).map(client -> new ClaudeProblemClassifier(
                client,
                properties.model(),
                OutputConfig.Effort.of(properties.effort().toLowerCase(Locale.ROOT)),
                properties.maxTokens(),
                properties.refusalFallback()
        ));
        return new TriageService(ai, new KeywordProblemClassifier(),
                meterRegistry.getIfAvailable(SimpleMeterRegistry::new));
    }

    /**
     * @param apiKey          clave de la API de Anthropic; vacía apaga la IA
     * @param baseUrl         para apuntar a un servidor de prueba; vacío usa la API real
     * @param model           modelo de Claude que clasifica
     * @param effort          esfuerzo de razonamiento (low, medium, high, xhigh, max)
     * @param timeout         cuánto se espera a la API antes de caer a palabras clave
     * @param maxRetries      reintentos del SDK ante errores transitorios
     * @param maxTokens       tope de tokens de la respuesta, razonamiento incluido
     * @param refusalFallback si un rechazo de los filtros de seguridad se reintenta del lado de la API con otro modelo
     */
    @ConfigurationProperties("triage.anthropic")
    public record AnthropicProperties(
            String apiKey,
            String baseUrl,
            String model,
            String effort,
            Duration timeout,
            int maxRetries,
            long maxTokens,
            boolean refusalFallback
    ) {
    }
}

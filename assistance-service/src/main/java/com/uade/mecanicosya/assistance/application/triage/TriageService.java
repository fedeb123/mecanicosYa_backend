package com.uade.mecanicosya.assistance.application.triage;

import com.uade.mecanicosya.assistance.domain.VehicleType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Clasifica un pedido con el modelo de IA y, si no hay modelo configurado o falla (timeout, error de la API,
 * respuesta inválida o rechazo), con el clasificador por palabras clave. El pedido nunca se frena por la IA.
 *
 * <p>Registra el timer {@code mecanicosya.triage} con las etiquetas {@code classifier} (AI o KEYWORDS) y
 * {@code outcome} (ok o failed), visible en {@code /actuator/metrics/mecanicosya.triage}.
 */
public class TriageService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TriageService.class);

    private final Optional<ProblemClassifier> aiClassifier;
    private final ProblemClassifier fallbackClassifier;
    private final MeterRegistry meterRegistry;

    public TriageService(
            Optional<ProblemClassifier> aiClassifier,
            ProblemClassifier fallbackClassifier,
            MeterRegistry meterRegistry
    ) {
        this.aiClassifier = aiClassifier;
        this.fallbackClassifier = fallbackClassifier;
        this.meterRegistry = meterRegistry;
    }

    public Triage classify(String description, VehicleType vehicleType) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("The description is required");
        }
        if (aiClassifier.isPresent()) {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                Triage triage = aiClassifier.get().classify(description, vehicleType);
                sample.stop(timer("AI", "ok"));
                return triage;
            } catch (RuntimeException exception) {
                sample.stop(timer("AI", "failed"));
                Throwable cause = exception.getCause();
                log.warn("AI triage failed, falling back to keywords: {}{}", exception.getMessage(),
                        cause == null ? "" : " (" + cause + ")");
            }
        }
        Timer.Sample sample = Timer.start(meterRegistry);
        Triage triage = fallbackClassifier.classify(description, vehicleType);
        sample.stop(timer("KEYWORDS", "ok"));
        return triage;
    }

    public boolean aiEnabled() {
        return aiClassifier.isPresent();
    }

    @Override
    public void close() throws Exception {
        if (aiClassifier.isPresent() && aiClassifier.get() instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    private Timer timer(String classifier, String outcome) {
        return Timer.builder("mecanicosya.triage")
                .description("Time to classify the text of an assistance request")
                .tag("classifier", classifier)
                .tag("outcome", outcome)
                .register(meterRegistry);
    }
}

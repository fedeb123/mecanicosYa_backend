package com.uade.mecanicosya.assistance.application.triage;

import com.uade.mecanicosya.assistance.domain.TriageSource;
import com.uade.mecanicosya.assistance.domain.Urgency;
import com.uade.mecanicosya.assistance.domain.VehicleType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TriageServiceTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    void usesTheAiAnswerWhenItWorks() {
        Triage aiAnswer = new Triage(ProblemCategory.WHEEL, Urgency.HIGH, 0.9, TriageSource.AI);
        TriageService service = new TriageService(
                Optional.of((description, vehicleType) -> aiAnswer), new KeywordProblemClassifier(), meterRegistry);

        assertThat(service.classify("la rueda quedó hecha un ocho", VehicleType.BICYCLE)).isEqualTo(aiAnswer);
        assertThat(meterRegistry.get("mecanicosya.triage").tag("classifier", "AI").tag("outcome", "ok").timer().count())
                .isEqualTo(1);
    }

    @Test
    void fallsBackToKeywordsWhenTheAiFails() {
        TriageService service = new TriageService(Optional.of((description, vehicleType) -> {
            throw new TriageUnavailableException("timeout");
        }), new KeywordProblemClassifier(), meterRegistry);

        Triage triage = service.classify("se me cortó la cadena", VehicleType.BICYCLE);

        assertThat(triage.source()).isEqualTo(TriageSource.KEYWORDS);
        assertThat(triage.category()).isEqualTo(ProblemCategory.CHAIN);
        assertThat(meterRegistry.get("mecanicosya.triage").tag("classifier", "AI").tag("outcome", "failed").timer().count())
                .isEqualTo(1);
    }

    @Test
    void usesKeywordsWhenThereIsNoApiKey() {
        TriageService service = new TriageService(Optional.empty(), new KeywordProblemClassifier(), meterRegistry);

        assertThat(service.aiEnabled()).isFalse();
        assertThat(service.classify("pinché", VehicleType.BICYCLE).source()).isEqualTo(TriageSource.KEYWORDS);
    }

    @Test
    void aBlankDescriptionIsRejected() {
        TriageService service = new TriageService(Optional.empty(), new KeywordProblemClassifier(), meterRegistry);

        assertThatThrownBy(() -> service.classify(" ", VehicleType.BICYCLE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

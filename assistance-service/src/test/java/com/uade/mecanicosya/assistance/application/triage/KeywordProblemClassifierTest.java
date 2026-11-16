package com.uade.mecanicosya.assistance.application.triage;

import com.uade.mecanicosya.assistance.application.triage.LabeledCases.LabeledCase;
import com.uade.mecanicosya.assistance.application.triage.LabeledCases.Score;
import com.uade.mecanicosya.assistance.domain.TriageSource;
import com.uade.mecanicosya.assistance.domain.Urgency;
import com.uade.mecanicosya.assistance.domain.VehicleType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordProblemClassifierTest {

    private final KeywordProblemClassifier classifier = new KeywordProblemClassifier();

    @Test
    void aBrokenChainNeedsAChainRepair() {
        Triage triage = classifier.classify("Se me cortó la cadena en Palermo", VehicleType.BICYCLE);

        assertThat(triage.category()).isEqualTo(ProblemCategory.CHAIN);
        assertThat(triage.requiredSkill()).isEqualTo("CHAIN_REPAIR");
        assertThat(triage.urgency()).isEqualTo(Urgency.MEDIUM);
        assertThat(triage.source()).isEqualTo(TriageSource.KEYWORDS);
    }

    @Test
    void theMotorDependsOnTheVehicle() {
        assertThat(classifier.classify("el motor no anda", VehicleType.MOTORCYCLE).category())
                .isEqualTo(ProblemCategory.ENGINE);
        assertThat(classifier.classify("el motor no anda", VehicleType.E_BIKE).category())
                .isEqualTo(ProblemCategory.ELECTRICAL);
    }

    @Test
    void brakesThatDoNotStopAreUrgent() {
        Triage triage = classifier.classify("La moto NO FRENA", VehicleType.MOTORCYCLE);

        assertThat(triage.category()).isEqualTo(ProblemCategory.BRAKES);
        assertThat(triage.urgency()).isEqualTo(Urgency.HIGH);
    }

    @Test
    void aNoiseIsNotUrgent() {
        assertThat(classifier.classify("el freno chirría un poco", VehicleType.BICYCLE).urgency())
                .isEqualTo(Urgency.LOW);
    }

    @Test
    void aTextWithoutKnownWordsIsOtherWithLowConfidence() {
        Triage triage = classifier.classify("bici rota", VehicleType.BICYCLE);

        assertThat(triage.category()).isEqualTo(ProblemCategory.OTHER);
        assertThat(triage.confidence()).isLessThan(0.5);
    }

    /**
     * Línea de base contra el set etiquetado. El umbral es el mínimo aceptable para el respaldo; el número real se
     * imprime y queda en docs/CLASIFICACION_CON_IA.md.
     */
    @Test
    void scoresAboveTheBaselineOnTheLabeledCases() {
        List<LabeledCase> cases = LabeledCases.load();
        List<Triage> answers = cases.stream()
                .map(labeled -> classifier.classify(labeled.description(), labeled.vehicleType()))
                .toList();

        Score score = LabeledCases.score(cases, answers);
        System.out.printf("Palabras clave: categoría %d/%d, urgencia %d/%d%n",
                score.categoryHits(), score.total(), score.urgencyHits(), score.total());
        score.misses().forEach(miss -> System.out.println("  " + miss));

        assertThat(cases).hasSize(40);
        assertThat(score.categoryAccuracy()).isGreaterThanOrEqualTo(0.75);
        assertThat(score.urgencyAccuracy()).isGreaterThanOrEqualTo(0.6);
    }
}

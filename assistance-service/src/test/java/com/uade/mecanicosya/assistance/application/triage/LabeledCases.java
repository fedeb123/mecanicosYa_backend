package com.uade.mecanicosya.assistance.application.triage;

import com.uade.mecanicosya.assistance.domain.Urgency;
import com.uade.mecanicosya.assistance.domain.VehicleType;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/** Las 40 descripciones etiquetadas a mano de {@code triage/casos-etiquetados.json}. */
public final class LabeledCases {

    private LabeledCases() {
    }

    public record LabeledCase(int id, VehicleType vehicleType, String description, ProblemCategory category,
                              Urgency urgency) {
    }

    public static List<LabeledCase> load() {
        try (InputStream input = LabeledCases.class.getResourceAsStream("/triage/casos-etiquetados.json")) {
            return JsonMapper.builder().build().readValue(input, new TypeReference<List<LabeledCase>>() {
            });
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Aciertos de categoría y de urgencia de un clasificador sobre los casos. */
    public record Score(int total, int categoryHits, int urgencyHits, List<String> misses) {

        public double categoryAccuracy() {
            return (double) categoryHits / total;
        }

        public double urgencyAccuracy() {
            return (double) urgencyHits / total;
        }
    }

    public static Score score(List<LabeledCase> cases, List<Triage> answers) {
        int categoryHits = 0;
        int urgencyHits = 0;
        List<String> misses = new java.util.ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            LabeledCase expected = cases.get(i);
            Triage actual = answers.get(i);
            boolean categoryOk = expected.category() == actual.category();
            boolean urgencyOk = expected.urgency() == actual.urgency();
            categoryHits += categoryOk ? 1 : 0;
            urgencyHits += urgencyOk ? 1 : 0;
            if (!categoryOk || !urgencyOk) {
                misses.add("#%d %s: esperaba %s/%s, dio %s/%s".formatted(expected.id(), expected.description(),
                        expected.category(), expected.urgency(), actual.category(), actual.urgency()));
            }
        }
        return new Score(cases.size(), categoryHits, urgencyHits, misses);
    }
}

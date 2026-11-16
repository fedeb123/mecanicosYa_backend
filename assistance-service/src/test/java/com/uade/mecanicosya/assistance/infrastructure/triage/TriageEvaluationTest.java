package com.uade.mecanicosya.assistance.infrastructure.triage;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.OutputConfig;
import com.uade.mecanicosya.assistance.application.triage.KeywordProblemClassifier;
import com.uade.mecanicosya.assistance.application.triage.LabeledCases;
import com.uade.mecanicosya.assistance.application.triage.LabeledCases.LabeledCase;
import com.uade.mecanicosya.assistance.application.triage.LabeledCases.Score;
import com.uade.mecanicosya.assistance.application.triage.ProblemCategory;
import com.uade.mecanicosya.assistance.application.triage.Triage;
import com.uade.mecanicosya.assistance.domain.TriageSource;
import com.uade.mecanicosya.assistance.domain.Urgency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mide el clasificador con Claude contra las 40 descripciones etiquetadas: exactitud de categoría y urgencia y
 * latencia. Llama a la API real, así que corre sólo si se pide:
 *
 * <pre>
 * export ANTHROPIC_API_KEY=...   # la clave queda en la terminal, nunca en el repo
 * mvn -pl assistance-service -am test -Dtest=TriageEvaluationTest -Dtriage.eval=true -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * El resultado queda en {@code assistance-service/target/triage-eval/resultado.md}.
 */
@EnabledIfSystemProperty(named = "triage.eval", matches = "true")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class TriageEvaluationTest {

    @Test
    void measuresTheModelOnTheLabeledCases() throws IOException {
        String model = Objects.requireNonNullElse(System.getenv("TRIAGE_MODEL"), "claude-opus-5-5");
        String effort = Objects.requireNonNullElse(System.getenv("TRIAGE_EFFORT"), "low");
        List<LabeledCase> cases = LabeledCases.load();
        KeywordProblemClassifier keywords = new KeywordProblemClassifier();
        List<Triage> aiAnswers = new ArrayList<>();
        List<Triage> keywordAnswers = new ArrayList<>();
        List<Long> latencies = new ArrayList<>();
        int failures = 0;

        try (ClaudeProblemClassifier claude = new ClaudeProblemClassifier(
                AnthropicOkHttpClient.builder()
                        .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                        .timeout(Duration.ofSeconds(30))
                        .maxRetries(2)
                        .build(),
                model,
                OutputConfig.Effort.of(effort),
                2048,
                true
        )) {
            for (LabeledCase labeled : cases) {
                keywordAnswers.add(keywords.classify(labeled.description(), labeled.vehicleType()));
                long start = System.nanoTime();
                try {
                    aiAnswers.add(claude.classify(labeled.description(), labeled.vehicleType()));
                } catch (RuntimeException exception) {
                    failures++;
                    aiAnswers.add(new Triage(ProblemCategory.OTHER, Urgency.MEDIUM, 0, TriageSource.AI));
                    System.out.println("#" + labeled.id() + " falló: " + exception);
                }
                latencies.add((System.nanoTime() - start) / 1_000_000);
            }
        }

        Score ai = LabeledCases.score(cases, aiAnswers);
        Score keyword = LabeledCases.score(cases, keywordAnswers);
        List<Long> sorted = latencies.stream().sorted().toList();
        long median = sorted.get(sorted.size() / 2);
        long p95 = sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);

        StringBuilder report = new StringBuilder()
                .append("# Evaluación de la clasificación\n\n")
                .append("Modelo `").append(model).append("`, esfuerzo `").append(effort).append("`, ")
                .append(cases.size()).append(" casos.\n\n")
                .append("| Clasificador | Categoría | Urgencia |\n|---|---|---|\n")
                .append(row("Claude", ai)).append(row("Palabras clave", keyword))
                .append("\nLatencia de Claude: mediana ").append(median).append(" ms, p95 ").append(p95)
                .append(" ms. Llamadas fallidas: ").append(failures).append(".\n\n")
                .append("## Errores de Claude\n\n");
        ai.misses().forEach(miss -> report.append("- ").append(miss).append('\n'));
        Path output = Path.of("target", "triage-eval", "resultado.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report);
        System.out.println(report);

        assertThat(failures).isZero();
    }

    private static String row(String name, Score score) {
        return "| %s | %d/%d (%.0f%%) | %d/%d (%.0f%%) |%n".formatted(name,
                score.categoryHits(), score.total(), score.categoryAccuracy() * 100,
                score.urgencyHits(), score.total(), score.urgencyAccuracy() * 100);
    }
}

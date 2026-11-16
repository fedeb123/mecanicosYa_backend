package com.uade.mecanicosya.dispatch.domain;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingStrategySelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NearestQualifiedMechanicStrategy.class, BestRatedNearbyMechanicStrategy.class,
                    FastestEtaMechanicStrategy.class)
            .withBean(TravelTimeEstimator.class, () -> new StraightLineTravelTimeEstimator(20, 1.3));

    @Test
    void usesTheNearestStrategyByDefault() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(MatchingStrategy.class)
                .getBean(MatchingStrategy.class)
                .isInstanceOf(NearestQualifiedMechanicStrategy.class));
    }

    @Test
    void switchesToTheBestRatedStrategyByConfiguration() {
        contextRunner
                .withPropertyValues("dispatch.matching.strategy=best-rated", "dispatch.matching.max-distance-km=3")
                .run(context -> assertThat(context)
                        .hasSingleBean(MatchingStrategy.class)
                        .getBean(MatchingStrategy.class)
                        .isInstanceOf(BestRatedNearbyMechanicStrategy.class));
    }

    @Test
    void switchesToTheFastestEtaStrategyByConfiguration() {
        contextRunner
                .withPropertyValues("dispatch.matching.strategy=fastest-eta")
                .run(context -> assertThat(context)
                        .hasSingleBean(MatchingStrategy.class)
                        .getBean(MatchingStrategy.class)
                        .isInstanceOf(FastestEtaMechanicStrategy.class));
    }
}

package com.uade.mecanicosya.dispatch.domain;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingStrategySelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NearestQualifiedMechanicStrategy.class, BestRatedNearbyMechanicStrategy.class);

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
}

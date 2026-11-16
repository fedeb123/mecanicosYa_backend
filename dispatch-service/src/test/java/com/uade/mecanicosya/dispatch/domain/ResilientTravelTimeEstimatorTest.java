package com.uade.mecanicosya.dispatch.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.uade.mecanicosya.dispatch.domain.TravelTimeTestData.mechanic;
import static org.assertj.core.api.Assertions.assertThat;

class ResilientTravelTimeEstimatorTest {

    private final StraightLineTravelTimeEstimator straightLine = new StraightLineTravelTimeEstimator(20, 1.3);
    private final List<MechanicCandidate> mechanics = List.of(
            mechanic("Ada", -34.5800, -58.4255, 4.5),
            mechanic("Grace", -34.5840, -58.4300, 4.0)
    );

    @Test
    void usesTheRoutesWhenTheServiceAnswers() {
        RouteEstimate route = new RouteEstimate(2.4, 7, EtaSource.ROUTE);
        ResilientTravelTimeEstimator estimator = new ResilientTravelTimeEstimator(
                Optional.of((lat, lon, list) -> List.of(route, route)), straightLine);

        assertThat(estimator.estimate(-34.5795, -58.4250, mechanics)).containsExactly(route, route);
    }

    @Test
    void estimatesInAStraightLineWhenTheServiceFails() {
        ResilientTravelTimeEstimator estimator = new ResilientTravelTimeEstimator(Optional.of((lat, lon, list) -> {
            throw new RouteServiceUnavailableException("timeout");
        }), straightLine);

        assertThat(estimator.estimate(-34.5795, -58.4250, mechanics))
                .extracting(RouteEstimate::source)
                .containsOnly(EtaSource.ESTIMATE);
    }

    @Test
    void completesTheMechanicsWithoutARoute() {
        RouteEstimate route = new RouteEstimate(2.4, 7, EtaSource.ROUTE);
        ResilientTravelTimeEstimator estimator = new ResilientTravelTimeEstimator(
                Optional.of((lat, lon, list) -> Arrays.asList(route, null)), straightLine);

        assertThat(estimator.estimate(-34.5795, -58.4250, mechanics))
                .extracting(RouteEstimate::source)
                .containsExactly(EtaSource.ROUTE, EtaSource.ESTIMATE);
    }

    @Test
    void withoutAnApiKeyEstimatesInAStraightLine() {
        ResilientTravelTimeEstimator estimator = new ResilientTravelTimeEstimator(Optional.empty(), straightLine);

        assertThat(estimator.estimate(-34.5795, -58.4250, mechanics)).hasSize(2)
                .allSatisfy(estimate -> assertThat(estimate.source()).isEqualTo(EtaSource.ESTIMATE));
    }
}

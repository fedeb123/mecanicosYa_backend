package com.uade.mecanicosya.dispatch.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.uade.mecanicosya.dispatch.domain.TravelTimeTestData.mechanic;
import static org.assertj.core.api.Assertions.assertThat;

class FastestEtaMechanicStrategyTest {

    private static final double LAT = -34.5795;
    private static final double LON = -58.4250;

    @Test
    void theOneWhoArrivesFirstGoesFirstEvenIfHeIsFartherInAStraightLine() {
        MechanicCandidate near = mechanic("Cerca", -34.5800, -58.4255, 4.0);
        MechanicCandidate fast = mechanic("Rápida", -34.5900, -58.4300, 4.0);
        // El más cercano en línea recta tiene que dar la vuelta por la vía del tren.
        Map<MechanicCandidate, RouteEstimate> routes = Map.of(
                near, new RouteEstimate(3.1, 14, EtaSource.ROUTE),
                fast, new RouteEstimate(1.6, 6, EtaSource.ROUTE)
        );
        FastestEtaMechanicStrategy strategy = new FastestEtaMechanicStrategy(
                (lat, lon, mechanics) -> mechanics.stream().map(routes::get).toList(), 5, 10);

        List<MechanicSelection> ranked = strategy.rank(LAT, LON, List.of(near, fast));

        assertThat(ranked).extracting(selection -> selection.mechanic().name()).containsExactly("Rápida", "Cerca");
        assertThat(ranked.getFirst().etaMinutes()).isEqualTo(6);
        assertThat(ranked.getFirst().distanceKm()).isEqualTo(1.6);
        assertThat(ranked.getFirst().etaSource()).isEqualTo(EtaSource.ROUTE);
    }

    @Test
    void onlyAsksTheRouteOfTheNearestMechanicsInsideTheRadius() {
        List<MechanicCandidate> asked = new ArrayList<>();
        FastestEtaMechanicStrategy strategy = new FastestEtaMechanicStrategy((lat, lon, mechanics) -> {
            asked.addAll(mechanics);
            return new StraightLineTravelTimeEstimator(20, 1.3).estimate(lat, lon, mechanics);
        }, 5, 2);
        MechanicCandidate first = mechanic("Uno", -34.5800, -58.4255, 4.0);
        MechanicCandidate second = mechanic("Dos", -34.5850, -58.4300, 4.0);
        MechanicCandidate third = mechanic("Tres", -34.5900, -58.4350, 4.0);
        MechanicCandidate faraway = mechanic("Lejos", -34.7000, -58.6000, 5.0);

        List<MechanicSelection> ranked = strategy.rank(LAT, LON, List.of(faraway, third, second, first));

        assertThat(asked).containsExactly(first, second);
        assertThat(ranked).hasSize(2);
    }

    @Test
    void atTheSameTimeTheBestRatedGoesFirst() {
        MechanicCandidate good = mechanic("Buena", -34.5800, -58.4255, 3.0);
        MechanicCandidate better = mechanic("Mejor", -34.5810, -58.4260, 4.9);
        RouteEstimate same = new RouteEstimate(1.0, 4, EtaSource.ROUTE);
        FastestEtaMechanicStrategy strategy = new FastestEtaMechanicStrategy(
                (lat, lon, mechanics) -> mechanics.stream().map(mechanic -> same).toList(), 5, 10);

        assertThat(strategy.rank(LAT, LON, List.of(good, better)))
                .extracting(selection -> selection.mechanic().name())
                .containsExactly("Mejor", "Buena");
    }
}

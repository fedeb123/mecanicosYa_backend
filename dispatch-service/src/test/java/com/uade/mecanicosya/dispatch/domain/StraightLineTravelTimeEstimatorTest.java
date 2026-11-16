package com.uade.mecanicosya.dispatch.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.uade.mecanicosya.dispatch.domain.TravelTimeTestData.mechanic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StraightLineTravelTimeEstimatorTest {

    @Test
    void estimatesTheTimeFromTheStraightDistanceWithADetour() {
        StraightLineTravelTimeEstimator estimator = new StraightLineTravelTimeEstimator(20, 1.3);
        // Obelisco a Plaza de Mayo: unos 1,1 km en línea recta.
        MechanicCandidate obelisco = mechanic("Ada", -34.6037, -58.3816, 4.5);

        RouteEstimate estimate = estimator.estimate(-34.6083, -58.3712, List.of(obelisco)).getFirst();

        assertThat(estimate.source()).isEqualTo(EtaSource.ESTIMATE);
        assertThat(estimate.distanceKm()).isBetween(1.0, 1.2);
        // 1,07 km * 1,3 a 20 km/h son 4,2 minutos: se redondea para arriba.
        assertThat(estimate.etaMinutes()).isEqualTo(5);
    }

    @Test
    void rejectsAnInvalidConfiguration() {
        assertThatThrownBy(() -> new StraightLineTravelTimeEstimator(0, 1.3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StraightLineTravelTimeEstimator(20, 0.9))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

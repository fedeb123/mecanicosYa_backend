package com.uade.mecanicosya.dispatch.application;

import com.uade.mecanicosya.dispatch.domain.EtaSource;
import com.uade.mecanicosya.dispatch.domain.RoutePlan;
import com.uade.mecanicosya.dispatch.domain.RouteServiceUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RouteFacadeTest {

    @Test
    void withoutAnApiKeyTheRouteIsAStraightLineWithoutSteps() {
        RouteFacade facade = new RouteFacade(Optional.empty(), 20, 1.3);

        RoutePlan plan = facade.plan(-34.6045, -58.3830, -34.6037, -58.3816);

        assertThat(plan.source()).isEqualTo(EtaSource.ESTIMATE);
        assertThat(plan.points()).hasSize(2);
        assertThat(plan.steps()).isEmpty();
        assertThat(plan.distanceKm()).isBetween(0.1, 0.2);
        assertThat(plan.durationMinutes()).isEqualTo(1);
    }

    @Test
    void whenTheRouteServiceFailsItFallsBackToAStraightLine() {
        RouteFacade facade = new RouteFacade(Optional.of((a, b, c, d) -> {
            throw new RouteServiceUnavailableException("down");
        }), 20, 1.3);

        assertThat(facade.plan(-34.6045, -58.3830, -34.6037, -58.3816).source()).isEqualTo(EtaSource.ESTIMATE);
    }

    @Test
    void usesTheRouteServiceWhenItAnswers() {
        RoutePlan byStreet = new RoutePlan(EtaSource.ROUTE, 0.43, 3, List.of(), List.of());
        RouteFacade facade = new RouteFacade(Optional.of((a, b, c, d) -> byStreet), 20, 1.3);

        assertThat(facade.plan(-34.6045, -58.3830, -34.6037, -58.3816)).isSameAs(byStreet);
    }
}

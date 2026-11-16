package com.uade.mecanicosya.dispatch.api;

import com.uade.mecanicosya.dispatch.application.RouteFacade;
import com.uade.mecanicosya.dispatch.domain.EtaSource;
import com.uade.mecanicosya.dispatch.domain.RoutePlan;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
@Tag(name = "Routes")
public class RouteController {

    private final RouteFacade routes;

    public RouteController(RouteFacade routes) {
        this.routes = routes;
    }

    @GetMapping
    @Operation(summary = "Route from the mechanic to the rider, with a line for the map and turn-by-turn steps "
            + "(role RIDER, MECHANIC or ADMIN)",
            description = "By street with OpenRouteService, with the same profile as the offers' arrival time "
                    + "(ORS_PROFILE). Without an "
                    + "API key, or if it does not answer, a straight line without steps (source ESTIMATE).")
    public RouteResponse route(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double fromLatitude,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double fromLongitude,
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double toLatitude,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double toLongitude
    ) {
        return RouteResponse.from(routes.plan(fromLatitude, fromLongitude, toLatitude, toLongitude));
    }

    public record RouteResponse(
            EtaSource source,
            double distanceKm,
            int durationMinutes,
            List<List<Double>> points,
            List<StepResponse> steps
    ) {
        static RouteResponse from(RoutePlan plan) {
            return new RouteResponse(
                    plan.source(),
                    plan.distanceKm(),
                    plan.durationMinutes(),
                    plan.points().stream().map(point -> List.of(point[0], point[1])).toList(),
                    plan.steps().stream().map(StepResponse::from).toList()
            );
        }
    }

    public record StepResponse(String instruction, String street, int distanceMeters, int durationSeconds,
                               double latitude, double longitude) {
        static StepResponse from(RoutePlan.Step step) {
            return new StepResponse(step.instruction(), step.street(), (int) Math.round(step.distanceMeters()),
                    (int) Math.round(step.durationSeconds()), step.latitude(), step.longitude());
        }
    }
}

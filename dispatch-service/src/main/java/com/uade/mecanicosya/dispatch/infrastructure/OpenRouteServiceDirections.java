package com.uade.mecanicosya.dispatch.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uade.mecanicosya.dispatch.domain.EtaSource;
import com.uade.mecanicosya.dispatch.domain.GeoDistance;
import com.uade.mecanicosya.dispatch.domain.RoutePlan;
import com.uade.mecanicosya.dispatch.domain.RoutePlanner;
import com.uade.mecanicosya.dispatch.domain.RouteServiceUnavailableException;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ruta con pasos en castellano con la Directions API de OpenRouteService. A la API le llegan sólo las dos
 * coordenadas, sin nombres ni ids.
 */
public class OpenRouteServiceDirections implements RoutePlanner {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient restClient;
    private final String profile;

    /** @param profile el mismo que usa el tiempo de las ofertas, así la navegación y la oferta coinciden */
    public OpenRouteServiceDirections(RestClient.Builder builder, String baseUrl, String apiKey, String profile,
                                      Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", apiKey)
                .build();
        this.profile = profile;
    }

    @Override
    public RoutePlan plan(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
        // ORS usa [longitud, latitud].
        Map<String, Object> body = Map.of(
                "coordinates", List.of(List.of(fromLongitude, fromLatitude), List.of(toLongitude, toLatitude)),
                "language", "es",
                "instructions", true,
                "units", "m"
        );
        byte[] json = JSON.writeValueAsBytes(body);
        DirectionsResponse response;
        try {
            response = restClient.post()
                    .uri("/v2/directions/{profile}/geojson", profile)
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(json.length)
                    .accept(MediaType.APPLICATION_JSON, MediaType.valueOf("application/geo+json"))
                    .body(json)
                    .retrieve()
                    .body(DirectionsResponse.class);
        } catch (RestClientException exception) {
            throw new RouteServiceUnavailableException("OpenRouteService did not answer: " + exception.getMessage(),
                    exception);
        }
        if (response == null || response.features() == null || response.features().isEmpty()) {
            throw new RouteServiceUnavailableException("OpenRouteService found no route");
        }
        Feature feature = response.features().getFirst();
        List<List<Double>> coordinates = feature.geometry() == null ? null : feature.geometry().coordinates();
        if (coordinates == null || coordinates.size() < 2 || feature.properties() == null
                || feature.properties().summary() == null) {
            throw new RouteServiceUnavailableException("OpenRouteService answered a route without a line");
        }
        List<double[]> points = coordinates.stream().map(c -> new double[]{c.get(1), c.get(0)}).toList();
        List<RoutePlan.Step> steps = new ArrayList<>();
        if (feature.properties().segments() != null) {
            for (Segment segment : feature.properties().segments()) {
                if (segment.steps() == null) {
                    continue;
                }
                for (Step step : segment.steps()) {
                    int start = step.wayPoints() == null || step.wayPoints().isEmpty() ? 0
                            : Math.min(step.wayPoints().getFirst(), points.size() - 1);
                    double[] at = points.get(start);
                    steps.add(new RoutePlan.Step(step.instruction(), blankToNull(step.name()), step.distance(),
                            step.duration(), at[0], at[1]));
                }
            }
        }
        Summary summary = feature.properties().summary();
        return new RoutePlan(
                EtaSource.ROUTE,
                GeoDistance.round(summary.distance() / 1000),
                (int) Math.max(1, Math.ceil(summary.duration() / 60)),
                points,
                steps
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() || "-".equals(value) ? null : value;
    }

    record DirectionsResponse(List<Feature> features) {
    }

    record Feature(Geometry geometry, Properties properties) {
    }

    record Geometry(List<List<Double>> coordinates) {
    }

    record Properties(Summary summary, List<Segment> segments) {
    }

    record Summary(double distance, double duration) {
    }

    record Segment(List<Step> steps) {
    }

    record Step(double distance, double duration, String instruction, String name,
                @JsonProperty("way_points") List<Integer> wayPoints) {
    }
}

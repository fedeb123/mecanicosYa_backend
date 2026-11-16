package com.uade.mecanicosya.dispatch.infrastructure;

import com.uade.mecanicosya.dispatch.domain.EtaSource;
import com.uade.mecanicosya.dispatch.domain.GeoDistance;
import com.uade.mecanicosya.dispatch.domain.MechanicCandidate;
import com.uade.mecanicosya.dispatch.domain.RouteEstimate;
import com.uade.mecanicosya.dispatch.domain.RouteServiceUnavailableException;
import com.uade.mecanicosya.dispatch.domain.TravelTimeEstimator;
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
import java.util.stream.IntStream;

/**
 * Tiempo de llegada por calle con la Matrix API de OpenRouteService: una sola llamada con todos los mecánicos como
 * origen y el pedido como destino. A la API le llegan sólo coordenadas, sin nombres ni ids.
 *
 * <p>Una celda {@code null} en la respuesta (no hay ruta para ese mecánico) devuelve {@code null} en esa posición,
 * y {@link com.uade.mecanicosya.dispatch.domain.ResilientTravelTimeEstimator} la completa en línea recta.
 */
public class OpenRouteServiceClient implements TravelTimeEstimator {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient restClient;
    private final String profile;

    public OpenRouteServiceClient(RestClient.Builder builder, String baseUrl, String apiKey, String profile,
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
    public List<RouteEstimate> estimate(double toLatitude, double toLongitude, List<MechanicCandidate> mechanics) {
        if (mechanics.isEmpty()) {
            return List.of();
        }
        // ORS usa [longitud, latitud]. Los mecánicos son los orígenes y el pedido, el último punto, es el destino.
        List<List<Double>> locations = new ArrayList<>();
        mechanics.forEach(mechanic -> locations.add(List.of(mechanic.longitude(), mechanic.latitude())));
        locations.add(List.of(toLongitude, toLatitude));
        Map<String, Object> body = Map.of(
                "locations", locations,
                "sources", IntStream.range(0, mechanics.size()).boxed().toList(),
                "destinations", List.of(mechanics.size()),
                "metrics", List.of("duration", "distance"),
                "units", "km"
        );
        // Se serializa antes para mandar Content-Length: algunos servidores no aceptan un cuerpo chunked.
        byte[] json = JSON.writeValueAsBytes(body);
        MatrixResponse response;
        try {
            response = restClient.post()
                    .uri("/v2/matrix/{profile}", profile)
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(json.length)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(MatrixResponse.class);
        } catch (RestClientException exception) {
            throw new RouteServiceUnavailableException("OpenRouteService did not answer: " + exception.getMessage(),
                    exception);
        }
        if (response == null || response.durations() == null || response.durations().size() != mechanics.size()) {
            throw new RouteServiceUnavailableException("OpenRouteService answered an unexpected matrix");
        }
        List<RouteEstimate> estimates = new ArrayList<>(mechanics.size());
        for (int index = 0; index < mechanics.size(); index++) {
            Double seconds = cell(response.durations(), index);
            Double km = cell(response.distances(), index);
            estimates.add(seconds == null || km == null ? null
                    : new RouteEstimate(GeoDistance.round(km), (int) Math.ceil(seconds / 60), EtaSource.ROUTE));
        }
        return estimates;
    }

    private static Double cell(List<List<Double>> matrix, int row) {
        if (matrix == null || row >= matrix.size() || matrix.get(row) == null || matrix.get(row).isEmpty()) {
            return null;
        }
        return matrix.get(row).getFirst();
    }

    /** Lo que se usa de la respuesta: segundos y kilómetros de cada origen a cada destino. */
    record MatrixResponse(List<List<Double>> durations, List<List<Double>> distances) {
    }
}

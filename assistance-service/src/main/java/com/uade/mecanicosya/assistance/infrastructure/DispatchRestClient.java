package com.uade.mecanicosya.assistance.infrastructure;

import com.uade.mecanicosya.assistance.application.DispatchCommand;
import com.uade.mecanicosya.assistance.application.DispatchFailedException;
import com.uade.mecanicosya.assistance.application.DispatchPort;
import com.uade.mecanicosya.assistance.application.DispatchOutcome;
import com.uade.mecanicosya.assistance.application.DispatchUnavailableException;
import com.uade.mecanicosya.shared.observability.CorrelationIdPropagator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class DispatchRestClient implements DispatchPort {

    private final RestClient restClient;

    public DispatchRestClient(
            RestClient.Builder builder,
            @Value("${clients.dispatch-service.base-url}") String baseUrl,
            @Value("${clients.dispatch-service.timeout:5s}") Duration timeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(new CorrelationIdPropagator())
                .build();
    }

    @Override
    public DispatchOutcome dispatch(DispatchCommand command) {
        DispatchResponse response;
        try {
            response = restClient.post()
                    .uri("/api/dispatches")
                    .body(command)
                    .retrieve()
                    .body(DispatchResponse.class);
        } catch (ResourceAccessException exception) {
            throw new DispatchUnavailableException(
                    "dispatch-service did not respond: " + exception.getMessage(),
                    exception
            );
        } catch (RestClientResponseException exception) {
            throw new DispatchFailedException(
                    "dispatch-service answered " + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new DispatchFailedException(
                    "dispatch-service returned an unreadable response: " + exception.getMessage(),
                    exception
            );
        }
        return toOutcome(response);
    }

    @Override
    public void cancel(UUID assistanceId) {
        try {
            restClient.post()
                    .uri("/api/dispatches/{assistanceId}/cancel", assistanceId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (!exception.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new DispatchFailedException(
                        "dispatch-service answered " + exception.getStatusCode().value() + " to the cancellation",
                        exception
                );
            }
            // Nunca llegó a despacharse: no hay ofertas que retirar.
        } catch (RestClientException exception) {
            throw new DispatchUnavailableException(
                    "dispatch-service did not respond to the cancellation: " + exception.getMessage(),
                    exception
            );
        }
    }

    private static DispatchOutcome toOutcome(DispatchResponse response) {
        return switch (response) {
            case null -> throw new DispatchFailedException("dispatch-service returned an empty response");
            case DispatchResponse(_, _, _, String status, Instant expiresAt, List<Object> offers, _)
                    when "OFFERED".equals(status) && expiresAt != null ->
                    new DispatchOutcome.Offered(expiresAt, offers == null ? 0 : offers.size());
            case DispatchResponse(_, UUID mechanicId, Double distanceKm, String status, _, _, _)
                    when "MATCHED".equals(status) && mechanicId != null ->
                    new DispatchOutcome.Matched(mechanicId, distanceKm == null ? 0 : distanceKm);
            case DispatchResponse(_, _, _, String status, _, _, _) when "NO_CANDIDATE".equals(status) ->
                    new DispatchOutcome.NoCandidate();
            case DispatchResponse unknown -> throw new DispatchFailedException(
                    "dispatch-service answered an unknown status: " + unknown.status()
            );
        };
    }
}

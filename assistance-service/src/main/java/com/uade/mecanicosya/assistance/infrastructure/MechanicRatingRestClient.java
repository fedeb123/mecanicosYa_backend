package com.uade.mecanicosya.assistance.infrastructure;

import com.uade.mecanicosya.assistance.application.MechanicRatingPort;
import com.uade.mecanicosya.assistance.application.MechanicServiceFailedException;
import com.uade.mecanicosya.assistance.application.MechanicServiceUnavailableException;
import com.uade.mecanicosya.shared.observability.CorrelationIdPropagator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Component
public class MechanicRatingRestClient implements MechanicRatingPort {

    private final RestClient restClient;

    public MechanicRatingRestClient(
            RestClient.Builder builder,
            @Value("${clients.mechanic-service.base-url}") String baseUrl,
            @Value("${clients.mechanic-service.timeout:3s}") Duration timeout
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
    public void rate(UUID mechanicId, UUID assistanceId, int score) {
        try {
            restClient.post()
                    .uri("/api/mechanics/{id}/ratings", mechanicId)
                    .body(new RatingRequest(assistanceId, score))
                    .retrieve()
                    .toBodilessEntity();
        } catch (ResourceAccessException exception) {
            throw new MechanicServiceUnavailableException(
                    "mechanic-service did not respond: " + exception.getMessage(),
                    exception
            );
        } catch (RestClientResponseException exception) {
            throw new MechanicServiceFailedException(
                    "mechanic-service answered " + exception.getStatusCode().value() + " to the rating",
                    exception
            );
        } catch (RestClientException exception) {
            throw new MechanicServiceFailedException(
                    "mechanic-service could not take the rating: " + exception.getMessage(),
                    exception
            );
        }
    }

    record RatingRequest(UUID assistanceId, int score) {
    }
}

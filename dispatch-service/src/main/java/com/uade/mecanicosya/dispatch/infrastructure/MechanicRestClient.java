package com.uade.mecanicosya.dispatch.infrastructure;

import com.uade.mecanicosya.dispatch.application.MechanicDirectory;
import com.uade.mecanicosya.dispatch.application.MechanicDirectoryUnavailableException;
import com.uade.mecanicosya.dispatch.domain.MechanicCandidate;
import com.uade.mecanicosya.shared.observability.CorrelationIdPropagator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
public class MechanicRestClient implements MechanicDirectory {

    private final RestClient restClient;

    public MechanicRestClient(
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
    public List<MechanicCandidate> findCandidates(String skill, String vehicleType) {
        try {
            List<MechanicCandidate> candidates = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/mechanics/candidates")
                            .queryParam("skill", skill)
                            .queryParamIfPresent("vehicleType", Optional.ofNullable(vehicleType))
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return candidates == null ? List.of() : candidates;
        } catch (RestClientException exception) {
            throw new MechanicDirectoryUnavailableException(
                    "mechanic-service could not return candidates: " + exception.getMessage(),
                    exception
            );
        }
    }
}

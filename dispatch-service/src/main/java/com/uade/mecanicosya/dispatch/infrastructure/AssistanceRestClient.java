package com.uade.mecanicosya.dispatch.infrastructure;

import com.uade.mecanicosya.dispatch.application.AssistanceAssignmentPort;
import com.uade.mecanicosya.dispatch.application.AssistanceClosedException;
import com.uade.mecanicosya.dispatch.application.AssistanceServiceUnavailableException;
import com.uade.mecanicosya.shared.observability.CorrelationIdPropagator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Component
public class AssistanceRestClient implements AssistanceAssignmentPort {

    private final RestClient restClient;

    public AssistanceRestClient(
            RestClient.Builder builder,
            @Value("${clients.assistance-service.base-url}") String baseUrl,
            @Value("${clients.assistance-service.timeout:3s}") Duration timeout
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
    public void assign(UUID assistanceId, UUID mechanicId) {
        try {
            restClient.post()
                    .uri("/api/assistances/{id}/assign", assistanceId)
                    .body(new AssignRequest(mechanicId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
                throw new AssistanceClosedException(assistanceId);
            }
            throw new AssistanceServiceUnavailableException(
                    "assistance-service answered " + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new AssistanceServiceUnavailableException(
                    "assistance-service did not respond: " + exception.getMessage(),
                    exception
            );
        }
    }

    record AssignRequest(UUID mechanicId) {
    }
}

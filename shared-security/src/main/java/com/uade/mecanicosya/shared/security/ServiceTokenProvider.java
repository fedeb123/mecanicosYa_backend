package com.uade.mecanicosya.shared.security;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Pide a identity-service el token de este servicio (rol {@code SERVICE}) con su id y su secreto, y lo reutiliza
 * hasta un minuto antes de que venza.
 */
public class ServiceTokenProvider {

    static final Duration RENEW_BEFORE = Duration.ofMinutes(1);

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;

    private String token;
    private Instant expiresAt = Instant.MIN;

    public ServiceTokenProvider(String identityUrl, String clientId, String clientSecret, Duration timeout, Clock clock) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().baseUrl(identityUrl).requestFactory(requestFactory).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    public synchronized String token() {
        if (token == null || clock.instant().isAfter(expiresAt.minus(RENEW_BEFORE))) {
            ServiceTokenResponse response;
            try {
                response = restClient.post()
                        .uri("/api/auth/service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new ServiceTokenRequest(clientId, clientSecret))
                        .retrieve()
                        .body(ServiceTokenResponse.class);
            } catch (RestClientException exception) {
                throw new ServiceTokenUnavailableException(
                        "identity-service did not issue a token for " + clientId + ": " + exception.getMessage(),
                        exception
                );
            }
            if (response == null || response.accessToken() == null) {
                throw new ServiceTokenUnavailableException("identity-service returned an empty token", null);
            }
            token = response.accessToken();
            expiresAt = response.expiresAt();
        }
        return token;
    }

    record ServiceTokenRequest(String clientId, String clientSecret) {
    }

    record ServiceTokenResponse(String accessToken, Instant expiresAt) {
    }
}

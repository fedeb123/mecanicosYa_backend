package com.uade.mecanicosya.shared.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Optional;

/**
 * Agrega el token de servicio a las llamadas REST entre servicios. Sin secreto configurado (los tests) no agrega
 * nada. No se usa con APIs externas, que tienen su propia clave.
 */
public class ServiceTokenInterceptor implements ClientHttpRequestInterceptor {

    private final Optional<ServiceTokenProvider> provider;

    public ServiceTokenInterceptor(Optional<ServiceTokenProvider> provider) {
        this.provider = provider;
    }

    public static ServiceTokenInterceptor disabled() {
        return new ServiceTokenInterceptor(Optional.empty());
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (provider.isPresent() && !request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
            request.getHeaders().setBearerAuth(provider.get().token());
        }
        return execution.execute(request, body);
    }
}

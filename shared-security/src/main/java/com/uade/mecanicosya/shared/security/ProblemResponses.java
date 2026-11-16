package com.uade.mecanicosya.shared.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Las respuestas 401 y 403 de los filtros de seguridad, con el mismo formato ProblemDetail que el resto de los
 * errores de la API.
 */
public final class ProblemResponses {

    private ProblemResponses() {
    }

    public static AuthenticationEntryPoint unauthorized() {
        return (request, response, exception) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            write(response, 401, "Unauthorized", "Missing, expired or invalid access token");
        };
    }

    public static AccessDeniedHandler forbidden() {
        return (request, response, exception) ->
                write(response, 403, "Forbidden", "Your role cannot perform this operation");
    }

    static void write(HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\"}");
    }
}

package com.uade.mecanicosya.shared.security;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Arma el filtro de seguridad de cada servicio: sin sesión, con el token de identity-service y con las reglas
 * por rol que pasa el servicio. Swagger, el health check y {@code /error} quedan abiertos; todo lo demás pide token.
 */
public class ApiSecurity {

    static final String[] PUBLIC_PATHS = {
            "/error",
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final JwtAuthenticationConverter authenticationConverter;

    public ApiSecurity() {
        this.authenticationConverter = new JwtAuthenticationConverter();
        this.authenticationConverter.setJwtGrantedAuthoritiesConverter(new JwtRoleConverter());
    }

    public SecurityFilterChain filterChain(
            HttpSecurity http,
            Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> rules
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(PUBLIC_PATHS).permitAll();
                    rules.customize(authorize);
                    authorize.anyRequest().authenticated();
                })
                .oauth2ResourceServer(server -> server
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter))
                        .authenticationEntryPoint(ProblemResponses.unauthorized())
                        .accessDeniedHandler(ProblemResponses.forbidden()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(ProblemResponses.unauthorized())
                        .accessDeniedHandler(ProblemResponses.forbidden()));
        return http.build();
    }
}

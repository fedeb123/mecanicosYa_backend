package com.uade.mecanicosya.shared.security;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Lo que comparten los servicios que validan el token de identity-service. La clave pública se baja de
 * {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}; cada servicio declara sus reglas con
 * {@link ApiSecurity}.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    ApiSecurity apiSecurity() {
        return new ApiSecurity();
    }

    @Bean
    SecurityExceptionHandler securityExceptionHandler() {
        return new SecurityExceptionHandler();
    }

    @Bean
    WebMvcConfigurer currentUserResolver() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CurrentUserArgumentResolver());
            }
        };
    }

    /**
     * Sin {@code mecanicosya.security.service-client.secret} (los tests, o un servicio que no llama a otros) el
     * interceptor no agrega nada.
     */
    @Bean
    ServiceTokenInterceptor serviceTokenInterceptor(
            @Value("${mecanicosya.security.identity-url:http://localhost:8084}") String identityUrl,
            @Value("${mecanicosya.security.service-client.id:${spring.application.name}}") String clientId,
            @Value("${mecanicosya.security.service-client.secret:}") String clientSecret,
            @Value("${mecanicosya.security.service-client.timeout:3s}") Duration timeout
    ) {
        if (clientSecret.isBlank()) {
            return ServiceTokenInterceptor.disabled();
        }
        return new ServiceTokenInterceptor(Optional.of(
                new ServiceTokenProvider(identityUrl, clientId, clientSecret, timeout, Clock.systemUTC())
        ));
    }

    /** Suma el botón "Authorize" a Swagger UI para pegar el access token. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(OpenApiCustomizer.class)
    static class OpenApiSecurity {

        static final String SCHEME = "bearerAuth";

        @Bean
        OpenApiCustomizer bearerAuthentication() {
            return openApi -> {
                if (openApi.getComponents() == null) {
                    openApi.setComponents(new Components());
                }
                openApi.getComponents().addSecuritySchemes(SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));
                openApi.addSecurityItem(new SecurityRequirement().addList(SCHEME));
            };
        }
    }
}

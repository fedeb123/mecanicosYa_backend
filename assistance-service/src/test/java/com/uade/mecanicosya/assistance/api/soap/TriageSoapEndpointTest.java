package com.uade.mecanicosya.assistance.api.soap;

import com.uade.mecanicosya.shared.security.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El servicio SOAP por HTTP de punta a punta: el WSDL publicado, un sobre válido, uno que no cumple el esquema
 * y uno sin token. Sin clave de Anthropic clasifica con palabras clave. Si no hay Docker, se saltea.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "messaging.outbox.relay.enabled=false",
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.admin.auto-create=false",
                "triage.anthropic.api-key="
        }
)
@Import(TriageSoapEndpointTest.Tokens.class)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
class TriageSoapEndpointTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @TestConfiguration
    static class Tokens {
        @Bean
        JwtDecoder jwtDecoder() {
            return TestJwt.decoder();
        }
    }

    @Test
    void theWsdlIsPublishedWithoutAToken() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ws/triage.wsdl")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("wsdl:definitions")
                .contains("name=\"ClassifyProblem\"")
                .contains("TriageService")
                .contains("http://localhost:" + port + "/ws");
    }

    @Test
    void aValidEnvelopeIsClassified() throws Exception {
        HttpResponse<String> response = post(envelope("Se me cortó la cadena y estoy parado en Palermo", "BICYCLE"),
                TestJwt.rider(UUID.randomUUID()));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("ClassifyProblemResponse")
                .containsPattern("<(\\w+:)?category>CHAIN</(\\w+:)?category>")
                .contains("KEYWORDS");
    }

    @Test
    void anEnvelopeThatBreaksTheSchemaGetsAClientFault() throws Exception {
        HttpResponse<String> response = post(envelope("Pinché la rueda", "TRUCK"), TestJwt.rider(UUID.randomUUID()));

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).contains("Fault").contains("Client").contains("Validation error");
    }

    @Test
    void aMessageWithoutATokenIsRejected() throws Exception {
        HttpResponse<String> response = post(envelope("Pinché la rueda", "BICYCLE"), null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> post(String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ws"))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"\"")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String envelope(String description, String vehicleType) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:tri="http://mecanicosya.com/ws/triage">
                   <soapenv:Header/>
                   <soapenv:Body>
                      <tri:ClassifyProblemRequest>
                         <tri:description>%s</tri:description>
                         <tri:vehicleType>%s</tri:vehicleType>
                      </tri:ClassifyProblemRequest>
                   </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(description, vehicleType);
    }
}

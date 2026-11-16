package com.uade.mecanicosya.identity;

import com.uade.mecanicosya.shared.messaging.MessagingConfiguration;
import com.uade.mecanicosya.shared.observability.ObservabilityConfiguration;
import com.uade.mecanicosya.shared.security.SecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@ConfigurationPropertiesScan
@Import({ObservabilityConfiguration.class, MessagingConfiguration.class, SecurityConfiguration.class})
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}

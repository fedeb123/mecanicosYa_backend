package com.uade.mecanicosya.assistance;

import com.uade.mecanicosya.shared.messaging.OutboxConfiguration;
import com.uade.mecanicosya.shared.security.SecurityConfiguration;
import com.uade.mecanicosya.shared.observability.ObservabilityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({ObservabilityConfiguration.class, OutboxConfiguration.class, SecurityConfiguration.class})
public class AssistanceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssistanceServiceApplication.class, args);
    }
}

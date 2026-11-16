package com.uade.mecanicosya.dispatch;

import com.uade.mecanicosya.shared.messaging.OutboxConfiguration;
import com.uade.mecanicosya.shared.observability.ObservabilityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({ObservabilityConfiguration.class, OutboxConfiguration.class})
public class DispatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DispatchServiceApplication.class, args);
    }
}

package com.uade.mecanicosya.mechanics;

import com.uade.mecanicosya.shared.observability.ObservabilityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(ObservabilityConfiguration.class)
public class MechanicServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MechanicServiceApplication.class, args);
    }
}

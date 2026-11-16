package com.uade.mecanicosya.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Crea el admin inicial con {@code ADMIN_EMAIL} y {@code ADMIN_PASSWORD}. Es la única forma de tener un admin:
 * el alta pública sólo crea repartidores y mecánicos.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AuthFacade authFacade;
    private final IdentityProperties properties;

    public AdminBootstrap(AuthFacade authFacade, IdentityProperties properties) {
        this.authFacade = authFacade;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        IdentityProperties.Admin admin = properties.admin();
        if (admin == null || isBlank(admin.email()) || isBlank(admin.password())) {
            LOGGER.warn("ADMIN_EMAIL or ADMIN_PASSWORD is not set: no admin account, so nobody can approve mechanics");
            return;
        }
        authFacade.ensureAdmin(admin.email(), admin.password());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

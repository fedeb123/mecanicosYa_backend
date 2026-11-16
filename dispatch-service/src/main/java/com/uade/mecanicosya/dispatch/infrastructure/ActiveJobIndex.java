package com.uade.mecanicosya.dispatch.infrastructure;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Un mecánico tiene a lo sumo un trabajo abierto. Hibernate no sabe crear índices parciales, así que este se crea al
 * arrancar, después de que Hibernate actualizó las tablas (por eso depende del {@link EntityManagerFactory}).
 *
 * <p>Con el índice, si el mismo mecánico acepta dos pedidos a la vez, la base deja pasar sólo uno: el otro choca con
 * el índice y recibe 409 {@code MECHANIC_BUSY}.
 */
@Component
public class ActiveJobIndex {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveJobIndex.class);

    static final String CREATE_INDEX = """
            create unique index if not exists dispatch_matches_one_active_job_per_mechanic
                on dispatch_matches (mechanic_id)
             where status = 'MATCHED' and completed_at is null
            """;

    private final JdbcTemplate jdbcTemplate;

    public ActiveJobIndex(JdbcTemplate jdbcTemplate, EntityManagerFactory schemaReady) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void create() {
        try {
            jdbcTemplate.execute(CREATE_INDEX);
        } catch (DataAccessException exception) {
            // Pasa con datos de antes de este cambio, si un mecánico ya quedó con dos trabajos abiertos. El chequeo
            // al aceptar sigue andando; con una base limpia (docker compose down --volumes) el índice se crea.
            LOGGER.warn("Could not create the one-active-job-per-mechanic index: {}", exception.getMessage());
        }
    }
}

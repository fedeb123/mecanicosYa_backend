# Guía de evidencias

Las evidencias de texto y las capturas se generan con un solo comando (requiere Docker Desktop):

```bash
./scripts/evidencias.sh
```

El resultado queda en [`evidencias/`](evidencias/README.md). El listado de abajo sirve como guía para la defensa.

## Capturas recomendadas

1. `docker compose ps` con PostgreSQL y los tres servicios saludables.
2. Swagger de cada servicio.
3. Alta de dos mecánicos con habilidades diferentes.
4. Actualización de disponibilidad.
5. Alta de una asistencia.
6. Log del evento de dominio y de las dos llamadas REST remotas.
7. Resultado persistido en `dispatch-service`.
8. Asistencia actualizada a `MATCHED` con el identificador del mecánico.
9. Respuesta de validación inválida con Problem Details.
10. Resultado exitoso de `mvn test`.

## Defensa breve sugerida

1. Explicar el problema y el alcance exclusivamente backend.
2. Mostrar el diagrama de componentes.
3. Recorrer una petición desde Controller hasta Repository.
4. Señalar cada patrón pedido en el código.
5. Ejecutar `scripts/demo.sh`.
6. Mostrar que cada microservicio puede consultarse de manera independiente.
7. Explicar que SOAP, mensajería e IA corresponden a la siguiente etapa.


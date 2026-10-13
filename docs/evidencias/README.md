# Evidencias de ejecución

Generadas sobre el entorno de Docker Compose. Cada archivo de texto incluye la fecha, el commit, los comandos ejecutados y sus respuestas completas.

## Índice

| # | Evidencia | Qué demuestra | Requisito |
|---|---|---|---|
| 01 | [Build y tests](01-build-y-tests.txt) · [GitHub Actions](capturas/01-github-actions.png) | `./mvnw clean verify` con JDK 25: los cuatro módulos compilan y pasan los tests | Gestión de dependencias (Maven) |
| 02 | [docker compose ps](02-docker-compose-ps.txt) | PostgreSQL, los tres servicios y nginx levantados y saludables | Entorno y despliegue en contenedores |
| 03 | OpenAPI: [mechanic](03-openapi-mechanic-service.json) · [assistance](03-openapi-assistance-service.json) · [dispatch](03-openapi-dispatch-service.json) — Swagger: [mechanic](capturas/03-swagger-mechanic-service.png) · [assistance](capturas/03-swagger-assistance-service.png) · [dispatch](capturas/03-swagger-dispatch-service.png) | Contratos REST publicados por cada servicio | Servicios orientados a componentes |
| 04 | [Bases por servicio](04-bases-por-servicio.txt) | Tablas de `mechanics_db`, `assistances_db` y `dispatch_db`, e historial de estados persistido | Componente de acceso a datos |
| 05 | [Flujo completo](05-demo-flujo-completo.txt) | Alta de mecánicos, cliente, vehículo y asistencia hasta quedar `MATCHED` | Aplicación funcional |
| 06 | [Acceso local](06-acceso-local-logs.txt) | En un mismo hilo y proceso: consultas e inserts del repositorio, publicación del evento y reacción del listener | Invocación directa · eventos de dominio (Observer) |
| 07 | [Acceso remoto](07-acceso-remoto-correlation-id.txt) | El mismo `X-Correlation-Id` en los logs de assistance, dispatch y mechanic | Acceso remoto por interfaz (REST) |
| 08 | [Strategy](08-strategy-nearest-vs-best-rated.txt) | El mismo pedido da `MATCHED` con `nearest` y `NO_CANDIDATE` con `best-rated` (radio de 5 km), sólo cambiando configuración | Patrón Strategy · configuración externalizada |
| 09 | [Errores](09-errores-problem-details.txt) | Respuestas 400, 404 y 409 en formato Problem Details | Validación y manejo de errores |
| 10 | [Fallas remotas](10-fallas-remotas-y-reintento.txt) | Servicio remoto sin responder: timeout de 5 s y 3 s, respuestas 503 y 502, la asistencia queda `REQUESTED` y el reintento la asigna | Acceso remoto · resiliencia |
| 11 | [Consola web](capturas/11-consola-web.png) | Consola de pruebas servida por nginx con los tres servicios en línea | Demo |

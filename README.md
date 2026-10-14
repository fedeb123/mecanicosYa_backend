# MecanicosYa - Trabajo Práctico Primera Parte

Backend independiente que adapta el dominio de asistencia mecánica de MecanicosYa al **Trabajo Práctico Primera Parte de Desarrollo de Aplicaciones II**.

Este repositorio contiene únicamente microservicios y endpoints. La aplicación Android se desarrolla por separado y podrá consumir estas APIs más adelante.

## Alcance

La solución demuestra:

- framework empresarial con Java 25 y Spring Boot 4.1.1;
- arquitectura de microservicios y capas internas;
- componentes de dominio, acceso a datos y utilidades;
- patrones Factory, Repository, Strategy, Observer y Facade;
- integración local mediante interfaces y eventos de dominio;
- integración remota síncrona mediante REST;
- endpoints documentados con OpenAPI/Swagger;
- configuración externalizada, validación, manejo de errores y logging;
- ejecución reproducible mediante Docker Compose.

SOAP, mensajería con colas (RabbitMQ o Kafka), API externa e IA quedan reservados para la Segunda Parte o el Integrador y no forman parte de esta entrega.

## Microservicios

| Servicio | Puerto | Responsabilidad |
|---|---:|---|
| `mechanic-service` | 8081 | Mecánicos, habilidades, ubicación y disponibilidad |
| `assistance-service` | 8082 | Solicitudes de asistencia y su ciclo de vida |
| `dispatch-service` | 8083 | Selección del mecánico compatible más conveniente |

## Ejecución

Requisito: Docker Desktop con Docker Compose.

```bash
docker compose up --build
```

Cuando los contenedores estén saludables:

```bash
./scripts/demo.sh
```

## Consola de pruebas (front)

Con el compose levantado, en [http://localhost:8080](http://localhost:8080) hay un front mínimo para probar los servicios a mano: alta de mecánicos y disponibilidad, búsqueda de candidatos, clientes y vehículos, pedido y seguimiento de asistencias (iniciar, completar, cancelar, reintentar despacho) y una demo que repite `scripts/demo.sh`.

Es HTML, CSS y JavaScript sin build, en `frontend/`, servido por un nginx que hace de proxy a los tres servicios (`/mechanic`, `/assistance`, `/dispatch`), así que no hace falta CORS. Cada request viaja con un `X-Correlation-Id` que se ve en el panel lateral y en los logs. Si un servicio está caído, el front muestra el 502/503 en vez de romperse.

Es una herramienta de prueba: el alcance del TP sigue siendo solo backend.

Para detenerlos:

```bash
docker compose down
```

Para borrar también las bases de laboratorio:

```bash
docker compose down --volumes
```

## Endpoints y Swagger

- Mechanics: [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)
- Assistances: [http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html)
- Dispatch: [http://localhost:8083/swagger-ui.html](http://localhost:8083/swagger-ui.html)

Los contratos OpenAPI JSON se publican en `/v3/api-docs` en cada servicio.

## Pruebas

Para compilar y testear fuera de Docker hace falta el JDK 25.

```bash
./mvnw clean verify
```

El mismo comando corre en GitHub Actions en cada push y PR a `develop` y `main`.

## Evidencias

```bash
./scripts/evidencias.sh
```

Levanta el entorno, ejecuta los escenarios de la entrega (flujo completo, acceso local y remoto, Strategy, errores y fallas remotas) y guarda salidas y capturas en `docs/evidencias/`.

## Documentación

- [Adaptación exacta de la consigna](docs/CONSIGNA_Y_ALCANCE.md)
- [Arquitectura y patrones](docs/ARQUITECTURA.md)
- [Diagrama de clases](docs/DIAGRAMA_DE_CLASES.md)
- [Diagramas (componentes, despliegue, capas, clases, secuencia, estados)](docs/diagramas/README.md)
- [Informe de arquitectura de componentes](docs/INFORME_ARQUITECTURA_COMPONENTES.md)
- [Endpoints](docs/ENDPOINTS.md)
- [Guía de evidencias](docs/GUIA_DE_EVIDENCIAS.md)
- [Evidencias de ejecución local y remota](docs/evidencias/README.md)


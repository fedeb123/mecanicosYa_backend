# MecanicosYa - Trabajo Práctico Segunda Parte

Backend independiente que adapta el dominio de asistencia mecánica de MecanicosYa a los **Trabajos Prácticos Primera y Segunda Parte de Desarrollo de Aplicaciones II**: una aplicación empresarial con componentes, integrada de forma sincrónica por REST y asincrónica por Kafka, con una API externa real y un componente de IA.

Este repositorio contiene únicamente microservicios y endpoints. La aplicación Android se desarrolla por separado y podrá consumir estas APIs más adelante.

## Alcance

La solución demuestra:

- framework empresarial con Java 25 y Spring Boot 4.1.1;
- arquitectura de microservicios y capas internas;
- componentes de dominio, acceso a datos y utilidades;
- patrones Factory, Repository, Strategy, Observer y Facade;
- integración local mediante interfaces y eventos de dominio;
- integración remota síncrona mediante REST;
- integración asíncrona con Kafka: outbox transaccional, consumidores idempotentes y dead-letter topics, visibles en Kafka UI;
- clasificación del pedido con IA (API de Claude), con respaldo por palabras clave y un set de 40 casos etiquetados para medirla;
- API externa: tiempo de llegada por calle con OpenRouteService y una estrategia que ofrece el pedido primero al que llega antes, con respaldo en línea recta;
- usuarios y roles (repartidor, mecánico, admin) con JWT, validación de mecánicos por un admin y control del dueño de cada recurso en el servidor;
- un servicio SOAP con WSDL (la clasificación del pedido, en `/ws/triage.wsdl`);
- endpoints documentados con OpenAPI/Swagger, con los contratos exportados en [`docs/contratos/`](docs/contratos/) junto al contrato AsyncAPI de los eventos;
- configuración externalizada, validación, manejo de errores y logging;
- ejecución reproducible mediante Docker Compose.

## Microservicios

| Servicio | Puerto | Responsabilidad |
|---|---:|---|
| `identity-service` | 8084 | Cuentas, login, tokens JWT y tokens entre servicios |
| `mechanic-service` | 8081 | Mecánicos, habilidades, ubicación, disponibilidad y validación |
| `assistance-service` | 8082 | Solicitudes de asistencia y su ciclo de vida |
| `dispatch-service` | 8083 | Selección del mecánico compatible más conveniente |

## Ejecución

Requisito: Docker Desktop con Docker Compose. El paso a paso, la configuración y los problemas comunes están en el [manual de despliegue](docs/DESPLIEGUE.md).

```bash
docker compose up --build
```

Cuando los contenedores estén saludables:

```bash
./scripts/demo.sh
```

Las dos claves externas se exportan en la terminal antes de levantar el compose y nunca van al repo:

- `export ANTHROPIC_API_KEY=...` para que los pedidos se clasifiquen con IA; sin ella, la clasificación usa palabras clave;
- `export ORS_API_KEY=...` para que el tiempo de llegada salga de OpenRouteService; sin ella, se estima en línea recta. Con `export DISPATCH_MATCHING_STRATEGY=fastest-eta` el pedido se ofrece primero al que llega antes.

Sin claves todo funciona igual.

Todos los endpoints piden un token, salvo el registro y el login. El compose crea un admin para aprobar mecánicos: `admin@mecanicosya.local` con contraseña `admin-demo-123` (se cambian con `ADMIN_EMAIL` y `ADMIN_PASSWORD`). Cómo se obtiene el token y qué puede hacer cada rol está en [Usuarios y roles](docs/USUARIOS_Y_ROLES.md).

El compose también levanta Kafka (un nodo KRaft, expuesto en `localhost:29092`) y Kafka UI en [http://localhost:8090](http://localhost:8090), donde se ven los topics, los mensajes y el lag de cada consumidor.

## Consola de pruebas (front)

Con el compose levantado, en [http://localhost:8080](http://localhost:8080) hay un front mínimo para probar los servicios a mano: registro e inicio de sesión, aprobación de mecánicos pendientes, perfiles de mecánicos y disponibilidad, búsqueda de candidatos, clientes y vehículos, pedido y seguimiento de asistencias (iniciar, completar, cancelar, volver a publicar), las ofertas de cada mecánico para aceptar o pasar, y una demo que repite `scripts/demo.sh`. La consola guarda la sesión de cada cuenta que crea y hace cada pedido con el token de quien lo haría en la app.

Es HTML, CSS y JavaScript sin build, en `frontend/`, servido por un nginx que hace de proxy a los cuatro servicios (`/identity`, `/mechanic`, `/assistance`, `/dispatch`), así que no hace falta CORS. Cada request viaja con un `X-Correlation-Id` que se ve en el panel lateral y en los logs. Si un servicio está caído, el front muestra el 502/503 en vez de romperse.

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

- Identity: [http://localhost:8084/swagger-ui.html](http://localhost:8084/swagger-ui.html)
- Mechanics: [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)
- Assistances: [http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html)
- Dispatch: [http://localhost:8083/swagger-ui.html](http://localhost:8083/swagger-ui.html)

Los contratos OpenAPI se publican en `/v3/api-docs` (JSON) y `/v3/api-docs.yaml` en cada servicio, y una copia de cada uno está en [`docs/contratos/`](docs/contratos/) (`./scripts/exportar-contratos.sh` la actualiza). En Swagger, el botón *Authorize* recibe el `accessToken` del login.

## Pruebas

Para compilar y testear fuera de Docker hace falta el JDK 25.

```bash
./mvnw clean verify
```

El mismo comando corre en GitHub Actions en cada push y PR a `develop` y `main`.

Los tests de concurrencia, idempotencia, permisos y Kafka levantan PostgreSQL y Kafka con Testcontainers: necesitan Docker, y sin Docker se saltean.

## Evidencias

Las de la Segunda Parte están en `docs/evidencias/segunda-parte/` y se explican en la [guía de evidencias](docs/GUIA_DE_EVIDENCIAS.md).

Las de la Primera Parte quedan en `docs/evidencias/`. Las generó `./scripts/evidencias.sh` antes de que la API pidiera token, así que ese script ya no corre tal cual contra esta versión.

## Documentación

- [Consigna y alcance](docs/CONSIGNA_Y_ALCANCE.md)
- [Manual de despliegue](docs/DESPLIEGUE.md)
- [Arquitectura y patrones](docs/ARQUITECTURA.md)
- [Diagrama de clases](docs/DIAGRAMA_DE_CLASES.md)
- [Diagramas (componentes, despliegue, capas, clases, secuencia, estados)](docs/diagramas/README.md)
- [Informe de arquitectura de componentes](docs/INFORME_ARQUITECTURA_COMPONENTES.md)
- [Endpoints](docs/ENDPOINTS.md)
- [Ofertas y aceptación de pedidos](docs/OFERTAS_Y_ACEPTACION.md)
- [Mensajería con Kafka](docs/MENSAJERIA.md)
- [Clasificación con IA](docs/CLASIFICACION_CON_IA.md)
- [API externa: rutas y tiempo de llegada](docs/API_EXTERNA_RUTAS.md)
- [Usuarios y roles](docs/USUARIOS_Y_ROLES.md)
- [Servicio SOAP](docs/SERVICIO_SOAP.md)
- [Contratos OpenAPI, AsyncAPI y WSDL](docs/contratos/)
- [Guía de evidencias](docs/GUIA_DE_EVIDENCIAS.md)
- [Evidencias de ejecución local y remota](docs/evidencias/README.md)


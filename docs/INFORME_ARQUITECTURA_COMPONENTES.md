# Informe de arquitectura de componentes

Trabajo Práctico Inicial · Desarrollo de Aplicaciones II · MecanicosYa

## 1. Problema

MecanicosYa conecta a repartidores cuyo vehículo se averió durante la jornada con mecánicos que pueden trasladarse hasta su ubicación. Hoy el vehículo es la bicicleta, y el modelo ya admite otros tipos, como la moto. Este trabajo implementa el backend. La aplicación Android se desarrolla en otra materia y consumirá estos endpoints más adelante.

## 2. Entorno de desarrollo

| Elemento | Versión o herramienta |
|---|---|
| Lenguaje y JDK | Java 25 LTS (Eclipse Temurin) |
| Framework | Spring Boot 4.1.1 |
| Gestión de dependencias | Maven 3.9, proyecto multi-módulo |
| Base de datos | PostgreSQL 16 con PostGIS 3.4 |
| Ejecución | Docker Compose: un contenedor por servicio más PostgreSQL |
| Documentación de APIs | springdoc-openapi (Swagger UI) |
| IDE | Visual Studio Code |

## 3. Componentes reutilizables

La consigna pide al menos tres componentes independientes: de dominio, de acceso a datos y de utilidad. Cada microservicio tiene los tres, separados por paquete, y la utilidad transversal está en un módulo Maven propio.

| Tipo | Componentes | Por qué es reutilizable |
|---|---|---|
| Dominio | `AssistanceRequest`, `Client`, `Vehicle`, `StatusChange` (asistencias); `Mechanic` con sus habilidades y tipos de vehículo (mecánicos); `DispatchMatch`, `MatchingStrategy` (despacho) | Las reglas viven en las entidades (por ejemplo, no se asigna un mecánico a una asistencia cerrada) y no dependen de los controladores ni de HTTP |
| Acceso a datos | `AssistanceRepository`, `ClientRepository`, `VehicleRepository`, `StatusChangeRepository`, `DispatchRepository` y `MechanicRepository` | Son interfaces en el paquete `domain` que extienden Spring Data JPA. Los casos de uso no conocen SQL ni la base concreta |
| Utilidad | Módulo `shared-observability`: `ObservabilityConfiguration` registra `CorrelationIdFilter`, que toma o genera el encabezado `X-Correlation-Id`, lo pone en el MDC para los logs y lo devuelve en la respuesta; validación con Bean Validation; errores con Problem Details; configuración externalizada en `application.yml` con variables de entorno | Lo usan los tres servicios sin duplicar código, y cada valor de entorno (URL de base, credenciales, URLs de otros servicios) se cambia sin recompilar |

## 4. Aplicación multicapa

Cada servicio sigue presentación → servicios → dominio, con la infraestructura como adaptador:

- **Presentación (`api`)**: controladores REST, DTOs validados y manejo de errores.
- **Lógica de negocio (`application` + `domain`)**: fachadas con los casos de uso, eventos de dominio, puertos, entidades, factories y estrategias.
- **Datos (`infrastructure` + repositorios)**: Spring Data JPA sobre PostgreSQL y clientes REST hacia otros servicios.

El detalle por servicio está en [ARQUITECTURA.md](ARQUITECTURA.md#arquitectura-en-capas).

## 5. Gestión de dependencias

Cada servicio hereda del proyecto padre `mecanicosya-backend` (versión `0.1.0-SNAPSHOT`). Los módulos son `shared-observability`, `mechanic-service`, `assistance-service` y `dispatch-service`, y cada servicio depende de `shared-observability` con la versión del proyecto. El `Dockerfile` compila todo con `mvn package` en una etapa de build y copia sólo el JAR del módulo indicado en la imagen final.

## 6. Acceso local y remoto entre componentes

| Modo | Dónde se ve | Cómo funciona |
|---|---|---|
| Invocación directa (local) | `AssistanceFacade` → `AssistanceFactory` y `AssistanceRepository` | Llamadas Java dentro del mismo proceso y la misma transacción |
| A través de interfaz, local | `AssistanceFacade` publica `AssistanceRequestedEvent` (lo recibe `AssistanceDispatchCoordinator`) y `AssistanceStatusChangedEvent` (lo recibe `StatusHistoryRecorder`) | Comunicación desacoplada por eventos de dominio dentro del proceso: el emisor no conoce al receptor |
| A través de interfaz, remota | `AssistanceDispatchCoordinator` → `DispatchPort` → `DispatchRestClient` → `POST /api/dispatches`; `DispatchFacade` → `MechanicDirectory` → `MechanicRestClient` → `GET /api/mechanics/candidates` | El caso de uso depende sólo de la interfaz (`DispatchPort`, `MechanicDirectory`). La implementación hace la llamada HTTP REST, y podría reemplazarse por una implementación local, por ejemplo en un test, sin tocar el caso de uso |

La diferencia práctica es que la invocación remota puede fallar por red o por un servicio caído. Por eso cada cliente REST tiene un tiempo máximo de respuesta y traduce la falla a una excepción propia del caso de uso, que la API devuelve como `503` (servicio no disponible) o `502` (el otro servicio respondió con error). En el alta, el coordinador captura el error, deja la asistencia en `REQUESTED` y expone `POST /api/assistances/{id}/dispatch` para reintentar.

## 7. Diagramas

- Clases: [DIAGRAMA_DE_CLASES.md](DIAGRAMA_DE_CLASES.md)
- Componentes: [ARQUITECTURA.md, vista de componentes](ARQUITECTURA.md#vista-de-componentes)
- Despliegue: [ARQUITECTURA.md, despliegue](ARQUITECTURA.md#despliegue)
- Secuencia del flujo principal: [ARQUITECTURA.md, flujo principal](ARQUITECTURA.md#flujo-principal)

## 8. Evidencias de ejecución

Las capturas se toman siguiendo [GUIA_DE_EVIDENCIAS.md](GUIA_DE_EVIDENCIAS.md):

- **Local**: resultado de `mvn test` y logs de una fachada llamando a Factory y Repository dentro del proceso.
- **Remota**: `docker compose ps` con los servicios saludables, el alta de una asistencia y los logs de las dos llamadas REST (asistencias → despacho → mecánicos), con la asistencia final en `MATCHED`.

## 9. Decisiones y límites

- Una base lógica por servicio, con usuario propio. En el laboratorio comparten una instancia de PostgreSQL sólo para ahorrar recursos.
- Entre servicios no hay claves foráneas: cada uno guarda el `UUID` del otro.
- Las entidades llevan anotaciones JPA para mantener el ejercicio compacto; las reglas siguen en el dominio.
- SOAP, colas de mensajería, API externa e inteligencia artificial corresponden a la Segunda Parte y al Integrador.

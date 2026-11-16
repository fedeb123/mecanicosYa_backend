# Consigna y alcance

## Separación respecto del proyecto móvil

Este trabajo conserva el problema de negocio de MecanicosYa, pero es una entrega backend independiente. La aplicación Android, Retrofit, pantallas y navegación se desarrollan en otra materia y no se incluyen aquí.

Los endpoints quedan listos para que la app móvil los consuma: el login devuelve el rol y el estado de validación del mecánico, y el alta de pedidos acepta reintentos y pedidos creados sin conexión.

## Primera Parte: aplicación empresarial con componentes

| Actividad obligatoria | Implementación |
|---|---|
| Framework empresarial | Cuatro aplicaciones Java 25 con Spring Boot 4.1.1 |
| Patrones Factory, Repository, Strategy, Observer y Facade | Aplicados explícitamente en dominio y aplicación ([Arquitectura](ARQUITECTURA.md)) |
| Servicios orientados a componentes | Identidad, Mecánicos, Asistencias y Despacho, más las librerías `shared-observability`, `shared-messaging` y `shared-security` |
| Interfaces internas | Puertos `MechanicDirectory`, `DispatchPort`, `ProblemClassifier` y `TravelTimeEstimator` |
| Eventos de dominio | `AssistanceRequestedEvent` y listener posterior al commit |
| Modelado de procesos | Diagramas de secuencia, componentes, clases y despliegue |
| Arquitectura en capas | API, aplicación, dominio e infraestructura |
| Aplicación funcional | Registrar mecánico, pedir asistencia, ofrecerla, aceptarla, completarla y calificarla |
| Documentación de patrones | Tabla y referencias en [Arquitectura](ARQUITECTURA.md) |
| Gestión de dependencias | Maven multi-módulo |

## Segunda Parte: integración sincrónica y asincrónica

| Actividad obligatoria | Implementación | Dónde está |
|---|---|---|
| Al menos dos servicios REST con OpenAPI | Cuatro servicios REST, cada uno con Swagger UI y su contrato OpenAPI exportado | [Endpoints](ENDPOINTS.md), [`contratos/`](contratos/) |
| Al menos un servicio SOAP con WSDL | `assistance-service` expone por SOAP la clasificación del pedido (`ClassifyProblem`), contract-first con XSD y WSDL publicado en `/ws/triage.wsdl`. El resto del sistema es REST | [Servicio SOAP](SERVICIO_SOAP.md), [`contratos/triage.wsdl`](contratos/triage.wsdl) |
| Mensajería y colas | Kafka (un nodo KRaft) con Kafka UI, tres topics y dead-letter topics por consumidor | [Mensajería](MENSAJERIA.md), [`contratos/eventos.asyncapi.yaml`](contratos/eventos.asyncapi.yaml) |
| Productores y consumidores | `ASSISTANCE_REQUESTED`, `ASSISTANCE_CANCELLED`, `ASSISTANCE_COMPLETED`, `ASSISTANCE_RATED`, `DISPATCH_OFFERED`, `DISPATCH_MATCHED`, `DISPATCH_UNMATCHED` y `MECHANIC_VERIFICATION_CHANGED`, publicados con outbox transaccional y consumidos de forma idempotente | [Mensajería](MENSAJERIA.md#topics) |
| Arquitectura SOA o microservicios livianos | Cuatro microservicios con una base cada uno, que se hablan por REST cuando necesitan la respuesta en el momento y por Kafka para avisar cambios | [Arquitectura](ARQUITECTURA.md) |
| API externa real | OpenRouteService (Matrix API): tiempo de llegada por calle de cada mecánico, con respaldo en línea recta | [API externa: rutas](API_EXTERNA_RUTAS.md) |
| Componente de IA | Clasificación del tipo de problema y de la urgencia a partir del texto del repartidor con la API de Claude, expuesta en `POST /api/triage` (REST) y en la operación SOAP `ClassifyProblem`, y usada en el alta del pedido, con respaldo por palabras clave | [Clasificación con IA](CLASIFICACION_CON_IA.md) |
| Evidencias de prueba | Tests unitarios y de integración con Testcontainers, `scripts/demo.sh` y capturas | [Guía de evidencias](GUIA_DE_EVIDENCIAS.md) |

Además de lo que pide la consigna, la Segunda Parte suma lo que necesita la app móvil para el circuito completo de pedido y aceptación:

- [ofertas](OFERTAS_Y_ACEPTACION.md): el mecánico decide si toma el pedido, el primero que acepta gana y el segundo recibe `409`;
- [idempotencia](OFERTAS_Y_ACEPTACION.md#que-un-reintento-no-duplique-el-pedido): un reintento con la misma `Idempotency-Key` no duplica el pedido, y un pedido que estuvo demasiado tiempo en cola en el teléfono se rechaza;
- [usuarios y roles](USUARIOS_Y_ROLES.md) con JWT: el servidor valida el rol y el dueño de cada recurso, y un mecánico no puede trabajar hasta que un admin lo aprueba.

El [manual de despliegue](DESPLIEGUE.md) explica cómo levantar todo en un laboratorio con Docker Compose.

## Casos de uso

1. Registrarse como repartidor o mecánico e iniciar sesión.
2. Aprobar a un mecánico pendiente de validación (admin).
3. Registrar el perfil del mecánico, sus habilidades y los tipos de vehículo que atiende.
4. Actualizar disponibilidad y ubicación.
5. Registrar el perfil del repartidor y sus vehículos.
6. Clasificar la descripción del problema con IA, por REST o por SOAP.
7. Crear una solicitud de asistencia, también desde un pedido que estuvo en cola sin conexión.
8. Avisar la solicitud por Kafka a `dispatch-service`.
9. Buscar candidatos compatibles en `mechanic-service` y ordenarlos con una estrategia intercambiable, incluido el tiempo de llegada por calle.
10. Ofrecer el pedido a los mejores candidatos y avisar la oferta por Kafka.
11. Aceptar o pasar una oferta; el primero que acepta gana.
12. Iniciar y completar la asistencia.
13. Cancelar un pedido abierto y retirar sus ofertas.
14. Calificar al mecánico y sumar la calificación a su promedio por Kafka.

## Fuera de alcance

- Aplicación Android o frontend de producción. La consola de `frontend/` es una herramienta de prueba.
- Pagos y seguimiento del mecánico en tiempo real.
- Notificaciones push.

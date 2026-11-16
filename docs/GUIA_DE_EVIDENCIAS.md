# Guía de evidencias

Las evidencias de la Segunda Parte están en [`docs/evidencias/segunda-parte/`](evidencias/segunda-parte/). Se sacaron sobre el compose levantado desde cero, con un volumen de Postgres nuevo, y sin las claves de Anthropic ni de OpenRouteService, así que la clasificación usa palabras clave y el tiempo de llegada se estima en línea recta. La fila 13 es la misma demo con las claves reales.

Las evidencias de la Primera Parte siguen en [`docs/evidencias/`](evidencias/README.md) y se regeneran con `./scripts/evidencias.sh`.

## Qué prueba cada una

| # | Archivo | Qué muestra | Requisito de la consigna |
|---|---|---|---|
| 1 | [`01-compose-ps.txt`](evidencias/segunda-parte/01-compose-ps.txt) | Los cuatro servicios, PostgreSQL y Kafka sanos, Kafka UI y la consola arriba, y `postgres-setup` terminado bien | Ejecución en contenedores |
| 2 | [`02-swagger-identity-service.png`](evidencias/segunda-parte/02-swagger-identity-service.png), [`02-swagger-mechanic-service.png`](evidencias/segunda-parte/02-swagger-mechanic-service.png), [`02-swagger-assistance-service.png`](evidencias/segunda-parte/02-swagger-assistance-service.png), [`02-swagger-dispatch-service.png`](evidencias/segunda-parte/02-swagger-dispatch-service.png) | Swagger de cada servicio. Los contratos completos están en [`contratos/`](contratos/) | Servicios REST con OpenAPI |
| 3 | [`04-demo-sh.txt`](evidencias/segunda-parte/04-demo-sh.txt) | `scripts/demo.sh` de punta a punta: cuentas, aprobación, pedido clasificado, oferta por Kafka, dos aceptaciones simultáneas con un solo ganador, trabajo completado y calificación que llega por Kafka a `mechanic-service` | Productores y consumidores, integración sincrónica y asincrónica |
| 4 | [`05-demo-consola.png`](evidencias/segunda-parte/05-demo-consola.png) | La misma demo en la consola de pruebas | Aplicación funcional |
| 5 | [`06-mecanico-pendiente.png`](evidencias/segunda-parte/06-mecanico-pendiente.png), [`07-mecanico-aprobado.png`](evidencias/segunda-parte/07-mecanico-aprobado.png) | El admin ve un mecánico pendiente de validación y lo aprueba | Usuarios y roles |
| 6 | [`08-clasificacion.png`](evidencias/segunda-parte/08-clasificacion.png) | `POST /api/triage` clasifica el texto del repartidor antes de crear el pedido: tipo de problema, habilidad, urgencia, fuente y confianza | Componente de IA expuesto por REST |
| 7 | [`09-kafka-topics.png`](evidencias/segunda-parte/09-kafka-topics.png) | Los topics de cada agregado y sus dead-letter topics | Mensajería y colas |
| 8 | [`09-kafka-consumer-groups.png`](evidencias/segunda-parte/09-kafka-consumer-groups.png) | Un consumer group por servicio, todos estables y sin lag | Consumidores |
| 9 | [`09-kafka-mensajes-assistance-events.png`](evidencias/segunda-parte/09-kafka-mensajes-assistance-events.png), [`09-kafka-mensajes-mechanic-events.png`](evidencias/segunda-parte/09-kafka-mensajes-mechanic-events.png) | Los mensajes publicados, con su clave y sus headers | Productores |
| 10 | [`10-permisos.txt`](evidencias/segunda-parte/10-permisos.txt) | `401` sin token, `403` con otro rol y con un mecánico pendiente, el payload de un token y la clave pública | Manejo de errores y seguridad |
| 11 | [`11-tiempo-de-llegada.png`](evidencias/segunda-parte/11-tiempo-de-llegada.png), [`11-tiempo-de-llegada.txt`](evidencias/segunda-parte/11-tiempo-de-llegada.txt) | Las ofertas con el tiempo de llegada por calle y la estrategia `fastest-eta`, que le ofrece el pedido primero al que llega antes aunque esté más lejos en línea recta. Esta corrida usó un servidor local que responde como la Matrix API de OpenRouteService, porque el entorno donde se sacaron no tenía salida a internet | API externa |
| 13 | [`13-claves-reales.txt`](evidencias/segunda-parte/13-claves-reales.txt) | La demo con las claves reales de Anthropic y OpenRouteService: la clasificación sale con `source: AI` y las ofertas con `etaSource: ROUTE`. También el respaldo en línea recta cuando OpenRouteService rechaza la clave | Componente de IA y API externa |
| 12 | [`12-mvn-verify.txt`](evidencias/segunda-parte/12-mvn-verify.txt) | `mvn verify`: 175 tests en verde (shared-security 8, identity-service 10, mechanic-service 24, assistance-service 78 con 1 omitido, dispatch-service 55), incluidos los 4 del servicio SOAP | Evidencias de prueba |
| 14 | [`14-evaluacion-ia.md`](evidencias/segunda-parte/14-evaluacion-ia.md) | `TriageEvaluationTest` con la clave real sobre los 40 casos etiquetados: Claude acierta la categoría en 40 de 40 y la urgencia en 37 de 40, con una mediana de 1,9 s por pedido. Las palabras clave aciertan 33 y 38 | Componente de IA |
| 15 | [`15-soap.txt`](evidencias/segunda-parte/15-soap.txt) | El WSDL publicado sin token, un sobre SOAP clasificado, un SOAP Fault `Client` por un sobre que no cumple el esquema, `401` sin token y la misma clasificación por REST | Servicio SOAP con WSDL y contrato |

Los tests de integración que respaldan cada parte están listados en la sección Pruebas de [Mensajería](MENSAJERIA.md#pruebas), [Ofertas y aceptación](OFERTAS_Y_ACEPTACION.md#pruebas), [Clasificación con IA](CLASIFICACION_CON_IA.md#tests) y [Usuarios y roles](USUARIOS_Y_ROLES.md).

## Cómo volver a sacarlas

1. `docker compose down --volumes && docker compose up -d --build --wait`.
2. `docker compose ps -a` y `./scripts/demo.sh > docs/evidencias/segunda-parte/04-demo-sh.txt`.
3. En http://localhost:8080, Demo rápida, Correr demo. Después, en Cuentas, Ver pendientes.
4. Kafka UI en http://localhost:8090: Topics, Consumers y los mensajes de cada topic.
5. `./scripts/exportar-contratos.sh` y `mvn verify`.

Con `ANTHROPIC_API_KEY` y `ORS_API_KEY` exportadas antes del paso 1, la clasificación sale con `triageSource: AI` y las ofertas con `etaSource: ROUTE`.

## Defensa breve sugerida

1. Explicar el problema: repartidores que se quedan con la bici o la moto rota en la calle y mecánicos que deciden si toman el pedido.
2. Mostrar el diagrama de componentes y el de despliegue de [Arquitectura](ARQUITECTURA.md).
3. Levantar el compose y correr la Demo rápida.
4. En Kafka UI, abrir `assistance.events` y seguir un pedido por su clave y su `X-Correlation-Id`.
5. Explicar la outbox: el evento se guarda en la misma transacción que el pedido, así que no se pierde si Kafka está caído.
6. Mostrar la carrera de dos aceptaciones: una gana y la otra recibe `409 ALREADY_TAKEN`.
7. Clasificar un texto en la consola y explicar las decisiones de IA: el modelo, el respaldo por palabras clave y la evaluación con 40 casos ([Clasificación con IA](CLASIFICACION_CON_IA.md)).
8. Mostrar el tiempo de llegada de OpenRouteService en las ofertas y el respaldo en línea recta ([API externa](API_EXTERNA_RUTAS.md)).
9. Mostrar un `403` de un mecánico pendiente y su aprobación por el admin.

# Endpoints

Salvo el registro, el login y el health check, todos piden `Authorization: Bearer <accessToken>`. La columna **Quién** dice qué rol puede llamar a cada uno; sin token la respuesta es `401` y con otro rol, `403`. Cuando el endpoint opera sobre un recurso, además se valida que sea de quien llama: el id del repartidor o del mecánico sale del token, no de un parámetro. El detalle está en [Usuarios y roles](USUARIOS_Y_ROLES.md).

## `identity-service`

| Método | Endpoint | Quién | Descripción |
|---|---|---|---|
| POST | `/api/auth/register` | Público | Crea la cuenta (`email`, `password` de 8 a 72 caracteres, `role` `RIDER` o `MECHANIC`) y devuelve la sesión. Un mecánico arranca `PENDING_VERIFICATION` |
| POST | `/api/auth/login` | Público | `{email, password}` → `{accessToken, refreshToken, expiresAt, userId, role, verificationStatus}`. `401` si alguno de los dos está mal |
| POST | `/api/auth/refresh` | Público | Cambia un refresh token por una sesión nueva; el usado deja de servir. El token nuevo trae el estado de validación actual |
| POST | `/api/auth/logout` | Público | Revoca el refresh token; responde `204` |
| GET | `/api/auth/me` | Cualquiera con token | La cuenta del token |
| POST | `/api/auth/service-token` | Servicios | `{clientId, clientSecret}` → token con rol `SERVICE` para las llamadas entre servicios |
| GET | `/.well-known/jwks.json` | Público | La clave pública con la que los otros servicios validan los tokens |

## `mechanic-service`

| Método | Endpoint | Quién | Descripción |
|---|---|---|---|
| POST | `/api/mechanics` | Mecánico | Crea el perfil del mecánico del token con sus habilidades y, opcionalmente, los tipos de vehículo que atiende (`vehicleTypes`, por defecto `BICYCLE`). El id y el email salen del token. Queda `PENDING_VERIFICATION`; `409` si ya tiene perfil. `kind` dice cómo atiende: `MOBILE` (por defecto, va hasta el repartidor y su ubicación sigue al GPS) o `WORKSHOP` (taller fijo: exige `address` y `phone`, y su ubicación no cambia al ponerse disponible) |
| GET | `/api/mechanics?verificationStatus=...` | Admin | Lista los mecánicos, por ejemplo los pendientes de validación |
| POST | `/api/mechanics/{id}/approve` | Admin | Aprueba al mecánico. Su próximo token (login o refresh) dice `APPROVED` |
| GET | `/api/mechanics/{id}` | Cualquiera con token | Consulta un mecánico, con su `verificationStatus` |
| PATCH | `/api/mechanics/{id}/availability` | El mecánico (o un admin) | Cambia disponibilidad y ubicación. `403` mientras está pendiente de validación |
| POST | `/api/mechanics/{id}/ratings` | Servicio o admin | Suma la calificación de una asistencia (`assistanceId`, `score` de 1 a 5) al promedio del mecánico. Es idempotente: la misma asistencia no cuenta dos veces. El flujo normal no lo usa: la calificación llega por Kafka |
| GET | `/api/mechanics/candidates?skill=...&vehicleType=...` | Servicio o admin | Busca mecánicos aprobados y disponibles con la habilidad; `vehicleType` es opcional y filtra por tipo de vehículo; `kind` (opcional) filtra por `MOBILE` o `WORKSHOP` |
| GET | `/api/mechanics/nearby?latitude=...&longitude=...&radiusKm=3&vehicleType=...` | Repartidor o admin | Mecánicos aprobados y disponibles cerca, del más cercano al más lejano (línea recta, radio por defecto 3 km, máximo 20). Devuelve id, nombre, ubicación, distancia y puntaje, sin email. Lo usa el mapa de la app |

## `assistance-service`

| Método | Endpoint | Quién | Descripción |
|---|---|---|---|
| POST | `/api/clients` | Repartidor | Crea el perfil del repartidor del token (`fullName`, `phone`). El id del cliente es su `userId` y el email sale del token |
| GET | `/api/clients/{id}` | El repartidor (o un admin) | Consulta un cliente |
| POST | `/api/clients/{id}/vehicles` | El repartidor (o un admin) | Registra un vehículo (`BICYCLE`, `E_BIKE`, `MOTORCYCLE`; la moto requiere patente) |
| GET | `/api/clients/{id}/vehicles` | El repartidor (o un admin) | Lista los vehículos del cliente |
| POST | `/api/assistances` | Repartidor | Crea el pedido del repartidor del token y lo publica en Kafka para que se ofrezca a los mecánicos cercanos; `offersExpireAt` aparece cuando vuelve la oferta. Exige el header `Idempotency-Key`; `clientCreatedAt` es opcional. `clientId` ya no hace falta (si viene, tiene que ser el del token). `problemType` y `requiredSkill` también son opcionales: si faltan, los completa la clasificación del texto, que además guarda `urgency`, `triageSource` y `triageConfidence`. `mechanicKind` es opcional: `MOBILE` (a domicilio) o `WORKSHOP` (ir a un taller); dispatch solo se lo ofrece a mecánicos de ese tipo. Sin el campo, a cualquiera |
| POST | `/api/triage` | Cualquiera con token | Clasifica una descripción (`description` y `vehicleType` opcional) sin crear un pedido: devuelve `problemType`, `requiredSkill`, `urgency`, `confidence` y `source` (`AI` o `KEYWORDS`) |
| POST | `/ws` (SOAP) | Cualquiera con token | La misma clasificación como servicio SOAP: operación `ClassifyProblem` con `ClassifyProblemRequest` (`description`, `vehicleType` opcional) y `ClassifyProblemResponse`. El sobre se valida contra el esquema y uno inválido recibe un SOAP Fault de cliente. Ver [Servicio SOAP](SERVICIO_SOAP.md) |
| GET | `/ws/triage.wsdl` | Público | El WSDL del servicio SOAP; copia en [`contratos/triage.wsdl`](contratos/triage.wsdl) |
| GET | `/api/assistances` | Repartidor, mecánico o admin | El repartidor ve sus pedidos y el mecánico sus trabajos, del más nuevo al más viejo. Un admin pasa `clientId` o `mechanicId` |
| GET | `/api/assistances/{id}` | Su repartidor, su mecánico o un admin | Consulta estado, mecánico asignado y hasta cuándo se puede aceptar |
| GET | `/api/assistances/{id}/history` | Su repartidor, su mecánico o un admin | Historial de cambios de estado |
| POST | `/api/assistances/{id}/dispatch` | Su repartidor o un admin | Vuelve a publicar un pedido `UNMATCHED`, o reintenta un despacho que falló |
| POST | `/api/assistances/{id}/assign` | Servicio | Lo llama `dispatch-service` cuando un mecánico acepta (REQUESTED → MATCHED) |
| POST | `/api/assistances/{id}/start` | El mecánico asignado | MATCHED → IN_PROGRESS |
| POST | `/api/assistances/{id}/complete` | El mecánico asignado | IN_PROGRESS → COMPLETED |
| POST | `/api/assistances/{id}/cancel` | Su repartidor o un admin | Cancela un pedido abierto y retira sus ofertas |
| POST | `/api/assistances/cancel-by-key` | Repartidor | Cancela el pedido creado con el `Idempotency-Key` del header; `404` si nunca llegó |
| POST | `/api/assistances/{id}/rating` | Su repartidor | Califica al mecánico de un pedido `COMPLETED` (`score` de 1 a 5 y `comment` opcional); una sola vez por pedido |

## `dispatch-service`

| Método | Endpoint | Quién | Descripción |
|---|---|---|---|
| POST | `/api/dispatches` | Servicio o admin | Ofrece el pedido a los mejores candidatos (idempotente; si quedó sin mecánico, abre una ronda nueva). Lo usa el reintento manual; el alta normal llega por Kafka |
| GET | `/api/dispatches/{assistanceId}` | Servicio o admin | Estado del despacho y ofertas de la ronda actual, con distancia y tiempo de llegada (`etaMinutes`, `etaSource`) de cada mecánico; con el pedido tomado, `etaMinutes` del que lo tomó |
| GET | `/api/dispatches/offers` | Mecánico aprobado | Pedidos que el mecánico del token tiene ofrecidos y todavía puede aceptar, con cuánto tarda en llegar. Un admin pasa `mechanicId` |
| GET | `/api/routes?fromLatitude=...&fromLongitude=...&toLatitude=...&toLongitude=...` | Repartidor, mecánico o admin | Ruta del mecánico al repartidor para la app: la línea para el mapa y los pasos en castellano, por calle con OpenRouteService, con el mismo perfil que las ofertas (`ORS_PROFILE`). Sin clave o si la API no responde, línea recta sin pasos (`source: ESTIMATE`) |
| POST | `/api/dispatches/{assistanceId}/accept` | Mecánico aprobado | El mecánico del token toma el pedido. El cuerpo es opcional; si trae `mechanicId`, tiene que ser el del token |
| POST | `/api/dispatches/{assistanceId}/decline` | Mecánico aprobado | El mecánico pasa; responde `204` |
| POST | `/api/dispatches/{assistanceId}/cancel` | Servicio o admin | Retira las ofertas de un pedido cancelado (lo mismo que hace el evento `ASSISTANCE_CANCELLED`) |

El algoritmo que ordena a los candidatos se elige con `DISPATCH_MATCHING_STRATEGY`: `nearest` (por defecto), `best-rated` o `fastest-eta`. Con cualquiera de los tres, el tiempo de llegada sale de OpenRouteService si hay `ORS_API_KEY`, y si no se estima en línea recta: `etaSource` es `ROUTE` o `ESTIMATE` ([API externa: rutas](API_EXTERNA_RUTAS.md)).

## Errores

Todos los errores usan Problem Details. Los que la app tiene que distinguir llevan además un campo `code`.

| HTTP | `code` | Cuándo |
|---|---|---|
| 200 | | Reintento de `POST /api/assistances` con una `Idempotency-Key` ya usada: devuelve el mismo pedido con `Idempotent-Replayed: true` |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | Falta el header `Idempotency-Key` |
| 401 | | Sin token, token vencido o firmado por otra clave; en login, email o contraseña incorrectos |
| 403 | | El rol no puede llamar a ese endpoint, el recurso es de otro usuario, o el mecánico está pendiente de validación |
| 404 | `NO_OFFER` | El mecánico no tiene una oferta de ese pedido en la ronda actual |
| 409 | `ALREADY_TAKEN` | Otro mecánico aceptó primero |
| 409 | `MECHANIC_BUSY` | El mecánico ya está en otro trabajo: tiene que terminarlo antes de aceptar otro pedido |
| 409 | `OFFER_EXPIRED` | La ronda de ofertas venció |
| 409 | `ASSISTANCE_CLOSED` | El pedido se canceló o ya no espera mecánico |
| 409 | | Transición no permitida, email repetido, perfil ya creado para esa cuenta o segunda calificación del mismo pedido |
| 422 | `REQUEST_EXPIRED` | El pedido estuvo más de 15 minutos en cola en el teléfono |
| 502 | | El otro servicio respondió con error (al volver a publicar un pedido) |
| 503 | | El otro servicio no respondió a tiempo (al volver a publicar un pedido o al avisar una aceptación) |

El detalle del flujo está en [Ofertas y aceptación](OFERTAS_Y_ACEPTACION.md). Los DTOs completos se pueden consultar en Swagger o en `/v3/api-docs`.

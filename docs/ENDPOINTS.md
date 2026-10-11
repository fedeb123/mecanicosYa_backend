# Endpoints

## `mechanic-service`

| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/api/mechanics` | Registra un mecánico con sus habilidades y, opcionalmente, los tipos de vehículo que atiende (`vehicleTypes`, por defecto `BICYCLE`) |
| GET | `/api/mechanics/{id}` | Consulta un mecánico |
| PATCH | `/api/mechanics/{id}/availability` | Cambia disponibilidad y ubicación |
| GET | `/api/mechanics/candidates?skill=...&vehicleType=...` | Busca mecánicos disponibles con la habilidad; `vehicleType` es opcional y filtra por tipo de vehículo |

## `assistance-service`

| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/api/clients` | Registra un cliente (email único) |
| GET | `/api/clients/{id}` | Consulta un cliente |
| POST | `/api/clients/{id}/vehicles` | Registra un vehículo (`BICYCLE`, `E_BIKE`, `MOTORCYCLE`; la moto requiere patente) |
| GET | `/api/clients/{id}/vehicles` | Lista los vehículos del cliente |
| POST | `/api/assistances` | Crea una solicitud con `clientId` y `vehicleId` y dispara el despacho |
| GET | `/api/assistances/{id}` | Consulta estado y asignación |
| GET | `/api/assistances/{id}/history` | Historial de cambios de estado |
| POST | `/api/assistances/{id}/dispatch` | Reintenta el despacho manualmente |
| POST | `/api/assistances/{id}/start` | MATCHED → IN_PROGRESS |
| POST | `/api/assistances/{id}/complete` | IN_PROGRESS → COMPLETED |
| POST | `/api/assistances/{id}/cancel` | Cancela una solicitud abierta |

Una transición no permitida o un email repetido responde 409. Si el reintento de despacho no llega a `dispatch-service` responde 503, y si `dispatch-service` contesta con error, 502.

## `dispatch-service`

| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/api/dispatches` | Ejecuta matching de forma idempotente |
| GET | `/api/dispatches/{assistanceId}` | Consulta el resultado |

Si `mechanic-service` no responde o falla, `POST /api/dispatches` responde 503. El algoritmo de selección se elige con `DISPATCH_MATCHING_STRATEGY`: `nearest` (por defecto) o `best-rated`.

Los DTOs completos y códigos de respuesta pueden consultarse en Swagger o `/v3/api-docs`.


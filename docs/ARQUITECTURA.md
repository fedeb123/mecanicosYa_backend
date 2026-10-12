# Arquitectura y patrones

## Vista de componentes

```mermaid
flowchart LR
    CLIENT[Cliente REST futuro<br/>Android o Swagger]
    CLIENT -->|REST| MECH[mechanic-service :8081]
    CLIENT -->|REST| ASSIST[assistance-service :8082]
    CLIENT -->|REST| DISPATCH[dispatch-service :8083]

    ASSIST -->|evento interno| OBS[AssistanceDispatchCoordinator]
    OBS -->|REST remoto| DISPATCH
    DISPATCH -->|REST remoto| MECH

    MECH --> MDB[(mechanics_db)]
    ASSIST --> ADB[(assistances_db)]
    DISPATCH --> DDB[(dispatch_db)]
```

## Flujo principal

```mermaid
sequenceDiagram
    participant C as Cliente
    participant A as assistance-service
    participant E as AssistanceDispatchCoordinator
    participant D as dispatch-service
    participant M as mechanic-service

    C->>M: POST /api/mechanics
    C->>M: PATCH /api/mechanics/{id}/availability
    C->>A: POST /api/clients
    C->>A: POST /api/clients/{id}/vehicles
    C->>A: POST /api/assistances (clientId, vehicleId)
    A->>A: AssistanceFacade: Factory + Repository (estado REQUESTED)
    A->>A: publica AssistanceRequestedEvent
    A->>A: commit de la transacción
    A->>E: evento entregado después del commit
    E->>D: POST /api/dispatches
    D->>M: GET /api/mechanics/candidates?skill=...&vehicleType=...
    M-->>D: candidatos compatibles
    D->>D: MatchingStrategy + Repository
    D-->>E: respuesta MATCHED o NO_CANDIDATE (DispatchOutcome)
    E->>A: applyMatch: estado MATCHED (transacción nueva)
    A-->>C: 201 Created (cuerpo con el estado inicial REQUESTED)
    C->>A: GET /api/assistances/{id}
    A-->>C: asistencia MATCHED con mechanicId
```

El listener corre en el mismo hilo del request, después del commit. Por eso el despacho remoto ocurre antes de que el cliente reciba el `201`, aunque el cuerpo de esa respuesta se arma con el estado inicial `REQUESTED`. El estado actualizado se consulta con `GET /api/assistances/{id}`.

Si `dispatch-service` falla, el coordinador registra el error y la asistencia queda creada en `REQUESTED`: el alta no se revierte. El despacho se reintenta con `POST /api/assistances/{id}/dispatch`.

### Fallas de un servicio remoto

Cada cliente REST tiene un tiempo máximo de conexión y de respuesta, configurable por variable de entorno. Cuando el otro servicio no responde a tiempo o no está levantado, la API devuelve un error explícito en formato Problem Details en lugar de un 500:

| Quién llama | Qué falla | Respuesta | Tiempo máximo |
|---|---|---|---|
| `dispatch-service` | `mechanic-service` caído, lento o con error | `503` en `POST /api/dispatches` | `MECHANIC_SERVICE_TIMEOUT` (3 s) |
| `assistance-service` | `dispatch-service` caído o lento | `503` en `POST /api/assistances/{id}/dispatch` | `DISPATCH_SERVICE_TIMEOUT` (5 s) |
| `assistance-service` | `dispatch-service` responde con error | `502` en `POST /api/assistances/{id}/dispatch` | |

En el alta (`POST /api/assistances`) el error no se propaga: la asistencia se crea igual y queda en `REQUESTED` hasta el reintento.

## Arquitectura en capas

Los tres servicios usan las mismas cuatro capas. Las dependencias apuntan hacia el dominio: `api` usa `application`, `application` usa `domain`, e `infrastructure` implementa los puertos que define `application`.

```mermaid
flowchart TB
    subgraph PRES[Capa de presentación: api]
        P1[Controladores REST]
        P2[DTOs y Bean Validation]
        P3[Manejo de errores con Problem Details]
    end

    subgraph SERV[Capa de servicios: application]
        S1[Fachadas con los casos de uso]
        S2[Eventos de dominio y listeners]
        S3[Puertos hacia otros servicios]
    end

    subgraph DOM[Capa de dominio: domain]
        D1[Entidades y reglas]
        D2[Factories]
        D3[Interfaces Repository]
        D4[Estrategias]
    end

    subgraph INFRA[Capa de infraestructura: infrastructure]
        I1[Clientes REST que implementan los puertos]
        I2[Spring Data JPA y PostgreSQL]
    end

    TRANS[Transversal: shared-observability, configuración externalizada, logging, actuator]

    PRES --> SERV
    SERV --> DOM
    INFRA -.implementa.-> S3
    INFRA -.implementa.-> D3
    TRANS -.-> PRES
    TRANS -.-> SERV
```

- `api`: controladores, DTOs, validación y errores HTTP. No contiene reglas de negocio.
- `application`: casos de uso, fachadas, eventos y puertos. Es la capa de servicios que pide la consigna.
- `domain`: entidades, reglas, factories, repositorios y estrategias.
- `infrastructure`: adaptadores concretos, como JPA y los clientes REST.

Las entidades usan anotaciones JPA para mantener el ejercicio compacto. Las reglas continúan dentro del dominio y no en los controladores.

### mechanic-service por capa

```mermaid
flowchart TB
    subgraph api
        MC[MechanicController]
        MDT[MechanicDtos]
        ME[MechanicExceptionHandler]
    end
    subgraph application
        MF[MechanicFacade]
    end
    subgraph domain
        M[Mechanic / VehicleType]
        MFA[MechanicFactory]
        MR[[MechanicRepository]]
    end

    MC --> MF
    MF --> MFA
    MF --> MR
    MFA -.crea.-> M
```

`mechanic-service` no llama a otros servicios, así que no tiene capa `infrastructure`: su único adaptador es el repositorio de Spring Data.

### assistance-service por capa

```mermaid
flowchart TB
    subgraph api
        AC[AssistanceController]
        CC[ClientController]
        AD[AssistanceDtos / ClientDtos]
        AE[AssistanceExceptionHandler]
    end
    subgraph application
        AF[AssistanceFacade]
        CF[ClientFacade]
        ADC[AssistanceDispatchCoordinator]
        SHR[StatusHistoryRecorder]
        EV[AssistanceRequestedEvent]
        SEV[AssistanceStatusChangedEvent]
        DP[[DispatchPort]]
    end
    subgraph domain
        AR[AssistanceRequest / AssistanceStatus]
        CV[Client / Vehicle / VehicleType]
        SC[StatusChange]
        AFA[AssistanceFactory]
        REP[[AssistanceRepository / ClientRepository / VehicleRepository / StatusChangeRepository]]
    end
    subgraph infrastructure
        DRC[DispatchRestClient]
    end

    AC --> AF
    AC --> ADC
    CC --> CF
    AF --> CF
    AF --> AFA
    AF --> REP
    CF --> REP
    AF -.publica.-> EV
    AF -.publica.-> SEV
    EV -.escucha.-> ADC
    SEV -.escucha.-> SHR
    SHR --> REP
    ADC --> AF
    ADC --> DP
    DRC -.implementa.-> DP
    DRC -->|REST| DS[(dispatch-service)]
```

### dispatch-service por capa

```mermaid
flowchart TB
    subgraph api
        DC[DispatchController]
        DR[DispatchRequest / DispatchResponse]
        DE[DispatchExceptionHandler]
    end
    subgraph application
        DF[DispatchFacade]
        MD[[MechanicDirectory]]
    end
    subgraph domain
        DM[DispatchMatch]
        MS[[MatchingStrategy]]
        NQ[NearestQualifiedMechanicStrategy]
        BR[BestRatedNearbyMechanicStrategy]
        DRE[[DispatchRepository]]
    end
    subgraph infrastructure
        MRC[MechanicRestClient]
    end

    DC --> DF
    DF --> MD
    DF --> MS
    DF --> DRE
    NQ -.implementa.-> MS
    BR -.implementa.-> MS
    MRC -.implementa.-> MD
    MRC -->|REST| MSV[(mechanic-service)]
```

Los elementos con doble borde son interfaces.

## Patrones exigidos

| Patrón | Ubicación | Propósito |
|---|---|---|
| Factory | `MechanicFactory`, `AssistanceFactory` | Crear agregados válidos y normalizados |
| Repository | `MechanicRepository`, `AssistanceRepository`, `ClientRepository`, `VehicleRepository`, `StatusChangeRepository`, `DispatchRepository` (interfaces del paquete `domain` que extienden Spring Data JPA) | Separar persistencia de casos de uso |
| Strategy | `MatchingStrategy`, implementada por `NearestQualifiedMechanicStrategy` (el más cercano) y `BestRatedNearbyMechanicStrategy` (el mejor calificado dentro de un radio); la propiedad `dispatch.matching.strategy` elige cuál se usa | Cambiar el algoritmo de selección por configuración, sin tocar `DispatchFacade` |
| Observer | `AssistanceFacade` publica `AssistanceRequestedEvent`, que escucha `AssistanceDispatchCoordinator` (`@TransactionalEventListener(AFTER_COMMIT)`), y `AssistanceStatusChangedEvent`, que escucha `StatusHistoryRecorder` (`@EventListener`) | Reaccionar a los eventos sin acoplar el alta al despacho ni las transiciones al historial |
| Facade | `MechanicFacade`, `ClientFacade`, `AssistanceFacade`, `DispatchFacade` | Exponer casos de uso simples a controladores |

## Java moderno en el código

| Característica | Dónde | Para qué |
|---|---|---|
| Records | DTOs de la API, `DispatchCommand`, eventos de dominio, `MechanicCandidate`, `MechanicSelection` | Datos inmutables sin código repetido |
| Interfaz sellada + `switch` con patrones de record | `DispatchOutcome` (`Matched` o `NoCandidate`) en `AssistanceDispatchCoordinator` y `DispatchRestClient` | El compilador exige cubrir todos los casos, sin `default` ni flags de estado |
| Patrones sin nombre (`_`) | `DispatchRestClient.toOutcome` | Ignorar los campos que el caso no usa |
| Virtual threads | `spring.threads.virtual.enabled` en los tres servicios | Cada request corre en un hilo liviano, útil cuando espera llamadas REST a otro servicio |
| Text blocks | Tests de los clientes REST | JSON legible dentro del código |

## Acceso local y remoto

- Local: una fachada invoca Factory, Repository y reglas mediante interfaces Java dentro del proceso.
- Remoto: `assistance-service` llama a `dispatch-service`, y este llama a `mechanic-service`, mediante HTTP REST.

El cliente móvil futuro sólo necesitará conocer los endpoints públicos; no comparte clases Java con el backend.

## Despliegue

```mermaid
flowchart TB
    HOST[Equipo de laboratorio]
    subgraph Docker[Docker Compose]
        M[mechanic-service :8081]
        A[assistance-service :8082]
        D[dispatch-service :8083]
        P[(PostgreSQL/PostGIS :5432)]
    end

    HOST --> M
    HOST --> A
    HOST --> D
    M --> P
    A --> P
    D --> P
    A --> D
    D --> M
```

Cada servicio tiene una base lógica, usuario y contraseña independientes. La misma instancia PostgreSQL se comparte únicamente para reducir recursos en el laboratorio.


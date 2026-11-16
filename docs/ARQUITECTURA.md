# Arquitectura y patrones

Los diagramas de esta página están actualizados a la Segunda Parte. Los de la Primera Parte, en HTML y PNG, siguen en [`diagramas/`](diagramas/README.md).

## Vista de componentes

```mermaid
flowchart LR
    CLIENT[App Android, consola<br/>o Swagger]
    CLIENT -->|REST: registro, login, refresh| IDENT[identity-service :8084]
    CLIENT -->|REST con Bearer token| MECH[mechanic-service :8081]
    CLIENT -->|REST con Bearer token| ASSIST[assistance-service :8082]
    CLIENT -->|REST con Bearer token| DISPATCH[dispatch-service :8083]
    MECH -->|mechanic.events: aprobación| KAFKA
    KAFKA -->|aprobación| IDENT
    MECH & ASSIST & DISPATCH -.->|clave pública JWKS, token de servicio| IDENT

    ASSIST -->|assistance.events| KAFKA[(Kafka)]
    KAFKA -->|alta y cancelación| DISPATCH
    KAFKA -->|calificación| MECH
    DISPATCH -->|dispatch.events| KAFKA
    KAFKA -->|oferta, asignación, sin mecánico| ASSIST
    DISPATCH -->|REST remoto: candidatos| MECH
    DISPATCH -->|REST remoto: aviso de aceptación| ASSIST
    ASSIST -->|REST remoto: volver a publicar| DISPATCH
    UI[Kafka UI :8090] --> KAFKA
    ASSIST -->|HTTPS: sólo el texto del problema| CLAUDE[API de Claude]
    DISPATCH -->|HTTPS: sólo coordenadas| ORS[OpenRouteService]

    IDENT --> IDB[(identity_db)]
    MECH --> MDB[(mechanics_db)]
    ASSIST --> ADB[(assistances_db)]
    DISPATCH --> DDB[(dispatch_db)]
```

## Flujo principal

```mermaid
sequenceDiagram
    participant C as Cliente
    participant A as assistance-service
    participant Q as Kafka
    participant D as dispatch-service
    participant M as mechanic-service
    participant K as Mecánico

    Note over C,K: Cada pedido lleva el token de identity-service. El mecánico ya fue aprobado por un admin (ver Usuarios y roles)
    K->>M: PATCH /api/mechanics/{id}/availability
    C->>A: POST /api/clients
    C->>A: POST /api/clients/{id}/vehicles
    C->>A: POST /api/assistances (Idempotency-Key, vehicleId, description: el cliente sale del token)
    A->>A: TriageService: problema, habilidad y urgencia (Claude o palabras clave)
    A->>A: AssistanceFacade: Factory + Repository (estado REQUESTED)
    A->>A: AssistanceRequestedEvent: el evento queda en el outbox, en la misma transacción
    A-->>C: 201 Created (REQUESTED)
    A->>Q: ASSISTANCE_REQUESTED en assistance.events
    Q->>D: ASSISTANCE_REQUESTED
    D->>M: GET /api/mechanics/candidates?skill=...&vehicleType=...
    M-->>D: candidatos compatibles
    D->>D: MatchingStrategy ordena a los candidatos
    D->>D: TravelTimeEstimator: tiempo de llegada por calle (OpenRouteService) o en línea recta
    D->>D: guarda las ofertas con distancia y tiempo de llegada
    D->>Q: DISPATCH_OFFERED o DISPATCH_UNMATCHED en dispatch.events
    Q->>A: awaitOffers (offersExpireAt) o markUnmatched
    K->>D: GET /api/dispatches/offers (el mecánico sale del token)
    K->>D: POST /api/dispatches/{id}/accept
    D->>D: UPDATE condicional: el primero que acepta toma el pedido
    D->>A: POST /api/assistances/{id}/assign
    A-->>D: asistencia MATCHED
    D-->>K: 200 con el despacho MATCHED
    C->>A: GET /api/assistances/{id}
    A-->>C: asistencia MATCHED con mechanicId
```

El alta no espera al despacho: responde `201` con el pedido en `REQUESTED` y el resto viaja por Kafka. En menos de un segundo el pedido tiene `offersExpireAt`, la hora hasta la que los mecánicos pueden aceptar, o pasa a `UNMATCHED` si no había candidatos. Los topics, el outbox, los reintentos y Kafka UI están en [Mensajería con Kafka](MENSAJERIA.md).

`dispatch-service` no asigna solo: ofrece el pedido a los mejores candidatos y se lo queda el primero que acepta. Cómo se evita que dos mecánicos tomen el mismo pedido, que un reintento lo duplique y que llegue un pedido viejo está en [Ofertas y aceptación](OFERTAS_Y_ACEPTACION.md).

Si `dispatch-service` está caído, el evento lo espera en Kafka y se procesa cuando vuelve. El alta nunca falla por eso. El despacho también se puede pedir a mano con `POST /api/assistances/{id}/dispatch`, que llama a `dispatch-service` por REST.

### Fallas de un servicio remoto

Cada cliente REST tiene un tiempo máximo de conexión y de respuesta, configurable por variable de entorno. Cuando el otro servicio no responde a tiempo o no está levantado, la API devuelve un error explícito en formato Problem Details en lugar de un 500:

| Quién llama | Qué falla | Respuesta | Tiempo máximo |
|---|---|---|---|
| `dispatch-service` | `mechanic-service` caído, lento o con error | `503` en `POST /api/dispatches` | `MECHANIC_SERVICE_TIMEOUT` (3 s) |
| `assistance-service` | `dispatch-service` caído o lento | `503` en `POST /api/assistances/{id}/dispatch` | `DISPATCH_SERVICE_TIMEOUT` (5 s) |
| `assistance-service` | `dispatch-service` responde con error | `502` en `POST /api/assistances/{id}/dispatch` | |
| `dispatch-service` | OpenRouteService caído, lento, sin clave o sin ruta para un mecánico | Nada: el tiempo de llegada se estima en línea recta y la oferta sale igual | `ORS_TIMEOUT` (3 s) |
| `dispatch-service` | `assistance-service` caído o lento al avisar la aceptación | `503` en `POST /api/dispatches/{id}/accept`; la oferta sigue abierta | `ASSISTANCE_SERVICE_TIMEOUT` (3 s) |
| `assistance-service`, `dispatch-service` | `identity-service` no da el token de servicio | Lo mismo que si no respondiera el servicio de destino | `mecanicosya.security.service-client.timeout` (3 s) |

La clasificación con IA tampoco frena el alta: si la API de Claude no responde en `TRIAGE_TIMEOUT` (10 s) o falla, el pedido se clasifica con palabras clave y sigue. Está en [Clasificación con IA](CLASIFICACION_CON_IA.md).

Lo que viaja por Kafka (el alta, la cancelación y la calificación) no depende de que el otro servicio esté levantado: el mensaje queda en el topic y el consumidor lo procesa cuando vuelve.

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

### identity-service por capa

```mermaid
flowchart TB
    subgraph api
        AC[AuthController / JwksController]
        AD[AuthDtos]
        IE[IdentityExceptionHandler]
    end
    subgraph application
        AF[AuthFacade]
        TI[TokenIssuer]
        AB[AdminBootstrap]
    end
    subgraph domain
        UA[UserAccount / RefreshToken]
        UR[[UserAccountRepository / RefreshTokenRepository]]
    end
    subgraph infrastructure
        ML[MechanicEventsListener]
    end

    AC --> AF
    AB --> AF
    ML --> AF
    AF --> TI
    AF --> UR
    UR -.persiste.-> UA
```

Los otros tres servicios no tienen una capa nueva: cada uno declara qué rol llama a cada endpoint con `ApiSecurity`, y los controladores reciben un `CurrentUser` con el id del token para validar que el recurso sea suyo.

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
        RT[MechanicRating]
        MFA[MechanicFactory]
        MR[[MechanicRepository / MechanicRatingRepository]]
    end

    MC --> MF
    MF --> MFA
    MF --> MR
    MFA -.crea.-> M
    MF -.registra.-> RT
```

`mechanic-service` no llama a otros servicios, así que no tiene capa `infrastructure`: sus únicos adaptadores son los repositorios de Spring Data. Recibe llamadas REST de `dispatch-service` (candidatos) y consume de Kafka las calificaciones que publica `assistance-service`.

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
        MRP[[MechanicRatingPort]]
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
        AEP[AssistanceEventPublisher]
        KMR[KafkaMechanicRatingPublisher]
        DEL[DispatchEventsListener]
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
    EV -.escucha.-> AEP
    SEV -.escucha.-> AEP
    SHR --> REP
    ADC --> AF
    ADC --> DP
    DRC -.implementa.-> DP
    DRC -->|REST| DS[(dispatch-service)]
    AF --> MRP
    KMR -.implementa.-> MRP
    AEP -->|outbox| K[(Kafka)]
    KMR -->|outbox| K
    K -->|dispatch.events| DEL
    DEL --> AF
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
        AAP[[AssistanceAssignmentPort]]
        DEP[[DispatchEventPublisher]]
    end
    subgraph domain
        DM[DispatchMatch]
        MS[[MatchingStrategy]]
        NQ[NearestQualifiedMechanicStrategy]
        BR[BestRatedNearbyMechanicStrategy]
        FE[FastestEtaMechanicStrategy]
        TTE[[TravelTimeEstimator]]
        RTE[ResilientTravelTimeEstimator]
        DRE[[DispatchRepository]]
    end
    subgraph infrastructure
        MRC[MechanicRestClient]
        ARC[AssistanceRestClient]
        KDP[KafkaDispatchEventPublisher]
        AEL[AssistanceEventsListener]
        ORC[OpenRouteServiceClient]
    end

    DC --> DF
    AEL --> DF
    DF --> AAP
    DF --> DEP
    ARC -.implementa.-> AAP
    KDP -.implementa.-> DEP
    KDP -->|outbox| KQ[(Kafka)]
    DF --> MD
    DF --> MS
    DF --> DRE
    NQ -.implementa.-> MS
    BR -.implementa.-> MS
    FE -.implementa.-> MS
    FE --> TTE
    DF --> TTE
    RTE -.implementa.-> TTE
    ORC -.implementa.-> TTE
    RTE --> ORC
    MRC -.implementa.-> MD
    MRC -->|REST| MSV[(mechanic-service)]
```

Los elementos con doble borde son interfaces.

## Patrones exigidos

| Patrón | Ubicación | Propósito |
|---|---|---|
| Factory | `MechanicFactory`, `AssistanceFactory` | Crear agregados válidos y normalizados |
| Repository | `MechanicRepository`, `MechanicRatingRepository`, `AssistanceRepository`, `ClientRepository`, `VehicleRepository`, `StatusChangeRepository`, `DispatchRepository`, `DispatchOfferRepository` (interfaces del paquete `domain` que extienden Spring Data JPA) | Separar persistencia de casos de uso |
| Strategy | `MatchingStrategy`, implementada por `NearestQualifiedMechanicStrategy` (primero el más cercano), `BestRatedNearbyMechanicStrategy` (primero el mejor calificado dentro de un radio) y `FastestEtaMechanicStrategy` (primero el que llega antes por calle); la propiedad `dispatch.matching.strategy` elige cuál se usa | Cambiar por configuración el orden en que se ofrece el pedido, sin tocar `DispatchFacade` |
| Observer | `AssistanceFacade` publica `AssistanceRequestedEvent` y `AssistanceStatusChangedEvent`. Los escuchan `StatusHistoryRecorder`, que guarda el historial, y `AssistanceEventPublisher`, que los deja en el outbox para Kafka (los dos con `@EventListener`, dentro de la transacción) | Reaccionar a los eventos sin acoplar el alta al despacho ni las transiciones al historial |
| Strategy (clasificación) | `ProblemClassifier`, implementada por `ClaudeProblemClassifier` (API de Claude) y `KeywordProblemClassifier` (palabras clave); `TriageService` usa la primera y cae a la segunda | Clasificar con IA sin que el pedido dependa de que la API responda |
| Transactional Outbox | `Outbox` y `OutboxRelay` en `shared-messaging` | Publicar en Kafka sólo lo que se confirmó en la base, sin perder eventos si Kafka está caído |
| Facade | `MechanicFacade`, `ClientFacade`, `AssistanceFacade`, `DispatchFacade` | Exponer casos de uso simples a controladores |

## Java moderno en el código

| Característica | Dónde | Para qué |
|---|---|---|
| Records | DTOs de la API, `DispatchCommand`, eventos de dominio, `MechanicCandidate`, `MechanicSelection` | Datos inmutables sin código repetido |
| Interfaz sellada + `switch` con patrones de record | `DispatchOutcome` (`Offered`, `Matched` o `NoCandidate`) en `AssistanceDispatchCoordinator` y `DispatchRestClient` | El compilador exige cubrir todos los casos, sin `default` ni flags de estado |
| Patrones sin nombre (`_`) | `DispatchRestClient.toOutcome` | Ignorar los campos que el caso no usa |
| Virtual threads | `spring.threads.virtual.enabled` en los tres servicios | Cada request corre en un hilo liviano, útil cuando espera llamadas REST a otro servicio |
| Text blocks | Tests de los clientes REST | JSON legible dentro del código |

## Acceso local y remoto

- Local: una fachada invoca Factory, Repository y reglas mediante interfaces Java dentro del proceso.
- Remoto: `assistance-service` llama a `dispatch-service`, y este llama a `mechanic-service`, mediante HTTP REST, con un token de servicio que emite `identity-service`.

La app móvil sólo necesita conocer los endpoints públicos y el contrato del login; no comparte clases Java con el backend.

## Despliegue

```mermaid
flowchart TB
    HOST[Equipo de laboratorio]
    subgraph Docker[Docker Compose]
        F[frontend nginx :8080]
        I[identity-service :8084]
        M[mechanic-service :8081]
        A[assistance-service :8082]
        D[dispatch-service :8083]
        K[(Kafka :29092)]
        U[Kafka UI :8090]
        P[(PostgreSQL/PostGIS :5432)]
        S[postgres-setup]
    end

    HOST --> F
    HOST --> U
    F --> I & M & A & D
    I & M & A & D --> P
    M & A & D --> K
    I --> K
    U --> K
    S -.crea identity_db.-> P
    A --> D
    D --> M
    D --> A
```

Cada servicio tiene una base lógica, usuario y contraseña independientes (`identity_db`, `mechanics_db`, `assistances_db`, `dispatch_db`). La misma instancia PostgreSQL se comparte únicamente para reducir recursos en el laboratorio.


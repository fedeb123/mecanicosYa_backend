# Diagrama de clases

Un diagrama por microservicio. Muestra las clases de dominio, aplicación e infraestructura; los DTOs y controladores están en [ARQUITECTURA.md](ARQUITECTURA.md#arquitectura-en-capas).

Entre servicios no hay asociaciones: cada uno guarda el `UUID` del otro.

## assistance-service

```mermaid
classDiagram
    direction LR

    class Client {
        <<Entity>>
        -UUID id
        -String fullName
        -String phone
        -String email
        -Instant createdAt
    }

    class Vehicle {
        <<Entity>>
        -UUID id
        -Client client
        -VehicleType type
        -String brand
        -String model
        -String color
        -String plate
        +belongsTo(UUID clientId) boolean
    }

    class VehicleType {
        <<enumeration>>
        BICYCLE
        E_BIKE
        MOTORCYCLE
        +requiresPlate() boolean
    }

    class AssistanceRequest {
        <<Entity>>
        -UUID id
        -Client client
        -Vehicle vehicle
        -VehicleType vehicleType
        -String description
        -String problemType
        -String requiredSkill
        -double latitude
        -double longitude
        -AssistanceStatus status
        -UUID assignedMechanicId
        -Integer rating
        -String ratingComment
        -Instant ratedAt
        -String idempotencyKey
        -Instant clientCreatedAt
        -Instant offersExpireAt
        -Instant createdAt
        -long version
        +awaitOffers(Instant expiresAt)
        +assignMechanic(UUID mechanicId)
        +markUnmatched()
        +republish()
        +start()
        +complete()
        +cancel()
        +rate(int score, String comment, Instant ratedAt)
    }

    class AssistanceStatus {
        <<enumeration>>
        REQUESTED
        UNMATCHED
        MATCHED
        IN_PROGRESS
        COMPLETED
        CANCELLED
    }

    class StatusChange {
        <<Entity>>
        -UUID id
        -AssistanceRequest assistance
        -AssistanceStatus fromStatus
        -AssistanceStatus toStatus
        -Instant changedAt
    }

    class AssistanceFactory {
        <<Factory>>
        -Clock clock
        +create(Client, Vehicle, ...) AssistanceRequest
    }

    class ClientFacade {
        <<Facade>>
        +register(String fullName, String phone, String email) Client
        +find(UUID clientId) Client
        +registerVehicle(UUID clientId, VehicleType type, ...) Vehicle
        +vehicles(UUID clientId) List~Vehicle~
        +findVehicle(UUID vehicleId) Vehicle
    }

    class AssistanceFacade {
        <<Facade>>
        +request(UUID clientId, UUID vehicleId, ..., Instant clientCreatedAt, Triage triage) AssistanceCreation
        +find(UUID id) AssistanceRequest
        +history(UUID id) List~StatusChange~
        +awaitOffers(UUID assistanceId, Instant expiresAt)
        +applyMatch(UUID assistanceId, UUID mechanicId)
        +assign(UUID assistanceId, UUID mechanicId) AssistanceRequest
        +markUnmatched(UUID assistanceId)
        +start(UUID id) AssistanceRequest
        +complete(UUID id) AssistanceRequest
        +cancel(UUID id) AssistanceRequest
        +rate(UUID id, int score, String comment) AssistanceRequest
    }

    class AssistanceIntake {
        +request(UUID clientId, UUID vehicleId, String description, ...) AssistanceCreation
    }

    class TriageService {
        +classify(String description, VehicleType vehicleType) Triage
    }

    class ProblemClassifier {
        <<interface>>
        +classify(String description, VehicleType vehicleType) Triage
    }

    class ClaudeProblemClassifier
    class KeywordProblemClassifier

    class Triage {
        <<record>>
        ProblemCategory category
        Urgency urgency
        double confidence
        TriageSource source
    }

    class AssistanceRequestedEvent {
        <<record>>
        UUID eventId
        UUID assistanceId
        UUID vehicleId
        VehicleType vehicleType
        String requiredSkill
    }

    class AssistanceStatusChangedEvent {
        <<record>>
        UUID assistanceId
        AssistanceStatus fromStatus
        AssistanceStatus toStatus
        Instant occurredAt
    }

    class AssistanceDispatchCoordinator {
        +dispatch(UUID assistanceId) AssistanceRequest
    }

    class AssistanceEventPublisher {
        <<Observer>>
        -Outbox outbox
        +onRequested(AssistanceRequestedEvent event)
        +onStatusChanged(AssistanceStatusChangedEvent event)
    }

    class DispatchEventsListener {
        <<KafkaListener>>
        +onDispatchEvent(String payload)
    }

    class OfferExpiryJob {
        <<Scheduled>>
        +expireOffers()
    }

    class RequestPolicy {
        <<record>>
        Duration maxAge
        Duration maxClockSkew
    }

    class StatusHistoryRecorder {
        <<Observer>>
        +onStatusChanged(AssistanceStatusChangedEvent event)
    }

    class DispatchPort {
        <<interface>>
        +dispatch(DispatchCommand command) DispatchOutcome
    }

    class DispatchRestClient {
        -RestClient restClient
        +dispatch(DispatchCommand command) DispatchOutcome
    }

    class MechanicRatingPort {
        <<interface>>
        +rate(UUID mechanicId, UUID assistanceId, int score)
    }

    class KafkaMechanicRatingPublisher {
        -Outbox outbox
        +rate(UUID mechanicId, UUID assistanceId, int score)
    }

    class DispatchUnavailableException {
        <<exception>>
    }

    class DispatchFailedException {
        <<exception>>
    }

    class DispatchCommand {
        <<record>>
        UUID assistanceId
        String requiredSkill
        double latitude
        double longitude
        VehicleType vehicleType
    }

    class DispatchOutcome {
        <<sealed interface>>
    }

    class Offered {
        <<record>>
        Instant expiresAt
        int offers
    }

    class Matched {
        <<record>>
        UUID mechanicId
        double distanceKm
    }

    class NoCandidate {
        <<record>>
    }

    Client "1" <-- "0..*" Vehicle : client
    Client "1" <-- "0..*" AssistanceRequest : client
    Vehicle "1" <-- "0..*" AssistanceRequest : vehicle
    AssistanceRequest "1" <-- "0..*" StatusChange : assistance
    AssistanceRequest --> AssistanceStatus
    Vehicle --> VehicleType

    AssistanceFactory ..> AssistanceRequest : crea
    ClientFacade --> ClientRepository
    ClientFacade --> VehicleRepository
    AssistanceIntake --> TriageService
    AssistanceIntake --> AssistanceFacade
    TriageService --> ProblemClassifier
    ClaudeProblemClassifier ..|> ProblemClassifier
    KeywordProblemClassifier ..|> ProblemClassifier
    ProblemClassifier ..> Triage
    AssistanceFacade --> AssistanceFactory
    AssistanceFacade --> AssistanceRepository
    AssistanceFacade --> StatusChangeRepository
    AssistanceFacade --> ClientFacade
    AssistanceFacade ..> AssistanceRequestedEvent : publica
    AssistanceFacade ..> AssistanceStatusChangedEvent : publica
    AssistanceEventPublisher ..> AssistanceRequestedEvent : escucha
    AssistanceEventPublisher ..> AssistanceStatusChangedEvent : escucha
    DispatchEventsListener --> AssistanceFacade
    OfferExpiryJob --> AssistanceFacade
    AssistanceFacade --> RequestPolicy
    StatusHistoryRecorder ..> AssistanceStatusChangedEvent : escucha
    StatusHistoryRecorder --> StatusChangeRepository
    AssistanceDispatchCoordinator --> AssistanceFacade
    AssistanceDispatchCoordinator --> DispatchPort
    DispatchRestClient ..|> DispatchPort
    AssistanceFacade --> MechanicRatingPort
    KafkaMechanicRatingPublisher ..|> MechanicRatingPort
    DispatchRestClient ..> DispatchUnavailableException : lanza
    DispatchRestClient ..> DispatchFailedException : lanza
    DispatchPort ..> DispatchCommand
    DispatchPort ..> DispatchOutcome
    Offered ..|> DispatchOutcome
    Matched ..|> DispatchOutcome
    NoCandidate ..|> DispatchOutcome

    class ClientRepository {
        <<interface>>
    }
    class VehicleRepository {
        <<interface>>
    }
    class AssistanceRepository {
        <<interface>>
    }
    class StatusChangeRepository {
        <<interface>>
    }
```

Los cuatro repositorios son interfaces del paquete `domain` que extienden `JpaRepository`. Dentro del servicio, `Vehicle`, `AssistanceRequest` y `StatusChange` referencian a sus entidades por asociación JPA (clave foránea). Una transición no permitida lanza `InvalidStatusTransitionException`, y una segunda calificación `AssistanceAlreadyRatedException`; la API traduce las dos a 409.

`DispatchOutcome` es una interfaz sellada con tres casos: `Offered` (el pedido quedó ofrecido hasta `expiresAt`), `Matched` y `NoCandidate`. `AssistanceEventPublisher` escucha los eventos de dominio dentro de la transacción y deja en el outbox los eventos de Kafka (alta y cancelación); `KafkaMechanicRatingPublisher` hace lo mismo con la calificación. `DispatchEventsListener` aplica lo que avisa dispatch-service por `dispatch.events`. `AssistanceDispatchCoordinator` queda para volver a publicar un pedido por REST. `OfferExpiryJob` pasa a `UNMATCHED` los pedidos cuya ronda de ofertas venció. La restricción única sobre (`client_id`, `idempotency_key`) hace que un reintento devuelva el pedido existente en vez de crear otro.

`AssistanceIntake` clasifica el texto del pedido antes del alta, fuera de la transacción: `TriageService` usa `ClaudeProblemClassifier` si hay `ANTHROPIC_API_KEY` y `KeywordProblemClassifier` si no la hay o la API falla ([Clasificación con IA](CLASIFICACION_CON_IA.md)). El pedido guarda `urgency`, `triageSource` y `triageConfidence`.

`AssistanceRequestedEvent` también lleva `occurredAt`, `eventType`, `clientId`, `description`, `problemType` y coordenadas; el diagrama muestra sólo los campos que usa el despacho.

## dispatch-service

```mermaid
classDiagram
    direction LR

    class DispatchMatch {
        <<Entity>>
        -UUID assistanceId
        -UUID mechanicId
        -Double distanceKm
        -DispatchStatus status
        -int round
        -Instant offersExpireAt
        -Instant completedAt
        -Instant createdAt
        -long version
        +offered(UUID assistanceId, ..., Instant offersExpireAt)$ DispatchMatch
        +noCandidate(UUID assistanceId, ...)$ DispatchMatch
        +reopen(Instant offersExpireAt) int
        +expire()
        +cancel()
        +complete(Instant now)
        +isActiveJob() boolean
        +isOpen(Instant now) boolean
    }

    class DispatchStatus {
        <<enumeration>>
        OFFERED
        MATCHED
        NO_CANDIDATE
        UNMATCHED
        CANCELLED
    }

    class DispatchOffer {
        <<Entity>>
        -UUID id
        -UUID assistanceId
        -int round
        -UUID mechanicId
        -double distanceKm
        -int ranking
        -OfferStatus status
        -Instant respondedAt
        +accept(Instant now)
        +decline(Instant now)
    }

    class OfferStatus {
        <<enumeration>>
        PENDING
        ACCEPTED
        DECLINED
        LOST
        EXPIRED
        WITHDRAWN
    }

    class MatchingStrategy {
        <<interface>>
        +rank(double lat, double lon, List~MechanicCandidate~ candidates) List~MechanicSelection~
        +select(double lat, double lon, List~MechanicCandidate~ candidates) Optional~MechanicSelection~
    }

    class NearestQualifiedMechanicStrategy {
        +rank(...) List~MechanicSelection~
    }

    class BestRatedNearbyMechanicStrategy {
        -double maxDistanceKm
        +rank(...) List~MechanicSelection~
    }

    class FastestEtaMechanicStrategy {
        -double maxDistanceKm
        -int maxRouteCandidates
        +rank(...) List~MechanicSelection~
    }

    class TravelTimeEstimator {
        <<interface>>
        +estimate(double lat, double lon, List~MechanicCandidate~ mechanics) List~RouteEstimate~
    }

    class ResilientTravelTimeEstimator
    class StraightLineTravelTimeEstimator
    class OpenRouteServiceClient

    class RouteEstimate {
        <<record>>
        double distanceKm
        int etaMinutes
        EtaSource source
    }

    class GeoDistance {
        <<utility>>
        +haversineKm(double lat1, double lon1, double lat2, double lon2)$ double
        +round(double value)$ double
    }

    class MechanicCandidate {
        <<record>>
        UUID id
        String name
        String email
        double latitude
        double longitude
        boolean available
        double rating
        Set~String~ skills
    }

    class MechanicSelection {
        <<record>>
        MechanicCandidate mechanic
        double distanceKm
        double score
    }

    class DispatchRepository {
        <<interface>>
        +claim(UUID assistanceId, int round, UUID mechanicId, double distanceKm, Instant now) int
    }

    class DispatchOfferRepository {
        <<interface>>
        +findOpenOffers(UUID mechanicId, Instant now) List~OpenOffer~
        +closePending(UUID assistanceId, int round, OfferStatus status) int
    }

    class DispatchFacade {
        <<Facade>>
        +process(DispatchCommand command) DispatchMatch
        +find(UUID assistanceId) DispatchMatch
        +openOffers(UUID mechanicId) List~OpenOffer~
        +accept(UUID assistanceId, UUID mechanicId) DispatchMatch
        +decline(UUID assistanceId, UUID mechanicId)
        +cancel(UUID assistanceId) DispatchMatch
        +expireOffers() int
    }

    class OfferPolicy {
        <<record>>
        int maxOffers
        Duration ttl
    }

    class AssistanceAssignmentPort {
        <<interface>>
        +assign(UUID assistanceId, UUID mechanicId)
    }

    class AssistanceRestClient {
        -RestClient restClient
        +assign(UUID assistanceId, UUID mechanicId)
    }

    class OfferExpiryJob {
        <<Scheduled>>
        +expireOffers()
    }

    class MechanicDirectory {
        <<interface>>
        +findCandidates(String skill, String vehicleType) List~MechanicCandidate~
    }

    class MechanicRestClient {
        -RestClient restClient
        +findCandidates(String skill, String vehicleType) List~MechanicCandidate~
    }

    class MechanicDirectoryUnavailableException {
        <<exception>>
    }

    class DispatchCommand {
        <<record>>
        UUID assistanceId
        String requiredSkill
        double latitude
        double longitude
        String vehicleType
    }

    class DispatchEventPublisher {
        <<interface>>
        +offered(DispatchMatch dispatch, int offers)
        +matched(DispatchMatch dispatch)
        +unmatched(DispatchMatch dispatch, UnmatchedReason reason)
    }

    class KafkaDispatchEventPublisher {
        -Outbox outbox
    }

    class AssistanceEventsListener {
        <<KafkaListener>>
        +onAssistanceEvent(String payload)
    }

    DispatchMatch --> DispatchStatus
    DispatchOffer --> OfferStatus
    DispatchOfferRepository ..> DispatchOffer : persiste
    DispatchFacade --> DispatchOfferRepository
    DispatchFacade --> OfferPolicy
    DispatchFacade --> AssistanceAssignmentPort
    AssistanceRestClient ..|> AssistanceAssignmentPort
    DispatchFacade --> DispatchEventPublisher
    KafkaDispatchEventPublisher ..|> DispatchEventPublisher
    AssistanceEventsListener --> DispatchFacade
    OfferExpiryJob --> DispatchFacade
    NearestQualifiedMechanicStrategy ..|> MatchingStrategy
    BestRatedNearbyMechanicStrategy ..|> MatchingStrategy
    FastestEtaMechanicStrategy ..|> MatchingStrategy
    FastestEtaMechanicStrategy --> TravelTimeEstimator
    DispatchFacade --> TravelTimeEstimator
    ResilientTravelTimeEstimator ..|> TravelTimeEstimator
    StraightLineTravelTimeEstimator ..|> TravelTimeEstimator
    OpenRouteServiceClient ..|> TravelTimeEstimator
    ResilientTravelTimeEstimator --> OpenRouteServiceClient
    ResilientTravelTimeEstimator --> StraightLineTravelTimeEstimator
    TravelTimeEstimator ..> RouteEstimate
    NearestQualifiedMechanicStrategy ..> GeoDistance
    BestRatedNearbyMechanicStrategy ..> GeoDistance
    MatchingStrategy ..> MechanicCandidate
    MatchingStrategy ..> MechanicSelection
    MechanicSelection --> MechanicCandidate
    DispatchRepository ..> DispatchMatch : persiste
    DispatchFacade --> DispatchRepository
    DispatchFacade --> MatchingStrategy
    DispatchFacade --> MechanicDirectory
    DispatchFacade ..> DispatchCommand
    MechanicRestClient ..|> MechanicDirectory
    MechanicRestClient ..> MechanicDirectoryUnavailableException : lanza
```

`DispatchFacade.process` es idempotente: si ya existe un `DispatchMatch` abierto o tomado para la asistencia, lo devuelve sin volver a ejecutar la estrategia; si quedó sin mecánico, abre una ronda nueva. Cada ronda guarda un `DispatchOffer` por candidato. `DispatchFacade.accept` asigna con el `UPDATE` condicional de `DispatchRepository.claim` y avisa a assistance-service por el puerto `AssistanceAssignmentPort`, que implementa `AssistanceRestClient`. El detalle está en [Ofertas y aceptación](OFERTAS_Y_ACEPTACION.md).

Cada cambio del despacho (ofrecido, tomado o sin mecánico) se avisa con `DispatchEventPublisher`, que lo deja en el outbox para publicarlo en `dispatch.events`. `AssistanceEventsListener` recibe por Kafka las altas y las cancelaciones de pedidos.

La estrategia activa se elige con la propiedad `dispatch.matching.strategy`: `nearest` (valor por defecto) activa `NearestQualifiedMechanicStrategy`, que ordena a los mecánicos disponibles por cercanía y descuenta 100 m por cada punto de calificación; `best-rated` activa `BestRatedNearbyMechanicStrategy`, que pone primero al mejor calificado dentro de `dispatch.matching.max-distance-km` (5 km por defecto). El pedido se ofrece a los primeros `dispatch.offers.max-offers` (3 por defecto). `fastest-eta` activa `FastestEtaMechanicStrategy`, que pide la ruta por calle de los más cercanos dentro del radio y pone primero al que llega antes. Spring crea sólo una de las tres, así que `DispatchFacade` no sabe cuál está usando.

`DispatchFacade` completa el tiempo de llegada de las ofertas que la estrategia no calculó, con `TravelTimeEstimator`. La implementación es `ResilientTravelTimeEstimator`: usa `OpenRouteServiceClient` si hay `ORS_API_KEY` y `StraightLineTravelTimeEstimator` si no la hay, si la API falla o si no encuentra ruta para un mecánico ([API externa: rutas](API_EXTERNA_RUTAS.md)).

## mechanic-service

```mermaid
classDiagram
    direction LR

    class Mechanic {
        <<Entity>>
        -UUID id
        -String name
        -String email
        -double latitude
        -double longitude
        -boolean available
        -double rating
        -int ratingCount
        -Set~String~ skills
        -Set~VehicleType~ vehicleTypes
        -VerificationStatus verificationStatus
        -long version
        +changeAvailability(boolean available, double latitude, double longitude)
        +approve()
        +isApproved() boolean
        +addRating(int score)
        +hasSkill(String requiredSkill) boolean
        +serves(VehicleType vehicleType) boolean
    }

    class MechanicRating {
        <<Entity>>
        -UUID assistanceId
        -UUID mechanicId
        -int score
        -Instant createdAt
    }

    class MechanicRatingRepository {
        <<interface>>
    }

    class VehicleType {
        <<enumeration>>
        BICYCLE
        E_BIKE
        MOTORCYCLE
    }

    class VerificationStatus {
        <<enumeration>>
        PENDING_VERIFICATION
        APPROVED
    }

    class MechanicFactory {
        <<Factory>>
        +create(UUID id, String name, String email, double latitude, double longitude, Set~String~ skills, Set~VehicleType~ vehicleTypes) Mechanic
    }

    class MechanicEventPublisher {
        <<interface>>
        +verificationChanged(Mechanic mechanic)
    }

    class KafkaMechanicEventPublisher {
        <<Adapter>>
    }

    class MechanicRepository {
        <<interface>>
        +findAvailableBySkill(String skill) List~Mechanic~
        +findAvailableBySkillAndVehicleType(String skill, VehicleType vehicleType) List~Mechanic~
    }

    class MechanicFacade {
        <<Facade>>
        +register(UUID id, ...) Mechanic
        +approve(UUID id) Mechanic
        +list(VerificationStatus status) List~Mechanic~
        +find(UUID id) Mechanic
        +changeAvailability(UUID id, boolean available, double latitude, double longitude) Mechanic
        +findCandidates(String skill, VehicleType vehicleType) List~Mechanic~
        +rate(UUID mechanicId, UUID assistanceId, int score) Mechanic
    }

    Mechanic --> VehicleType
    Mechanic --> VerificationStatus
    MechanicFacade --> MechanicEventPublisher
    KafkaMechanicEventPublisher ..|> MechanicEventPublisher
    MechanicRatingRepository ..> MechanicRating : persiste
    MechanicFacade --> MechanicRatingRepository
    MechanicFactory ..> Mechanic : crea
    MechanicRepository ..> Mechanic : persiste
    MechanicFacade --> MechanicFactory
    MechanicFacade --> MechanicRepository
```

Las habilidades (`mechanic_skills`) y los tipos de vehículo que atiende (`mechanic_vehicle_types`) son colecciones de valores de `Mechanic` (`@ElementCollection`), no entidades propias. Si no se indican tipos al registrar un mecánico, atiende `BICYCLE`.

`MechanicRating` usa como clave la asistencia: cada asistencia califica una sola vez, y repetir el pedido no cambia el promedio. Mientras no tenga calificaciones, el mecánico muestra 5.0; la primera calificación reemplaza ese valor.

El id del mecánico es el `userId` de su cuenta en identity-service. Arranca `PENDING_VERIFICATION`: `changeAvailability(true, ...)` lanza `MechanicNotApprovedException` hasta que un admin lo aprueba. `approve` publica `MECHANIC_VERIFICATION_CHANGED` por el outbox con `KafkaMechanicEventPublisher`.

## identity-service

```mermaid
classDiagram
    direction LR

    class UserAccount {
        <<Entity>>
        -UUID id
        -String email
        -String passwordHash
        -Role role
        -VerificationStatus verificationStatus
        -Instant createdAt
        +register(String email, String passwordHash, Role role, Instant now)$ UserAccount
        +admin(String email, String passwordHash, Instant now)$ UserAccount
        +changeVerification(VerificationStatus status)
    }

    class RefreshToken {
        <<Entity>>
        -UUID id
        -UUID userId
        -String tokenHash
        -Instant expiresAt
        -Instant revokedAt
        +isActive(Instant now) boolean
        +revoke(Instant now)
    }

    class Role {
        <<enumeration>>
        RIDER
        MECHANIC
        ADMIN
        SERVICE
    }

    class VerificationStatus {
        <<enumeration>>
        PENDING_VERIFICATION
        APPROVED
    }

    class UserAccountRepository {
        <<interface>>
        +findByEmail(String email) Optional~UserAccount~
    }

    class RefreshTokenRepository {
        <<interface>>
        +findByTokenHash(String hash) Optional~RefreshToken~
    }

    class AuthFacade {
        <<Facade>>
        +register(String email, String password, Role role) AuthSession
        +login(String email, String password) AuthSession
        +refresh(String refreshToken) AuthSession
        +logout(String refreshToken)
        +serviceToken(String clientId, String clientSecret) IssuedToken
        +changeVerification(UUID mechanicId, VerificationStatus status)
    }

    class TokenIssuer {
        +forUser(UserAccount user) IssuedToken
        +forService(String serviceName) IssuedToken
    }

    class MechanicEventsListener {
        <<Observer>>
        +onMechanicEvent(String payload)
    }

    UserAccount --> Role
    UserAccount --> VerificationStatus
    UserAccountRepository ..> UserAccount : persiste
    RefreshTokenRepository ..> RefreshToken : persiste
    AuthFacade --> UserAccountRepository
    AuthFacade --> RefreshTokenRepository
    AuthFacade --> TokenIssuer
    MechanicEventsListener --> AuthFacade
```

`Role` está en `shared-security`, la librería que usan los cuatro servicios para validar el token (`ApiSecurity`, `CurrentUser`, `JwtRoleConverter`) y para pedir el token de servicio (`ServiceTokenProvider`, `ServiceTokenInterceptor`). El refresh token se guarda como hash SHA-256 y la contraseña con BCrypt. `TokenIssuer` firma con la clave RSA que se genera al arrancar.

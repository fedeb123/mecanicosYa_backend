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
        -Instant createdAt
        -long version
        +assignMechanic(UUID mechanicId)
        +start()
        +complete()
        +cancel()
    }

    class AssistanceStatus {
        <<enumeration>>
        REQUESTED
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
        +request(UUID clientId, UUID vehicleId, ...) AssistanceRequest
        +find(UUID id) AssistanceRequest
        +history(UUID id) List~StatusChange~
        +applyMatch(UUID assistanceId, UUID mechanicId)
        +start(UUID id) AssistanceRequest
        +complete(UUID id) AssistanceRequest
        +cancel(UUID id) AssistanceRequest
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
        <<Observer>>
        +onAssistanceRequested(AssistanceRequestedEvent event)
        +dispatch(UUID assistanceId) AssistanceRequest
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
    AssistanceFacade --> AssistanceFactory
    AssistanceFacade --> AssistanceRepository
    AssistanceFacade --> StatusChangeRepository
    AssistanceFacade --> ClientFacade
    AssistanceFacade ..> AssistanceRequestedEvent : publica
    AssistanceFacade ..> AssistanceStatusChangedEvent : publica
    AssistanceDispatchCoordinator ..> AssistanceRequestedEvent : escucha
    StatusHistoryRecorder ..> AssistanceStatusChangedEvent : escucha
    StatusHistoryRecorder --> StatusChangeRepository
    AssistanceDispatchCoordinator --> AssistanceFacade
    AssistanceDispatchCoordinator --> DispatchPort
    DispatchRestClient ..|> DispatchPort
    DispatchRestClient ..> DispatchUnavailableException : lanza
    DispatchRestClient ..> DispatchFailedException : lanza
    DispatchPort ..> DispatchCommand
    DispatchPort ..> DispatchOutcome
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

Los cuatro repositorios son interfaces del paquete `domain` que extienden `JpaRepository`. Dentro del servicio, `Vehicle`, `AssistanceRequest` y `StatusChange` referencian a sus entidades por asociación JPA (clave foránea). Una transición no permitida lanza `InvalidStatusTransitionException`, que la API traduce a 409.

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
        -Instant createdAt
        +matched(UUID assistanceId, UUID mechanicId, double distanceKm)$ DispatchMatch
        +noCandidate(UUID assistanceId)$ DispatchMatch
    }

    class DispatchStatus {
        <<enumeration>>
        MATCHED
        NO_CANDIDATE
    }

    class MatchingStrategy {
        <<interface>>
        +select(double lat, double lon, List~MechanicCandidate~ candidates) Optional~MechanicSelection~
    }

    class NearestQualifiedMechanicStrategy {
        +select(...) Optional~MechanicSelection~
    }

    class BestRatedNearbyMechanicStrategy {
        -double maxDistanceKm
        +select(...) Optional~MechanicSelection~
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
    }

    class DispatchFacade {
        <<Facade>>
        +process(DispatchCommand command) DispatchMatch
        +find(UUID assistanceId) DispatchMatch
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

    DispatchMatch --> DispatchStatus
    NearestQualifiedMechanicStrategy ..|> MatchingStrategy
    BestRatedNearbyMechanicStrategy ..|> MatchingStrategy
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

`DispatchFacade.process` es idempotente: si ya existe un `DispatchMatch` para la asistencia, lo devuelve sin volver a ejecutar la estrategia.

La estrategia activa se elige con la propiedad `dispatch.matching.strategy`: `nearest` (valor por defecto) activa `NearestQualifiedMechanicStrategy`, que elige al mecánico disponible más cercano y descuenta 100 m por cada punto de calificación; `best-rated` activa `BestRatedNearbyMechanicStrategy`, que elige al mejor calificado dentro de `dispatch.matching.max-distance-km` (5 km por defecto). Spring crea sólo una de las dos, así que `DispatchFacade` no sabe cuál está usando.

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
        -Set~String~ skills
        -Set~VehicleType~ vehicleTypes
        -long version
        +changeAvailability(boolean available, double latitude, double longitude)
        +hasSkill(String requiredSkill) boolean
        +serves(VehicleType vehicleType) boolean
    }

    class VehicleType {
        <<enumeration>>
        BICYCLE
        E_BIKE
        MOTORCYCLE
    }

    class MechanicFactory {
        <<Factory>>
        +create(String name, String email, double latitude, double longitude, Set~String~ skills, Set~VehicleType~ vehicleTypes) Mechanic
    }

    class MechanicRepository {
        <<interface>>
        +findAvailableBySkill(String skill) List~Mechanic~
        +findAvailableBySkillAndVehicleType(String skill, VehicleType vehicleType) List~Mechanic~
    }

    class MechanicFacade {
        <<Facade>>
        +register(...) Mechanic
        +find(UUID id) Mechanic
        +changeAvailability(UUID id, boolean available, double latitude, double longitude) Mechanic
        +findCandidates(String skill, VehicleType vehicleType) List~Mechanic~
    }

    Mechanic --> VehicleType
    MechanicFactory ..> Mechanic : crea
    MechanicRepository ..> Mechanic : persiste
    MechanicFacade --> MechanicFactory
    MechanicFacade --> MechanicRepository
```

Las habilidades (`mechanic_skills`) y los tipos de vehículo que atiende (`mechanic_vehicle_types`) son colecciones de valores de `Mechanic` (`@ElementCollection`), no entidades propias. Si no se indican tipos al registrar un mecánico, atiende `BICYCLE`.

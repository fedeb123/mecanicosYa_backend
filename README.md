# MecanicosYa - Backend

Trabajo Práctico Obligatorio de Desarrollo de Aplicaciones II (UADE). Backend de MecanicosYa: conecta repartidores cuyo vehículo se averió con mecánicos que pueden ir hasta su ubicación.

## Stack

- Java 25 y Spring Boot 4.1.1
- Maven multi-módulo (`./mvnw`)
- PostgreSQL + PostGIS, una base por servicio
- Docker Compose

## Estructura

```
mecanicosYa_backend/
├── pom.xml                  # Proyecto padre: versiones y módulos
├── shared-observability/    # Correlation id compartido por todos los servicios
├── infra/
│   ├── docker/Dockerfile    # Imagen genérica, recibe el módulo como argumento
│   └── postgres/            # Script que crea las bases de cada servicio
└── docker-compose.yml
```

Cada microservicio se suma como módulo con sus capas `api`, `application`, `domain` e `infrastructure`.

## Requisitos

- JDK 25
- Docker Desktop con Docker Compose

```bash
./mvnw clean verify
docker compose up --build
```

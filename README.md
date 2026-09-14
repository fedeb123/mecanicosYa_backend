# MecanicosYa - Backend
Trabajo Practico Obligatorio para la materia de Desarollo de Aplicaciones II en UADE. Este repositorio servirá como Backend para la aplicación de MecanicosYa

## Estructura Modelo de Archivos de la Aplicación

```
mecanicosYa_backend/
├── src/main/java/com/uade/mecanicosYa
│   ├── domain/                  # Lógica de negocio (Libre de frameworks)
│   │   ├── models/              # Entidades puras (Averia, Mecanico, Repartidor)
│   │   ├── exceptions/          # Excepciones propias del dominio
│   │   └── repositories/        # Interfaces de acceso a datos (Inversión de dependencias)
│   │
│   ├── application/             # Casos de uso y orquestación
│   │   ├── services/            # Implementación de lógica (AveriaService, IAClasificacionService)
│   │   └── dtos/                # Objetos de transferencia de datos para aislar el dominio
│   │
│   ├── infrastructure/          # Detalles técnicos, frameworks y bases de datos
│   │   ├── controllers/         # Endpoints REST (Swagger/OpenAPI)
│   │   ├── soap/                # Endpoints y configuración SOAP
│   │   ├── messaging/           # Productores y consumidores (RabbitMQ/Kafka)
│   │   ├── persistence/         # Implementación de los repositories (Spring Data JPA, Entidades DB)
│   │   └── clients/             # Consumo de APIs externas (Geolocalización, API de IA)
│   │
│   └── config/                  # Configuraciones de Spring, Beans, Seguridad, Swagger
│
├── src/main/resources/
│   ├── application.yml          # Configuración externalizada (DB, Colas, URLs de APIs)
│   └── wsdl/                    # Contratos SOAP
│
├── Dockerfile                   # Empaquetado de la aplicación
├── docker-compose.yml           # Levantar DB, RabbitMQ y la app
└── pom.xml / build.gradle       # Gestión de dependencias (Maven o Gradle)
```

## Requisitos
- Java
- SpringBoot
- Maven
FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn package -DskipTests

FROM eclipse-temurin:25-jre
ARG MODULE
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build "/workspace/${MODULE}/target/${MODULE}-0.1.0-SNAPSHOT.jar" app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

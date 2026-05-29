# Sitrep

[![CI](https://github.com/daanschutte/sitrep/actions/workflows/ci.yml/badge.svg)](https://github.com/daanschutte/sitrep/actions/workflows/ci.yml)

Modern aviation training management suite with a focus on simplicity and correctness.

## Prerequisites

- Java 25
- Docker

## Running locally

Copy the example env file and fill in your passwords (only needed once):

```bash
cp .env.example .env
```

Start Postgres:

```bash
docker compose up postgres -d
```

Run the app:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Swagger UI: http://localhost:8080/swagger-ui.html

Health: http://localhost:8080/actuator/health

## IntelliJ run configuration

To run the app from IntelliJ with the `dev` profile:

1. Open **Run > Edit Configurations**
2. Select the `SitrepApplication` configuration
3. Set **Active profiles** to `dev`

Alternatively, add `-Dspring.profiles.active=dev` to the **VM options** field.

## Running as a container

Build the JAR, then the image:

```bash
./mvnw package -DskipTests
docker build -t sitrep:local .
```

Start the full stack:

```bash
docker compose up
```

## Running tests

```bash
./mvnw verify
```

Tests use Testcontainers — Docker must be running.

## Adding a module

1. Create `src/main/java/com/camelbytes/sitrep/<module>/package-info.java` annotated with `@ApplicationModule`
2. Add `api/` for the public interface other modules can depend on
3. Add `internal/` for everything else — entities, repositories, service implementations, controllers

Spring Modulith enforces that other modules cannot access `internal/` packages. The `ModulithVerificationTest` will fail if a boundary is violated.

## Docs

- [Technical spec](docs/SitRep_Spec.md)
- [Development plan](docs/PLAN.md)

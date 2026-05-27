# Sitrep

Flight operations management for air force squadrons.

## Prerequisites

- Java 21
- Docker

## Running locally

Start Postgres:

```bash
docker compose up -d
```

Run the app:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Swagger UI: http://localhost:8080/swagger-ui.html

Health: http://localhost:8080/actuator/health

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

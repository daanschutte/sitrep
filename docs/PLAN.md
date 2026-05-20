# Sitrep — Development Plan

*Last updated: May 2026. Living document — update as decisions land.*

---

## Context

Sitrep is being built as both a portfolio piece and a sellable product. The design doc (`Sitrep_Technical-Design-v1.0.md`) is the authoritative architecture; this PLAN is the build sequence — phases, deliverables, decisions still open.

The product owner (Daan) writes all the implementation code; Claude's role is design, review, and explanation (per `CLAUDE.md`).

---

## Where We Are (May 2026)

Greenfield. Design doc and this plan are the only artefacts. Nothing built yet.

V1 ships in eight phases (0–7 below): foundation → tenancy → identity → inventory → scheduling engine → flightops → currencies → reporting. V2 is documented in the design doc §3.6.

---

## Decisions already locked in

See the design doc for full rationale. Summary:

- **Stack:** Java 21, Spring Boot 4, Maven 3.9, PostgreSQL 17, Spring Modulith, Spring Security + JWT (`oauth2-resource-server` + `NimbusJwtEncoder`), Spring Data JPA + Hibernate 6, Flyway 10, OpenPDF, JUnit 5 + AssertJ + Mockito + Testcontainers + ArchUnit
- **Architecture:** modular monolith via Spring Modulith. Seven application modules + `shared`.
- **Errors:** typed exception hierarchy + one `@RestControllerAdvice` → RFC 9457 `ProblemDetail`. No `Result<T>`.
- **Validation:** Bean Validation for shape; explicit `if/throw` for domain rules.
- **Multi-tenancy:** Hibernate `@Filter` + PostgreSQL Row Level Security. Tier 1/2/3 squadron access model.
- **Auth:** local JWT issuance, 30 min access + 8 hr rotating refresh. `@PreAuthorize` on services.
- **Domain:** abstract `ScheduledEvent` in `scheduling`; concrete `FlightEvent`/`SimEvent`/`GroundEvent` in `flightops` via TPH inheritance. State machine on the entity.
- **Cross-module:** sync via the other module's `@Service`; async via `ApplicationEventPublisher` + `@ApplicationModuleListener` (Spring Modulith's JPA outbox).
- **No:** Lombok, MapStruct (day 1), H2, repository abstractions, microservices, command bus, field injection.
- **Build:** JVM by default; GraalVM Native Image as `native` Maven profile for cloud demo.
- **Deployment:** Docker + Kubernetes; CloudNativePG for HA Postgres; pgBackRest on-prem, WAL-G in cloud; CP from CAP.

---

## Module dependency graph

```
shared (library — used by all)
├── tenancy            (no application module deps)
├── identity           (→ tenancy)
├── inventory          (→ tenancy)
├── scheduling         (→ identity, inventory, tenancy)
├── flightops          (→ scheduling, identity, inventory, currencies)
├── currencies         (→ identity, inventory, flightops*)
└── reporting          (→ flightops, identity, inventory  — read-only)
```

\* `flightops` and `currencies` have reciprocal sync API dependencies — see design doc §6.3.

Phase order follows this graph bottom-up.

---

## The Phases

Each phase ends with: passing CI (build + tests + module verification + architecture rules), updated docs if anything changed, demoable behaviour where applicable. Mark complete only when all three hold.

---

### Phase 0 — Foundation

*Project bootstrap. Everything else builds on this.*

**Goal:** an empty but production-shaped Spring Boot 4 application that boots, runs in Docker, has CI, persists nothing, and has all cross-cutting plumbing wired so subsequent module work is *just* domain work.

**Deliverables:**

- `pom.xml` — Spring Boot 4 parent, Java 21, all dependencies from design doc §5, plugins (Spring Boot Maven Plugin, Spotless with palantir-java-format, JaCoCo, Flyway Maven Plugin)
- Package skeleton — `com.camelbytes.sitrep` root, empty module folders with `package-info.java` declaring `@ApplicationModule` per future module
- `shared/domain/` — `BaseEntity` (UUID, `@CreatedDate`, `@LastModifiedDate`, `@Version`), `SquadronScoped` interface
- `shared/error/` — `DomainException` base + `NotFoundException`, `ConflictException`, `BusinessRuleException`, `ForbiddenException`, `IllegalStateTransitionException`
- `shared/web/` — `GlobalExceptionHandler` (`@RestControllerAdvice`) mapping the exception hierarchy + `MethodArgumentNotValidException` + `AccessDeniedException` + catch-all to `ProblemDetail`
- `shared/security/` — `CurrentUser` interface (no implementation yet — Phase 2 supplies it); placeholder Spring Security filter chain (permits all `/api/v1/**` for now, JWT filter inactive)
- `shared/config/` — `@EnableJpaAuditing`, virtual threads (`spring.threads.virtual.enabled=true`), Caffeine cache manager, Jackson config (Instant serialisation, etc.)
- `application.yml` + `application-{dev,test,prod}.yml`
- Docker Compose for local dev — Postgres 17, Adminer/pgAdmin for inspection
- Flyway baseline migration `V001__baseline.sql` (creates `sitrep_owner` and `sitrep_app` roles; no tables yet)
- `logback-spring.xml` with dev/prod profiles (human-readable vs JSON)
- SpringDoc OpenAPI configured; Swagger UI on non-prod profiles only at `/swagger-ui.html`
- Actuator endpoints — `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info`, `/actuator/prometheus`
- `Dockerfile` — multi-stage, distroless final image, runs as non-root
- `pom.xml` `native` profile placeholder (no native build in CI yet, but profile exists)
- GitHub Actions workflow — build → Spotless check → unit tests → integration tests → architecture tests → publish image
- `README.md` — quickstart (clone, `docker compose up`, `mvn spring-boot:run`)

**Tests:**

- One trivial unit test (verifies JUnit + AssertJ wiring)
- One `@SpringBootTest` + Testcontainers Postgres test verifying the app boots and Flyway runs cleanly
- `ApplicationModulesTest` calling `ApplicationModules.of(SitrepApplication.class).verify()` — placeholder, starts enforcing as modules arrive
- ArchUnit base test class with rules: no field injection, controllers only in `internal/web/`, services annotated with `@Service`, no `System.out`

**What this teaches / interview-worthy:**

- Modern Spring Boot 4 project layout
- Spring Modulith bootstrap and verification
- Testcontainers wiring
- RFC 9457 `ProblemDetail` machinery
- Custom exception hierarchy + `@RestControllerAdvice`
- CI pipeline shape for a Java project
- Distroless Docker images and why
- Virtual threads opt-in

---

### Phase 1 — Tenancy module

*Smallest module, no dependencies. Establishes the canonical module shape.*

**Goal:** a complete vertical slice (entity → repository → service → controller → tests → migration) that every later module copies.

**Domain:**
- `Squadron` (id, name, shortName, active, timestamps)
- Domain methods: `activate()`, `deactivate()`, `rename(name, shortName)` — invariants enforced on the entity, not in the service

**Endpoints:**

```
POST   /api/v1/squadrons
GET    /api/v1/squadrons
GET    /api/v1/squadrons/{id}
PUT    /api/v1/squadrons/{id}
PATCH  /api/v1/squadrons/{id}/activate
PATCH  /api/v1/squadrons/{id}/deactivate
```

**Deliverables:**

- `tenancy/Squadron` entity in `tenancy/domain/`
- `tenancy/SquadronService` (public) — orchestration, calls into entity
- `tenancy/internal/SquadronController`, `SquadronRepository extends JpaRepository<Squadron, UUID>`
- `tenancy/api/SquadronRequest`, `SquadronResponse` — records
- `package-info.java` with `@ApplicationModule(allowedDependencies = {"shared"})`
- Bean Validation on request DTOs
- Domain-level validation for duplicate name/short-name (`ConflictException`)
- Flyway migration `V002__create_squadron.sql`
- Unit tests for entity methods and service rules
- Integration tests covering every endpoint × every status code path
- `@ApplicationModuleTest` verifying the module bootstraps in isolation
- Endpoint security: temporarily `permitAll` (auth arrives in Phase 2 and will gate writes to `ADMIN`)

**Cross-module callout:** None. `Squadron` is the foundation; nothing depends *on* this yet, and this depends only on `shared`.

**What this teaches:** the canonical Spring Modulith module structure. Every subsequent module copies this layout.

---

### Phase 2 — Identity module

*Auth + Users. The biggest foundational phase — combines authentication, user management, and method-level authorisation.*

**Goal:** authenticated requests, JWT issuance and rotation, user CRUD, cross-squadron access grants, and `@PreAuthorize` enforcement applied retroactively to Tenancy endpoints from Phase 1.

**Domain:**
- `User` (id, username, passwordHash, displayName, email, callsign, active, mustChangePassword, homeSquadronId, role, wildcardAccess, timestamps, `@Version`)
- `SquadronAccess` (userId, squadronId — host squadron grants for Tier 2 users)
- `UserAvailability` (userId, category, startsAt, endsAt, notes)
- `UserQualification` (userId, platformId, defaultRole) — placeholder; `platformId` becomes a real FK in Phase 3
- `RefreshToken` (userId, tokenHash, expiresAt, revokedAt)

**Endpoints:**

```
POST /api/v1/auth/login                         { username, password } → tokens + mustChangePassword
POST /api/v1/auth/refresh                       { refreshToken }       → new tokens
POST /api/v1/auth/logout                                               → 204
GET  /api/v1/auth/me                                                   → current user
POST /api/v1/auth/change-password               { current, new }       → 204

POST   /api/v1/users
GET    /api/v1/users                            ?activeOnly&role&squadronId
GET    /api/v1/users/{id}
PUT    /api/v1/users/{id}
PATCH  /api/v1/users/{id}/activate
PATCH  /api/v1/users/{id}/deactivate
POST   /api/v1/users/{id}/squadron-access       { squadronId }
DELETE /api/v1/users/{id}/squadron-access/{squadronId}
POST   /api/v1/users/{id}/availability
DELETE /api/v1/users/{id}/availability/{availabilityId}
```

**Deliverables:**

- `identity/User` entity, `BCryptPasswordEncoder` for hashing
- `identity/AuthService` — login, refresh, logout, change password, current user. Issues JWT via `NimbusJwtEncoder`; validates via Spring Security's resource server.
- `identity/UserService` — CRUD, activate/deactivate, squadron access grants, qualifications stub, availability
- `identity/RefreshTokenStore` — refresh tokens hashed (SHA-256, not bcrypt — these are random opaque values); rotation on every use; revoke on logout / password change / deactivation
- `shared/security/CurrentUserImpl` — request-scoped `@Component` reading from `SecurityContextHolder`, parsing claims once per request
- Spring Security filter chain activated: JWT validation via `oauth2-resource-server`, public endpoints (`/auth/login`, `/auth/refresh`, `/actuator/health/*`), all other `/api/v1/**` requires authentication
- `@EnableMethodSecurity(prePostEnabled = true)` in security config
- `@PreAuthorize("hasRole('ADMIN')")` applied to: user/squadron mutations. `hasAnyRole('ADMIN', 'MANAGER', 'PLANNER')` for user/squadron list endpoints. Reads of self via `@auth.isSelf(#id)` SpEL bean.
- `shared/security/SquadronAuthz` SpEL bean exposing `canAccess(UUID squadronId)` reading `CurrentUser`
- MDC filter (`OncePerRequestFilter`) populating `userId`, `requestId` for every request
- JWT claims: `sub`, `username`, `role`, `home_squadron_id`, `squadron_access` (array), `wildcard_access` (boolean), `must_change_password`
- `RestAuthenticationEntryPoint` returning 401 `ProblemDetail`
- `AccessDeniedHandler` returning 403 `ProblemDetail`
- Configurable lifetimes in `application.yml`:
  ```yaml
  sitrep:
    auth:
      access-token-lifetime: PT30M
      refresh-token-lifetime: PT8H
      issuer: "sitrep"
  ```
- Flyway migrations: `users`, `squadron_access`, `user_availability`, `user_qualification` (PlaceHolder FK column), `refresh_token`
- Cross-module: `identity.UserService.grantSquadronAccess` calls `tenancy.SquadronService.exists(squadronId)` for FK existence check
- Integration tests: ✓ login → use access token → 401 on expiry → refresh → use new access token; ✓ logout revokes refresh; ✓ wrong role → 403; ✓ no token → 401; ✓ all CRUD paths; ✓ `@WithMockUser` unit tests on services

**What this teaches:**

- Spring Security 6 filter chain composition
- JWT design via Spring Security native primitives (no `jjwt`)
- Refresh token rotation patterns
- Method-level authorisation with custom SpEL beans
- Request-scoped beans
- MDC for structured logging
- Cross-module service interaction without entity leakage

---

### Phase 3 — Inventory module + multi-tenancy infrastructure

*Platforms and their resources. First squadron-scoped entities — multi-tenancy comes online.*

**Goal:** platform type management, per-squadron aircraft/simulator/room resources, and the Hibernate filter + RLS plumbing wired up for real.

**Domain:**
- `Platform` (id, name, category, authorisationProfileId, active) — *not* squadron-scoped; platforms are global types
- `Aircraft` (squadronId, platformId, tailNumber, isShared, isActive) — squadron-scoped
- `Simulator` (squadronId, platformId, isShared, isActive) — squadron-scoped
- `Room` (squadronId, platformId, isShared, isActive) — squadron-scoped
- `AuthorisationProfile` (id, name, steps[]) — global; concrete authorisation step logic activated in Phase 5
- `UserQualification.platformId` becomes a real FK to `Platform` (was placeholder in Phase 2)

**Endpoints:**

```
POST   /api/v1/platforms
GET    /api/v1/platforms
GET    /api/v1/platforms/{id}
PUT    /api/v1/platforms/{id}
PATCH  /api/v1/platforms/{id}/activate
PATCH  /api/v1/platforms/{id}/deactivate

POST   /api/v1/platforms/{platformId}/aircraft
GET    /api/v1/platforms/{platformId}/aircraft
PUT    /api/v1/platforms/{platformId}/aircraft/{aircraftId}
PATCH  /api/v1/platforms/{platformId}/aircraft/{aircraftId}/activate
PATCH  /api/v1/platforms/{platformId}/aircraft/{aircraftId}/deactivate

(same pattern for /simulators, /rooms)

POST   /api/v1/users/{id}/qualifications        { platformId, defaultRole }
DELETE /api/v1/users/{id}/qualifications/{qualificationId}
```

**Multi-tenancy infrastructure (this is the phase that adds it):**

- `@FilterDef(name = "squadronFilter", parameters = @ParamDef(name = "squadronIds", type = UUID[].class))` on `BaseEntity` subclasses implementing `SquadronScoped`
- `@Filter(name = "squadronFilter", condition = "squadron_id = ANY(:squadronIds)")` annotation
- `SquadronFilterEnabler` — `@Component` hooking into Hibernate `EntityManager` session creation; reads `CurrentUser.allAccessibleSquadronIds()` and enables the filter
- `SquadronContextStatementInspector` — Hibernate `StatementInspector` issuing `SET LOCAL app.accessible_squadron_ids = '{...}'` on each transaction start
- RLS migration applied to `aircraft`, `simulator`, `room`:
  ```sql
  ALTER TABLE aircraft ENABLE ROW LEVEL SECURITY;
  ALTER TABLE aircraft FORCE ROW LEVEL SECURITY;
  CREATE POLICY squadron_isolation ON aircraft
      USING (squadron_id = ANY (current_setting('app.accessible_squadron_ids', true)::uuid[]));
  ```
- Postgres role split confirmed: Flyway runs as `sitrep_owner` (bypasses RLS), application connects as `sitrep_app` (subject to RLS)
- `@DataJpaTest` + Testcontainers verifying: filter shows only accessible squadrons, RLS blocks even raw JDBC reads from other squadrons, Tier 3 wildcard user sees everything

**Cross-module:**

- `identity.UserService.grantQualification` calls `inventory.PlatformService.exists(platformId)` for FK check
- `inventory` reads no other module's services

**What this teaches:**

- Parent-child JPA aggregates
- Hibernate filter wiring
- PostgreSQL RLS design and the database role split
- Defense-in-depth at two layers
- Real squadron-scoped entities exercising the multi-tenancy machinery

---

### Phase 4 — Scheduling engine

*Abstract `ScheduledEvent`, state machine, conflict detection, daily program query. No concrete events yet — Phase 5 provides them.*

**Goal:** a usable scheduling engine that knows nothing about flight ops specifically.

**Domain:**

- `scheduling/ScheduledEvent` — abstract `@Entity` with `@Inheritance(SINGLE_TABLE)` + `@DiscriminatorColumn("event_type")`
- Status fields: `EventStatus` enum, `delayed`/`delayReason`/`originalScheduledStart`, `cancellationCategory`/`cancellationNotes`
- Time fields: `scheduledStart`, `scheduledEnd`
- `missionCallsign`, `formationId`
- `CrewAssignment` (entity, owned by event via `@OneToMany`)
- `Formation` entity
- State machine methods on the entity: `stage()`, `publish()`, `authorise()`, `accept()`, `markAirborne()`, `markLanded()`, `signBack(crewHours)`, `delay(reason, newStart)`, `cancel(category, notes)`. Each validates the transition and throws `IllegalStateTransitionException` on bad transitions.
- Application events defined in `scheduling/events/`:
  - `EventStagedEvent`, `EventPublishedEvent`, `EventAuthorisedEvent`, `EventAcceptedEvent`, `EventAirborneEvent`, `EventLandedEvent`, `EventSignedBackEvent`, `EventCancelledEvent`, `EventDelayedEvent`
- All published via `AbstractAggregateRoot` or via the service catching the state change

**Domain services (in `scheduling/internal/`):**

- `ConflictDetector` — checks crew double-booking across accessible squadrons, crew availability overlap (`UserAvailability`), platform qualification (`UserQualification`), resource double-booking (aircraft/simulator/room), configurable pre/post buffer windows. Returns warnings; never blocks.
- `DailyProgramQuery` — DTO-projection query returning the day's events with crew, optimised (no N+1)

**Public service (`SchedulingService`):**

- `getDailyProgram(squadronId, date)` → `DailyProgramResponse`
- `getEvent(eventId)` → `EventResponse`
- Lifecycle commands accept any concrete `ScheduledEvent`-subclass id and dispatch to the entity's transition method

**Endpoints in this phase:**

```
GET    /api/v1/scheduling/daily-program?squadronId&date
GET    /api/v1/scheduling/events/{id}
```

Mutating endpoints arrive in Phase 5 once we have concrete event types.

**Deliverables:**

- Flyway migration creating `scheduled_event` table with discriminator column and all type-specific nullable columns + check constraints per discriminator value
- RLS on `scheduled_event` (squadron-scoped)
- Unit tests for every state machine transition (every happy path + every rejected transition)
- Unit tests for `ConflictDetector` against realistic data
- Integration test for the daily program query showing no N+1

**What this teaches:**

- Domain aggregate root design with rich entities
- State machine implemented in code, not framework-driven
- JPA single-table inheritance
- Conflict-detection algorithm design
- Application-event publication via Spring Modulith
- Read-path optimisation with DTO projections

---

### Phase 5 — FlightOps module

*Concrete flight event types + the full authorisation/execution workflow. The biggest product phase.*

**Goal:** all flight-ops domain operations work end-to-end. Planners create events, authorisation officers sign them out, ops staff mark airborne/landed, instructors sign them back.

**Domain:**

- `flightops/FlightEvent extends ScheduledEvent` with `@DiscriminatorValue("FLIGHT")` — adds `aircraftId`, `squawk`, `approachType`
- `flightops/SimEvent extends ScheduledEvent` — adds `simulatorId`
- `flightops/GroundEvent extends ScheduledEvent` — adds `roomId`
- All inherit the state machine; some authorisation profiles may skip steps (sim, ground)

**Endpoints:**

```
POST   /api/v1/flightops/flight-events
POST   /api/v1/flightops/sim-events
POST   /api/v1/flightops/ground-events
POST   /api/v1/flightops/flight-events/bulk      // bulk ad-hoc N-event creation
POST   /api/v1/flightops/formations              // N-ship formation in one action

PUT    /api/v1/flightops/events/{id}             // update times, crew, mission callsign
PATCH  /api/v1/flightops/events/{id}/crew        // crew reassignment
PATCH  /api/v1/flightops/events/{id}/stage
PATCH  /api/v1/flightops/events/{id}/publish     // multi-shift selective publish
PATCH  /api/v1/flightops/events/{id}/delay       { reason, newStart }
PATCH  /api/v1/flightops/events/{id}/cancel      { category, notes }

POST   /api/v1/operations/authorise              { eventIds[], password }   // bulk sign flight out
POST   /api/v1/operations/accept                 { eventIds[] }
POST   /api/v1/operations/airborne               { eventId, time? }
POST   /api/v1/operations/landed                 { eventId, time? }
POST   /api/v1/operations/sign-back              { eventId, crewHours[] }   // sign flight back
PATCH  /api/v1/operations/squawk                 { eventId, squawk }

POST   /api/v1/operations/authorisation-profiles
PUT    /api/v1/operations/authorisation-profiles/{id}
GET    /api/v1/operations/authorisation-profiles
```

**Deliverables:**

- `flightops/FlightOpsService` — event creation, updates, crew, formations
- `flightops/OperationsService` — authorisation, execution, sign-back, squawks
- `flightops/AuthorisationProfileService` — profile CRUD (Admin only)
- Bulk creation in a single transaction
- Password re-verification on bulk authorise: hash and compare against user's stored hash; fail fast on mismatch, no events transition
- Currency warning aggregation: calls `currencies.CurrencyService.computeWarnings(...)`. In this phase the service returns empty (stub); Phase 6 provides the real implementation.
- Sign-back hours split: defaults to equal split based on event duration / N crew; client can override per crew member
- Squawk endpoint restricted to `OPS` role
- Airborne/landed restricted to `OPS` role
- Sign-back restricted to event captain or `INSTRUCTOR`/`MANAGER`/`ADMIN`
- Authorisation profile management restricted to `ADMIN`
- Spring Modulith `@ApplicationModuleListener` set up in this module to listen to its own published events for audit logging (audit ledger writes happen here)
- Integration tests covering: ✓ each event-type creation; ✓ bulk authorise happy path; ✓ wrong password → all reject; ✓ currency warnings surfaced (stub); ✓ ops-only role; ✓ sign-back hours split; ✓ cross-squadron formation creation; ✓ delay flag preserved through transitions; ✓ cancellation from each active state

**Cross-module:**

- Sync read from `currencies.CurrencyService.computeWarnings(...)`
- Reads from `inventory.AircraftService`, `inventory.SimulatorService`, `inventory.RoomService` for resource validation
- Reads from `identity.UserService` for crew validation
- Publishes domain events that `currencies` and `reporting` listen to

**What this teaches:**

- TPH inheritance with module-distributed subtypes
- Bulk command orchestration in a single transaction
- Password re-verification patterns
- Workflow state-machine driven from a configurable profile
- Cross-module event-driven integration with stub-first then real wiring

---

### Phase 6 — Currencies module

*Currency definitions, grants, computed warnings, ad-hoc flight creation.*

**Goal:** real currency state computation drives the authorisation warnings, and instructors can spawn currency flights directly from the currency view.

**Domain:**

- `currencies/CurrencyDefinition` — `code`, `displayName`, `platformId`, `ruleType` (`ROLLING_WINDOW_COUNT`, `DATE_BASED`), `windowDays`, `requiredCount`, `enforcement` (`WARN`, `BLOCK`)
- `currencies/CurrencyGrant` — `userId`, `currencyDefinitionId`, `grantedAt`, `expiresAt`, `grantedByUserId`, `notes`
- `currencies/CurrencyState` — record (computed view, not persisted): `userId`, `currencyDefinitionId`, `lastQualifyingEvent`, `current`, `expiresAt`
- Caffeine cache for per-user currency state, invalidated on `EventSignedBackEvent` and `CurrencyGrant` writes

**Endpoints:**

```
POST   /api/v1/currencies/definitions
PUT    /api/v1/currencies/definitions/{id}
GET    /api/v1/currencies/definitions

POST   /api/v1/currencies/grants
DELETE /api/v1/currencies/grants/{id}

GET    /api/v1/currencies/users/{userId}
GET    /api/v1/currencies/warnings?eventId
POST   /api/v1/currencies/users/{userId}/ad-hoc-flight
```

**Deliverables:**

- `currencies/CurrencyService` — definition CRUD, grant CRUD, state computation, `computeWarnings(eventId)` returning the list of `CurrencyWarning` records for that event's crew
- `currencies/internal/CurrencyEventListener` — `@ApplicationModuleListener` consuming `EventSignedBackEvent`, invalidating per-user cache for affected crew
- Real implementation of `computeWarnings` replaces the Phase 5 stub
- Ad-hoc currency flight: `currencies.CurrencyService.scheduleAdHocFlight(userId, definitionCode, targetDate)` calls `flightops.FlightOpsService.createFlightEvent(...)` with pre-populated platform and crew slot
- Cache invalidation on grant write via `@CacheEvict`
- Integration tests: ✓ sign-back triggers cache invalidation and recomputation; ✓ warnings shown during authorisation when currency expired; ✓ `BLOCK` enforcement actually blocks (not just warns); ✓ ad-hoc flight creation produces a valid Phase 5 `FlightEvent`

**Cross-module:**

- Listens to `flightops` events via `@ApplicationModuleListener`
- Calls `flightops.FlightOpsService.createFlightEvent(...)` synchronously for ad-hoc flights
- Reads `identity.UserService`, `inventory.PlatformService` for FK validation

**What this teaches:**

- Application-event-driven cross-module integration with Modulith's JPA outbox
- "Computed view" pattern for derived state
- Caffeine cache invalidation strategies (TTL + event-driven)
- Reciprocal cross-module API dependencies done carefully

---

### Phase 7 — Reporting module

*PDF generation for authorisation sheets.*

**Goal:** print-ready PDFs for the operational documents the customer carries to the flightline.

**Deliverables:**

- OpenPDF dependency
- `reporting/AuthorisationSheetService.generate(eventIds[])` → `byte[]`
- Template rendering — likely Thymeleaf-to-HTML-to-PDF via Flying Saucer, or direct OpenPDF document building (decide based on template complexity)
- Embedded fonts (air-gapped — no font CDN)
- `GET /api/v1/reports/authorisation-sheet?eventIds=...` returning `application/pdf`
- Snapshot tests verifying generated PDFs have expected structure (page count, key text strings, table rows)
- Role-gated: authorisation officer or admin only

**Cross-module:**

- Reads from `flightops`, `identity`, `inventory` via their services. Pure consumer; emits nothing.

**What this teaches:**

- PDF generation in air-gapped Java applications
- Snapshot testing for binary outputs
- Read-only module pattern

---

## Continuous (not phase-bound)

These run alongside every phase, not as separate phases:

- **Operational hardening** — CloudNativePG manifests, pgBackRest config, Helm chart, k8s deployment scripts, sealed-secrets setup. Build as deployment targets emerge.
- **Architecture tests** — every phase adds at least one ArchUnit rule covering its own module boundary
- **Security review** — every phase that touches auth or data access gets a paranoid second pass
- **Observability** — every phase adds at least one meaningful metric and structured log event
- **Native build** — every phase verifies `mvn -Pnative test` still passes (or fixes whatever broke). Don't let native rot.

---

## V2 (not in this PLAN's scope)

Documented in design doc §3.6. In rough priority:

1. Courses & Syllabus Board
2. Grade Sheets
3. Logbooks (auto-generated from `EventSignedBackEvent`)
4. Adjacent operational domains — `navalops`, `groundops` (validates the abstract `ScheduledEvent` design)
5. In-app notifications
6. Reporting dashboards
7. Degraded mode (PWA + IndexedDB + sync queue)
8. Weather / NOTAM integration (cloud deployments only)
9. Formal leave management

---

## Implementation order summary

```
Phase 0  Foundation
  → Phase 1  Tenancy (Squadrons)
    → Phase 2  Identity (Auth + Users)
      → Phase 3  Inventory (Platforms) + multi-tenancy infrastructure
        → Phase 4  Scheduling engine
          → Phase 5  FlightOps (concrete events + workflow)
            → Phase 6  Currencies
              → Phase 7  Reporting
```

Phases 0–3 are foundational. Phase 4 onward is product feature work. Each phase is shippable independently; CI keeps the main branch deployable throughout.

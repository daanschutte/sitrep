# Sitrep — Technical Design Document

*Version 1.0 — May 2026*

## 1. Product Overview

Sitrep is a military operations management system. V1 ships a flight training management product covering scheduling, flight authorisation, and currency tracking. The architecture is designed from day one to extend into adjacent operational domains — naval operations, ground operations, joint exercises — by plugging new domain modules into a shared operational scheduling engine.

The flight ops domain targets 150–250 students, 50–150 instructors, and operations room staff across multiple squadrons. Personnel data is retained long-term as careers progress (decades). The system targets 500 concurrent users to allow for growth.

V1 supports two operational modes per squadron simultaneously: syllabus-based training (Syllabus Board → Daily Program, deferred to V2) and operations-only mode (Daily Programs with ad-hoc events, no course or syllabus tracking). V1 focuses on the operations-only path so the scheduling and authorisation core can be hardened in production before syllabus management is layered on top.

## 2. Design Philosophy

**UX is a priority.** Every workflow should require as few clicks as possible, with minimal clutter. The system must feel fast and intuitive for planners, instructors, and ops room staff alike.

**Sensible defaults over configuration.** Competing products are overly complex (granular per-user menu permissions, dozens of security groups). Sitrep ships with opinionated defaults. Users should not have enough rope to break things.

**Idiomatic Spring Boot.** Use the framework's own primitives — `@Service`, `@Transactional`, Spring Data JPA, Spring Security, Bean Validation, `ApplicationEventPublisher`. No bespoke command buses, no reinvented dependency injection. The framework does most of the work; the application code is the domain.

**Modular monolith.** Microservices would add distributed-transaction complexity, service discovery overhead, and operational burden with zero benefit at this scale. Modules are enforced internally via Spring Modulith.

**Air-gapped first.** Runs without internet on a local LAN. Every dependency, font, container image, and observability stack must be deployable offline.

**Consistency over availability.** When a write cannot complete safely (mid-failover, network partition between Postgres replicas), the system fails the write rather than serving stale or split-brain data. CP from CAP.

**Rich domain entities.** No anaemic data classes. Business rules are enforced on the entity (private setters, methods that mutate state and emit events). Services orchestrate.

**Testability.** Domain entity methods, services, validators, and module integration are independently testable. Real PostgreSQL via Testcontainers for integration tests — no mocked repositories, no H2.

**Fail safely.** Custom domain exceptions, one global `@RestControllerAdvice` mapping to RFC 9457 `ProblemDetail`, structured logging via SLF4J. No raw stack traces in HTTP responses.

**Designed for extension.** The scheduling engine is domain-agnostic. Flight training events live in a separate module on top of it. Naval, ground, or joint-domain modules can be added later without touching the engine.

## 3. V1 Scope

### 3.1 Scheduling

**Daily Program.** The day's schedule per squadron. Planners arrange events via drag-and-drop, modify as needed, and selectively publish. Multi-shift operations — different planners publish their own portions independently.

**Bulk operations.** Selective bulk publishing; bulk ad-hoc event creation (planner specifies a count).

**Future planning.** Plan and publish events for any future date.

**Conflict detection.** Warn about instructor/student/resource availability conflicts including pre/post flight buffer times. Warnings only — planners can override.

**Formation missions.** Create formation groups (2-ship, 4-ship, any size) as a set of linked events in one action. Each member has its own event with independent crew assignments. Formation members can be from different squadrons. The formation has its own callsign.

### 3.2 Flight Authorisation Workflow

**Event lifecycle.** `Draft → Staged → Published → Authorised → Accepted → Airborne → Landed → SignedBack`. `Cancelled` is terminal, reachable from any active state with a structured category and optional notes. `IsDelayed` is a flag (not a status), applied when rescheduling.

**Configurable authorisation flow.** Each platform type is assigned an authorisation profile. Profiles define ordered steps and required roles. PC-21 flights use the full profile; simulators may skip authorisation; briefing rooms may use `Published → Accepted` only.

**Bulk authorisation (sign flight out).** Authorisation officer selects multiple events, confirms with password, receives currency warnings for any crew with outdated currencies before confirming.

**Sign flight back.** Post-flight close-out. Hours split is configurable (defaults to equal, can be set arbitrarily per crew member for multi-student sorties).

**Operations Room.** Dedicated Ops role for assigning squawks and marking airborne/landed. Ops users see the Daily Program filtered by squadron (own, all, or selection). Cannot modify scheduling.

**Squadron Program View.** Read-only list of the day's events with crew, times, statuses, squawks, and details.

### 3.3 Currencies

**Time-based currencies.** E.g. "instrument approach within last 90 days". Computed from `SignedBack` events.

**Date-based currencies.** Manually granted (training certificate valid until date X).

**Currency warnings.** Surfaced during authorisation when crew have outdated currencies. Each definition has an enforcement mode: `WARN` (visible at auth time, override allowed) or `BLOCK` (cannot authorise the event).

**Ad-hoc currency flights.** Instructors create currency flights directly from the currency view, pre-populated with the required platform and target user.

### 3.4 Foundational

**Authentication.** Username/password via Spring Security with locally-issued JWTs. No external IdP. Optional email/magic-link login can be added as an additional sign-in scheme.

**Roles.** `Admin`, `Manager`, `Planner`, `Instructor`, `Student`, `Ops`. Assigned at the user level (home squadron context). Cross-squadron access is additive.

**Users.** Personal callsigns, qualifications per platform type, availability windows (`Leave`, `Medical`, `Duty`, `Unfit`, `Other`). Day-of unavailability immediately flags conflicting events.

**Squadrons.** Multi-tenant data isolation via Hibernate filter + PostgreSQL Row Level Security.

**Platforms.** Platform types (Aircraft, Simulator, Room) with individual resources tracked under each (tail numbers for aircraft, named sim units, named rooms).

### 3.5 Cross-cutting

**Event history.** Tamper-evident audit ledger (hash-chained, INSERT-only) suitable for accident investigation.

**Cancellation categories.** Structured (`Weather`, `Aircraft Unserviceable`, `Crew Unfit`, `Crew Unavailable`, `Operational Priority`, `Syllabus Change`, `Other`) with optional notes.

**Print / Export.** PDF generation for authorisation sheets.

### 3.6 V2 Roadmap

- **Courses & Syllabus Board** — course definitions with predetermined syllabi; per-student progress; bulk staging.
- **Grade Sheets** — digital instructor gradesheets for syllabus events.
- **Logbooks** — auto-generate logbook entries on `SignedBack`; per-tail-number tracking.
- **Adjacent operational domains** — `navalops`, `groundops` modules on top of the same scheduling engine.
- **Degraded mode** — limited operations during LAN/server outages with later sync (PWA + IndexedDB + conflict resolution).
- **Notifications** — in-app feed for schedule changes, assignments, conflict alerts.
- **Weather / NOTAM integration** — read-only data feeds when network is available (cloud deployments only).
- **Formal leave management** — leave requests, approval workflow, integrated with availability.
- **Reporting dashboards** — operational analytics, cancellation trends.

## 4. Deployment Context

This is the constraint that shapes the rest of the design.

### 4.1 Primary deployment — air-gapped on-prem

- No internet access, no external IdP, no external image registry, no telemetry exfiltration
- Customer-supplied hardware in a customer data centre
- High availability with **consistency > availability** (CP). Brief unavailability during failover is acceptable; serving stale or partitioned data is not.
- Low latency (same data centre as users; single-digit-millisecond network)
- Long-running workload (servers run 24/7; JIT has plenty of time to warm up)
- Backups stored on-site to a customer-owned object store (MinIO or equivalent)

### 4.2 Secondary deployment — cloud demo / VPN clients

- Same container image, different config
- Internet-exposed with TLS termination + WAF
- May scale to zero between demos
- Future: certain customers connect to their on-prem instance via VPN

### 4.3 Common stack

- **Container:** Docker, distroless base image (`gcr.io/distroless/java21-debian12` or equivalent vendored copy)
- **Orchestration:** Kubernetes — k3s on-prem for small footprint, full upstream k8s for larger sites and cloud
- **Database:** PostgreSQL 17 via CloudNativePG operator. Primary + 1 synchronous replica (`synchronous_commit = remote_apply`). Manual or operator-managed failover.
- **Backups:** pgBackRest on-prem to local MinIO; WAL-G in cloud to managed object storage. Restore drills monthly.
- **Observability:** Prometheus + Grafana + Loki + Tempo — all air-gappable.
- **Ingress:** NGINX Ingress Controller (or HAProxy where customer prefers).
- **Secrets:** Sealed Secrets for GitOps-friendly air-gapped deployments; HashiCorp Vault if the customer already runs it.

### 4.4 Build artefacts — dual track

Two build profiles produce two artefacts from the same source:

- **JVM build** (default) — primary artefact for on-prem production. JIT-warmed HotSpot is the right runtime for our long-running, throughput-shaped workload.
- **GraalVM Native Image** (`mvn -Pnative native:compile`) — secondary artefact for the cloud demo instance. Sub-100 ms startup, ~50% memory footprint, smaller image. Suits cold-start and scale-to-zero scenarios.

CI builds both. Native is only published on the cloud-demo release branch. See §13 for the code-discipline requirements native image imposes.

## 5. Technology Stack

| Layer | Choice | Notes |
|---|---|---|
| Language | Java 21 LTS | Records, sealed classes, pattern matching, virtual threads |
| Framework | Spring Boot 4 | |
| Web | Spring MVC (servlet) | Virtual threads enabled (`spring.threads.virtual.enabled=true`) |
| Modularity | Spring Modulith 1.3 | Boundary enforcement, JPA-backed event publication |
| Security | Spring Security 6 | `oauth2-resource-server` for JWT validation; `NimbusJwtEncoder` for issuance |
| Persistence | Spring Data JPA + Hibernate 6 | `@Filter` for multi-tenancy ergonomic layer |
| Database | PostgreSQL 17 | Row Level Security for multi-tenancy enforcement |
| Migrations | Flyway 10 | Versioned SQL migrations |
| Connection pool | HikariCP | Spring Boot default |
| Cache | Caffeine via `@Cacheable` | Local cache, no distributed dependency |
| Validation | Jakarta Bean Validation (Hibernate Validator) | + explicit domain checks in services |
| API docs | SpringDoc OpenAPI 2.7 | Swagger UI in non-prod |
| Documents | OpenPDF (V1) | Lighter than JasperReports for the V1 scope |
| Logging | SLF4J + Logback | JSON in prod via `logback-spring.xml` profile |
| Metrics | Micrometer + Spring Boot Actuator | Prometheus scrape endpoint |
| Build | Maven 3.9 | Spring Boot parent POM |
| Format | Spotless + palantir-java-format | Enforced in CI |
| Coverage | JaCoCo | Reports published in CI |
| Native | GraalVM 21 Native Image | Optional `native` Maven profile |
| Testing | JUnit 5 + AssertJ + Mockito | + Testcontainers + ArchUnit + Spring Modulith test |
| Frontend | React 18 + TypeScript + Vite | Separate workspace |

**Explicitly not in the stack:**

- Microservices / Spring Cloud — single-deployment monolith
- Lombok — Java 21 records + idiomatic Java suffice; no bytecode-modifying annotation processors
- MapStruct from day 1 — start with manual mapping; adopt MapStruct only if and when boilerplate becomes a real cost
- jjwt — Spring Security's native JWT support replaces it
- H2 / in-memory test databases — real Postgres via Testcontainers
- AutoMapper / object-mapper libraries — manual mapping in services
- Repository abstractions wrapping JPA — `JpaRepository` is the abstraction

## 6. Architecture: Modular Monolith

A single Spring Boot application, single deployable artefact, internal module boundaries enforced at test time by Spring Modulith.

### 6.1 Package layout

```
com.camelbytes.sitrep                  (root, @SpringBootApplication, @Modulithic)
├── identity/                          module: auth + users + roles + qualifications + availability
├── tenancy/                           module: squadrons + cross-squadron access grants
├── inventory/                         module: platforms (types) + aircraft / simulators / rooms
├── scheduling/                        module: ScheduledEvent base, state machine, conflict detection, daily program engine
├── flightops/                         module: FlightEvent / SimEvent / GroundEvent, formations, authorisation workflow, sign-back, ops room
├── currencies/                        module: currency definitions, grants, warning computation, ad-hoc flight creation triggers
├── reporting/                         module: PDF generation (authorisation sheets)
└── shared/                            cross-module primitives (library, not a Modulith module)
    ├── domain/                        BaseEntity, SquadronScoped, audit ledger
    ├── error/                         DomainException hierarchy, GlobalExceptionHandler
    ├── security/                      CurrentUser, JWT plumbing, SpEL beans for authorisation expressions
    ├── config/                        JPA auditing config, virtual threads, Caffeine, JSON
    └── web/                           ProblemDetail helpers, OpenAPI customisation
```

### 6.2 Module internal structure

Every module follows the same internal shape:

```
{module}/
├── package-info.java                  @ApplicationModule declaration
├── {Module}Service.java               public — module's primary entrypoint
├── api/                               named interface — public DTOs (records, request/response shapes)
├── events/                            named interface — public ApplicationEvent records
├── domain/                            entities, value objects, enums (public where another module's entities extend them)
└── internal/                          package-private — controllers, repositories, internal helpers, mappers
```

**Visibility rules** enforced by Spring Modulith's `ApplicationModules.verify()`:

- Other modules may import types in `api/`, `events/`, the module's primary `@Service` interface, and the module root (e.g. the abstract `ScheduledEvent` class so that `flightops` can extend it).
- Other modules may **not** import anything in `internal/`. Controllers, repositories, mappers stay private.

**Why controllers live in `internal/`:** the public contract of a module is its `@Service` plus its DTOs/events. HTTP exposure is an implementation detail of `web` — another module never reaches an HTTP endpoint, only the service.

### 6.3 Module catalogue

| Module | Owns | Depends on |
|---|---|---|
| **identity** | `User`, `UserQualification`, `UserAvailability`, password hashing, JWT issuance, refresh-token storage | `tenancy` (FK existence check via service), `shared` |
| **tenancy** | `Squadron`, `SquadronAccess` | `shared` |
| **inventory** | `Platform` (type), `Aircraft`, `Simulator`, `Room`, `AuthorisationProfile` | `tenancy` (FK), `shared` |
| **scheduling** | abstract `ScheduledEvent`, `EventStatus`, `CrewAssignment`, `Formation`, `ConflictDetector`, daily program query | `identity`, `inventory`, `tenancy`, `shared` |
| **flightops** | `FlightEvent`, `SimEvent`, `GroundEvent`, authorisation workflow, sign-back, ops room, squawks | `scheduling`, `identity`, `inventory`, `currencies` (sync read for warnings), `shared` |
| **currencies** | `CurrencyDefinition`, `CurrencyGrant`, `CurrencyState` (computed), ad-hoc flight trigger | `identity`, `inventory`, `flightops` (sync write for ad-hoc flight), `shared` |
| **reporting** | PDF generation | reads `flightops`, `identity`, `inventory` via their services |

**Note on the `flightops ↔ currencies` cycle.** `flightops` calls `currencies.CurrencyService.computeWarnings(...)` synchronously during authorisation. `currencies` calls `flightops.FlightOpsService.scheduleAdHocFlight(...)` synchronously when the user creates an ad-hoc currency flight. These are reciprocal sync reads/writes against narrow public APIs — not a true dependency cycle because each module depends only on the other's API surface, not its internals. Spring Modulith accepts this; we document the contract carefully in both `api/` packages.

### 6.4 Module communication

Two patterns:

**Synchronous — inject another module's `@Service`.** Use for: reads where the caller needs the result, and user-initiated single-action workflows that must complete atomically (ad-hoc flight creation).

**Asynchronous — `ApplicationEventPublisher` + `@ApplicationModuleListener`.** Use for: side-effects after a state change where the caller doesn't need the result. The Spring Modulith JPA event publication starter persists every event in an `event_publication` table inside the originating transaction; the listener runs in a new transaction after commit. Failed listeners are retried; the row is marked complete only on success. This is the transactional outbox pattern, framework-provided.

```
flightops.OperationsService.signBack(eventId)
  → ScheduledEvent.signBack()
  → events.publishEvent(new EventSignedBackEvent(eventId, squadronId, signedBackAt))
  → COMMIT (event row persisted in event_publication table)
  → currencies @ApplicationModuleListener picks up the event, recomputes currency state for crew
```

**Forbidden in all cases:** importing another module's `internal/` types, repositories, controllers, or domain entities (except where inheritance requires it — e.g. `flightops.FlightEvent extends scheduling.ScheduledEvent`).

## 7. Persistence Layer

### 7.1 Entity base

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;
    // equals/hashCode by id, protected setters via package-private subclass methods
}
```

`@EnableJpaAuditing` is set once in `shared/config/`. Optimistic locking (`@Version`) is on by default — concurrent edits surface as `OptimisticLockException` mapped to `409 Conflict`.

UUIDs map to Postgres `uuid` natively. UUIDv4 from Hibernate's default generator. UUIDv7 (time-ordered) deferred until measurable index pain.

### 7.2 Naming strategy

Hibernate's `SpringPhysicalNamingStrategy` (default in Spring Boot): `ScheduledEvent` → `scheduled_event`, `OriginatingCourseId` → `originating_course_id`. Singular table names.

### 7.3 Multi-tenancy — two layers

**Layer 1: Hibernate `@Filter` (application ergonomics).** Entities implementing `SquadronScoped` are annotated with a `@FilterDef` + `@Filter`. The filter is enabled on every session by a `@Component` listening for new sessions; it reads the current user's home squadron and additional cross-squadron grants from the request-scoped `CurrentUser` bean and parameterises the filter with the resulting array of UUIDs.

```java
public interface SquadronScoped {
    UUID getSquadronId();
}
```

Effect: any JPA query — including those Spring Data generates from method names — automatically filters to the accessible squadrons. The application sees `EntityNotFoundException` for events outside its tenancy scope, not a leak.

**Layer 2: PostgreSQL Row Level Security (database enforcement).** Every squadron-scoped table has an RLS policy keyed off a session GUC:

```sql
ALTER TABLE flight_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE flight_event FORCE ROW LEVEL SECURITY;

CREATE POLICY squadron_isolation ON flight_event
    USING (squadron_id = ANY (current_setting('app.accessible_squadron_ids', true)::uuid[]));
```

A Hibernate `StatementInspector` issues `SET LOCAL app.accessible_squadron_ids = '{uuid1,uuid2,...}'` at transaction start, taken from `CurrentUser`. The application connects as a non-superuser role; `FORCE ROW LEVEL SECURITY` ensures even table owners are subject to policies. The Flyway role is separate and bypasses RLS (it's the owner during migrations).

The Hibernate filter is ergonomic; RLS is the wall. Filter misconfiguration cannot bypass RLS.

### 7.4 Inheritance — Single-Table strategy

`ScheduledEvent` is abstract with `@Inheritance(strategy = InheritanceType.SINGLE_TABLE)` and `@DiscriminatorColumn(name = "event_type")`. Concrete subclasses (`FlightEvent`, `SimEvent`, `GroundEvent`, future `NavalEvent` etc.) live in their domain module and use `@DiscriminatorValue`.

**Why single-table over joined or table-per-class:**

- Single SQL query for the Daily Program (no JOINs or UNION ALL across N tables)
- Polymorphic queries are cheap
- Discriminator column makes RLS trivial (one policy per table, not N)
- Cost: nullable columns for type-specific fields (manageable — ~9 nullable columns total)

Type-specific fields are nullable on the table but enforced as non-null via Bean Validation and database `CHECK` constraints scoped to the discriminator:

```sql
ALTER TABLE scheduled_event ADD CONSTRAINT flight_event_aircraft_required
    CHECK (event_type <> 'FLIGHT' OR aircraft_id IS NOT NULL);
```

### 7.5 Fetch strategies

All associations are lazy by default. N+1 is a code smell, not an inevitability.

Read paths use one of three strategies based on shape:

- **`@EntityGraph`** on repository methods for a one-level deep loader
- **`JOIN FETCH` JPQL** for hand-rolled fetch plans
- **Constructor-expression DTO projection** for read-only API responses — bypasses entity loading entirely

Read-heavy endpoints (Daily Program, currency overview) use DTO projections. Write paths and command handlers use entities.

### 7.6 Transactions

`@Transactional` lives on `@Service` methods, never on controllers. Default propagation (`REQUIRED`), default Postgres isolation (`READ COMMITTED`). Read methods use `@Transactional(readOnly = true)` to enable Hibernate's read-only flush mode and signal intent.

Cross-aggregate writes within a single user action stay in one transaction. Cross-module side-effects (currency recomputation after sign-back, audit log emission) are async via `@ApplicationModuleListener` so the originating transaction commits without waiting.

### 7.7 Audit ledger

Tamper-evident append-only ledger in `audit_entry`. Each entry stores:

- `id` (UUID), `created_at`, `actor_user_id`, `squadron_id`
- `entity_type`, `entity_id`, `operation` (`CREATE`, `UPDATE`, `STATE_TRANSITION`, etc.)
- `payload` (JSONB — canonical representation of the change)
- `prev_hash` (SHA-256 of the previous entry's `(prev_hash || canonical_payload)`)

Insert-only at the database level: the application role has `INSERT` on `audit_entry` and no `UPDATE` / `DELETE`. Optional HMAC signing can be added per deployment for stronger evidentiary value. This is an audit ledger, not full event sourcing — the write model is still conventional rows in domain tables.

### 7.8 Flyway

- Migrations in `src/main/resources/db/migration`
- Versioned naming: `V001__create_squadron_table.sql`, `V015__add_squawk_to_flight_event.sql`
- Repeatable migrations (`R__seed_default_authorisation_profiles.sql`) for idempotent seed data
- Production migration strategy: Flyway runs out-of-band before app pods roll. Migrations must be backward-compatible with the previous app version (additive only — destructive changes phase across releases: add new, dual-write, backfill, switch reads, drop old).
- Separate database roles: `sitrep_owner` (Flyway, owns tables, bypasses RLS), `sitrep_app` (the application, `SELECT/INSERT/UPDATE/DELETE`, subject to RLS).

## 8. Security — Authentication & Authorisation

### 8.1 Authentication

Spring Security with `oauth2-resource-server` for JWT validation. No external IdP; we issue our own tokens using `NimbusJwtEncoder`.

- **Access token:** 30 min, JWT, stateless. Claims: `sub` (user id), `username`, `role`, `home_squadron_id`, `squadron_access` (array of additional squadrons), `must_change_password`, standard `iat` / `exp`.
- **Refresh token:** 8 hr, stored server-side in `refresh_token` table. Rotated on every use. Revoked on logout, password change, or admin deactivation.

Login flow: `POST /api/v1/auth/login` → user authenticates → server issues both tokens. Client uses access token on every request. On 401, client silently calls `POST /api/v1/auth/refresh`. After 8 hr of inactivity the refresh token expires; user logs in again. With active use the session stays alive indefinitely.

`LoginResponse` includes `mustChangePassword` so the frontend redirects to a forced password-change flow on first login (new users are created by an admin with a temporary password).

### 8.2 Authorisation

Two layers:

**Layer 1: HTTP security filter chain.** Every `/api/v1/**` endpoint requires authentication. Public endpoints (`/api/v1/auth/login`, `/api/v1/auth/refresh`, `/actuator/health/*`) are explicit exceptions.

**Layer 2: Method-level via `@PreAuthorize`.** Role and access expressions live on `@Service` methods using Spring Security SpEL:

```java
@PreAuthorize("hasRole('ADMIN')")
public UUID createUser(CreateUserRequest request) { ... }

@PreAuthorize("hasAnyRole('ADMIN', 'PLANNER') and @squadronAuthz.canAccess(#squadronId)")
public EventResponse publishEvent(UUID squadronId, UUID eventId) { ... }
```

`@EnableMethodSecurity(prePostEnabled = true)` is set once. Roles are explicit — no hierarchical inheritance. Every protected method lists the roles that may invoke it.

Custom SpEL beans (`squadronAuthz`, `eventAuthz`) live in `shared/security/`; they read the request-scoped `CurrentUser` and answer fine-grained questions like "can this user access squadron X" or "is this user a member of crew on event Y".

### 8.3 `CurrentUser` bean

Request-scoped `@Component` reading from `SecurityContextHolder` once per request. Services and SpEL beans depend on `CurrentUser`, not raw Spring Security types.

```java
public interface CurrentUser {
    UUID id();
    String username();
    Role role();
    UUID homeSquadronId();
    Set<UUID> crossSquadronAccess();
    Set<UUID> allAccessibleSquadronIds();   // home + cross-access
    boolean hasRole(Role role);
    boolean canAccessSquadron(UUID squadronId);
}
```

Implementation reads claims from the JWT once on instantiation. Subsequent accesses within the request are cheap.

### 8.4 Cross-squadron access — three-tier model

- **Tier 1 (most users):** one home squadron, one role. Standard.
- **Tier 2 (small group):** instructors/planners working across squadrons (e.g. 1SQN using 2SQN's PC-21s). Additional `SquadronAccess` rows grant access on top of home.
- **Tier 3 (tiny group):** check instructors with universal access. Modelled with a `wildcard_access` boolean on `User`. Cheaper than N grants and the access set is conceptually "all squadrons", not "many".

JWT claims include home squadron, explicit cross-access grants, and the wildcard flag. The Hibernate filter and RLS policy honour all three.

## 9. Domain Model

### 9.1 Scheduling engine — abstract `ScheduledEvent`

The `scheduling` module owns the abstract base, the status machine, and the conflict detector. Domain modules (`flightops` now; `navalops`, `groundops` later) provide concrete subclasses with their domain-specific fields.

```java
// scheduling/ScheduledEvent.java
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "event_type")
@FilterDef(name = "squadronFilter",
           parameters = @ParamDef(name = "squadronIds", type = UUID[].class))
@Filter(name = "squadronFilter",
        condition = "squadron_id = ANY(:squadronIds)")
public abstract class ScheduledEvent extends BaseEntity implements SquadronScoped {

    private UUID squadronId;
    @Enumerated(EnumType.STRING) private EventStatus status;
    private Instant scheduledStart;
    private Instant scheduledEnd;

    private boolean delayed;
    private String delayReason;
    private Instant originalScheduledStart;

    @Enumerated(EnumType.STRING) private CancellationCategory cancellationCategory;
    private String cancellationNotes;

    private String missionCallsign;
    private UUID formationId;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CrewAssignment> crew = new ArrayList<>();

    // Domain methods — invariants enforced here, services orchestrate
    protected ScheduledEvent() {}   // JPA

    public void stage()    { /* validates DRAFT → STAGED */ }
    public void publish()  { /* validates STAGED → PUBLISHED */ }
    public void authorise(){ /* validates PUBLISHED → AUTHORISED */ }
    public void accept()   { /* validates → ACCEPTED */ }
    public void markAirborne(Instant t) { /* validates ACCEPTED → AIRBORNE */ }
    public void markLanded(Instant t)   { /* validates AIRBORNE → LANDED */ }
    public void signBack(List<CrewHours> hours) { /* validates LANDED → SIGNED_BACK */ }
    public void delay(String reason, Instant newStart) { ... }
    public void cancel(CancellationCategory category, String notes) { ... }
}
```

Each transition method:
- validates the current status permits the transition (throws `IllegalStateTransitionException` extending `BusinessRuleException`)
- updates state
- emits a domain event via `Spring`'s `AbstractAggregateRoot.registerEvent(...)` or via a passed-in publisher

```java
public enum EventStatus {
    DRAFT, STAGED, PUBLISHED, AUTHORISED,
    ACCEPTED, AIRBORNE, LANDED, SIGNED_BACK,
    CANCELLED
}
```

Delay is a flag, not a status. An event being moved to a new time may optionally be marked delayed; the event keeps its current status and continues through normal transitions.

### 9.2 Concrete flight events

```java
// flightops/FlightEvent.java
@Entity
@DiscriminatorValue("FLIGHT")
public class FlightEvent extends ScheduledEvent {
    private UUID aircraftId;
    private String squawk;
    @Enumerated(EnumType.STRING) private ApproachType approachType;
}

@Entity @DiscriminatorValue("SIM")
public class SimEvent extends ScheduledEvent {
    private UUID simulatorId;
}

@Entity @DiscriminatorValue("GROUND")
public class GroundEvent extends ScheduledEvent {
    private UUID roomId;
}
```

A future `navalops` module adds `@DiscriminatorValue("PATROL")` and its own subclass. The state machine, conflict detector, and daily program engine work against `ScheduledEvent` without changes.

### 9.3 Crew composition

Crew is slot-assigned per event. The slot determines (V2) logbook column and default hours split.

```java
@Entity
public class CrewAssignment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY) private ScheduledEvent event;
    private UUID userId;
    @Enumerated(EnumType.STRING) private CrewRole role;   // CAPTAIN, COPILOT, STUDENT, ...
    private UUID syllabusEventId;                          // V2 — null in V1
    private Duration flightHours;                          // null until sign-back; defaults to equal split
}
```

### 9.4 Formations

```java
@Entity
public class Formation extends BaseEntity implements SquadronScoped {
    private UUID squadronId;
    private String callsign;
    // Members are ScheduledEvents where event.formationId == this.id
}
```

Members can be from different squadrons. Each member is a fully-fledged event with independent crew. Created as a single user action ("create N-ship formation"); members can be added or removed post-creation.

### 9.5 Platforms

```java
// inventory/Platform.java
@Entity
public class Platform extends BaseEntity {
    private String name;                                              // "PC-21", "FTD"
    @Enumerated(EnumType.STRING) private PlatformCategory category;   // AIRCRAFT, SIMULATOR, ROOM
    private UUID authorisationProfileId;
    private boolean active;
}

@Entity public class Aircraft   extends BaseEntity implements SquadronScoped { /* squadronId, platformId, tailNumber, isShared, isActive */ }
@Entity public class Simulator  extends BaseEntity implements SquadronScoped { /* squadronId, platformId, isShared, isActive */ }
@Entity public class Room       extends BaseEntity implements SquadronScoped { /* squadronId, platformId, isShared, isActive */ }
```

### 9.6 User qualifications

Users hold qualifications per platform type with a default crew role:

```java
@Entity
public class UserQualification extends BaseEntity {
    private UUID userId;
    private UUID platformId;
    @Enumerated(EnumType.STRING) private CrewRole defaultRole;
}
```

Default role determines the crew slot when scheduled. The actual slot on the event can override (instructor flying recurrency in student slot; student flying solo in captain slot). Conflict detection verifies crew are qualified on the event's platform type.

### 9.7 Callsigns

Users have a personal callsign (persistent, rarely changed). Flying events display the captain's callsign by default, overridable with a mission callsign per event. Formation groups have their own callsign. The UI pre-fills from personal callsigns to minimise data entry.

### 9.8 Availability

`UserAvailability` records (`LEAVE`, `MEDICAL`, `DUTY`, `UNFIT`, `OTHER`) with date ranges feed into conflict detection. Day-of unavailability immediately flags conflicting events as warnings.

### 9.9 Currencies

A currency is a time-bound qualification computed from event history or granted explicitly.

```java
@Entity
public class CurrencyDefinition extends BaseEntity {
    private String code;                                                 // "INST_APPR"
    private String displayName;
    private UUID platformId;                                             // applies to which platform type
    @Enumerated(EnumType.STRING) private CurrencyRuleType ruleType;      // ROLLING_WINDOW_COUNT, DATE_BASED
    private int windowDays;
    private int requiredCount;
    @Enumerated(EnumType.STRING) private CurrencyEnforcement enforcement;// WARN, BLOCK
}

@Entity
public class CurrencyGrant extends BaseEntity {
    private UUID userId;
    private UUID currencyDefinitionId;
    private Instant grantedAt;
    private Instant expiresAt;
    private UUID grantedByUserId;
    private String notes;
}
```

`CurrencyState` is a computed view, not persisted. It is produced on demand from sign-back history + explicit grants. The currencies module listens to `EventSignedBackEvent` for cache invalidation (Caffeine, scoped per user).

## 10. API Surface & Error Handling

### 10.1 REST conventions

- URL path versioning from day one (`/api/v1/`)
- DTOs are records
- One `@RestController` per resource family (`UserController`, `SquadronController`, etc.) under each module's `internal/web/`
- Class-level `@RequestMapping`, method-level `@GetMapping` / `@PostMapping` etc. with relative paths
- Pagination via Spring Data's `Pageable` parameter (`page`, `size`, `sort`) for list endpoints
- ISO-8601 for dates; `Instant` (UTC) in the API surface; no zoned times

### 10.2 Error model — RFC 9457 ProblemDetail

Services throw typed exceptions from a `shared/error/` hierarchy:

```java
public abstract class DomainException extends RuntimeException { ... }

public class NotFoundException extends DomainException { ... }
public class ConflictException extends DomainException { ... }
public class BusinessRuleException extends DomainException { ... }
public class ForbiddenException extends DomainException { ... }
public class IllegalStateTransitionException extends BusinessRuleException { ... }
```

A single `@RestControllerAdvice` (`GlobalExceptionHandler`) maps:

| Exception | HTTP | ProblemDetail title |
|---|---|---|
| `NotFoundException` | 404 | "Not Found" |
| `BusinessRuleException` (incl. `IllegalStateTransitionException`) | 422 | "Business Rule Violation" |
| `ConflictException` (incl. `OptimisticLockingFailureException`) | 409 | "Conflict" |
| `ForbiddenException` | 403 | "Forbidden" |
| `MethodArgumentNotValidException` (Bean Validation) | 422 | "Validation Failed" — with `errors` array |
| `AccessDeniedException` (Spring Security) | 403 | "Forbidden" |
| `AuthenticationException` (Spring Security) | 401 | "Unauthorised" |
| `Exception` (catch-all) | 500 | "Internal Server Error" (no stack trace in body, full stack logged) |

`ProblemDetail` is built into Spring Boot. Each response includes `type`, `title`, `status`, `detail`, `instance` (request path), and any domain-specific extension fields (e.g. `errors[]` for validation, `entityId` for not-found).

### 10.3 OpenAPI

SpringDoc generates the spec from controllers, DTOs, and `@Operation` annotations on endpoints. Spec is published at `/v3/api-docs` and a Swagger UI at `/swagger-ui.html` (non-production profiles only). The spec is committed to the repo on every release for downstream consumers (frontend type generation).

## 11. Cross-cutting Concerns

### 11.1 Validation

Two-layer split:

- **Shape / format / required fields** — Jakarta Bean Validation on request DTOs: `@NotBlank`, `@Email`, `@Pattern`, `@Past`, `@Size`. Spring auto-wires this; failures throw `MethodArgumentNotValidException` mapped to 422.
- **Domain rules** — explicit `if` / `throw` inside `@Service` methods or domain entity methods. These are rules that require DB lookups (uniqueness checks), depend on current state (event status), or span multiple fields. They throw `BusinessRuleException` (or a subtype) mapped to 422 or 409.

### 11.2 Logging

SLF4J + Logback. Two profiles:

- **dev** — human-readable, colourised, single-line per event
- **prod** — JSON output via `logstash-logback-encoder`, one JSON document per line

A `OncePerRequestFilter` populates MDC with `requestId`, `userId`, `squadronId` on every request and clears it on completion. Every log line in production includes these fields automatically. Log levels are tunable at runtime via `/actuator/loggers`.

### 11.3 Caching

`@Cacheable` / `@CacheEvict` / `@CachePut` on read-heavy lookups (platforms list, authorisation profiles, user qualifications). Caffeine is the provider — local in-memory, no distributed dependency. Cache eviction on writes is explicit.

Currency state is cached per user with a TTL plus event-driven invalidation (`@ApplicationModuleListener` on `EventSignedBackEvent`).

### 11.4 Configuration

`@ConfigurationProperties` records per concern, validated with `@Validated`:

```java
@ConfigurationProperties(prefix = "sitrep.auth")
@Validated
public record AuthProperties(
    @NotNull Duration accessTokenLifetime,
    @NotNull Duration refreshTokenLifetime,
    @NotBlank String issuer
) {}
```

No `@Value` scattered through the code. Config is testable, validated at startup, and documented in `application.yml`.

### 11.5 Observability

- **Health** — `/actuator/health/liveness` and `/actuator/health/readiness` for Kubernetes probes
- **Metrics** — Micrometer; Prometheus scrape endpoint at `/actuator/prometheus`
- **Tracing** — Micrometer Tracing + OpenTelemetry exporter when a tracing backend (Tempo / Jaeger) is configured
- **Modulith events dashboard** — Spring Modulith provides actuator endpoints for the JPA event publication state

### 11.6 Concurrency

Optimistic locking via `@Version` on `BaseEntity`. Concurrent edits surface as `OptimisticLockingFailureException` mapped to 409. No pessimistic locking by default — if a specific aggregate needs it, `@Lock(LockModeType.PESSIMISTIC_WRITE)` on a repository method is the targeted tool.

Virtual threads (`spring.threads.virtual.enabled=true`) handle servlet request dispatch. Hibernate 6.6 has been audited for virtual-thread compatibility — no pinning concerns at the version we use.

## 12. Frontend

Separate workspace, React 18 + TypeScript SPA consuming the versioned REST API. Stack: Vite, TanStack Query, React Router v7, Tailwind, shadcn/ui. Architecture lives in a separate frontend design doc.

Key views (V1): Daily Program (drag-and-drop, multi-shift publishing), Squadron Program View, authorisation workflow, currency overview, Ops room view. Frontend types generated from the published OpenAPI spec via `openapi-typescript`.

## 13. Native Image Readiness

GraalVM Native Image is a secondary build target (cloud demo, scale-to-zero scenarios). The code is written to be native-compatible from day one so the secondary build is a profile switch, not a rewrite.

**Code-level discipline:**

- No runtime reflection over application classes (Jackson, Hibernate, and Spring's own reflection are handled by Spring's AOT processing)
- No dynamic class loading (`Class.forName(userInputString)`)
- No use of `MethodHandle` or `LambdaMetafactory` outside the framework
- All `@Conditional` config (`@ConditionalOnProperty`, etc.) is evaluated at build time, not runtime — design accordingly
- `@PostConstruct` / `@PreDestroy` work; lifecycle hooks via Spring are fine
- Spring AOP via `@Aspect` is supported through Spring's AOT-friendly proxy mechanism (`@PreAuthorize` works because it uses this)
- Hibernate works with proper reachability metadata; Spring Boot generates most of it automatically from `@Entity` scanning

**Build:**

- Default build: `mvn package` produces `sitrep.jar`, runs on JVM
- Native build: `mvn -Pnative native:compile` produces a native binary; takes 5–15 min vs ~1 min for JVM
- CI builds JVM on every push, native only on the cloud-demo release branch

## 14. Testing Strategy

| Layer | Tools | Scope |
|---|---|---|
| Unit | JUnit 5 + AssertJ + Mockito | Domain entity methods, service logic, validators |
| Repository | `@DataJpaTest` + Testcontainers Postgres | Repository queries, filter behaviour, RLS |
| Module | Spring Modulith `@ApplicationModuleTest` | Each module bootstraps with only its declared dependencies |
| Integration | `@SpringBootTest` + Testcontainers Postgres | Full HTTP request → response per endpoint, including security |
| Architecture | ArchUnit + `ApplicationModules.verify()` | Module boundaries, layer rules, naming, no field injection |
| Contract | Spring REST Docs or JSON snapshot | API contract stability across releases |
| Outbox | Targeted integration tests | Event publication, listener retry, idempotency |
| PDF | Snapshot tests | Generated documents have expected structure |
| Native build | `mvn -Pnative test` | Sanity check that the native image starts and serves a request |

**Conventions:**

- Naming: `methodName_scenario_expectedResult`. `@DisplayName` provides human-readable test reports in CI output.
- Real Postgres in every layer that touches the database — never H2, never mocked repositories. The schema is the schema.
- One Testcontainers Postgres per test class, shared via `@Container` static field with `Singleton` strategy for speed.
- Architecture tests run before integration tests in CI (fast, catch boundary violations early).

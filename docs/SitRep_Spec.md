# SitRep — Java/Spring Boot Technical Spec

Companion to the Functional Design. This document covers the **technical** approach: stack, module map, cross-cutting infrastructure, deployment model, and a detailed spec for Slice 1.

---

## Part A — Project-Level Spec

### A.1 Tech Stack

| Concern | Choice | Rationale |
|---|---|---|
| Language | Java 25 (LTS) | Current LTS (released September 2025). Virtual threads, pattern matching, records all mature. AOT cache available (Java 24+). |
| Framework | Spring Boot 4.x | Spring Boot 4.0+ at time of build. Spring Modulith 2.x targets Boot 4. |
| Module boundaries | Spring Modulith | Compile-time enforcement of module boundaries. Replaces the "modular monolith by convention" pattern with one that fails the build when violated. |
| Build | Maven (single module, Modulith uses packages) | Maven over Gradle for Dutch market familiarity; single module is fine with Modulith. |
| Database | PostgreSQL 17 | RLS for multi-tenancy, advisory locks, jsonb, generated columns. |
| Persistence | Spring Data JPA + Hibernate 6 | Idiomatic. |
| Migrations | Flyway | Versioned SQL migrations checked into repo. |
| Web | Spring MVC (not WebFlux) | No reactive need; Dutch shops will know MVC. Virtual threads give scale without reactive complexity. |
| Security | Spring Security 6 + `oauth2-resource-server` for JWT validation, `nimbus-jose-jwt` for signing | Idiomatic. JJWT works but isn't what shops expect. |
| API docs | springdoc-openapi | OpenAPI 3 generated from controllers. |
| Validation | Jakarta Bean Validation (Hibernate Validator) | Standard. |
| Testing | JUnit 5, AssertJ, Testcontainers, Spring Modulith test support, ArchUnit | Real Postgres in integration tests. ArchUnit for module boundary tests. |
| Logging | SLF4J + Logback + `logstash-logback-encoder` | JSON to stdout. |
| Tracing | Micrometer Tracing + Brave (or OpenTelemetry bridge) | Provides traceId/spanId in logs and metrics. |
| Observability | Spring Boot Actuator + Micrometer | `/actuator/health`, `/actuator/metrics`. Prometheus-compatible. |
| Container | Docker, multi-stage build with `bellsoft/liberica-openjre-debian:25-cds` | Spring Boot-aligned base image. CDS pre-built; AOT cache Dockerfile variant for fast startup. |
| Local orchestration | Docker Compose | App + Postgres. |
| Object storage abstraction | `BlobStore` interface, filesystem + S3-compatible impls | Added when first needed (PDF printing). Filesystem impl is the default for air-gapped. For the cloud demo, the S3-compatible impl talks to either real AWS S3 or an on-prem **MinIO** server (open-source S3-compatible object store that runs as a container — same SDK, same API, swap by config). |

### A.2 Twelve-Factor Compliance

- **Config**: All environment-dependent config from env vars. `application.yml` only holds defaults and structure. Spring's `Environment` resolves `${VAR}` placeholders.
- **Backing services**: Datasource URL, blob store URL, etc. are all env vars. Same image deploys to air-gapped LAN and cloud demo with different `.env`.
- **Build/release/run**: One Docker image, tagged per release. Configuration is layered on at deploy time.
- **Logs**: JSON to stdout via Logback. No log file management inside the container.
- **Disposability**: Spring Boot graceful shutdown enabled (`server.shutdown=graceful`). Outbox dispatcher checks shutdown flag between batches.
- **Stateless**: No in-memory session state. Refresh tokens in Postgres. File uploads (when added) go to `BlobStore`, not local disk.

### A.3 Module Map

Each module is a top-level package under `com.camelbytes.sitrep.<module>`. Spring Modulith enforces that modules only depend on other modules' explicitly exposed `api` sub-packages, not their `internal` packages.

| Module | Responsibility | Key entities | Depends on |
|---|---|---|---|
| `users` | User identity, qualifications, availability, activation lifecycle. Not tenant-scoped — users transfer between squadrons. | `User`, `Qualification`, `Availability` | — |
| `squadrons` | Squadron definitions, current squadron assignment for users (including role), cross-squadron access grants. | `Squadron`, `SquadronAssignment`, `CrossSquadronGrant` | `users` |
| `auth` | Login, JWT issuance, refresh token management, password hashing. Provides `currentUser()` / `currentTenantContext()` to other modules. | `RefreshToken` | `users`, `squadrons` |
| `platforms` | Aircraft, simulators, rooms. Platform types with authorization profiles. | `Platform`, `PlatformType`, `AuthorizationProfile`, `Tail` | `squadrons` |
| `courses` | Course definitions, syllabus structure, student enrolment, syllabus board projection. | `Course`, `SyllabusEvent`, `Enrolment`, `SyllabusProgress` | `users`, `squadrons` |
| `scheduling` | Daily program, event lifecycle (state machine), staging/publishing, formations, conflict detection, cancellations. The core of the system. | `Event`, `Formation`, `CrewSlot`, `DailyProgram` | `users`, `squadrons`, `platforms`, `courses` |
| `operations` | Ops room workflow — squawk assignment, airborne/landed transitions. | (no new entities; commands against `scheduling`) | `scheduling` |
| `grading` | Gradesheets attached to syllabus events on sign-back. | `Gradesheet`, `GradeItem` | `scheduling`, `courses` |
| `logbooks` | Auto-generated logbook entries on sign-back. Long-retention. | `LogbookEntry` | `scheduling`, `platforms` |
| `currency` | Time-based and manual currencies. Reads from logbooks; provides warnings to `scheduling`. | `CurrencyDefinition`, `ManualCurrencyRecord` | `logbooks`, `users`, `platforms` |
| `audit` | Append-only hash-chained audit ledger. Consumes outbox messages addressed to it. | `AuditEntry` | `outbox` |
| `outbox` | Transactional outbox infrastructure. Generic — any module can publish through it. | `OutboxMessage` | — |
| `printing` | PDF generation for authorization sheets etc. | (transient; outputs to `BlobStore`) | `scheduling`, `users` |

**Dependency direction**: lower in the table generally depends on (some subset of) those higher. `outbox` is the only true "depends on nothing" infrastructure module — every other module either depends on it or transitively does. `audit` is special: it depends on `outbox` (as a consumer) but is itself never called directly by domain modules — domain modules emit audit intent via outbox, decoupling them from the audit module.

### A.4 Cross-Cutting Infrastructure

#### A.4.1 Multi-Tenancy (Postgres RLS)

- App connects as a non-superuser role **without** `BYPASSRLS`.
- Every tenant-scoped table has a `squadron_id UUID NOT NULL` column and `ENABLE ROW LEVEL SECURITY`.
- Policy template per table:
  ```sql
  CREATE POLICY tenant_isolation ON <table>
    USING (squadron_id = ANY(
      COALESCE(
        NULLIF(current_setting('app.accessible_squadrons', true), '')::uuid[],
        ARRAY[]::uuid[]
      )
    ));
  ```
  The `, true` makes `current_setting` return empty instead of erroring when unset — important for Flyway migrations, Testcontainers bootstrap, and any other path that touches the table outside a normal authenticated request. When unset, the policy matches nothing (correct: no tenant context = no rows visible).
- A Spring `HandlerInterceptor` (or `@Transactional` AOP wrapper) executes `SET LOCAL app.accessible_squadrons = '{uuid1,uuid2,...}'` at the start of every request transaction, using the values from the auth context.
- **Tables NOT RLS-scoped** (must be visible across tenant context for the auth/admin flow to work):
  - `users.user` — users transfer between squadrons.
  - `squadrons.squadron`, `squadrons.squadron_assignment`, `squadrons.cross_squadron_grant` — needed to compute the `accessible_squadrons` claim at login.
  - `auth.refresh_token` — looked up by token hash, not by tenant.
  - `audit.audit_entry` — audit is system-wide.
  - `outbox.outbox_message` — infrastructure.
- For background workers (outbox dispatcher), the worker resolves the appropriate tenant context per message before invoking consumers. Since `outbox_message` and `audit_entry` are not RLS-scoped, the dispatcher itself doesn't need BYPASSRLS — it just needs to set the right tenant context before any consumer touches RLS-scoped tables.
- **Test coverage**: integration tests assert that a request authenticated as squadron A cannot see squadron B's data even when explicitly queried by ID.

#### A.4.2 Audit Ledger (Append-Only, Hash-Chained)

- Table `audit.audit_entry`: `id BIGSERIAL`, `created_at`, `actor_user_id`, `action`, `payload jsonb`, `prev_hash bytea`, `entry_hash bytea`.
- `entry_hash = sha256(prev_hash || canonical_json(payload) || created_at || action || actor_user_id)`.
- **Write path**: domain modules don't write audit entries directly. They publish to the outbox with `aggregate_type = 'audit'`. A dedicated single-threaded audit dispatcher consumes those messages in strict creation order, computes the hash from the previous entry, and inserts. This guarantees hash chain integrity even under concurrent producers.
- **Enforcement layer**: a dedicated Postgres role (`audit_writer`) has `INSERT` only on `audit_entry`; `UPDATE` and `DELETE` are revoked. The audit dispatcher executes `SET LOCAL ROLE audit_writer` within the insert transaction (see A.4.3). Even a compromised app cannot rewrite the chain — the app's normal role (`app_user`) has no UPDATE/DELETE on `audit_entry`, and `audit_writer` has none either.
- **Triggers as defence-in-depth**: `BEFORE UPDATE OR DELETE ON audit_entry` raises an exception, regardless of role. Catches mistakes by a superuser maintenance session.
- **Verification**: integration test walks the chain end-to-end and recomputes each `entry_hash`.

#### A.4.3 Transactional Outbox

- Single table `outbox.outbox_message`: `id UUID PK`, `sequence_no BIGSERIAL UNIQUE NOT NULL` (used for strict creation order — UUIDs alone don't order), `aggregate_type`, `aggregate_id`, `event_type`, `payload jsonb`, `created_at`, `dispatched_at`, `attempt_count`, `last_error`, `next_attempt_at`.
- Producers insert into `outbox_message` in the same `@Transactional` block as their domain write — atomicity is free.
- A `@Scheduled` dispatcher runs every few seconds, claims undispatched messages via `SELECT ... FOR UPDATE SKIP LOCKED`, dispatches them to in-process listeners (via Spring `ApplicationEventPublisher`), marks them dispatched.
- **Two dispatcher loops**:
  - **General dispatcher**: parallel-safe, uses `SKIP LOCKED`, processes everything except `aggregate_type = 'audit'`. Orders by `sequence_no` within each claim batch but doesn't require strict ordering across batches.
  - **Audit dispatcher**: single-threaded, orders strictly by `outbox_message.sequence_no` (BIGSERIAL guarantees gap-free monotonic ordering within a single Postgres instance). Uses `pg_try_advisory_lock` so only one replica runs the loop at a time. For audit inserts, the dispatcher executes `SET LOCAL ROLE audit_writer` within the transaction; the connection returns to `app_user` at transaction end. This keeps a single connection pool.
- **Idempotency**: consumers track processed message IDs in their own tables, so retries are safe.
- **Durability story**: the outbox row is co-committed with the domain change in the same transaction. If the domain change committed, the outbox row exists. If the dispatcher is down for hours, no message is lost — it'll be processed when the dispatcher recovers.
- Hand-rolled, not a library — the entire mechanism is ~150 lines and easy to explain in an interview.

#### A.4.4 State Machine (Event Lifecycle)

- Enum-driven, **not** Spring Statemachine.
  ```java
  enum EventStatus { DRAFT, STAGED, PUBLISHED, AUTHORIZED, ACCEPTED, AIRBORNE, LANDED, SIGNED_BACK, CANCELLED }
  ```
- Each transition is a method on the `Event` aggregate (e.g. `event.authorize(byUser)`). Java 21 switch expressions enforce exhaustiveness — the compiler requires every `EventStatus` case to be handled; illegal transitions fall to `default`:
  ```java
  public void authorize(User actor) {
      this.status = switch (this.status) {
          case PUBLISHED -> AUTHORIZED;
          default -> throw new IllegalStateTransitionException(this.status, AUTHORIZED);
      };
      // publish domain event to outbox
  }
  ```
- Profile variations (e.g. sim events skipping authorization) are a guard at the top of the relevant method, reading `platform.requiresAuthorization()` — a boolean on the `Platform` entity. No data-driven lookup needed.
- Testability: transitions are unit-testable without Spring.

#### A.4.5 Authentication

- `POST /api/v1/auth/login` — username + password → access token (JWT, HS256, 15 min) + refresh token (opaque, 30 days, HttpOnly cookie).
- `POST /api/v1/auth/refresh` — exchanges refresh token for new access token + rotated refresh token. Old refresh token is revoked.
- `POST /api/v1/auth/logout` — revokes refresh token.
- Passwords: BCrypt cost 12.
- JWT claims: `sub` (user id), `squadron` (current squadron id), `accessible_squadrons` (array), `roles` (array), `iat`, `exp`.
- Refresh tokens stored as SHA-256 hash so a DB leak doesn't grant access.
- **Brute-force protection**: deferred to a later slice. Will likely add a `login_attempt` table with IP/username-keyed counters and exponential backoff, applied as a Spring Security filter.

#### A.4.6 API Standards

- All routes under `/api/v1/...`.
- Errors return RFC 9457 Problem Details: `application/problem+json`.
- Status codes:
  - `422 Unprocessable Entity` — Bean Validation failures on otherwise well-formed requests. (Choosing 422 over 400 deliberately — 400 is for malformed requests, 422 is for syntactically-valid-but-semantically-invalid. Spring Boot 3's default maps `MethodArgumentNotValidException` to 400; we explicitly override this in the `@ControllerAdvice`.)
  - `400 Bad Request` — malformed JSON, missing required body, type mismatches.
  - `409 Conflict` — domain rule violations (e.g. illegal state transition, unique constraint).
  - `403 Forbidden` — auth context lacks required role.
  - `404 Not Found` — entity not found OR not visible to current tenant (don't leak existence).
  - `500` — unhandled, no stack traces in production responses.
- Global `@ControllerAdvice` maps exception types to Problem Details.

#### A.4.7 Logging & Observability

- Logback JSON encoder. Each log line includes: `timestamp`, `level`, `logger`, `message`, `traceId`, `spanId`, `userId`, `squadronId`, `requestId`.
- `traceId` / `spanId` from Micrometer Tracing (configured in stack above).
- MDC populated from auth context and request headers in a filter.
- Actuator endpoints: `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`. Locked down to internal port (separate management port) in production.

#### A.4.8 Testing Strategy

| Layer | Tool | Scope |
|---|---|---|
| Unit | JUnit 5, AssertJ, Mockito (sparingly) | Domain entities, state machine, validators, hash chain. No Spring context. |
| Module | Spring Modulith `@ApplicationModuleTest` | Verifies module boundaries; tests one module with others stubbed. |
| Integration | `@SpringBootTest` + Testcontainers Postgres | Real DB, real Spring context. Multi-tenant isolation tests live here. |
| Contract | springdoc-generated OpenAPI + schema diff in CI | Generated OpenAPI is checked in; CI fails on accidental breaking changes. |
| Architecture | ArchUnit + Spring Modulith verification | Module boundary tests, naming conventions, layering rules. |
| Outbox/reliability | Custom integration tests | Crash mid-dispatch, verify no message lost; idempotency under double-dispatch. |
| PDF | Structural assertions on generated PDFs | When `printing` module exists. |

### A.5 Deployment Model

- **Local dev**: `docker compose up`. App + Postgres. Hot reload via Spring DevTools.
- **Air-gapped production**: same compose stack, or Podman, or a Kubernetes manifest. Image pre-loaded onto the network. Config via env file.
- **Cloud demo**: same image, managed Postgres. Deployed via plain `docker run` on a small VM somewhere. The point is portability, not specific provider.
- **Replicas**: 2–3 stateless app containers behind a load balancer. The general outbox dispatcher uses `FOR UPDATE SKIP LOCKED` so multiple replicas can dispatch in parallel without coordination. The audit dispatcher uses an advisory lock (`pg_try_advisory_lock`) so only one replica is processing audit messages at a time — required for ordering.

### A.6 Repository Layout

```
sitrep/
├── docker-compose.yml
├── Dockerfile
├── pom.xml
├── README.md
├── docs/
│   ├── SitRep_Functional-Design.md     
│   ├── SitRep_Java_Spec.md              (this file)
│   └── decisions/                       (ADRs)
├── src/main/java/com/sitrep/
│   ├── SitRepApplication.java
│   ├── shared/                          (Result types, base entities, common exceptions)
│   ├── users/
│   │   ├── api/                         (public interface for other modules)
│   │   └── internal/                    (entities, repos, services — Modulith-enforced private)
│   ├── squadrons/...
│   ├── auth/...
│   ├── audit/...
│   ├── outbox/...
│   └── ...
├── src/main/resources/
│   ├── application.yml
│   ├── db/migration/                    (Flyway: V1__roles.sql, V2__users.sql, ...)
│   └── logback-spring.xml
└── src/test/java/...
```

### A.7 ADRs (Architecture Decision Records)

Lightweight markdown files in `docs/decisions/`, one per significant decision. Format: context → decision → consequences. First few to write:

- ADR-001: Modular monolith with Spring Modulith
- ADR-002: Postgres RLS for multi-tenancy (rejected alternatives: Hibernate `@Filter`, schema-per-tenant)
- ADR-003: Hand-rolled outbox over Debezium/library
- ADR-004: Enum-driven state machine over Spring Statemachine
- ADR-005: JWT access + opaque refresh tokens
- ADR-006: Java 25 + Spring Boot 4.x
- ADR-007: Audit ledger via outbox (eventual consistency) over synchronous writes

These are gold for interviews — concrete evidence you think about trade-offs.

---

## Part B — Slice 1 Detailed Spec

**Goal**: Stand up `users`, `squadrons`, `auth`, the multi-tenancy mechanism, the audit ledger, and the outbox — all the plumbing every later slice depends on.

**Acceptance**: by the end of slice 1, you can:
1. Create users and squadrons via API.
2. Log in, get tokens, hit a protected endpoint.
3. Demonstrate that a user from squadron A cannot read squadron B's data (via at least one tenant-scoped entity — we'll introduce a placeholder).
4. Show an immutable hash-chained audit trail of all the above.
5. See an outbox message produced and dispatched for at least one operation.
6. Run `docker compose up` from a clean checkout and have everything work.

### B.1 Modules in Slice 1

`users`, `squadrons`, `auth`, `audit`, `outbox`, `shared`. Plus a stub `platforms` module containing one tenant-scoped entity purely to exercise RLS.

### B.2 Domain Model

#### `users.User`
- `id UUID PK`
- `username VARCHAR(64) UNIQUE NOT NULL`
- `password_hash VARCHAR(60) NOT NULL` (BCrypt)
- `display_name VARCHAR(128) NOT NULL`
- `personal_callsign VARCHAR(16)` nullable
- `active BOOLEAN NOT NULL DEFAULT true`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`, `updated_at TIMESTAMP WITH TIME ZONE NOT NULL`

Not RLS-scoped. The `User` entity exposes `activate()`, `deactivate()`, and `changePasswordHash(newHash)` (it owns its hash, but doesn't know how to compute one). Password verification and hashing live in `auth.PasswordHasher` (a `BCryptPasswordEncoder` wrapper), keeping the BCrypt dependency out of the domain entity.

#### `squadrons.Squadron`
- `id UUID PK`
- `name VARCHAR(100) NOT NULL`
- `short_name VARCHAR(16) UNIQUE` nullable (e.g. "1SQN")
- `is_active BOOLEAN NOT NULL DEFAULT true`

Not RLS-scoped (squadron metadata is global). `enable()` / `disable()` domain methods. `short_name` replaces the original `code` field — more self-documenting.

#### `squadrons.SquadronAssignment`
- `id UUID PK`
- `user_id UUID FK -> users.user`
- `squadron_id UUID FK -> squadrons.squadron`
- `role VARCHAR(32) NOT NULL` — enum: ADMIN, MANAGER, PLANNER, INSTRUCTOR, STUDENT, OPS, BASIC
- `started_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `ended_at TIMESTAMPTZ` nullable
- Partial unique index: `CREATE UNIQUE INDEX ON squadron_assignment(user_id) WHERE ended_at IS NULL`

`ended_at IS NULL` replaces the `is_current` boolean — single source of truth. Transfer logic: end existing assignment (`saveAndFlush`), insert new one in same `@Transactional`. Not RLS-scoped.

#### `squadrons.SquadronGuestAccess`
- `id UUID PK`
- `user_id UUID FK -> users.user`
- `squadron_id UUID FK -> squadrons.squadron` (the squadron they're granted access to)
- `role VARCHAR(32) NOT NULL` — the role the user holds in the guest squadron
- `granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `revoked_at TIMESTAMPTZ` nullable

Replaces `CrossSquadronGrant`. Adds `role` field. Used when building `accessible_squadrons` claim at login. Not RLS-scoped.

#### `auth.RefreshToken`
- `id UUID PK`
- `user_id UUID NOT NULL FK -> users.user`
- `token_hash CHAR(64) NOT NULL` (hex SHA-256 of the random secret)
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `expires_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `revoked_at TIMESTAMP WITH TIME ZONE` nullable
- `replaced_by_id UUID` nullable (FK to next token after rotation)
- `created_ip INET`, `created_user_agent TEXT`

Not RLS-scoped.

#### `audit.AuditEntry`
- `id BIGSERIAL PK` (ordered insertion matters for the chain)
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `actor_user_id UUID` nullable (system actions)
- `action VARCHAR(64) NOT NULL` (e.g. `user.created`, `auth.login_succeeded`)
- `payload JSONB NOT NULL`
- `prev_hash BYTEA` nullable (null only for the genesis entry)
- `entry_hash BYTEA NOT NULL UNIQUE`

Not RLS-scoped. Insert-only via `audit_writer` role. `BEFORE UPDATE OR DELETE` trigger raises exception as defence-in-depth.

#### `outbox.OutboxMessage`
- `id UUID PK`
- `sequence_no BIGSERIAL UNIQUE NOT NULL` — strict creation order for ordered dispatchers (audit). UUIDs alone don't order.
- `aggregate_type VARCHAR(64) NOT NULL`
- `aggregate_id VARCHAR(64) NOT NULL`
- `event_type VARCHAR(64) NOT NULL`
- `payload JSONB NOT NULL`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`
- `dispatched_at TIMESTAMP WITH TIME ZONE` nullable
- `attempt_count INT NOT NULL DEFAULT 0`
- `last_error TEXT` nullable
- `next_attempt_at TIMESTAMP WITH TIME ZONE` nullable (for backoff)

Indexes: `(dispatched_at, next_attempt_at)` for dispatcher polling; `(aggregate_type, sequence_no) WHERE dispatched_at IS NULL` for the audit dispatcher's ordered scan. Not RLS-scoped.

#### `platforms.Room` (stub for slice 1, to exercise RLS)
- `id UUID PK`
- `squadron_id UUID NOT NULL` — **RLS-scoped**
- `name VARCHAR(128) NOT NULL`

This is a placeholder so we can demonstrate RLS works. Real `platforms` module gets built in slice 2.

### B.3 REST Endpoints (Slice 1)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | none | Username/password → tokens |
| `POST` | `/api/v1/auth/refresh` | refresh cookie | Rotate tokens |
| `POST` | `/api/v1/auth/logout` | refresh cookie | Revoke refresh token |
| `GET` | `/api/v1/users/me` | bearer | Current user info |
| `POST` | `/api/v1/users` | bearer + ADMIN | Create user (no squadron — assigned separately, matches functional design §4) |
| `POST` | `/api/v1/users/{id}/deactivate` | bearer + ADMIN | Deactivate user |
| `POST` | `/api/v1/squadrons` | bearer + ADMIN | Create squadron |
| `POST` | `/api/v1/squadrons/{id}/assignments` | bearer + ADMIN | Assign user to squadron with role; sets `is_current=true` and ends any prior current assignment in one transaction |
| `POST` | `/api/v1/rooms` | bearer + PLANNER | Create a room (tenant-scoped — RLS demo) |
| `GET` | `/api/v1/rooms` | bearer | List rooms visible to current tenant context |

`GET /api/v1/rooms` is the proof: a user in squadron A gets only A's rooms, even though all rooms are in one table.

### B.4 Flyway Migrations (Slice 1)

**Schema decision**: only the `audit` schema is a named Postgres schema — its privilege model (INSERT-only via `audit_writer`) requires the schema boundary. All other domain tables live in `public`. Adding a schema per module added ceremony with no real access-control benefit for those tables.

**Role/user creation**: `app_user` (LOGIN) and `audit_writer` (NOLOGIN) are created in `docker/init/init.sh`, not in Flyway. Passwords belong with the role definition. `GRANT SET ON ROLE audit_writer TO app_user` is also in `init.sh`. Flyway migrations assume these roles pre-exist (they always do — Docker init runs before the app starts; Testcontainers tests connect as superuser).

```
V1__roles_and_schemas.sql        (create audit schema; GRANT USAGE ON SCHEMA audit TO app_user)
V2__users.sql                    (user table in public)
V3__squadrons.sql                (squadron, squadron_assignment with partial unique index,
                                  cross_squadron_grant — all in public)
V4__auth.sql                     (refresh_token in public)
V5__audit.sql                    (audit.audit_entry; GRANT INSERT to audit_writer;
                                  REVOKE UPDATE/DELETE; BEFORE UPDATE OR DELETE trigger)
V6__outbox.sql                   (outbox_message in public, with dispatch index)
V7__platforms_room_stub.sql      (room table in public, ENABLE RLS, tenant_isolation policy)
```

Each migration grants the privileges its own tables need at the point of creation.

### B.5 Spring Modulith Configuration

- Each module has `package-info.java` annotated with `@ApplicationModule` declaring its named interface (the `api` sub-package).
- Cross-module references to `internal` packages fail the Modulith verification test.
- A single `ModulithVerificationTest` in `src/test/java` runs in CI and on local builds.

### B.6 Definition of Done (Slice 1)

- [ ] All seven Flyway migrations apply cleanly to a fresh Postgres.
- [ ] `docker compose up` from a clean checkout brings up app + Postgres, app passes its own health check.
- [ ] All endpoints in B.3 implemented with OpenAPI docs at `/swagger-ui.html`.
- [ ] Bean Validation on request DTOs, errors return RFC 9457 Problem Details.
- [ ] RLS integration test: user in squadron A cannot see squadron B's rooms via `GET /rooms` or `GET /rooms/{id}`.
- [ ] Audit chain integration test: walk all entries, verify each `entry_hash` matches recomputed hash of previous + payload.
- [ ] Audit immutability test: attempt direct UPDATE on `audit_entry` as `audit_writer` role fails.
- [ ] Outbox test: create a user, observe the `user.created` outbox message dispatched, audit entry written.
- [ ] Outbox crash safety test: kill the dispatcher mid-batch (simulated), restart, verify no message lost or double-dispatched.
- [ ] Audit ordering test: produce 100 outbox audit messages concurrently from multiple threads; verify the resulting hash chain is intact and entries are in producer-claim order.
- [ ] JWT auth flow test: login → access protected endpoint → refresh → revoked-token rejection.
- [ ] ArchUnit + Modulith verification test passes.
- [ ] README documents: how to run locally, how to run tests, how to add a new module.
- [ ] At least three ADRs written (Modulith, RLS, outbox).

---

## Part C — What's Next (Pencilled In, Not Spec'd)

After Phase 1, in roughly this order. We'll spec each in detail when we get there:

- **Phase 2: Platforms** — real `Platform`, `PlatformType`, `AuthorizationProfile`, `Tail`. Replace the room stub. Small, builds confidence in the patterns.
- **Phase 3: Courses & Syllabus** — `Course`, `SyllabusEvent`, enrolments. Read-only views including Syllabus Board projection.
- **Phase 4: Scheduling — the big one** — `Event`, state machine, daily program, crew slots, staging/publishing, conflict detection. Touches every cross-cutting concern.
- **Phase 5: Operations Room** — ops-specific transitions and views on top of scheduling.
- **Phase 6: Formations** — formation groups, cross-squadron membership.
- **Phase 7: Grading & Logbooks** — gradesheets, auto-generated logbook entries on sign-back.
- **Phase 8: Currency** — time-based and manual currencies, authorization warnings.
- **Phase 9: Cancellations refinements & Printing** — structured cancellation flow polish, PDF auth sheets via `BlobStore`.
- **Phase 10: Frontend** — separate concern, separate plan.

Each phase ends with: green CI, updated OpenAPI, an ADR or two, demo recording.

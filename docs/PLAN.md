# Sitrep — Development Plan

*Last updated: September 2026. Living document — update as decisions land.*

---

## Context

Sitrep is primarily a **backend-focused portfolio project** — built while targeting backend roles, and the goal is to demonstrate production-grade backend engineering (Modulith boundaries, RLS multi-tenancy, hand-rolled outbox, hash-chained audit ledger, properly-implemented JWT/refresh auth) rather than to ship a sellable product. Any commercial angle is a possible future bonus, not the current driver of scope or sequencing decisions.

A minimal React + TypeScript frontend will be added so recruiters have something tangible to click through — deliberately thin (a plain SPA against the REST API, no heavy tooling), not a product-grade UI. It is a secondary goal — real React/TS reps are wanted regardless, but backend depth takes priority when the two compete for time. The spec (`SitRep_Spec.md`) is the authoritative architecture reference; this plan is the build sequence — phases, deliverables, and open decisions.

All implementation code is written by the developer. Claude's role is design review, explanation, and pointing out idioms and gotchas (backend: review-only; frontend: more actively teach, since it's new territory — see `AGENTS.md`).

---

## Where We Are (September 2026)

Phase 0 is complete. Phase 1 is in progress — `users` module done, `squadrons` module done: `Squadron`, `SquadronAssignment`, `SquadronGuestAssignment` entities; primary-assignment writes (assign/transfer/changeRole/revoke) and guest-assignment writes (assign/changeRole/revoke) each behind their own controller; a `SquadronAccessCoordinator` composes primary + guest access into read-only `GET /squadrons/access` and `GET /squadrons/{id}/access` endpoints; an ArchUnit rule enforces `internal.assignment` never depends on `internal.guestassignment`; `SquadronQueryService` and `UserQueryService` cross-module interfaces wired.

**Current priority order** (chosen for hiring-signal impact — these are the most differentiated pieces of the system, not generic CRUD):
1. `auth` — JWT + refresh token flow (**design decided, not yet implemented** — see below)
2. `outbox` — hand-rolled dispatch loops
3. `audit` — hash-chained ledger
4. RLS proof via the `rooms` stub endpoint

Platforms/courses/scheduling (Phases 2–4) are deferred until Phase 1 is fully complete. First frontend slice: a login screen wired to the real JWT/refresh-cookie flow, once `auth` lands.

### `auth` module — design decided (2026-09-23), implementation not started

Full rationale and endpoint/entity detail is in `SitRep_Spec.md` §A.4.5 and §B.2/B.3 — this is the session-continuity summary.

- **Credential vs. identity split**: `User` (in `users`) stays identity-only — no password field. `auth.internal.credential` owns `Credential` (`userId`, `passwordHash`, `role: USER|ADMIN`) and `ActivationToken` (`userId`, `tokenHash`, `expiresAt`, `consumedAt`). Reasoning: `auth` already owns "prove who you are"; keeping the hash off `User` avoids `users` carrying an auth concern it doesn't otherwise need, and matches the FK-by-UUID (no JPA relationship) pattern already used between `squadrons` and `users`.
- **Email stays single-sourced in `users`**: `auth` resolves `email → userId` via a new `UserQueryService.findUserIdByEmail(String email)` method, rather than duplicating email onto `Credential`.
- **Account creation is two separate calls, not one transaction**: `POST /users` (exists) creates the user with no credential — same precedent as "user exists with no squadron yet." An admin then issues an activation token; no cross-module transaction is needed because there's no invariant broken by the gap (unlike e.g. a financial transfer). `auth → users` is the only allowed dependency direction, so a single atomic call would have to live awkwardly inside `auth` anyway.
- **Activation-token flow stands in for account-takeover protection**: since there's no email server yet, the admin-issue endpoint returns the raw token directly in the response (documented as a stand-in for the eventual email step). The token is hashed at rest like `RefreshToken`, single-use (`consumedAt`), and time-boxed — this is the real security control, not just a UX nicety, because a bare `userId` alone isn't proof of identity.
- **Package layout**: two sub-packages under `auth/internal` — `credential` (password + activation token together, one concept) and `session` (or `login`) owning `RefreshToken`, `LoginController` (`/auth/login`, `/auth/refresh`, `/auth/logout`), JWT issuance via `NimbusJwtEncoder`, and `CurrentUserImpl` (implements `shared.security.CurrentUser` — no `auth/api` package needed, `CurrentUser` in `shared` is the cross-module seam).
- **System role is intentionally narrow**: just `USER` / `ADMIN` on `Credential`, distinct from `SquadronRole` (assignment-scoped, e.g. `PLANNER`/`INSTRUCTOR`). The squadron-level role is what actually filters access within a squadron; the system role only gates squadron/user-management-type actions.
- **⚠️ Spec inconsistency to reconcile later**: `SitRep_Spec.md` §B.3's endpoint table still gates some endpoints with `bearer + PLANNER`, which doesn't fit a `USER`/`ADMIN` model. Once those endpoints are actually built, replace that gate with a `SquadronRole` check instead of a system role. Not fixed now — flagged so it isn't missed.
- **JWT signing key**: shared secret via env var for now, `// TODO` toward real secrets management later (matches how DB credentials are handled via `.env.example`).
- **TTLs**: access token 15m and refresh 30d were already locked; activation-token TTL (24–72h range, industry-standard for invite-style tokens vs. much shorter password-reset windows) is now also configurable in `application.yml` — exact number still to be picked.
- **`SecurityConfig` gets flipped in the same body of work**, not deferred: real `JwtDecoder` bean, `authorizeHttpRequests` matchers (public: `/auth/login`, `/auth/credentials`, `/actuator/health`; `hasRole(...)` per the spec's `bearer + ADMIN` endpoints; `authenticated()` otherwise), and a custom `JwtAuthenticationConverter` mapping the `role` claim to `GrantedAuthority` (Spring's default converter assumes a `scope`/`SCOPE_` claim shape, not this project's). Leaving `permitAll()` in place after `auth` lands would mean login/refresh works but nothing enforces it — treated as a half-finished state, not a smaller PR.

---

## Decisions Locked In

See the spec for full rationale. Summary:

- **Stack**: Java 25, Spring Boot 4.x, Maven (single module), PostgreSQL 17, Spring Modulith 2.x, Spring Data JPA + Hibernate 6, Flyway
- **Web**: Spring MVC, not WebFlux. Virtual threads (`spring.threads.virtual.enabled: true`).
- **Security**: Spring Security 6 + `oauth2-resource-server` for JWT validation, `nimbus-jose-jwt` for signing. Stateless — no sessions.
- **Multi-tenancy**: Postgres RLS only. No Hibernate `@Filter`. See ADR-002.
- **Outbox**: Hand-rolled (~150 lines total). Two dispatch loops: general (`SKIP LOCKED`) and audit (advisory lock, `SET LOCAL ROLE audit_writer`). See ADR-003.
- **State machine**: Enum-driven. Java 21 switch expressions on the entity. `platform.requiresAuthorization()` boolean handles profile variations. See ADR-004.
- **Audit**: Append-only hash-chained ledger. Insert-only via `audit_writer` role. Populated via outbox audit dispatcher. See ADR-007.
- **Auth**: JWT access tokens (HS256, 15 min) + opaque refresh tokens (30 days, HttpOnly cookie, SHA-256 hashed). BCrypt cost 12. See ADR-005.
- **Errors**: RFC 9457 `ProblemDetail`. `422` for Bean Validation failures. Global `@ControllerAdvice`. Add exceptions as each phase needs them.
- **Package root**: `dev.bravozulu.sitrep`
- **No**: Lombok, H2, field injection, Spring Statemachine, Debezium or outbox libraries, JJWT, MapStruct, pgAdmin
- **Container**: Docker multi-stage, `eclipse-temurin:21-jre`, non-root
- **Logging**: SLF4J + Logback + `logstash-logback-encoder` (runtime scope). JSON to stdout via `logback-spring.xml`. Human-readable in `dev` and `test` profiles; `logback-test.xml` in `src/test/resources` for unit tests.
- **Tracing**: `micrometer-tracing-bridge-brave` (add in Phase 1 when MDC filter is wired). Provides `traceId`/`spanId`.
- **Caffeine**: Add in Phase 8 (Currency) when first needed.
- **Add dependencies when first needed** — don't add upfront.
- **Add module `package-info.java` when building that module** — not as an empty skeleton.
- **Add Flyway migrations when the tables are needed** — no upfront schema-only migrations.

---

## Module Dependency Graph

```
outbox       (no deps — infrastructure used by all)
shared       (no deps — library used by all; no @ApplicationModule, no internal/ boundary)
users        (→ shared)
squadrons    (→ users)
auth         (→ users, squadrons)
platforms    (→ squadrons)
courses      (→ users, squadrons)
scheduling   (→ users, squadrons, platforms, courses)
operations   (→ scheduling)
grading      (→ scheduling, courses)
logbooks     (→ scheduling, platforms)
currency     (→ logbooks, users, platforms)
audit        (→ outbox — consumer only; domain modules never call audit directly)
printing     (→ scheduling, users — read-only consumer)
```

Each module has `api/` (exposed to other modules) and `internal/` (Modulith-enforced private). `shared` is marked `@ApplicationModule(type = OPEN)` — all its types are accessible to all modules. Controllers live flat in `internal/` alongside services and entities — no `web/` sub-package.

**Modulith named interface syntax**: `api/` packages must have a `package-info.java` with `@NamedInterface`. `allowedDependencies` must reference them as `"moduleName::api"`, not just `"moduleName"` — the bare module name does not resolve to the `api` sub-package.

---

## The Phases

Each phase ends with: green CI, `./mvnw verify` clean, updated OpenAPI checked in, demoable behaviour where applicable.

---

### Phase 0 — Foundation

**Goal:** production-shaped skeleton that boots, runs in Docker, and has all cross-cutting plumbing wired so subsequent module work is just domain work.

**Done:**
- `pom.xml` — Boot 4.0.6, Java 25, Modulith 2.0.6, all deps and plugins ✓
- `SitrepApplication` — `@Modulithic(systemName = "Sitrep")` ✓
- `shared/domain/BaseEntity` — TIME-style UUID, auditing timestamps, `@Version` ✓
- `shared/config/JpaConfig` — `@EnableJpaAuditing` ✓
- `shared/config/SecurityConfig` — CSRF disabled, `STATELESS`, `permitAll()` placeholder ✓
- `shared/security/CurrentUser` — interface with `userId()`, `currentSquadronId()`, `accessibleSquadronIds()`, `roles()` ✓
- `compose.yaml` — Postgres 17, app service, healthcheck, env var references ✓
- `docker/init/init.sh` — creates `app_user` (LOGIN) and `audit_writer` (NOLOGIN), grants role switch ✓
- `application.yml` — virtual threads, separate Flyway datasource, JPA config, actuator probes, Swagger off by default ✓
- `application-dev.yml` — local datasource defaults, Swagger enabled ✓
- `.env.example` — template for container credentials ✓
- `SitrepApplicationTests` + `TestcontainersConfiguration` + `TestSitrepApplication` ✓
- `ModulithVerificationTest` ✓
- Architecture tests — `InjectionRulesTests`, `IORulesTests`, `LocationRulesTests` ✓
- `README.md` + `CONTRIBUTING.md` ✓
- `BaseEntity.equals()` — fixed to use `instanceof` pattern matching ✓
- `Dockerfile` — multi-stage, Maven build inside Docker, `bellsoft/liberica-openjre-debian:25-cds`, non-root, AOT cache training run (`-XX:AOTCacheOutput`/`-XX:AOTCache`) ✓
- `.dockerignore` — keeps build context small and IDE/docs/secrets out of image builds ✓

**Complete.** ✓

**What this teaches:** Spring Boot 4 project layout, Spring Modulith bootstrap, Testcontainers wiring, RLS role separation, stateless JWT security baseline, twelve-factor config.

---

### Phase 1 — Users, Squadrons, Auth, Audit, Outbox

*All the plumbing every later phase depends on.*

**Goal:** authenticated requests, multi-tenancy proven via rooms RLS demo, hash-chained audit trail, outbox dispatching.

**Domain:** `User`, `Squadron`, `SquadronAssignment`, `SquadronGuestAssignment`, `RefreshToken`, `AuditEntry`, `OutboxMessage`, `Room` (RLS stub)

**Endpoints:** per spec §B.3

**Migrations:** per spec §B.4. Schema decision revised — only `audit` schema kept; all domain tables in `public`. Role creation in `docker/init/init.sh`; V1 creates audit schema and grants only.

**Key deliverables:**
- `users`, `squadrons`, `auth` modules — CRUD and full auth flow
- `CurrentUserImpl` — request-scoped, reads `SecurityContextHolder`
- JWT issuance via `NimbusJwtEncoder`; validation via `oauth2-resource-server`
- Refresh token rotation; revocation on logout
- `HandlerInterceptor` — `SET LOCAL app.accessible_squadrons` per request
- `outbox` module — hand-rolled general + audit dispatchers
- `audit` module — hash chain, `audit_writer` role enforcement, immutability trigger
- MDC filter — `userId`, `squadronId`, `requestId`, `traceId`
- Add `micrometer-tracing-bridge-brave` and `logstash-logback-encoder` to pom when wiring MDC
- RLS policy on `room`; `GET /api/v1/rooms` proves isolation

**Tests:** per spec §B.6 definition of done

**What this teaches:** Spring Security 6 filter chain, JWT via Spring native primitives, refresh token rotation, PostgreSQL RLS, hash-chained ledger, hand-rolled outbox, advisory locks.

---

### Phase 2 — Platforms

Real `Platform`, `PlatformType`, `AuthorizationProfile` (with `requiresAuthorization` boolean), `Tail`. Replaces room stub.

---

### Phase 3 — Courses & Syllabus

`Course`, `SyllabusEvent`, `Enrolment`, `SyllabusProgress`. Syllabus board projection.

---

### Phase 4 — Scheduling

`Event` with Java 21 switch-expression state machine, `Formation`, `CrewSlot`, `DailyProgram`. Staging, publishing, conflict detection, cancellations.

---

### Phase 5 — Operations Room

Ops-room workflow on top of scheduling. No new entities — commands against `scheduling`.

---

### Phase 6 — Formations

Formation groups, cross-squadron membership, formation-level state transitions.

---

### Phase 7 — Grading & Logbooks

`Gradesheet` on sign-back. Auto-generated `LogbookEntry` from outbox event.

---

### Phase 8 — Currency

`CurrencyDefinition`, `ManualCurrencyRecord`. Reads logbooks; warnings to scheduling. Add Caffeine here.

---

### Phase 9 — Printing

PDF auth sheets via `BlobStore`. Filesystem default; S3/MinIO for cloud.

---

## Continuous (not phase-bound)

- **ADRs** — write when decisions land. First seven in spec §A.7.
- **Architecture tests** — each phase adds at least one ArchUnit rule for its module boundary
- **OpenAPI** — checked in after every phase; CI fails on breaking changes
- **Security review** — every phase touching auth or data access gets a focused pass

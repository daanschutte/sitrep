# Phase 0 Plan Review

## Context

This is a review of the PLAN.md and SitRep_Spec.md against the current codebase state on `daanschutte/configure-application`. The goal is to surface gaps, gotchas, and design questions before implementation of the remaining Phase 0 deliverables begins. Per CLAUDE.md, Claude does not write implementation code — this review is the artifact.

---

## Current State vs Phase 0 Checklist

**Done:**
- `pom.xml` — Boot 4.0.6, Java 21, Modulith 2.0.6, plugins (Spotless, JaCoCo, Flyway Maven) ✓
- `SitrepApplication` — `@Modulithic(systemName = "Sitrep")` + `@SpringBootApplication` ✓
- `shared/domain/BaseEntity` — TIME-style UUID, auditing fields, `@Version` ✓
- `shared/config/JpaConfig` — `@EnableJpaAuditing` ✓
- `compose.yaml` — Postgres service (partial — see issues below) ✓
- `application.properties` — virtual threads, datasource basics ✓
- `SitrepApplicationTests` + `TestcontainersConfiguration` — context loads smoke test ✓

**Not yet built:**
- Module package skeleton (`package-info.java` with `@ApplicationModule` per module)
- `shared/error/` exception hierarchy (`SitrepException`, `NotFoundException`, etc.)
- `shared/web/GlobalExceptionHandler` (`@ControllerAdvice`, RFC 9457, 422 for validation)
- `shared/security/` `CurrentUser` interface + placeholder permit-all filter chain
- Migrate `application.properties` → `application.yml` + `application-{dev,test}.yml`
- Flyway `V1__roles_and_schemas.sql`
- `logback-spring.xml` (JSON + dev human-readable profile)
- SpringDoc / Swagger UI (non-prod only)
- Actuator liveness/readiness/prometheus config
- Dockerfile (multi-stage, eclipse-temurin:21-jre, non-root)
- GitHub Actions CI
- README.md
- `ModulithVerificationTest`
- `ArchBaseTest`

---

## Issues & Gotchas to Work Through

### 1. Missing pom.xml dependencies

Two things the spec requires that aren't in the pom yet:

- **`logstash-logback-encoder`** (`net.logstash.logback:logstash-logback-encoder`) — without this the `logback-spring.xml` JSON encoder won't work. Add as a compile dependency.
- **Micrometer Tracing + Brave** — `spring-boot-starter-actuator` brings Micrometer metrics but NOT tracing. You need `micrometer-tracing-bridge-brave` (runtime) to get `traceId`/`spanId` in logs and MDC. The `spring-modulith-observability` dependency is already there but it wraps Micrometer Tracing — it doesn't pull in a concrete bridge.

### 2. `postgres:latest` in compose.yaml and TestcontainersConfiguration

The spec says Postgres 17. `latest` is non-reproducible — a future pull could silently change behaviour or break migrations. Pin both to `postgres:17` now, before migrations exist, so everything is consistent from the start.

### 3. Package root inconsistency between spec and code

Spec §A.3 describes modules as `com.sitrep.<module>`. The codebase uses `com.camelbytes.sitrep`. The "Decisions Locked In" section of PLAN.md does say `com.camelbytes.sitrep` — but the spec's module dependency graph (`com.sitrep.*`) is a latent confusion. Worth aligning the spec or at minimum making the decision explicit in an ADR.

### 4. `BaseEntity.equals()` and JPA proxy behaviour

The current implementation:

```java
if (o == null || getClass() != o.getClass()) return false;
```

`getClass()` breaks with Hibernate lazy-loading proxies — Hibernate creates a subclass of your entity at runtime. When you compare a proxy to its real instance (which happens when you navigate a `@ManyToOne` and then fetch the real entity), `getClass()` returns different types even though they represent the same row.

The idiomatic fix for JPA entities is `instanceof` with the concrete class — but since `BaseEntity` is abstract, a pattern like:

```java
if (!(o instanceof BaseEntity other)) return false;
```

then `id != null && id.equals(other.id)` would work. However, since `id` is the discriminator, an even simpler approach many teams use is to just not override `equals`/`hashCode` on the base class and let each entity decide — or use the `id != null` identity pattern at the concrete level.

The existing `id != null && id.equals(other.id)` comparison at the bottom is the right approach; the `getClass()` guard at the top is the gotcha. Think through what behaviour you want for proxy comparisons.

### 5. Security auto-configuration is active right now

`spring-boot-starter-security` is on the classpath, which means Spring Security's auto-configuration is already running. The current context-loads test passes only because there are no protected endpoints yet. The moment you add any controller, requests will be blocked by default (form login / HTTP Basic). The placeholder filter chain (permit all `/api/v1/**`) needs to be in place before the first endpoint lands, not after.

Design question: what should the placeholder permit? `anyRequest().permitAll()` is the cleanest for Phase 0 — it makes the intent explicit and avoids having to remember to update the path list as endpoints arrive in Phase 1.

### 6. V1 migration and the runtime role question

The spec calls for `app_user` and `audit_writer` roles. But `compose.yaml` connects the app as `dev` (a superuser). For RLS to actually enforce tenant isolation, the app must connect as a non-BYPASSRLS role.

Two approaches for local dev:
- **Grant `dev` NOBYPASSRLS** — simplest for dev, but `dev` is a superuser and superusers bypass RLS by default. You'd need to `ALTER USER dev BYPASSRLS` to reset that, or create `dev` without superuser.
- **Have the app connect as `app_user`** — the V1 migration creates `app_user`, then the datasource URL in dev uses `app_user` credentials. This means Flyway (which runs migrations) still needs to run as a privileged user (`dev`), but the app runtime connects differently. Spring Boot supports separate Flyway datasource config (`spring.flyway.url`, `spring.flyway.user`) for exactly this reason.

This is worth designing before V1 migration is written — it affects both `compose.yaml` and `application.yml`.

### 7. Actuator probes need explicit config

`/actuator/health/liveness` and `/actuator/health/readiness` aren't exposed by default. You'll need:

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
```

in `application.yml`. Easy to miss and the endpoints will silently 404 without it.

### 8. Caffeine is in pom.xml — why?

Caffeine (the in-memory cache library) is in the pom as a compile dependency but isn't mentioned in the Phase 0 or Phase 1 spec. It appears first in Phase 8 (Currency — cache invalidation). Adding it now isn't wrong, but it goes against the spec's "add when first needed" principle and adds a dependency with no wiring. Consider removing it from the foundation pom and adding it in the phase that actually uses it.

### 9. SpringDoc compatibility with Spring Boot 4

`springdoc-openapi-starter-webmvc-ui` at version `3.0.2` — springdoc-openapi 3.x targets Spring Boot 3.x. Spring Boot 4's auto-configuration may have changed enough that this version breaks. The springdoc project typically releases a compatible version for each major Boot release. Worth checking the springdoc release notes for Boot 4 compatibility before spending time wiring it up.

---

## Design Questions Worth Thinking Through

1. **Flyway migration user vs runtime user** — see issue #6 above. What does the datasource config look like per environment?

2. **`ModulithVerificationTest` in CI** — this test calls `ApplicationModules.of(SitrepApplication.class).verify()`. It will fail until every referenced module (users, squadrons, auth, etc.) has at least a `package-info.java`. The package skeleton and the verification test need to land together in the same commit, or the test needs to be added after the skeleton.

3. **ArchUnit rules in `ArchBaseTest`** — the plan lists: no field injection, controllers in `internal/web/`, `@Service` on services, no `System.out`. Think about what "no field injection" means precisely: does it ban `@Autowired` on fields, or also `@Value`? `@Value` on fields is idiomatic for simple config binding; field `@Autowired` is the one to ban.

4. **`shared/security/CurrentUser` interface design** — what does it expose? Likely `UUID userId()`, `UUID currentSquadronId()`, `List<UUID> accessibleSquadrons()`, `Set<String> roles()`. Designing the interface now (even though the impl lands in Phase 1) prevents Phase 1 from needing to refactor code that already uses it.

---

## AI-Readiness: Fitted-For-Not-With

No AI integration now — this is a closed system. The note here is purely: don't make decisions in Phase 0–4 that turn AI into a major refactor later.

**Four things to preserve:**

1. **Keep `api/` read models flat and query-friendly.** Modulith already enforces that a future `ai` module can only call other modules' `api/` packages. If those packages expose clean DTOs rather than graph-walk objects, a future "who can slot in?" tool is a thin service call, not a JPA detective exercise. Design `api/` projections with readability in mind from the start.

2. **Keep `CurrentUser` injectable as an interface.** The normal implementation is request-scoped (JWT-parsed per request). A future AI chat handler running outside a normal HTTP request will need a synthetic `CurrentUser` constructed from the chat payload. This only works cleanly if nothing in the domain hardcodes `CurrentUserImpl` — coding to the interface already achieves this.

3. **Don't block SSE paths in the security filter chain.** The Phase 0 placeholder filter chain doesn't need SSE-specific config yet, but avoid adding `Content-Type` allow-lists or response header stripping that would silently break a streaming endpoint later. Leave the transport layer open.

4. **Don't shortcut the outbox for in-process-only events.** The outbox + event structure means a future `ai` module could subscribe to domain events without touching existing modules. The spec already mandates this pattern — just don't drift from it in later phases by emitting Spring `ApplicationEvent`s directly for convenience.

---

## Verification (Phase 0 done when...)

- `./mvnw verify` passes: Spotless check, compile, unit tests, integration test (context loads + Flyway V1 applies cleanly)
- `ModulithVerificationTest` green with the empty module skeleton
- `ArchBaseTest` green
- `docker compose up` → app starts → `/actuator/health` returns UP
- `/actuator/health/liveness` and `/actuator/health/readiness` return 200
- Swagger UI visible at `/swagger-ui.html` in dev profile
- JSON log lines in container stdout
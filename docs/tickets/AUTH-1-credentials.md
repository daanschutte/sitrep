# AUTH-1 — Credentials & activation tokens

**Status:** Done (2026-10-05) · **Branch:** `daanschutte/auth` · **Phase:** 1 (`auth`, step 1 of 3)

## Goal

An admin can issue a single-use activation token for an existing user; the token holder redeems it with a chosen password, creating the user's `Credential`. This is the prerequisite for login (AUTH-2) and the bootstrap admin (AUTH-3).

Design background: `PLAN.md` → "`auth` module", `SitRep_Spec.md` §A.4.5, §B.2.

## Scope

### `users` module
- `UserQueryService.findActiveUserIdByEmail(String email): Optional<UUID>` — empty for unknown **and** deactivated users (callers cannot tell them apart). Not used by this ticket's endpoints; lands now so AUTH-2 only touches `auth`.
- **Email normalisation**: emails are trimmed and lower-cased on create and on lookup, so `Chuck@Boom.com` and `chuck@boom.com` are the same account.

### `auth` module
- `auth/package-info.java`: `allowedDependencies = {"users::api", "shared"}`.
- `V05__auth.sql`: `credential` and `activation_token` tables (no `refresh_token` — that lands with AUTH-2, per "add migrations when the tables are needed").
- Entities in `auth.internal.credential`:
  - `Credential` — `userId` (unique, FK `users(id)`), `passwordHash` (`TEXT`), `role` (`SystemRole`: `USER` | `ADMIN`).
  - `ActivationToken` — `userId` (FK), `tokenHash` (`CHAR(64)`, unique), `role`, `expiresAt`, `consumedAt`, `revokedAt`.
- Token utility (`SecureRandom` 32 bytes → base64url raw token; SHA-256 hex hash) — reused by refresh tokens in AUTH-2.
- `PasswordEncoder` bean (delegating encoder, BCrypt cost 12) in `auth`'s own config.
- `Clock` bean; time-dependent logic uses it, never `Instant.now()` directly.
- Activation-token TTL configurable: `sitrep.auth.activation-token-ttl` (default `48h`), bound via a `@ConfigurationProperties` record.

### `Credential` lifecycle

Long-lived: one row per user, created once by redeeming an activation token, no expiry. Unlike the tokens (activation 48h, access 15m, refresh 30d), it lives as long as the account.

- **Deactivation does not touch it** — `User.isActive` gates login; the credential is kept so reactivation doesn't need a new activation token.
- **Mutations are in-place, all future tickets**: password change (should also revoke refresh tokens), role promote/demote, and transparent re-hashing on login when `PasswordEncoder.upgradeEncoding` reports an outdated `{id}`. This ticket only creates.
- **Known simplification**: `role` is an account-level authorisation, stored on `Credential` because there is exactly one password credential per user. If SSO/passkeys ever add multiple credentials per user, `role` moves to an account-level record. Note in the auth ADR.

### Endpoints

| Method | Path | Request | Success |
|---|---|---|---|
| `POST` | `/api/v1/auth/activation-tokens` | `{ userId, role }` | `201` `{ token, expiresAt }` |
| `POST` | `/api/v1/auth/credentials` | `{ token, password }` | `201` (no body) |

`activation-tokens` is ADMIN-only per spec, but `SecurityConfig` is still `permitAll()` until AUTH-2 flips it — **known, accepted gap** for this ticket only.

## Design decisions (confirmed 2026-10-05)

| # | Question | Decision | Why |
|---|---|---|---|
| D1 | Invalid token on `/credentials` (unknown, expired, consumed, revoked) | One generic `400` ProblemDetail, "Invalid or expired activation token" | Unauthenticated endpoint; distinct errors would tell a token-guesser which tokens exist. |
| D2 | Valid token, but user has since been deactivated | Same generic `400` as D1; token **revoked** | Every failure on this unauthenticated endpoint looks identical from outside. Needs a non-throwing active check (`auth` cannot catch `users.internal` exceptions across the module boundary). Revoking matters: if the user were reactivated within the TTL, an unrevoked token — possibly the leaked one that prompted the deactivation — would work again. |
| D3 | Valid token, but a `Credential` already exists for the user | Same generic `400` as D1; token **revoked** | Not the "clicked the link again" case — that token is already consumed, so it is D1. D3 is only reachable via a race (concurrent redemptions, or a token issued while another was mid-redemption); the `UNIQUE` on `credential.user_id` is the backstop. The token can never succeed, so keeping it alive has no upside. |
| D4 | Issuing a token when the user has older unconsumed tokens | Older ones get `revokedAt = now` | Only one live token per user; matches the `revoked_at` pattern used by squadron assignments. |
| D5 | Issuing a token for an unknown / deactivated user | `validateActiveUserExists` → `404` / `409` | Admin-facing; distinct errors are useful there. |
| D6 | Issuing a token for a user who already has a `Credential` | `409` | Prevents an activation token being used as a password-reset backdoor. Password reset is a separate future flow with a shorter TTL. |
| D8 | Any failed redemption of an **existing** token (D2, D3) | Token is revoked; unknown/expired/consumed/revoked tokens need no action | A token that failed for a state reason never becomes valid again. Implementation: revocation commits independently of the failed redemption (result-returning service, or a separate-bean `REQUIRES_NEW` / `TransactionTemplate` — not a same-bean `@Transactional` call, which bypasses the proxy). |
| D7 | Password rules | min 12 chars, max 72 **UTF-8 bytes**, no composition rules → `422` | NIST SP 800-63B; BCrypt only uses the first 72 bytes and Spring Security rejects longer input. |

## Acceptance criteria

- [x] Issue → redeem happy path creates a `Credential` with the token's role and a `{bcrypt}`-prefixed hash; the token is marked consumed.
- [x] Only the SHA-256 hash of a token is persisted; the raw token appears only in the issue response.
- [x] Redeeming the same token twice → second call `400` (D1).
- [x] Expired token → `400` (D1), verified with a controlled `Clock`, not `sleep`.
- [x] Issuing a second token revokes the first; redeeming the first → `400` (D4).
- [x] D2 and D3 → generic `400` **and the token is revoked** — the revocation must survive the failed request (it cannot be rolled back with the rest of the transaction, including when the `UNIQUE` violation marks it rollback-only); D5, D6 behave as specified.
- [x] Concurrent redemption cannot create two credentials for one user (DB constraint backstop).
- [x] Every redemption failure logs its specific reason (`unknown` / `expired` / `consumed` / `revoked` / `user_inactive` / `credential_exists`) with token id and user id where known — never the raw token. The response stays generic; the message covers both remedies ("already set your password → log in; otherwise ask your administrator"). `requestId` in the ProblemDetail follows when the MDC filter lands.
- [x] Password too short / over 72 bytes (incl. a multi-byte case) → `422`.
- [x] `findActiveUserIdByEmail` returns empty for unknown and deactivated users, matches case-insensitively.
- [x] Creating a user with a differently-cased duplicate email → `409`.
- [x] Modulith verification passes; `auth` depends only on `users::api` and `shared`.
- [x] `./mvnw verify` green. `http/auth.http` added for manual testing.

## Review outcome

- `/code-review`: 4 findings. Fixed — email normalisation enforced in the DB (`CHECK (email = lower(btrim(email)))` on `users`, so raw-SQL inserts such as the AUTH-3 seed can't bypass it); concurrent `issue` now maps optimistic-lock / index conflicts to `409`; a token revoked mid-redemption maps to the generic `400` (`REVOKED`). Dismissed — editing applied migration `V02` (accepted: dev-only DB, rebuilt).
- `/security-review`: no findings above threshold. **Carry-over to AUTH-2**: `POST /auth/activation-tokens` is unauthenticated and accepts a caller-chosen `ADMIN` role — must be `hasRole(ADMIN)` before login ships, and any `ADMIN` credentials created before then must be audited.
- Implementation notes vs plan: Boot's auto-configured `TransactionTemplate` is used (no bean of our own); `IssuedActivationToken` doubles as the response body; request/response records redact secrets in `toString()`.

## Out of scope

Login / refresh / logout, JWT, `RefreshToken`, flipping `SecurityConfig` (AUTH-2) · bootstrap admin (AUTH-3) · last-admin guard, password reset, breached-password checks, brute-force protection (TODOs in `PLAN.md`).

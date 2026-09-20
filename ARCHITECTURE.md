# Technical architecture — odentix-backend

> Living architecture and decision document for the backend SaaS for dental
> clinics (multi-tenant: each clinic is a `tenant`).
> **Status:** documents Phase 0 through Phase 11 and Phase 12 in progress. When closing each phase this file must
> be updated (rule in `AGENTS.md` §11: without that update the phase is not
> considered closed).
>
> Normative sources: `docs/documentacion_sistema_gestion_odontologica_v1.1.md`
> (product), `docs/roadmap_backend_fases.md` (tickets and DoD),
> `docs/schema.sql` (schema reference). Everything stated here comes from the
> real code and migrations, not from design documents.

---

## 1. Overview

Modular monolith in Java: a single deployable, organized by business
module (not by technical layer). No microservices, queues, or Kubernetes by
explicit decision: no infrastructure is introduced before scale
justifies it. The Angular frontend is a separate project; there is no frozen
API contract yet.

### Stack

| Piece | Version / decision |
|---|---|
| Java | 25 (LTS; never non-LTS versions) |
| Spring Boot | 4.1.1 (see §2: reorganized autoconfiguration packages) |
| Build | Maven (wrapper `./mvnw` included) |
| Database | PostgreSQL 16+ (never H2 nor embedded, not even in tests) |
| Migrations | Flyway, `ddl-auto` always `validate` |
| Auth | Spring Security + own JWT (JJWT 0.12.6, HMAC-SHA256) |
| Integration tests | Testcontainers 2.x on `postgres:16` |
| Deployment | Multi-stage Docker + Render; CI on GitHub Actions |

### Global conventions

- No Lombok: explicit getters/setters/constructors (FASE0-01 decision
  while learning the framework; check `pom.xml` before assuming otherwise).
- Code and comments in Spanish, consistent with the file being edited.
- `snake_case` in DB, plural tables, UUID as PK (`gen_random_uuid()`),
  `TIMESTAMPTZ` (never `TIMESTAMP`), amounts `NUMERIC(12,2)` with `_cop` suffix,
  `updated_at` managed by the `set_updated_at()` trigger (never from Java).

---

## 2. Note on Spring Boot 4.1.1

In this version the autoconfiguration packages were reorganized compared to
3.x, and almost every tutorial/Stack Overflow answer uses the old packages.
**Before writing any autoconfiguration import, verify the real package**
(packages already confirmed in this project):

- `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`
- `org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration`
- `org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration`

The same applies to neighboring dependencies: Testcontainers 2.x renamed its
artifacts with a prefix (`testcontainers-postgresql`,
`testcontainers-junit-jupiter`; the Java packages `org.testcontainers.*` did
not change). The BOM version is pinned by the parent (`testcontainers.version`).

---

## 3. Package structure

FASE0-03 decision: organization **by business module** (familiar to anyone
coming from modular Angular), each with its internal sub-layers.

```
com.julio.odentix.odentix_backend/
├── shared/      # cross-cutting: entity/, context/, common exceptions
│   ├── entity/  # TenantAwareEntity (base of everything business-related)
│   └── context/ # TenantContext, TenantIdentifierResolver
├── tenant/      # client clinic (root of multi-tenancy)
├── auth/        # User, UserRole, JWT login, filter, UserService
├── audit/       # AuditLog, AuditAction, AuditService (append-only)
├── patient/     # Phase 2
├── appointment/ # Phase 3
├── treatmentplan/ # Phase 4
├── billing/     # Phase 4
├── crm/         # Phase 5
└── inventory/   # Phase 7
```

Rules:

1. Each module has its sub-layers (`controller`, `service`, `repository`,
   `entity`, `dto`) **inside** its package. There are no top-level packages
   by layer. New module = same pattern, no permission needed.
2. `shared` is the only thing importable from any module. Dependencies between
   business modules point toward `tenant`/`patient`, never the other way
   without justification.
3. Changing this pattern requires explicit discussion.

---

## 4. Per-environment configuration

`dev` / `test` / `prod` profiles (base `application.yml` + one per profile).
Secrets only via environment variables with `${VAR:default}` syntax; the
repo only holds local defaults with no production value.

| Variable | Local default (dev/test) | Prod |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5434/odentix` | no default (mandatory) |
| `DB_USERNAME` / `DB_PASSWORD` | `odentix` / `odentix` | no default (mandatory) |
| `PORT` | `8081` fixed locally | `${PORT:8080}` (Render injects `$PORT`) |
| `JWT_SECRET` | development key in `application.yml` | **mandatory**: real `JWT_SECRET` (≥32 characters) |
| `JWT_EXPIRATION_MINUTES` | `15` (15 min) | `15` recommended; adjustable |
| `JWT_REFRESH_TOKEN_EXPIRATION_DAYS` | `7` (7 days) | `7` recommended; adjustable |

Dev-machine particularities (do not generalize):

- App on `8081` because `8080` is taken by `AgentService.exe`.
- Docker Postgres on `5434` because ports `5432` and `5433` (IPv4) are taken
  by native `postgresql-x64-17/18` services.

Commands:

```bash
docker compose up -d                                   # local Postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev  # app on :8081
./mvnw test                                            # integration (Testcontainers)
./mvnw clean verify                                    # what CI runs
```

> After deleting or renaming resources (`application.properties` → YAML, etc.),
> run with `clean`: incremental builds don't remove stale outputs from
> `target/` and an old file may stay active on the classpath.

---

## 5. Database and migrations

- `ddl-auto: validate` in every profile, no exceptions.
- Every schema change goes in a new Flyway migration (`V{n}__...sql`);
  an applied migration is never edited, nor is `schema.sql` touched expecting
  it to apply on its own.
- Before creating a table, check its reference in `docs/schema.sql`.

| Migration | Contents |
|---|---|
| `V1__init` | `set_updated_at()` function + temporary `migration_probe` table (verify the mechanism; removed in Phase 1) |
| `V2__create_tenants` | `pgcrypto`/`citext` extensions, `current_tenant_id()` function, `tenant_status` type, `tenants` table, index, `updated_at` trigger, RLS (`tenant_self_isolation`) |
| `V3__create_users` | `user_role` type, `users` table (`CITEXT` email, unique `(tenant_id, email)`), `(tenant_id, role)` index, trigger, RLS |
| `V4__create_audit_log` | `audit_action` type, `audit_log` table, indexes, trigger, RLS (see §8 for 2 documented deviations) |
| `V5__create_patients` | `pg_trgm` extension, `immutable_unaccent()` function, `patients` table, trgm index for insensitive search, trigger, RLS |
| `V6__create_clinical_records` | `clinical_records` table with `patient_id`, `recorded_by_id` FKs, trigger, RLS |
| `V7__create_odontogram_entries` | `odontogram_entry_type` type, `odontogram_entries` table, FDI-notation `CHECK` constraint, trigger, RLS |
| `V8__create_patient_files` | `storage_provider` type, `patient_files` table (S3 metadata), trigger, RLS |
| `V9__test_patient_file_data` | Test migration (only applied in the `test` profile via `V999`) |
| `V10__create_refresh_tokens` | `refresh_tokens` table (SHA-256 hash of the token, `expires_at`, `revoked`, `revoked_at`, `tenant_id`/`user_id` FKs), indexes, trigger, RLS (allows `current_tenant_id() IS NULL` because refresh happens before `TenantContext` exists) |

RLS pattern on every business table (defense in depth, §6):

```sql
ALTER TABLE <table> ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON <table>
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
```

The production DB role must not have `BYPASSRLS`. RLS and the tenant
filter are never disabled "to test faster": if something is hard to test
with isolation, the problem is in the test.

---

## 6. Multi-tenancy

The worst possible bug is leaking data across clinics. Three independent
layers, none replacing another:

1. **Automatic application filter (FASE1-09).** `TenantAwareEntity`
   (`shared/entity`) provides UUID `id`, `tenant_id` (Hibernate `@TenantId`,
   non-null and immutable), `createdAt/updatedAt`. Hibernate filters and assigns
   the tenant automatically in queries and in `findById`, resolving it via
   `TenantIdentifierResolver` → `TenantContext`.
2. **Repository convention (defense in depth).** Every query filters
   by tenant even though the automatic filter exists
   (`findByTenantId...`, never JPQL/native without `tenant_id`).
3. **RLS in PostgreSQL** (§5 pattern).

### `TenantContext` (FASE1-08)

Static `ThreadLocal<UUID>` (`setTenantId` / `getTenantId` /
`getRequiredTenantId` / `clear`). Populated by the JWT filter at the start of
each request and cleaned in `finally` (Tomcat threads are reused; without
cleanup there would be leakage across requests). Without a tenant (startup,
system tasks), `TenantIdentifierResolver` uses the nil-UUID as root session.

### `tenant_id` convention (FASE1-04)

Every new business entity inherits `TenantAwareEntity` from its first
migration. Per-table checklist: `tenant_id UUID NOT NULL REFERENCES
tenants(id)` column + index + RLS + inheriting entity (+ association to `Tenant`
with `insertable/updatable = false` if it needs to navigate) + filtered repo +
**mandatory cross-tenant test** (data in A, act as B, verify invisibility;
`404` before `403` to not even confirm existence).

Intentional exceptions: `tenants` (it is the root), `users` (tenant via
association since FASE1-02) and global catalogs (`plans`, `plan_features`,
`plan_limits` in Phase 11).

---

## 7. Authentication and authorization (Phase 1 + pre-Phase 3 improvements)

### Model

- `Tenant`: id, `name`, `tax_id` (NIT), `status` (`trial`/`active`/`suspended`/
  `cancelled`), `timezone` (`America/Bogota`).
- `User`: belongs to a single `Tenant` (mandatory `@ManyToOne`), `email`
  (`CITEXT`, unique **per tenant**, not global), `password_hash` (BCrypt, never
  plain text), `fullName`, `role`, `is_active`, `lastLoginAt`.
- `UserRole` (lowercase enum, mirror of the PG `user_role` type):
  `propietario`, `odontologo`, `recepcion`, `auxiliar`,
  `especialista_externo`. One role per user in this phase.
- `UserService.createUser(...)` (internal, no public registration): validates
  data, requires an existing tenant, rejects duplicate email per tenant, hashes
  with `BCryptPasswordEncoder` and saves as active.
- `RefreshToken` (V10): persisted session entity. Stores `token_hash`
  (SHA-256 of the plain-text token, never the raw token), `expires_at`,
  `revoked`, `revoked_at`, FKs to `tenant_id` and `user_id`. Does not extend
  `TenantAwareEntity` (like `User`): refresh operations happen
  before `TenantContext` exists. Its RLS allows
  `current_tenant_id() IS NULL` for the same reason.

### Login — `POST /api/v1/auth/login` (public)

```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@clinica.com","password":"Secreta123"}'
# 200 → {"accessToken":"...","refreshToken":"...","tokenType":"Bearer","expiresInSeconds":900,
#        "user":{"email":"...","role":"propietario","tenantId":"..."}}
# Invalid credentials → 401 {"error":"Credenciales inválidas"} (deliberately generic:
# it doesn't distinguish unknown user from wrong password, anti-enumeration)
```

Optional `tenantId` in the request: only used to disambiguate if the same
email existed in several clinics. Inactive user → generic 401.

**Brute-force and DoS protection (in-memory rate limiting):**
- **Per-IP rate limiting:** max 10 requests/minute per IP to `/api/v1/auth/login` (configurable via `LOGIN_RATE_LIMIT_IP_MAX`). Supports proxies (`X-Forwarded-For` or `getRemoteAddr`). When exceeded → `429 Too Many Requests` with `Retry-After: <seconds>` header before running BCrypt or querying the database.
- **Temporary per-account/email lockout:** max 5 consecutive failed attempts in a 15-minute window (configurable via `LOGIN_RATE_LIMIT_EMAIL_MAX_ATTEMPTS`). On the 5th failure, the account is locked for 15 minutes (`LOGIN_RATE_LIMIT_LOCKOUT_MINUTES`). Attempts during the lockout get `429 Too Many Requests` with a `Retry-After` header without spending CPU on BCrypt. A successful login resets the failure counter.
- `LoginRateLimitService` automatically cleans expired entries every 5 minutes (`@Scheduled`).

### Session renewal — `POST /api/v1/auth/refresh` (public)

Atomically rotates the refresh token: invalidates the previous token and issues
a new pair (access token + refresh token). Reuse detection: renewing
with an already-revoked token → 401. Expired token → 401.
User inactive at refresh time → 401.

```bash
curl -X POST http://localhost:8081/api/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<7-day-token>"}'
# 200 → {"accessToken":"...","refreshToken":"...","tokenType":"Bearer","expiresInSeconds":900}
# Revoked/expired/invalid token → 401 {"error":"Refresh token revocado|expirado|inválido"}
```

### Logout — `POST /api/v1/auth/logout` (public)

Revokes the refresh token. The access token (15 min) stays valid until
its natural expiration, but without a refresh token the session cannot be renewed.

```bash
curl -X POST http://localhost:8081/api/v1/auth/logout \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<token-to-revoke>"}'
# 204 No Content
```

### JWT

- HMAC-SHA256 (`JwtService`, JJWT), **15-min access token** (configurable
  with `JWT_EXPIRATION_MINUTES`; default in dev/test `15`).
- **7-day refresh token** (configurable with `JWT_REFRESH_TOKEN_EXPIRATION_DAYS`).
  Only the SHA-256 hash is stored in DB; the plain-text token lives
  exclusively on the client and in HTTPS transit.
- JWT claims: `sub` = user_id, `tenant_id`, `email`, `role`.
- `JwtAuthenticationFilter` (`OncePerRequestFilter`): validates signature and
  validity of the JWT, distinguishing valid, expired, and invalid/tampered
  tokens via `JwtValidationResult`. Then it **verifies in real time** in DB
  that the user exists in its tenant and is active (`user.isActive()`). If
  not → clean `SecurityContextHolder` → instant 401 even with a valid JWT.
  The role is synced from DB on every request (not from the token claim), so a
  role change takes effect immediately without revoking the JWT.
- **Granular error distinction in `JwtAuthenticationEntryPoint` (RFC 6750):**
  To help frontend UX auto-refresh sessions, unauthenticated requests
  receive the standard `WWW-Authenticate` header and a JSON payload
  with a specific error code without breaking compatibility (`error: "No autorizado"`):
  - `token_expired`: HTTP 401, `code: "token_expired"`, header `WWW-Authenticate: Bearer error="invalid_token", error_description="The access token expired"`. Tells the client to call `POST /api/v1/auth/refresh`.
  - `token_invalid`: HTTP 401, `code: "token_invalid"`, header `WWW-Authenticate: Bearer error="invalid_token", error_description="The access token is invalid or tampered"`. Indicates a manipulated or corrupt token (forces logout).
  - `user_inactive`: HTTP 401, `code: "user_inactive"`, header `WWW-Authenticate: Bearer error="invalid_token", error_description="User is inactive or not found"`.
  - `token_missing`: HTTP 401, `code: "token_missing"`, header `WWW-Authenticate: Bearer error="unauthorized"`.

### Access rules (`SecurityConfig`, stateless, no sessions, CSRF off in API)

- Public: `/actuator/health`, `/actuator/info`, `/api/v1/auth/login`,
  `/api/v1/auth/refresh`, `/api/v1/auth/logout`.
- Everything else requires authentication; `@EnableMethodSecurity` allows
  `@PreAuthorize("hasRole('PROPIETARIO')")` (uppercase role = uppercase enum;
  lowercase `UserRole` is mapped with `toUpperCase()`).
- Tested errors: no token → 401 `{"error":"No autorizado"}`; insufficient
  role → 403 `{"error":"Acceso denegado","message":"..."}`.
- User deactivated in DB → instant 401 even with a valid JWT.

### `GET /api/v1/me` (protected)

Returns the token user's profile (`@AuthenticationPrincipal`):
no token → 401; valid token → 200 with its data; deactivated user → 401.

---

## 8. Auditing

`audit_log` table (append-only: written and read filtered by tenant;
never update/delete): `tenant_id` (`CASCADE` FK), `user_id` (`SET NULL` FK,
nullable), `action` (`audit_action`), `entity_name`, `entity_id` (nullable),
`detail` JSONB (nullable), timestamps. `(tenant_id, created_at)` and
`(tenant_id, entity_name, entity_id)` indexes + RLS.

Deviations from `schema.sql` (documented in `V4`): UUID PK instead of
`BIGSERIAL` (to inherit `TenantAwareEntity` and its automatic filter) and
`updated_at` column (required by the base mapping; never updated via API; the
`toString` omits `detail` in case it carries sensitive data).

`AuditService.log(tenantId, userId, action, entityName, entityId, detail)`
(`Map<String,Object>` → JSONB): validates required fields, runs in
`REQUIRES_NEW` so the audited flow's rollback (e.g. failed login)
doesn't erase the entry.

Login auditing (FASE1-14): `login_success` (with user) and `login_failed`
when the tenant is attributable (user found, or request `tenantId`
even if the email doesn't exist → null `userId`). Decision: **with no
attributable tenant nothing is logged** (inventing one would violate isolation;
`tenant_id` is NOT NULL). Detail with email only: never passwords.

---

## 9. Testing

- Integration against a real PostgreSQL via `AbstractIntegrationTest`
  (`@SpringBootTest` + `@Testcontainers`, shared static `postgres:16`
  container, `@DynamicPropertySource`). Never H2 nor DB mocks.
- Web with MockMvc (`@AutoConfigureMockMvc`); test-only controllers live
  in `src/test` (never deployed).
- Required per business endpoint/repository: normal case + **cross-tenant**
  + role authorization if applicable. DoD = automated test, not "tested by
  hand".
- Recorded lesson: the Testcontainers DB is shared across methods, so
  test data that must be unique (login emails) is generated unique per
  method; otherwise the result depends on execution order.

---

## 10. Observability and deployment

- Actuator: `health` and `info` exposed (health with `show-details: never`).
- Multi-stage `Dockerfile` (`maven:3.9-eclipse-temurin-25` → running
  `eclipse-temurin:25-jre`; tags verified, not assumed) + `.dockerignore`;
  `prod` profile by default via `SPRING_PROFILES_ACTIVE`, `$PORT` port.
- Production on Render (app + managed Postgres 16); CI on GitHub Actions
  (`clean verify` on push/PR to `dev` and `main`; Testcontainers spins up its DB).

---

## 11. Decision and deviation log

| # | Decision / deviation | Why |
|---|---|---|
| FASE0-01 | No Lombok, explicit code | Learn the framework without hidden magic |
| FASE0-03 | Packages by business module | Familiar (modular Angular); each module with its sub-layers |
| FASE0-05 | `ddl-auto: validate` always | Schema only changes via versioned Flyway |
| FASE0-06 | Testcontainers 2.x: `testcontainers-*` artifacts | The 2.x BOM renamed them (verified in the local POM, not assumed) |
| FASE0 | `clean` after deleting/renaming resources | `target/` keeps stale files that stay active on the classpath |
| FASE0 | Docker PG on `5434` | Native `postgresql-x64-17/18` take 5432 and 5433-IPv4 in dev |
| FASE1-04 | `TenantAwareEntity` + `@TenantId` | Forgetting the filter becomes a design error, not a memory one |
| FASE1-05 | Integration test instead of unit test | AGENTS.md §7 mandates: only the real DB proves the hash is stored |
| FASE1-13 | `audit_log` with UUID PK + `updated_at` | Inheriting the automatic filter outweighs copying `schema.sql` exactly |
| FASE1-13 | `AuditService.log` in `REQUIRES_NEW` | Without it, the failed-login rollback would erase its audit entry |
| FASE1-14 | Failures without tenant are not audited | Inventing a tenant violates isolation; `tenant_id` is NOT NULL |
| FASE2-02 | Soft delete with `is_active` on `Patient` | Preserve clinical history without destructive physical deletion |
| FASE2-03 | Search insensitive to accents/case | `unaccent` extension and indexable `immutable_unaccent` function with trigrams |
| FASE2-07 | Append-only odontogram entries | Each entry is a clinical moment; several entries coexist on the same tooth |
| FASE2-09 | Compensation on S3 file upload | If the DB save fails after uploading to S3, the object is deleted to avoid orphans |
| FASE2-10 | Temporary presigned URLs with S3Presigner | Direct download from storage/CDN without saturating backend bandwidth (15 min) |
| FASE3-01 | Physical resources and professionals (`Professional`, `Room`) | Decoupled from the login user; `Room` optional on the appointment |
| FASE3-02 | Overlap prevention via PostgreSQL `EXCLUDE USING gist` constraint | Engine-level guarantee against concurrent races using `tstzrange`; cancelled/no_show appointments excluded (`WHERE status NOT IN ('cancelada', 'no_show')`) |
| FASE3-03 | Agenda query with optional range and professional filters | Ascending chronological order and strict cross-tenant isolation |
| FASE3-04 | Service-layer state machine with explicit transitions | Domain validations (`programada` -> `confirmada` -> `atendida` / `no_show` / `cancelada`) with descriptive HTTP 400 |
| FASE3-05 | Agenda value aggregation (`summarizeValue`) | Excludes cancelled and no_show appointments, consistent with freed-up space |
| FASE3-06 | `WaitlistEntry` for unsatisfied demand | Temporal availability window (`desired_from`, `desired_to`) and procedure of interest |
| FASE3-07 | Slot recovery when cancelling an appointment | Immediate suggestion of compatible candidates in the cancellation response and via dedicated endpoint, FIFO order excluding the cancelling patient |
| FASE4-01 | Treatment plans and items (`TreatmentPlan`, `TreatmentPlanItem`) | Extend `TenantAwareEntity`, tenant-consistency trigger `check_treatment_plan_tenant_consistency` (validates patient and professional belong to the same tenant), FDI tooth check (11..48) and automatic `total_price_cop` recalculation |
| FASE4-02 | Plan state machine (`TreatmentPlanService`) | States: `borrador` -> `presentado` -> `aceptado` / `rechazado` -> `en_ejecucion` -> `completado` / `cancelado`. Automatic `presented_at` and `last_contact_at` timestamps for CRM/opportunity follow-up |
| FASE4-03 | Simple billing and payments (`Invoice`, `InvoiceItem`, `Payment`) | Mandatory `tenant_id` + `patient_id` relation, optional `treatment_plan_id` relation. Atomic correlative invoice numbering (`FAC-000001`) with `V16` and `check_invoice_tenant_consistency` trigger |
| FASE4-04 | Invoice state transition and overpayment control | When registering payments (`POST /invoices/{id}/payments`), the invoice automatically transitions `pendiente` -> `parcial` -> `pagada`. Overpayments rejected with HTTP 400 (`IllegalArgumentException`) |
| FASE4-05 | Essential-plan checkpoint validated via E2E | Full 13-step business and clinical flow (`EssentialPlanFlowIntegrationTest`, 13 steps) via MockMvc without direct database manipulation + alignment with Essential-plan limits |

---

## 12. Per-phase history

### Phase 0 — Foundations (completed)

Spring Boot project running, `dev/test/prod` profiles with env-based secrets,
documented module structure, reproducible Postgres
(`docker compose up -d`), Flyway from V1 with `validate`, Testcontainers with
reusable base class, verified multi-stage Docker image (`build` + `run`
+ `/actuator/health`), manual Render deploy and CI blocking red PRs. DoD:
`GET /actuator/health` → `UP` locally, in container, and in the cloud.

### Phase 1 — Identity, authentication, and multi-tenancy (completed, FASE1-01–14)

`Tenant` + `User`/`UserRole` (per-tenant unique email) + `TenantAwareEntity` and
automatic `@TenantId`/`TenantIdentifierResolver` filter + per-request
`TenantContext` + BCrypt `UserService` + JWT login (`/api/v1/auth/login`,
`/api/v1/me`) + `@PreAuthorize` authorization + `audit_log` auditing.
Exit checklist verified: FASE1-10 5/5 and FASE1-12 5/5 in `clean verify`
(what CI runs), base ready for Phase 2.

### Session security improvements (pre-Phase 3)

Implemented before starting Phase 3 (agenda and appointments):

- **Atomic-rotation refresh tokens** (`V10__create_refresh_tokens.sql`,
  `RefreshToken`, `RefreshTokenService`): 15-min access token + 7-day refresh
  token with SHA-256 hash in DB. `POST /api/v1/auth/refresh` rotates
  the token and issues a new pair. `POST /api/v1/auth/logout` revokes the token.
  Revoked-token reuse detection → 401.
- **Real-time user validation** (`JwtAuthenticationFilter`):
  on every authenticated request the DB is queried to verify `isActive()` and
  the tenant. Deactivating a user takes effect immediately without waiting for
  the JWT to expire. The role is also read from DB on every request (real-time
  sync).
- **Login rate limiting and brute-force protection (`LoginRateLimitService`):**
  defense in depth without heavy dependencies: (1) 10 requests/minute per IP
  limit on `/api/v1/auth/login` with HTTP 429 and `Retry-After`; (2) 15-minute
  temporary lockout after 5 consecutive failures per email, rejecting requests
  with HTTP 429 without running the expensive BCrypt computation; (3) counter
  reset after successful login; (4) periodic cleanup of expired records every
  5 minutes.
- **Green tests after auth improvements:** `LoginRateLimitIntegrationTest` (4/4), `AuthRefreshIntegrationTest` (5/5) and `JwtSecurityIntegrationTest` (7/7).

### Phase 2 — Patients and base clinical history (completed, FASE2-01–10)

`patient` module:
- `Patient` (FASE2-01/02/03): CRUD, soft delete with `is_active`, pagination and accent/case-insensitive search with PostgreSQL `pg_trgm` and `immutable_unaccent()`.
- Centralized exception handling with `GlobalExceptionHandler` and `ApiErrorResponse`.
- `ClinicalRecord` (FASE2-05/06): clinical-history model and endpoints (`POST /{id}/clinical-records`, `GET /{id}/clinical-records`).
- `OdontogramEntry` (FASE2-07/08): non-destructive odontogram model, FDI notation validation (11–48), 4 entry types and endpoints (`POST /{id}/odontogram`, `GET /{id}/odontogram` grouped by tooth and type).
- S3 storage and files (FASE2-09/10): `PatientFile` with Flyway `V9`, S3 SDK v2 client (`software.amazon.awssdk:s3`), multipart upload `POST /{id}/files` with automatic S3-delete compensation if the database fails, listing `GET /{id}/files` and temporary presigned-URL generation (15 min) `GET /{id}/files/{fileId}/download-url` with `S3Presigner`.
- **Role access control (`@PreAuthorize`) in `PatientController`:**
  - Clinical history (`POST /{id}/clinical-records`): restricted to practitioners (`PROPIETARIO`, `ODONTOLOGO`, `ESPECIALISTA_EXTERNO`).
  - Clinical history (`GET /{id}/clinical-records`): confidential for care staff (`PROPIETARIO`, `ODONTOLOGO`, `ESPECIALISTA_EXTERNO`, `AUXILIAR`). Reception blocked with 403.
  - Odontogram (`POST /{id}/odontogram`): reserved to `PROPIETARIO`, `ODONTOLOGO`, `ESPECIALISTA_EXTERNO`.
  - Soft delete (`DELETE /{id}`): destructive action reserved exclusively to `PROPIETARIO`.
  - Demographic management (`POST /patients`, `PATCH /patients/{id}`): `PROPIETARIO`, `RECEPCION`, `ODONTOLOGO`, `AUXILIAR`.
  - 11 authorization tests in `PatientRoleAuthorizationIntegrationTest` (11/11 green).
- Total project tests: **124/124 green** in `./mvnw.cmd clean verify`.

### Phase 3 — Agenda and appointments (completed, FASE3-01–07)

`appointment` module:
- `Professional` and `Room` (FASE3-01): per-tenant professional-resources and office/chair catalog with existence and active-status validation. Flyway migration `V11__create_professionals_and_rooms.sql`.
- `Appointment` and overlap prevention (FASE3-02): appointment model with `V12__create_appointments.sql` migration. PostgreSQL `EXCLUDE USING gist` constraint on `(tenant_id WITH =, professional_id WITH =, tstzrange(starts_at, ends_at) WITH &&)` to prevent physical double-booking at engine level. Cancelled and no_show appointments are excluded from the constraint (`WHERE status NOT IN ('cancelada', 'no_show')`), freeing the slot automatically. Error handling in `GlobalExceptionHandler` translating to HTTP 409 Conflict.
- Agenda query endpoints (FASE3-03): `GET /api/v1/appointments` with optional range (`from`, `to`) and professional (`professionalId`) filters, ordered by `starts_at ASC`. Cross-tenant isolation verified.
- State machine and valid transitions (FASE3-04): `PATCH /api/v1/appointments/{id}/status` supporting the `programada` → `confirmada` → `atendida` / `no_show` / `cancelada` cycle. Illegal transitions rejected with HTTP 400.
- Appointment economic value (FASE3-05): `estimated_value_cop` field and `GET /api/v1/appointments/summary-value` aggregation endpoint summing and counting in-range appointments excluding freed terminal states (`cancelada`, `no_show`).
- Waitlist (FASE3-06): `WaitlistEntry` model with `V13__create_waitlist_entries.sql` migration and `POST /api/v1/waitlist` endpoint to register unsatisfied demand with availability window and desired procedure.
- Slot recovery on appointment cancellation (FASE3-07):
  - When transitioning an appointment to `cancelada` via `PATCH /api/v1/appointments/{id}/status`, the backend automatically computes compatible waitlist candidates and appends them in `AppointmentResponse.waitlistCandidates`.
  - Dedicated query endpoint: `GET /api/v1/appointments/{id}/waitlist-candidates`.
  - Compatibility logic: same tenant, active status (`WaitlistStatus.activa`), compatible procedure (same `procedureId` or `null` wildcard), time window overlapping the freed slot, exclusion of the cancelling patient, and FIFO order by registration date.
  - Verified with `SlotRecoveryIntegrationTest` (7/7 green).
- Total project tests: **170/170 green** in `./mvnw.cmd clean verify`.

### Phase 4 — Treatment plans and basic invoicing (completed, FASE4-01–05)

`treatmentplan` and `billing` modules:
- **Treatment plan and item modeling (FASE4-01):**
  - `TreatmentPlan` and `TreatmentPlanItem` entities mapped with `TenantAwareEntity`.
  - Flyway migration `V14__create_treatment_plans.sql` with FDI check for teeth (`tooth_number BETWEEN 11 AND 48`), tenant-isolation trigger (`check_treatment_plan_tenant_consistency`) and automatic `total_price_cop` recalculation.
  - `TreatmentPlanRepositoryIntegrationTest` (7/7 green).
- **Plan endpoints and state machine (FASE4-02):**
  - REST endpoints under `/api/v1/treatment-plans` with `@Tag("Planes de Tratamiento")` and Swagger/OpenAPI docs.
  - Creation with items (`POST /api/v1/treatment-plans`), diagnostic/professional data update (`PUT /{id}`), fetch (`GET /{id}`) and paginated listing with filters (`GET /api/v1/treatment-plans?patientId=...&status=...`).
  - Lifecycle management (`PATCH /{id}/status`): states `borrador` -> `presentado` -> `aceptado` / `rechazado` -> `en_ejecucion` -> `completado` / `cancelado`. Automatic `presented_at` and `last_contact_at` timestamps supporting the opportunity engine and CRM.
  - `TreatmentPlanIntegrationTest` (10/10 green).
- **Invoicing and payment modeling (FASE4-03):**
  - `Invoice`, `InvoiceItem` and `Payment` entities with Flyway migration `V15__create_invoices_and_payments.sql`.
  - `check_invoice_tenant_consistency` trigger guaranteeing patient and treatment plan strictly belong to the same tenant.
  - Flyway migration `V16__create_invoice_number_seq.sql` for atomic correlative invoice numbering (`FAC-000001`) per tenant.
  - `BillingModelIntegrationTest` (4/4 green).
- **Invoicing endpoints and payment registration (FASE4-04):**
  - REST endpoints under `/api/v1/invoices` with `@Tag("Facturación")`.
  - Manual invoice creation or from an approved treatment plan (`POST /api/v1/invoices`), fetch by id (`GET /{id}`) and listing by patient/status (`GET /api/v1/invoices`).
  - Charge and payment registration (`POST /api/v1/invoices/{id}/payments`) with payment-method support (`efectivo`, `tarjeta`, `transferencia`, `otro`).
  - Automatic reactive state transition: `pendiente` -> `parcial` -> `pagada` when accumulated payments settle the invoice total.
  - Strict overpayment control: rejects payments exceeding the outstanding balance with HTTP 400 (`IllegalArgumentException`).
  - `InvoiceIntegrationTest` (9/9 green).
- **Essential-plan checkpoint and E2E demo (FASE4-05):**
  - 13-step business flow covered end to end in `EssentialPlanFlowIntegrationTest`: Owner and Receptionist authentication → patient intake → appointment booking → clinical consult → odontogram finding → appointment completion → discounted quote creation → plan presentation and acceptance → invoicing → partial payments to full settlement → treatment closure.
  - 100% executed through the REST API with no direct database manipulation.
  - Review and alignment with Essential-plan limits (`max_patients`, `max_users`, `max_sedes`).
- Total project tests: **201/201 green** in `./mvnw.cmd clean verify`.

### Phase 5 — Lead CRM (completed, FASE5-01–04)

`crm` module:
- **`Lead` modeling and its sales pipeline (FASE5-01):**
  - `Lead` entity: commercial contact with name, phone, email (`citext`), marketing channel (`source`), ad campaign (`campaign`), procedure of interest (`procedure_of_interest`), estimated value (`estimated_value_cop`), owner assignment (`assigned_to`) and converted patient (`converted_patient_id`).
  - 9-stage pipeline modeled with native PostgreSQL `lead_status` enum: `nuevo`, `contactado`, `calificado`, `cita_propuesta`, `cita_agendada`, `cita_asistida`, `tratamiento_propuesto`, `tratamiento_aceptado`, `perdido`.
  - `LeadActivity` entity: chronological history of contact interactions (`llamada`, `whatsapp`, `email`, `nota`) with author user and explanatory notes.
  - Flyway migration `V17__create_leads.sql` with Row Level Security enabled (`tenant_isolation`), multi-tenant consistency triggers (`check_lead_tenant_consistency` and `check_lead_activity_tenant_consistency`) and partial analytic index `idx_leads_unresponded` (`WHERE status = 'nuevo'`) feeding the opportunity engine (Phase 9).
  - `LeadRepositoryIntegrationTest` (5/5 green).
- **Lead CRUD and status-change endpoints (FASE5-02):**
  - REST endpoints under `/api/v1/leads` with OpenAPI docs (`@Tag("CRM Leads")`).
  - Manual creation (`POST /api/v1/leads`), fetch by id (`GET /{id}`) and paginated listing with dynamic filters (`GET /api/v1/leads?status=...&assignedToId=...&source=...`) via Spring Data JPA `Specification<Lead>`.
  - Flexible sales-pipeline transition (`PATCH /{id}/status`): unlike clinical appointments and treatments, the sales funnel freely allows forward and backward moves following real prospect behavior, automatically logging `nota`-type activities when justification is supplied.
  - Activity logging (`POST /{id}/activities`) with reactive `last_contact_at` timestamp update on the lead for direct interactions, and descending chronological history query (`GET /{id}/activities`).
- **Lead-to-patient/appointment conversion (FASE5-03):**
  - Conversion endpoint: `POST /api/v1/leads/{id}/convert`.
  - Turns a prospect into an active clinic `Patient` with smart name inference (`fullName` automatically split into `firstName` and `lastName` if not specified in the body) and contact-data inheritance.
  - Optional first medical appointment (`Appointment`) scheduling in the same atomic transaction under `@Transactional`. With an appointment, the prospect status automatically advances to `cita_agendada`; without one it advances to `calificado`.
  - Reasonable idempotency: if the lead already has a `converted_patient_id`, later calls return the existing patient with `alreadyConverted = true` (HTTP 200 OK) without duplicating `patients` rows.
  - Automatic traceability activity logging linking the collaborator and the scheduled appointment.
- **Conversion and response-speed metrics (FASE5-04):**
  - `LeadMetricsService` analytic service decoupled from the operational flow (SRP).
  - `GET /api/v1/leads/metrics/conversion`: computes total prospect volume, patient-converted count, and global conversion rate, broken down by channel (`bySource`) and campaign (`byCampaign`) for an optional date range (`from`, `to`).
  - `GET /api/v1/leads/metrics/response-time`: measures response speed from prospect creation to first interaction (`MIN(la.created_at)`), reporting attended leads, unresponded leads, response rate, and minute/hour averages.
  - Verified with `LeadIntegrationTest` (19/19 green) and `OpenApiDocsIntegrationTest` (1/1 green).
- Total project tests: **225/225 green** in `./mvnw.cmd clean verify`.

### Phase 6 — Receivables and staged payments (completed, FASE6-01–04)

`billing`/`receivables` module:
- **`PaymentPlan` and `Installment` modeling (FASE6-01):**
  - `PaymentPlan` entity (extends `TenantAwareEntity`, FK to `TreatmentPlan` as plain UUID to decouple the JPA graph, total agreed amount in `NUMERIC(12,2)` and installment count).
  - `Installment` entity (individual installments with `@ManyToOne` to `PaymentPlan`, installment number, amount in `NUMERIC(12,2)`, `due_date`, `status` and `paid_at` timestamp).
  - Native PostgreSQL `installment_status` enum type: `pendiente`, `pagada`, `vencida` with `@JdbcType(PostgreSQLEnumJdbcType.class)`.
  - `uq_installments_plan_number UNIQUE (payment_plan_id, installment_number)` uniqueness constraint to avoid duplicates within a plan.
  - Flyway migration `V18__create_payment_plans.sql`: creates tables with Row Level Security enabled and forced (`tenant_isolation`), `set_updated_at` trigger and `mark_overdue_installments()` PL/pgSQL function.
  - Verified with `PaymentPlanRepositoryIntegrationTest` (5/5 green).
- **Payment-plan endpoints and paid-installment registration (FASE6-02):**
  - `POST /api/v1/treatment-plans/{id}/payment-plan`: creates a plan in N monthly installments with standard banker's rounding (`HALF_UP`) and remainder absorption in the last installment to match the agreed amount to the cent. If the treatment already has an active plan, responds with HTTP 409 Conflict via `ConflictException`.
  - `POST /api/v1/installments/{id}/pay`: settles an installment, transitioning its status to `pagada` and recording `paid_at`. Automatically generates its settled `Invoice` and corresponding line item to keep full accounting traceability. Paying an already-paid installment returns HTTP 409 Conflict.
  - `PaymentPlanController` and `InstallmentController` protected with `@PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")`.
  - Verified with `PaymentPlanIntegrationTest` (6/6 green) with lifecycle and 404 cross-tenant isolation tests.
- **Daily overdue-installments job (FASE6-03):**
  - `OverdueInstallmentsJob` service in `billing.service`.
  - `execute()` method: transactional, runs the native SQL function `mark_overdue_installments()`, updates `pendiente` installments with `due_date < CURRENT_DATE` to `vencida`, and logs affected rows with SLF4J.
  - `@Scheduled(cron = "${odentix.jobs.overdue-installments.cron:0 0 2 * * *}")` trigger configurable via `application.yml`.
  - System-context execution (outside HTTP requests) operating on `ROOT_TENANT_ID` and `current_tenant_id() IS NULL` RLS to update all clinics globally and atomically.
  - Verified with `OverdueInstallmentsJobIntegrationTest` (4/4 green).
- **Consolidated receivables dashboard (FASE6-04):**
  - `GET /api/v1/portfolio/summary` endpoint in `PortfolioController`.
  - `PortfolioService` service and aggregate query in `InstallmentRepository.getPortfolioSummary(tenantId)`.
  - Computes in real time:
    - Total agreed receivables (`totalAmountCop`).
    - Overdue receivables (`overdueAmountCop`): installments in `vencida` status or past-due pending ones before the job run.
    - Upcoming receivables (`upcomingAmountCop`): pending installments with future due dates.
    - Current / paid receivables (`paidAmountCop`): settled installments.
    - Total outstanding balance (`outstandingAmountCop`): overdue + upcoming sum.
    - Installment counts per category (`totalInstallmentsCount`, `overdueInstallmentsCount`, `upcomingInstallmentsCount`, `paidInstallmentsCount`).
  - If a clinic has no payment plans, returns all amounts as `0.00` and counts as `0` (no nulls).
  - Verified with `PortfolioIntegrationTest` (4/4 green).
- **OpenAPI documentation and AGENTS.md §4 rule:**
  - All receivables operations registered and exhaustively verified in `OpenApiDocsIntegrationTest`:
    - `POST /api/v1/treatment-plans/{id}/payment-plan`
    - `POST /api/v1/installments/{id}/pay`
    - `GET /api/v1/portfolio/summary`
- Total project tests: **244/244 green** in `./mvnw.cmd clean verify`.

### Phase 7 — External specialists and inventory (completed, FASE7-01–04)

`specialist` and `inventory` modules:
- **`Specialist` and settlement modeling (FASE7-01):**
  - `Specialist` entity (extends `TenantAwareEntity`, 1—1 with `Professional` via `professional_id UNIQUE`, `fee_percentage NUMERIC(5,2)` 0–100).
  - `SpecialistSettlement` entity (`DATE` period with `period_end >= period_start` CHECK, `NUMERIC(12,2)` amounts, `settlement_status` status, nullable `paid_at`) + native `SettlementStatus` enum with `@JdbcType(PostgreSQLEnumJdbcType.class)`.
  - `V19__create_specialists.sql` migration: `check_specialist_is_external` trigger reused as-is from `schema.sql` + application validation in `@PrePersist/@PreUpdate` (defense in depth), `tenant_isolation` RLS on both tables.
  - Verified with `SpecialistRepositoryIntegrationTest` (7/7 green).
- **Settlement calculation and endpoint (FASE7-02):**
  - `POST /api/v1/specialists/{id}/settlements` (`SettlementController`, `PROPIETARIO` only, 201 + `Location`): gross production = `SUM(total_cop)` of invoices issued in the period linked to the professional's treatments (excludes `anulada` and treatment-less ones) via `InvoiceRepository.sumFacturadoPorProfesionalEnPeriodo` (JPQL with explicit tenant filter); fees = gross × percentage / 100 (HALF_UP); period bounds in the clinic's timezone; per-period idempotency (409).
  - Verified with `SettlementIntegrationTest` (7/7 green).
- **Inventory modeling (FASE7-03):**
  - `InventoryItem` entities (per-tenant unique name, `quantity`, `min_threshold`) and `StockMovement` (`quantity_delta` ≠ 0, `reason`, plain-UUID `created_by`).
  - `V20__create_inventory.sql` migration: partial `idx_inventory_items_critical` index, `apply_stock_movement` function + trigger reused as-is (the trigger is the only thing moving stock; the CHECK rolls back negative consumption), RLS on both. Deviation: `updated_at` on `stock_movements` (required by `TenantAwareEntity`).
  - Verified with `InventoryRepositoryIntegrationTest` (7/7 green).
- **Inventory endpoints and alerts (FASE7-04):**
  - `InventoryController` (`/api/v1/inventory`, broad operational roles): item CRUD (DELETE only with no movements → 409), `POST /items/{id}/movements` (returns resulting stock; negative → 409 "Stock insuficiente"; `delta = 0` → 400), `GET /critical` (via `findCritical` with explicit JPQL, uses the partial index).
  - Verified with `InventoryIntegrationTest` (8/8 green).
- **OpenAPI documentation and AGENTS.md §4 rule:**
  - The 8 new endpoints registered and verified in `OpenApiDocsIntegrationTest`:
    - `POST /api/v1/specialists/{id}/settlements`
    - `POST/GET /api/v1/inventory/items`, `GET /api/v1/inventory/critical`,
      `GET/PATCH/DELETE /api/v1/inventory/items/{id}`,
      `POST /api/v1/inventory/items/{id}/movements`
- Total project tests: **273/273 green** in `./mvnw.cmd clean verify`.

### Phase 8 — Automations, notifications, and tasks (completed, FASE8-01–06)

`task` and `notification` modules:
- **`Task` modeling and basic CRUD (FASE8-01):**
  - `Task` entity (extends `TenantAwareEntity`; polymorphic `related_entity_type/id` reference without FK — intentional, note in `schema.sql` §14; plain-UUID `assignedTo`; `dueAt`; PG `TaskStatus`/`TaskPriority` enums with `@JdbcType(PostgreSQLEnumJdbcType.class)`).
  - `V21__create_tasks.sql` migration (`idx_tasks_tenant_assignee_status` and `idx_tasks_related_entity` indexes, RLS).
  - `TaskController` (`/api/v1/tasks`: create with tenant-validated assignee → 404, list, `GET /mine`, view, PATCH without status, idempotent `POST /{id}/complete` with 409 if cancelled, DELETE).
  - Verified with `TaskIntegrationTest` (6/6 green).
- **Automatic unconfirmed-appointment rule (FASE8-02):**
  - `UnconfirmedAppointmentJob` (hourly `@Scheduled`, configurable cron): `programada` appointments starting within 24h generate unassigned high-priority tasks; idempotency via linked open tasks; multi-tenant system context with per-appointment `tenantId`.
  - Verified with `UnconfirmedAppointmentJobIntegrationTest` (2/2; per-appointment assertions because the DB is shared across suites and the job is global).
- **Decoupled notifications + email (FASE8-03):**
  - `Notification` entity (channel, recipient, `templateKey`, JSONB `payload`, status, `sentAt`/`errorDetail`) + `V22__create_notifications.sql` migration (enums, RLS).
  - `NotificationSender` interface + `NotificationException`; default `LoggingNotificationSender` and `SmtpEmailNotificationSender` with `enabled=true` (everything via env, 5s timeouts; new `spring-boot-starter-mail` dependency; `management.health.mail.enabled=false` because email is optional).
  - Post-closure update (per-clinic sender): `V27__tenant_notification_email.sql` (`tenants.notification_email/name`); the `From` comes from the clinic with global fallback and `Reply-To` to the clinic. Provider note: a variable `From` requires SMTP with multiple verified senders (Gmail won't do). Verified with `SmtpSenderIntegrationTest` (3/3). No tenants endpoint: the address is loaded via DB/console for now.
  - Self-managed settings (pre-Phase 12): `GET/PATCH /api/v1/tenant/settings` (owner only, always their clinic) to view and change the sender; empty falls back to global. Verified with `TenantSettingsIntegrationTest` (3/3 + own WhatsApp without exposing the token).
  - Per-clinic WhatsApp (pre-Phase 12): `V28` (`whatsapp_phone_number_id` + AES-GCM-**encrypted** token via `DataEncryptionService` with `DATA_ENCRYPTION_KEY`); `PATCH /tenant/settings` saves number + write-only token (GET never returns it); the adapter sends from the clinic's own number with global fallback; a half-set credential is never exposed anywhere. Verified with `DataEncryptionServiceTest` (4/4) and own-number sending. Stated limit: receiving replies still doesn't exist.
  - `NotificationService`: common core that never throws (failures → `fallida`); `sendAppointmentConfirmation` and `sendAppointmentScheduled`.
  - Verified with `NotificationServiceIntegrationTest` (4/4 green).
- **Appointment-event triggers (FASE8-04):**
  - `AppointmentService`: create → scheduled-notice; real transition to `confirmada` → confirmation (no resend on no-op). Same transaction, no cycles.
  - Verified with `AppointmentNotificationIntegrationTest` (2/2) and `AppointmentNotificationResilienceIntegrationTest` (1/1, downed provider via `@MockitoBean`: 201/200 all the same, 2 `fallida`).
- **WhatsApp adapter (FASE8-05):**
  - `WhatsappNotificationSender` (`RestClient`, 5s timeouts, Cloud API, env-only credentials) + per-channel dispatch in the service (`List<NotificationSender>`) + `sendAppointmentWhatsAppConfirmation` (patient phone).
  - Verified with no real network: `WhatsappSenderIntegrationTest` (4/4, fake JDK server) and `WhatsappNotificationServiceIntegrationTest` (2/2, `@DynamicPropertySource`). Administrative pending: Meta-approved number + real token.
- **Slot recovery (FASE8-06):**
  - `AppointmentCancelledEvent` + `SlotRecoveryListener`: cancelled high-value appointment (`risk_level = alto`) with candidates → automatic task for reception with details; Spring events to avoid `appointment` ↔ `task` cycles.
  - Verified with `SlotRecoveryAutomationIntegrationTest` (4/4 green).
- Total project tests at Phase 8 close: **298/298 green** in `./mvnw.cmd clean verify`.

### Phase 9 — Opportunity engine (completed, FASE9-01–04)

`opportunity` module (the product differentiator: detect → propose → execute → measure):
- **Modeling and first rule (FASE9-01):** `Opportunity` entity (type, polymorphic FK-less reference, estimated value, 1–5 priority, status, `detectedAt`/`resolvedAt`) + `V23__create_opportunities.sql` migration + `TreatmentPlanFollowupJob` job (presented/deciding plan with no contact in 3 days, idempotent) + `GET /api/v1/opportunities` inbox.
- **Rule expansion (FASE9-02):** 5 jobs (`LeadUnrespondedJob` every 2h/24h; `HighRiskAppointmentJob` daily scheduled+high at 48h; `InactivePatientJob` daily with no appointments or plans in 6 months; `OverdueInstallmentOpportunityJob` 02:30 after marking; `CriticalInventoryJob` every 6h) + `SlotOpportunityListener` (cancellation with candidates). Candidate queries in their repos; deterministic priorities and values per rule; idempotency by open entity; multi-tenant system context. Verified with `OpportunityRulesIntegrationTest` (6/6) and `SlotOpportunityListenerIntegrationTest` (2/2). Notes: `Instant` doesn't support MONTHS (cut with `OffsetDateTime`); the guard is per entity, not per type.
- **Recommended actions (FASE9-03):** `V24__create_opportunity_actions.sql` (with extra `channel`), `OpportunityAction` entity (`action_type` TEXT without PG enum; task title on the first `suggested_message` line), `OpportunityActionFactory` (always a task + message if there's a recipient), `POST /{id}/actions/{actionId}/execute` (linked task or message via new `sendCustomMessage`; 409 if executed/no recipient), inbox with `actions`. Retrofit across the 7 detectors. Verified with `OpportunityActionIntegrationTest` (4/4).
- **Recovered value (FASE9-04):** attribution criterion in Javadoc (`resuelta` + executed action with `executedAt <= resolvedAt` + `resolvedAt` in period; without action it's organic). `PATCH /{id}/status` (prerequisite: sets/clears `resolvedAt`) + `GET /recovered-value` grouped by category. Verified with `RecoveredValueIntegrationTest` (3/3). Stated limitation: a human marks `resuelta`.
- Total project tests at Phase 9 close: **323/323 green** in `./mvnw.cmd clean verify`.

### Phase 10 — Administrative AI (completed, FASE10-01–03)

`assistant/` module (questions and messages over real clinic data, via Groq):
- **Administrative assistant (FASE10-01):**
  - `GroqChatClient` (`RestClient` with no new deps): `POST {base}/chat/completions` with Bearer `GROQ_API_KEY`; configurable `GROQ_MODEL` (default `openai/gpt-oss-20b`, current example from their docs); 15s timeouts; errors → `AssistantException` mapped to 502. Without a key it responds with a clear 502, nothing breaks.
  - `AssistantContextService`: tenant snapshot only with `TenantContext` (open opportunities, undecided plans + total, 24h appointments, overdue receivables + total, criticals by name, new leads; top 5 per section to bound cost).
  - `POST /api/v1/assistant/ask` (validated question max 500, `{answer, model}` response; anti-hallucination system prompt in code; broad read roles).
  - Verified with `AssistantIntegrationTest` (3/3: body to the provider with Bearer/model/A's data and nothing of B; 400/401) and `GroqChatClientTest` (4/4: parsing, rejection, malformed, no key) against a local fake server. Notes: no `RestClient.Builder` bean (static `RestClient.builder()` is used); an earlier failure was incremental cache (`clean` fixed it).
- **Assisted message generation (FASE10-02):**
  - `POST /api/v1/assistant/suggest-message` (tenant appointment + optional hint): returns `{message, suggestedChannel, model}` as an editable draft. Pure generation, verifiable by absence of rows in `notifications`/`tasks`.
  - Verified with `SuggestMessageIntegrationTest` (2/2).
- **AI resilience (FASE10-03):**
  - Fixed-template fallback with `fallback: true` (ask → real snapshot summary; suggest → template with name/date), both 200. Logging with structured `log.warn` (operation, model, latency, truncated error, no PII); no table because there's no per-patient attempt to audit. The 15s stay (hardening further would break legitimate responses).
  - Verified with `AssistantFallbackIntegrationTest` (2/2, HTTP 500 provider).
- **Test infrastructure:** `max_connections=200` on the `AbstractIntegrationTest` PG (cached contexts with their own pools exhausted the 100 default: `too many clients`).
- Total project tests at Phase 10 close: **334/334 green** in `./mvnw.cmd clean verify`.

### Phase 11 — Subscriptions and plans (completed, FASE11-01–05)

The SaaS itself bills and governs access (`subscription/` and `saas/` modules):
- **Entities (FASE11-01):** `Plan`, `PlanFeature`/`FeatureKey`, `PlanLimit`/`LimitKey`, `TenantSubscription` + `V25__create_plans_and_subscriptions.sql` with seeds (verified with `SubscriptionIntegrationTest` 4/4).
- **Feature-gating (FASE11-02):** `@PreAuthorize` SpEL with `@subscriptionService.requireFeature` alongside roles (Boot 4.1 removed the AOP starter: no aspect, zero deps). Matrix: leads, receivables (not simple invoicing), settlements, inventory vs per-method alerts, opportunities, AI. 403 with upgrade message; fail-open without subscription (pre-billing). Verified with `FeatureGateIntegrationTest` (4/4).
- **Numeric limits (FASE11-03):** `LimitExceededException` → 429 (+`Retry-After` on quota). `max_patients`/`max_users` over active ones; WhatsApp quota from period-sent `notifications`; NULL = unlimited. Verified with `LimitEnforcementIntegrationTest` (5/5).
- **Bold (FASE11-04):** `BoldClient` with only documented features (links + lookup, `x-api-key`); per-cycle idempotent checkout; HMAC webhook + fast 200 + idempotency (`SALE_APPROVED`→active+extends, rejected/voided→past_due); daily job (7-day grace→cancelled, renewal at ≤3 days). `V26__create_saas_payments.sql`. Verified with `SaasBillingIntegrationTest` (5/5, real HMAC against local fake). Administrative pending: keys and URL in panel.bold.co + Render.
- **Annual cycle (FASE11-05):** `GET /billing/subscription` + locked-in discount (`annual < 12×monthly` in seeds) + cycle switching (same-cycle-only idempotency). Verified with `BillingCycleIntegrationTest` (3/3).
- Total project tests at Phase 11 close: **355/355 green** in `./mvnw.cmd clean verify`.

### Phase 12 — Hardening and production (in progress, FASE12-01–04 previously closed)

- **FASE12-01 rate limiting, 12-02 secrets, 12-03 observability, 12-04 logs:** previously closed (login with 429/`Retry-After`, secrets audit, Sentry + Actuator, JSON logs with MDC `tenant_id`).
- **FASE12-05 CI/CD:** `deploy` job in `ci.yml` (push to `main` after green CI → Render Deploy Hook). Manual pending: `RENDER_DEPLOY_HOOK_URL` secret, `main` protection with required CI, disable duplicate auto-deploy, and verify with a real merge.
- **FASE12-06 backups:** ⛔ blocked — Render's free plan has no backups. Non-negotiable exit condition before the first paying customer: upgrade plan, enable backups, and test a real restore.
- **Boot/prod hardening (findings from real 2026-09-19 deploys):** container-sized heap (`JAVA_OPTS` with `MaxRAMPercentage=50`, bounded metaspace, serial GC); `bootstrap-mode: lazy` prod-only (JPQL parsing took ~13 min on free CPU); `@Value` numerics tolerant to empty vars; `health.mail.enabled=false`.
- **Per-tenant notification identities (pre-Phase 12):** per-clinic email sender (`V27` + `GET/PATCH /tenant/settings`) and AES-GCM-encrypted WhatsApp credentials (`V28` + `DATA_ENCRYPTION_KEY`), write-only token.
- Total project tests: **367/367 green** in `./mvnw.cmd clean verify`.

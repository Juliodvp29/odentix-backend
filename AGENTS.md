# AGENTS.md — odentix-backend

This file holds the instructions for any coding agent (Claude
Code, Cursor, Copilot Workspace, etc.) working in this repository.
If you are reading this as an agent: these rules take priority over your
default general judgment. If a user instruction in the chat
contradicts something critical here (especially the multi-tenancy section),
flag the contradiction instead of applying it silently.

---

## 1. What this project is

Backend of a management SaaS for dental clinics in Colombia
(multi-tenant: each clinic is a `tenant`). The product differentiator
is not just keeping records, but detecting lost business
opportunities (unanswered leads, treatments without follow-up, overdue
receivables, etc.) and proposing an action — don't keep this in mind for
infrastructure code, but if you touch business logic, the design
criterion is "this helps the clinic act", not just "this records
a data point".

Reference documents at the repo root (read them before touching anything
you don't fully understand):

- `docs/documentacion_sistema_gestion_odontologica_v1.1.md  ` — architecture and product.
- `docs/roadmap_backend_fases.md` — phased roadmap and tickets.
- `docs/schema.sql` — reference database schema.

This backend is being built **alone** for now (the Angular frontend is a
separate project/roadmap). Don't assume an Angular client
is already consuming these endpoints — there is no frozen API contract.

### Available skills

There is a `.agents/skills/` folder at the repo root with
task-specific instructions (e.g. how to generate a certain report,
a particular convention, a checklist for a certain kind of change).
**Before starting any task, check whether `.agents/skills/` contains
a skill relevant to what you are about to do and follow it** — it takes
priority over your default general judgment, just like the rest of this
file. If the folder doesn't have any skill related to the
current task yet, simply follow the rules in this `AGENTS.md`.

---

## 2. Exact stack and versions

- **Java 25 (LTS)** — never suggest or use non-LTS versions (26, etc.).
- **Spring Boot 4.1.1** — ⚠️ IMPORTANT: in this version the
  autoconfiguration packages were reorganized compared to Spring Boot 3.x. The
  vast majority of tutorials, Stack Overflow answers, and even a
  language model's own knowledge use the old packages.
  **Before writing any import or reference to a Spring Boot
  autoconfiguration class, verify the real package name**
  (examples already confirmed in this project):
  - `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`
  - `org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration`
  - `org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration`
    If you are unsure about an autoconfiguration class's package in
    4.1.1, say so explicitly instead of assuming the Spring Boot 3 package.
- **Maven** (not Gradle).
- **PostgreSQL 16+** — never H2 or any embedded database, not even for
  quick tests (see the testing section).
- **Flyway** for every schema change.
- **Spring Security** with JWT (own implementation, no OAuth2/Keycloak
  in this scope).
- Target deployment: Docker + Render/Railway (no Kubernetes, no
  microservices — a modular monolith on purpose).

---

## 3. Essential commands

```bash
# Start local Postgres (once docker-compose.yml exists, Phase 0.4)
docker compose up -d

# Compile and run tests
./mvnw clean verify

# Tests only
./mvnw test

# Run the app (dev profile)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

If any of these commands doesn't exist yet in the repo's current state
(e.g. `docker-compose.yml` before Phase 0.4), don't invent or simulate it:
say so and ask which roadmap phase we are at.

---

## 4. Architecture and package conventions

Organized **by business module**, not by technical layer (FASE0-03 decision,
documented because Julio comes from a more Angular-style module-oriented world):

```
com.julio.odentix.odentix_backend/
├── shared/           # TenantAwareEntity, TenantContext, common exceptions
├── tenant/
├── auth/
├── audit/            # AuditLog, AuditService (append-only, since FASE1-13)
├── patient/
├── appointment/
├── treatmentplan/
├── billing/
├── crm/
├── inventory/
└── ...
```

Each module is internally organized by sub-layer (`controller`,
`service`, `repository`, `entity`, `dto`) **inside its own package**,
not as shared top-level packages. If you are creating a new module,
follow this same pattern without asking; if you are going to _change_ the
pattern, ask first — it's a decision already made and documented.

### Interactive documentation (Swagger UI)

The project exposes OpenAPI/Swagger UI (`springdoc-openapi` 3.x, the line
compatible with Spring Boot 4 — 2.x is Boot 3 only): UI at
`/swagger-ui.html`, JSON at `/v3/api-docs`. Rules:

- Every new endpoint gets `@Tag` (at controller level) and `@Operation`
  with a clear summary; without this the UI is born empty and becomes useless.
- When closing each phase, verify that Swagger UI renders the new
  endpoints (with the app in dev + the *Authorize* button with a real JWT).
- Documentation is **disabled in prod** (`application-prod.yml`):
  it's a development tool, it must not be publicly exposed.

---

## 5. Multi-tenancy — critical rules (non-negotiable)

This is the most important aspect of the whole project. A bug here leaks
patient data across different clinics, which is the worst possible security
scenario for this product.

1. **Every new business entity** (not global catalogs like `plans`)
   must extend/include `tenant_id` from its first migration, and the
   Java class must inherit from `TenantAwareEntity` (or the equivalent
   pattern in place at that point in the project).
2. **Never write a manual JPQL/native query touching a business table
   without filtering by `tenant_id`**, even if you believe the automatic
   filter (Hibernate Filter / `TenantContext`) already covers it. Defense in
   depth: two independent layers, neither replaces the other.
3. Row Level Security in PostgreSQL (see `schema.sql`) is the second layer.
   If you write a Flyway migration creating a table with `tenant_id`,
   it **must** end up with RLS enabled following the same pattern as the
   rest (`tenant_isolation` policy on `current_tenant_id()`).
4. If you write a test for an endpoint or repository touching a business
   table, **always include a case verifying cross-tenant
   isolation** (create data in tenant A, authenticate/simulate as
   tenant B, verify it can neither be seen nor modified). It's not
   optional nor "something to add later".
5. Never log or expose in error messages the data contents of a
   tenant other than the current request's, not even in debug logs.

---

## 6. Database and migrations

- **`ddl-auto` always `validate`**, in every profile, with no
  exceptions. Never `update` nor `create`, not even "just for a quick test".
- Every schema change goes in a new Flyway migration
  (`V{n}__descripcion.sql`), never by editing an already-applied migration
  (even a recent one) nor by modifying the reference `schema.sql`
  directly expecting it to apply on its own.
- Follow the conventions already established in `schema.sql`: UUID as PK
  (`gen_random_uuid()`), `snake_case`, plural tables, `TIMESTAMPTZ` (not
  `TIMESTAMP`), amounts in `NUMERIC(12,2)` with the `_cop` suffix, `updated_at`
  managed by the generic `set_updated_at()` trigger (don't set it by
  hand from Java).
- Before creating a new table, check whether a reference definition for
  it already exists in `docs/schema.sql` and stay consistent with
  that definition unless the user explicitly asks to change it.

---

## 7. Testing

- **Testcontainers with a real PostgreSQL**, never H2 nor database
  mocks for integration tests. A base class for this already exists
  (see roadmap FASE0-06) — reuse it instead of creating a new one.
- Every new endpoint or repository touching a business table
  needs at least: a normal-behavior test, a
  cross-tenant isolation test (section 5), and a role-authorization test
  if the endpoint has a role restriction.
- Don't mark a ticket/task as done if the DoD (Definition of
  Done) described in `roadmap_backend_fases.md` for that ticket isn't
  covered by a verifiable automated test, not just "manually tested once".

---

## 8. Security

- Never hardcode secrets, credentials, or API keys in code or in
  versioned files. They go in environment variables.
- The database role used by the application in production must not
  have `BYPASSRLS` — if you need to write a migration or administrative
  script that does require it, say so explicitly instead of
  assuming elevated permissions by default.
- Never implement or suggest disabling RLS or the tenant filter
  "temporarily to test something faster". If something is hard to
  test with isolation enabled, the problem is in the test, not a
  reason to lower the security bar.
- AI or external-integration endpoints (WhatsApp, AI
  providers) must be implemented with failure handling that **never blocks**
  the operational flow that originated them (see roadmap section 8.3/10.3:
  short timeout + fixed-template fallback + failure logging).

---

## 9. Code style

- No Lombok for now (FASE0-01 decision, while learning
  the framework — explicit code). If Lombok is ever added
  to the project, this rule becomes obsolete; check the
  real `pom.xml` before assuming.
- Variable, method, and code-comment names in Spanish or
  English — stay consistent with whatever already exists in the file you
  are editing instead of mixing.
- Prefer explicit, readable code over "clever". Julio is
  actively learning Spring Boot — if you make a non-obvious decision
  (e.g. a Hibernate Filters pattern, a non-trivial security configuration),
  add a brief comment explaining the why, not just the what.
- Comments are written in impersonal style (no first person) and without
  emojis. References like "rule §5 of AGENTS.md" don't belong in source
  comments either: state the rule in neutral terms
  (e.g. "explicit per-tenant filter", "explicit code by project convention").

---

## 10. What NEVER to do

- Don't add Kubernetes, a second service, heavy message queues
  (Kafka, RabbitMQ), or any other unrequested infrastructure "because it's
  best practice". The project's explicit architectural principle is
  not to introduce infrastructure complexity before scale justifies it.
- Don't assume the Angular frontend already consumes an endpoint in a
  specific way — it doesn't exist yet.
- Don't invent configuration values, external provider endpoints
  (WhatsApp, payment gateways), or sample credentials as if they were
  real.
- Don't mark a roadmap ticket as resolved without meeting its DoD as
  written in `roadmap_backend_fases.md`.
- Don't change the package convention, the multi-tenancy model, or the
  `plan_features`/`plan_limits` keys without flagging it explicitly —
  they are decisions already made with documented reasoning.

---

## 11. Mandatory workflow for every task

This flow applies to **any non-trivial change** (a full phase,
a roadmap ticket, or even a one-off adjustment inside an already-started
ticket). Don't skip it for looking "obvious" or "quick" — the idea is for
Julio to review and learn from each step, not just receive finished code.

1. **Plan before code.** Before creating or modifying any
   file, present a brief plan: what you are going to do, which files you
   are going to touch or create, and which non-trivial decisions you are
   going to make (if there is more than one reasonable way to solve it,
   say so and explain which one you pick and why). No long document
   needed — a few clear lines are enough.
2. **Wait for approval.** Don't execute the plan until Julio
   explicitly confirms it. If he requests changes to the plan, adjust it and
   present it again before touching code.
3. **Execute the approved plan.** Implement exactly what was agreed. If
   along the way you discover the plan doesn't work or something
   important is missing, stop and explain the problem instead of improvising
   a different solution without notice.
4. **Test before calling it done.** Run the relevant tests
   (`./mvnw test` or `./mvnw clean verify` depending on scope) and verify the
   DoD of the corresponding ticket in `roadmap_backend_fases.md` if
   applicable. If something was hard to test automatically, say
   so explicitly instead of calling the change good without evidence.
5. **Julio commits at the end, and only if everything above passed.** One
   commit per completed task/ticket, with a clear message (see convention
   below). The agent never commits on its own: it marks tickets, verifies
   tests, and proposes the commit message. Never consider code done that
   doesn't compile or has red tests, and never treat a change as committed
   without Julio having seen the final result of steps 3 and 4.
6. **Update `ARCHITECTURE.md` when closing each phase.** When every
   ticket in a phase is green: document what was built (new
   endpoints with usage examples, entities and migrations, technical
   decisions and deviations from the roadmap/`schema.sql`) and check off the
   exit checklist in `roadmap_backend_fases.md`. Without this update the phase
   is not considered closed.

This cycle (plan → approval → execution → tests → commit) repeats
in each phase and in each adjustment within a phase, not just once at
the start of the project.

### Commit convention

Messages in English, short format: `[FASE0-01] Configure base
Spring Boot project`. If the change doesn't belong to a roadmap ticket (a
minor adjustment, a fix), use an equally clear description without the
ticket prefix.

---

## 12. Current project status / living notes

_(Update this section as the project moves forward — visibly outdated
is more useful than nonexistent.)_

- Completed phase: **Phase 0 — Project foundations and skeleton** (all FASE0-01 to FASE0-09 tickets completed).
- Cloud deployment: live on Render (`https://odentix-backend.onrender.com/actuator/health`).
- Production database: managed PostgreSQL 16 on Render (`odentix-postgres` in the Ohio region).
- CI pipeline: live in GitHub Actions (`.github/workflows/ci.yml`) with Java 25 and Testcontainers on `dev` and `main`.
- Phase 1 completed: FASE1-01 to FASE1-14 green (`Tenant`, `User`, `UserRole`, `TenantAwareEntity`, `UserService`, JWT login, `JwtAuthenticationFilter`, per-request `TenantContext`, automatic tenant filtering in repositories with `@TenantId` and `TenantIdentifierResolver`, critical cross-tenant isolation test, role authorization with `@PreAuthorize`, role authorization test with `RoleAuthorizationIntegrationTest` 5/5 green, audit base `audit_log` with reusable `AuditService` and green `AuditServiceIntegrationTest`). Exit checklist verified: FASE1-10 5/5 and FASE1-12 5/5 in `clean verify` (what CI runs), `TenantAwareEntity` ready. Decision made in FASE1-14: failures with no attributable tenant are not audited (see roadmap).
- Completed phase: **Phase 2 — Patients and base clinical history** (all FASE2-01 to FASE2-10 tickets completed; exit checklist verified and `ARCHITECTURE.md` updated).
- **Security improvements implemented (pre-Phase 3):**
  1. Session and refresh tokens with atomic rotation (`V10__create_refresh_tokens.sql`, `RefreshToken`, `RefreshTokenService`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`); access token reduced to 15 min; real-time validation of `user.isActive()` and DB-synced role on every request in `JwtAuthenticationFilter`.
  2. Role authorization (`@PreAuthorize`) in `PatientController`: clinical acts (clinical history and odontogram) reserved to practitioners (`PROPIETARIO`, `ODONTOLOGO`, `ESPECIALISTA_EXTERNO`); confidential clinical history (reception blocked); soft delete reserved to `PROPIETARIO`.
  3. Brute-force and DoS protection on `POST /api/v1/auth/login`: per-IP rate limiting (10 req/min) and temporary per-email lockout (5 consecutive failures -> 15 min lockout with HTTP 429 Too Many Requests and `Retry-After` header) implemented in `LoginRateLimitService`.
  4. Granular JWT error distinction (`JwtValidationResult`, `JwtAuthenticationEntryPoint`): RFC 6750 `WWW-Authenticate` header and JSON codes (`token_expired`, `token_invalid`, `user_inactive`, `token_missing`) to optimize automatic frontend session refresh.
- Completed phase: **Phase 5 — Lead CRM** (all FASE5-01 to FASE5-04 tickets completed; exit checklist verified and `ARCHITECTURE.md` updated).
- Completed phase: **Phase 6 — Receivables and staged payments** (all FASE6-01 to FASE6-04 tickets completed; exit checklist verified and `ARCHITECTURE.md` updated).
- Completed phase: **Phase 11 — Subscriptions and plans** (all FASE11-01 to FASE11-05 tickets completed; exit checklist verified and `ARCHITECTURE.md` updated).
- Last local verification: `./mvnw.cmd clean verify` on **September 19, 2026**, with **367/367 tests passing** (including gating, limits, Bold, annual cycle, plus per-tenant identities, self-managed adjustments and boot hardening, production.
- Next phase: **Phase 12 — Hardening and production** (12-01 to 12-04 closed; 12-05 in code pending verification with a real merge; 12-06 blocked by free plan without backups).
- Interactive documentation: Swagger UI live in dev/test (`/swagger-ui.html`, JSON at `/v3/api-docs`) with `springdoc-openapi` 3.1.1 and JWT `bearerAuth` scheme; disabled in prod. Standing rule (§4): every new endpoint is annotated with `@Tag`/`@Operation` and each phase closing verifies the UI. Endpoint coverage verified in `OpenApiDocsIntegrationTest` including `GET /api/v1/opportunities`.

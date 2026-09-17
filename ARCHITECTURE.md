# Arquitectura técnica — odentix-backend

> Documento vivo de arquitectura y decisiones del backend SaaS para clínicas
> odontológicas (multi-tenant: cada clínica es un `tenant`).
> **Estado:** documenta Fase 0, Fase 1, Fase 2 y Fase 3. Al cerrar cada fase este archivo debe
> actualizarse (regla en `AGENTS.md` §11: sin esa actualización la fase no se
> considera cerrada).
>
> Fuentes normativas: `docs/documentacion_sistema_gestion_odontologica_v1.1.md`
> (producto), `docs/roadmap_backend_fases.md` (tickets y DoD),
> `docs/schema.sql` (referencia de esquema). Todo lo aquí afirmado sale del
> código y las migraciones reales, no de los documentos de diseño.

---

## 1. Visión general

Monolito modular en Java: un solo desplegable, organizado por módulo de
negocio (no por capa técnica). No hay microservicios, colas ni Kubernetes por
decisión explícita: no se introduce infraestructura antes de que la escala la
justifique. El frontend Angular es un proyecto separado; no hay contrato de
API congelado todavía.

### Stack

| Pieza | Versión / decisión |
|---|---|
| Java | 25 (LTS; nunca versiones no-LTS) |
| Spring Boot | 4.1.1 (ver §2: paquetes de autoconfiguración reorganizados) |
| Build | Maven (wrapper `./mvnw` incluido) |
| Base de datos | PostgreSQL 16+ (nunca H2 ni embebidas, ni en tests) |
| Migraciones | Flyway, `ddl-auto` siempre en `validate` |
| Auth | Spring Security + JWT propio (JJWT 0.12.6, HMAC-SHA256) |
| Tests integración | Testcontainers 2.x sobre `postgres:16` |
| Despliegue | Docker multi-stage + Render; CI en GitHub Actions |

### Convenciones globales

- Sin Lombok: getters/setters/constructores explícitos (decisión de FASE0-01
  mientras se aprende el framework; revisar `pom.xml` antes de asumirlo).
- Código y comentarios en español, consistente con el archivo que se edita.
- `snake_case` en BD, tablas en plural, UUID como PK (`gen_random_uuid()`),
  `TIMESTAMPTZ` (nunca `TIMESTAMP`), montos `NUMERIC(12,2)` con sufijo `_cop`,
  `updated_at` gestionado por el trigger `set_updated_at()` (nunca desde Java).

---

## 2. Nota sobre Spring Boot 4.1.1

En esta versión los paquetes de autoconfiguración se reorganizaron respecto a
3.x, y casi todo tutorial/Stack Overflow usa los paquetes viejos. **Antes de
escribir cualquier import de autoconfiguración, verificar el paquete real**
(paquetes ya confirmados en este proyecto):

- `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`
- `org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration`
- `org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration`

Lo mismo aplica a dependencias vecinas: Testcontainers 2.x renombró sus
artefactos con prefijo (`testcontainers-postgresql`,
`testcontainers-junit-jupiter`; los paquetes Java `org.testcontainers.*` no
cambiaron). La versión del BOM la fija el parent (`testcontainers.version`).

---

## 3. Estructura de paquetes

Decisión FASE0-03: organización **por módulo de negocio** (familiar para quien
viene de Angular modular), cada uno con sus subcapas internas.

```
com.julio.odentix.odentix_backend/
├── shared/      # transversal: entity/, context/, excepciones comunes
│   ├── entity/  # TenantAwareEntity (base de todo lo de negocio)
│   └── context/ # TenantContext, TenantIdentifierResolver
├── tenant/      # clínica cliente (raíz del multi-tenancy)
├── auth/        # User, UserRole, login JWT, filtro, UserService
├── audit/       # AuditLog, AuditAction, AuditService (append-only)
├── patient/     # Fase 2
├── appointment/ # Fase 3
├── treatmentplan/ # Fase 4
├── billing/     # Fase 4
├── crm/         # Fase 5
└── inventory/   # Fase 7
```

Reglas:

1. Cada módulo tiene sus subcapas (`controller`, `service`, `repository`,
   `entity`, `dto`) **dentro** de su paquete. No hay paquetes top-level por
   capa. Módulo nuevo = mismo patrón, sin pedir permiso.
2. `shared` es lo único importable desde cualquier módulo. Entre módulos de
   negocio las dependencias apuntan hacia `tenant`/`patient`, nunca al revés
   sin justificarlo.
3. Cambiar este patrón requiere discusión explícita.

---

## 4. Configuración por entorno

Perfiles `dev` / `test` / `prod` (`application.yml` base + uno por perfil).
Secretos solo por variables de entorno con sintaxis `${VAR:default}`; en el
repo solo hay defaults locales sin valor productivo.

| Variable | Default local (dev/test) | Prod |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5434/odentix` | sin default (obligatoria) |
| `DB_USERNAME` / `DB_PASSWORD` | `odentix` / `odentix` | sin default (obligatorias) |
| `PORT` | `8081` fijo en local | `${PORT:8080}` (Render inyecta `$PORT`) |
| `JWT_SECRET` | clave de desarrollo en `application.yml` | **obligatoria**: `JWT_SECRET` real (≥32 caracteres) |
| `JWT_EXPIRATION_MINUTES` | `15` (15 min) | `15` recomendado; ajustable |
| `JWT_REFRESH_TOKEN_EXPIRATION_DAYS` | `7` (7 días) | `7` recomendado; ajustable |

Particularidades de la máquina de desarrollo (no generalizar):

- App en `8081` porque el `8080` lo ocupa `AgentService.exe`.
- Postgres de Docker en `5434` porque los puertos `5432` y `5433` (IPv4) los
  ocupan servicios nativos `postgresql-x64-17/18`.

Comandos:

```bash
docker compose up -d                                   # Postgres local
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev  # app en :8081
./mvnw test                                            # integración (Testcontainers)
./mvnw clean verify                                    # lo que corre el CI
```

> Tras borrar o renombrar recursos (`application.properties` → YAML, etc.),
> correr con `clean`: los builds incrementales no eliminan salidas obsoletas
> de `target/` y un archivo viejo puede seguir activo en el classpath.

---

## 5. Base de datos y migraciones

- `ddl-auto: validate` en todos los perfiles, sin excepción.
- Todo cambio de esquema va en una migración Flyway nueva (`V{n}__...sql`);
  nunca se edita una aplicada ni se toca `schema.sql` esperando que aplique solo.
- Antes de crear una tabla, revisar su referencia en `docs/schema.sql`.

| Migración | Contenido |
|---|---|
| `V1__init` | Función `set_updated_at()` + tabla temporal `migration_probe` (verificar el mecanismo; se elimina en Fase 1) |
| `V2__create_tenants` | Extensiones `pgcrypto`/`citext`, función `current_tenant_id()`, tipo `tenant_status`, tabla `tenants`, índice, trigger `updated_at`, RLS (`tenant_self_isolation`) |
| `V3__create_users` | Tipo `user_role`, tabla `users` (email `CITEXT`, unique `(tenant_id, email)`), índice `(tenant_id, role)`, trigger, RLS |
| `V4__create_audit_log` | Tipo `audit_action`, tabla `audit_log`, índices, trigger, RLS (ver §8 por 2 desviaciones documentadas) |
| `V5__create_patients` | Extensión `pg_trgm`, función `immutable_unaccent()`, tabla `patients`, índice trgm para búsqueda insensible, trigger, RLS |
| `V6__create_clinical_records` | Tabla `clinical_records` con FK `patient_id`, `recorded_by_id`, trigger, RLS |
| `V7__create_odontogram_entries` | Tipo `odontogram_entry_type`, tabla `odontogram_entries`, restricción `CHECK` notación FDI, trigger, RLS |
| `V8__create_patient_files` | Tipo `storage_provider`, tabla `patient_files` (metadatos S3), trigger, RLS |
| `V9__test_patient_file_data` | Migración de test (solo se aplica en perfil `test` mediante `V999`) |
| `V10__create_refresh_tokens` | Tabla `refresh_tokens` (hash SHA-256 del token, `expires_at`, `revoked`, `revoked_at`, FK `tenant_id`/`user_id`), índices, trigger, RLS (permite `current_tenant_id() IS NULL` porque el refresh se hace antes de que exista `TenantContext`) |

Patrón RLS en cada tabla de negocio (defensa en profundidad, §6):

```sql
ALTER TABLE <tabla> ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON <tabla>
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
```

El rol de BD de producción no debe tener `BYPASSRLS`. Nunca se desactiva RLS
ni el filtro de tenant "para probar más rápido": si algo es difícil de probar
con aislamiento, el problema es del test.

---

## 6. Multi-tenancy

El peor bug posible es filtrar datos entre clínicas. Tres capas
independientes, ninguna sustituye a otra:

1. **Filtro automático de aplicación (FASE1-09).** `TenantAwareEntity`
   (`shared/entity`) aporta `id` UUID, `tenant_id` (`@TenantId` de Hibernate,
   no-nulo e inmutable), `createdAt/updatedAt`. Hibernate filtra y asigna el
   tenant automáticamente en queries y en `findById`, resolviéndolo vía
   `TenantIdentifierResolver` → `TenantContext`.
2. **Convención en repositorios (defensa en profundidad).** Cada query
   filtra por tenant aunque exista el filtro automático
   (`findByTenantId...`, nunca JPQL/nativa sin `tenant_id`).
3. **RLS en PostgreSQL** (patrón §5).

### `TenantContext` (FASE1-08)

`ThreadLocal<UUID>` estático (`setTenantId` / `getTenantId` /
`getRequiredTenantId` / `clear`). Lo puebla el filtro JWT al inicio de cada
request y lo limpia en `finally` (hilos de Tomcat se reutilizan; sin limpieza
habría fuga entre requests). Sin tenant (arranque, tareas de sistema),
`TenantIdentifierResolver` usa el sentinel nil-UUID como sesión raíz.

### Convención `tenant_id` (FASE1-04)

Toda entidad de negocio nueva hereda `TenantAwareEntity` desde su primera
migración. Checklist por tabla: columna `tenant_id UUID NOT NULL REFERENCES
tenants(id)` + índice + RLS + entidad heredada (+ asociación a `Tenant` con
`insertable/updatable = false` si necesita navegar) + repo filtrado + **test
cross-tenant obligatorio** (datos en A, actuar como B, verificar invisibilidad;
`404` antes que `403` para ni confirmar existencia).

Excepciones intencionales: `tenants` (es la raíz), `users` (tenant vía
asociación desde FASE1-02) y catálogos globales (`plans`, `plan_features`,
`plan_limits` en Fase 11).

---

## 7. Autenticación y autorización (Fase 1 + mejoras previas a Fase 3)

### Modelo

- `Tenant`: id, `name`, `tax_id` (NIT), `status` (`trial`/`active`/`suspended`/
  `cancelled`), `timezone` (`America/Bogota`).
- `User`: pertenece a un único `Tenant` (`@ManyToOne` obligatorio), `email`
  (`CITEXT`, único **por tenant**, no global), `password_hash` (BCrypt, nunca
  texto plano), `fullName`, `role`, `is_active`, `lastLoginAt`.
- `UserRole` (enum en minúsculas, espejo del tipo PG `user_role`):
  `propietario`, `odontologo`, `recepcion`, `auxiliar`,
  `especialista_externo`. Un rol por usuario en esta fase.
- `UserService.createUser(...)` (interno, sin registro público): valida datos,
  exige tenant existente, rechaza email duplicado por tenant, hashea con
  `BCryptPasswordEncoder` y guarda activo.
- `RefreshToken` (V10): entidad de sesión persistida. Almacena `token_hash`
  (SHA-256 del token en texto plano, nunca el token crudo), `expires_at`,
  `revoked`, `revoked_at`, FK a `tenant_id` y `user_id`. No extiende
  `TenantAwareEntity` (al igual que `User`): las operaciones de refresh se
  producen antes de que exista `TenantContext`. Su RLS permite
  `current_tenant_id() IS NULL` por la misma razón.

### Login — `POST /api/v1/auth/login` (público)

```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@clinica.com","password":"Secreta123"}'
# 200 → {"accessToken":"...","refreshToken":"...","tokenType":"Bearer","expiresInSeconds":900,
#        "user":{"email":"...","role":"propietario","tenantId":"..."}}
# Credenciales inválidas → 401 {"error":"Credenciales inválidas"} (genérico a
# propósito: no distingue usuario inexistente de clave errónea, anti-enumeración)
```

`tenantId` opcional en el request: solo se usa para desambiguar si el mismo
email existiera en varias clínicas. Usuario inactivo → 401 genérico.

**Protección contra fuerza bruta y DoS (Rate Limiting en memoria):**
- **Rate limiting por IP:** Máximo 10 peticiones/minuto por IP hacia `/api/v1/auth/login` (configurable vía `LOGIN_RATE_LIMIT_IP_MAX`). Soporta proxies (`X-Forwarded-For` o `getRemoteAddr`). Al excederlo → `429 Too Many Requests` con cabecera `Retry-After: <segundos>` antes de ejecutar BCrypt ni consultar la base de datos.
- **Bloqueo temporal por cuenta/email:** Máximo 5 intentos fallidos consecutivos en una ventana de 15 minutos (configurable vía `LOGIN_RATE_LIMIT_EMAIL_MAX_ATTEMPTS`). Al 5to fallo, la cuenta queda bloqueada por 15 minutos (`LOGIN_RATE_LIMIT_LOCKOUT_MINUTES`). Intentos posteriores durante el bloqueo reciben `429 Too Many Requests` con cabecera `Retry-After` sin gastar CPU en BCrypt. Un login exitoso resetea el contador de fallos.
- `LoginRateLimitService` limpia automáticamente entradas vencidas cada 5 minutos (`@Scheduled`).

### Renovación de sesión — `POST /api/v1/auth/refresh` (público)

Rota el refresh token de forma atómica: invalida el token anterior y emite
un nuevo par (access token + refresh token). Detección de reúso: si se
intenta renovar con un token ya revocado → 401. Token expirado → 401.
Usuario inactivo al momento del refresh → 401.

```bash
curl -X POST http://localhost:8081/api/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<token-de-7-dias>"}'
# 200 → {"accessToken":"...","refreshToken":"...","tokenType":"Bearer","expiresInSeconds":900}
# Token revocado/expirado/inválido → 401 {"error":"Refresh token revocado|expirado|inválido"}
```

### Cierre de sesión — `POST /api/v1/auth/logout` (público)

Revoca el refresh token. El access token (15 min) sigue siendo válido hasta
su expiración natural, pero sin refresh token la sesión no se puede renovar.

```bash
curl -X POST http://localhost:8081/api/v1/auth/logout \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<token-a-revocar>"}'
# 204 No Content
```

### JWT

- HMAC-SHA256 (`JwtService`, JJWT), **access token 15 min** (configurable
  con `JWT_EXPIRATION_MINUTES`; default en dev/test `15`).
- **Refresh token 7 días** (configurable con `JWT_REFRESH_TOKEN_EXPIRATION_DAYS`).
  Solo el hash SHA-256 se almacena en BD; el token en texto plano vive
  exclusivamente en el cliente y en tránsito HTTPS.
- Claims del JWT: `sub` = user_id, `tenant_id`, `email`, `role`.
- `JwtAuthenticationFilter` (`OncePerRequestFilter`): valida firma y vigencia
  del JWT distinguiendo tokens válidos, expirados e inválidos/manipulados vía
  `JwtValidationResult`. Luego **verifica en tiempo real** en BD que el usuario exista en
  su tenant y esté activo (`user.isActive()`). Si no → `SecurityContextHolder`
  limpio → 401 instantáneo aunque el JWT sea válido. El rol se sincroniza desde
  BD en cada request (no desde el claim del token), por lo que un cambio de rol
  tiene efecto inmediato sin necesidad de revocar el JWT.
- **Distinción granular de errores en `JwtAuthenticationEntryPoint` (RFC 6750):**
  Para facilitar la UX del frontend al refrescar sesión automáticamente, las peticiones
  no autenticadas reciben la cabecera estándar `WWW-Authenticate` y un payload JSON
  con código de error específico sin romper compatibilidad (`error: "No autorizado"`):
  - `token_expired`: HTTP 401, `code: "token_expired"`, cabecera `WWW-Authenticate: Bearer error="invalid_token", error_description="The access token expired"`. Indica al cliente que debe invocar `POST /api/v1/auth/refresh`.
  - `token_invalid`: HTTP 401, `code: "token_invalid"`, cabecera `WWW-Authenticate: Bearer error="invalid_token", error_description="The access token is invalid or tampered"`. Indica token manipulado o corrupto (fuerza cierre de sesión).
  - `user_inactive`: HTTP 401, `code: "user_inactive"`, cabecera `WWW-Authenticate: Bearer error="invalid_token", error_description="User is inactive or not found"`.
  - `token_missing`: HTTP 401, `code: "token_missing"`, cabecera `WWW-Authenticate: Bearer error="unauthorized"`.

### Reglas de acceso (`SecurityConfig`, stateless, sin sesiones, CSRF off en API)

- Públicas: `/actuator/health`, `/actuator/info`, `/api/v1/auth/login`,
  `/api/v1/auth/refresh`, `/api/v1/auth/logout`.
- Todo lo demás exige autenticación; `@EnableMethodSecurity` permite
  `@PreAuthorize("hasRole('PROPIETARIO')")` (rol en mayúsculas = enum en
  mayúsculas; `UserRole` en minúsculas se mapea con `toUpperCase()`).
- Errores probados: sin token → 401 `{"error":"No autorizado"}`; rol
  insuficiente → 403 `{"error":"Acceso denegado","message":"..."}`.
- Usuario desactivado en BD → 401 inmediato incluso con JWT válido.

### `GET /api/v1/me` (protegida)

Devuelve el perfil del usuario del token (`@AuthenticationPrincipal`):
sin token → 401; token válido → 200 con sus datos; usuario desactivado → 401.

---

## 8. Auditoría

Tabla `audit_log` (append-only: se escribe y se lee filtrada por tenant;
nunca update/delete): `tenant_id` (FK `CASCADE`), `user_id` (FK `SET NULL`,
anulable), `action` (`audit_action`), `entity_name`, `entity_id` (anulable),
`detail` JSONB (anulable), timestamps. Índices `(tenant_id, created_at)` y
`(tenant_id, entity_name, entity_id)` + RLS.

Desviaciones de `schema.sql` (documentadas en `V4`): PK UUID en vez de
`BIGSERIAL` (para heredar `TenantAwareEntity` y su filtro automático) y
columna `updated_at` (la exige el mapeo base; nunca se actualiza por API; el
`toString` omite `detail` por si trae datos sensibles).

`AuditService.log(tenantId, userId, action, entityName, entityId, detail)`
(`Map<String,Object>` → JSONB): valida obligatorios, corre en
`REQUIRES_NEW` para que el rollback del flujo que audita (ej. login fallido)
no borre la entrada.

Auditoría de login (FASE1-14): `login_success` (con usuario) y `login_failed`
cuando el tenant es atribuible (usuario encontrado, o `tenantId` del request
aunque el email no exista → `userId` null). Decisión: **sin tenant atribuible
no se registra nada** (inventarlo violaría el aislamiento; `tenant_id` es NOT
NULL). Detalle solo con el email: nunca contraseñas.

---

## 9. Testing

- Integración contra PostgreSQL real vía `AbstractIntegrationTest`
  (`@SpringBootTest` + `@Testcontainers`, contenedor `postgres:16` estático
  compartido, `@DynamicPropertySource`). Nunca H2 ni mocks de BD.
- Web con MockMvc (`@AutoConfigureMockMvc`); controladores solo-de-test viven
  en `src/test` (no se despliegan).
- Exigido por endpoint/repositorio de negocio: caso normal + **cross-tenant**
  + autorización por rol si aplica. DoD = prueba automatizada, no "probado a
  mano".
- Lección registrada: la BD de Testcontainers se comparte entre métodos, así
  que los datos de prueba que deban ser únicos (emails para login) se generan
  únicos por método; si no, el resultado depende del orden de ejecución.

---

## 10. Observabilidad y despliegue

- Actuator: `health` e `info` expuestos (health con `show-details: never`).
- `Dockerfile` multi-stage (`maven:3.9-eclipse-temurin-25` → running
  `eclipse-temurin:25-jre`; tags verificados, no asumidos) + `.dockerignore`;
  perfil `prod` por defecto vía `SPRING_PROFILES_ACTIVE`, puerto `$PORT`.
- Producción en Render (app + Postgres 16 administrado); CI en GitHub Actions
  (`clean verify` en push/PR a `dev` y `main`; Testcontainers levanta su BD).

---

## 11. Registro de decisiones y desviaciones

| # | Decisión / desviación | Por qué |
|---|---|---|
| FASE0-01 | Sin Lombok, código explícito | Aprender el framework sin magia oculta |
| FASE0-03 | Paquetes por módulo de negocio | Familiar (Angular modular); cada módulo con sus subcapas |
| FASE0-05 | `ddl-auto: validate` siempre | El esquema solo cambia vía Flyway versionado |
| FASE0-06 | Testcontainers 2.x: artefactos `testcontainers-*` | El BOM 2.x los renombró (verificado en el POM local, no asumido) |
| FASE0 | `clean` tras borrar/renombrar recursos | `target/` conserva archivos obsoletos que siguen activos en el classpath |
| FASE0 | PG Docker en `5434` | Nativos `postgresql-x64-17/18` ocupan 5432 y 5433-IPv4 en dev |
| FASE1-04 | `TenantAwareEntity` + `@TenantId` | El olvido del filtro se vuelve error de diseño, no de memoria |
| FASE1-05 | Test de integración en vez de unitario | AGENTS.md §7 manda: solo la BD real prueba que el hash se guarda |
| FASE1-13 | `audit_log` con PK UUID + `updated_at` | Heredar el filtro automático pesa más que calcar `schema.sql` |
| FASE1-13 | `AuditService.log` en `REQUIRES_NEW` | Sin esto, el rollback del login fallido borraría su auditoría |
| FASE1-14 | Fallos sin tenant no se auditan | Inventar tenant viola aislamiento; `tenant_id` es NOT NULL |
| FASE2-02 | Baja lógica con `is_active` en `Patient` | Preservar historial clínico sin borrado físico destructivo |
| FASE2-03 | Búsqueda insensible a tildes/mayúsculas | Extensión `unaccent` y función indexable `immutable_unaccent` con trigramas |
| FASE2-07 | Entradas de odontograma append-only | Cada entrada es un momento clínico; varias entradas coexisten en la misma pieza |
| FASE2-09 | Compensación en subida de archivos S3 | Si el guardado en BD falla tras subir a S3, se elimina el objeto para evitar huérfanos |
| FASE2-10 | URLs prefirmadas temporales con S3Presigner | Descarga directa desde storage/CDN sin saturar ancho de banda del backend (15 min) |
| FASE3-01 | Recursos físicos y profesionales (`Professional`, `Room`) | Desacoplados del usuario de login; `Room` opcional en la cita |
| FASE3-02 | Prevención de solapamiento vía constraint PostgreSQL `EXCLUDE USING gist` | Garantía a nivel de motor contra carreras concurrentes usando `tstzrange`; citas canceladas/no_show excluidas (`WHERE status NOT IN ('cancelada', 'no_show')`) |
| FASE3-03 | Consulta de agenda con filtros opcionales de rango y profesional | Ordenamiento cronológico ascendente y aislamiento cross-tenant estricto |
| FASE3-04 | Máquina de estados en servicio con transiciones explícitas | Validaciones en dominio (`programada` -> `confirmada` -> `atendida` / `no_show` / `cancelada`) con HTTP 400 descriptivo |
| FASE3-05 | Agregación de valor de agenda (`summarizeValue`) | Excluye citas canceladas y no_show, coherente con el espacio liberado |
| FASE3-06 | `WaitlistEntry` para demanda insatisfecha | Ventana de disponibilidad temporal (`desired_from`, `desired_to`) y procedimiento de interés |
| FASE3-07 | Recuperación de espacio al cancelar cita | Sugerencia inmediata de candidatos compatibles en la respuesta de cancelación y vía endpoint dedicado, con orden FIFO y exclusión del paciente cancelador |

---

## 12. Historial por fase

### Fase 0 — Fundamentos (completada)

Proyecto Spring Boot corriendo, perfiles `dev/test/prod` con secretos por
entorno, estructura por módulos documentada, Postgres reproducible
(`docker compose up -d`), Flyway desde V1 con `validate`, Testcontainers con
clase base reutilizable, imagen Docker multi-stage verificada (`build` + `run`
+ `/actuator/health`), despliegue manual en Render y CI que bloquea PRs en
rojo. DoD: `GET /actuator/health` → `UP` en local, contenedor y nube.

### Fase 1 — Identidad, autenticación y multi-tenancy (completada, FASE1-01–14)

`Tenant` + `User`/`UserRole` (email único por tenant) + `TenantAwareEntity` y
filtro automático `@TenantId`/`TenantIdentifierResolver` + `TenantContext`
por request + `UserService` con BCrypt + login JWT (`/api/v1/auth/login`,
`/api/v1/me`) + autorización `@PreAuthorize` + auditoría `audit_log`.
Checklist de salida verificado: FASE1-10 5/5 y FASE1-12 5/5 en `clean verify`
(lo que corre el CI), base lista para Fase 2.

### Mejoras de seguridad de sesión (pre-Fase 3)

Implementadas antes de iniciar Fase 3 (agenda y citas):

- **Refresh tokens con rotación atómica** (`V10__create_refresh_tokens.sql`,
  `RefreshToken`, `RefreshTokenService`): access token de 15 min + refresh
  token de 7 días con hash SHA-256 en BD. `POST /api/v1/auth/refresh` rota
  el token y emite nuevo par. `POST /api/v1/auth/logout` revoca el token.
  Detección de reúso de tokens revocados → 401.
- **Validación en tiempo real del usuario** (`JwtAuthenticationFilter`):
  en cada request autenticado se consulta la BD para verificar `isActive()` y
  el tenant. Desactivar un usuario tiene efecto inmediato sin esperar a que
  expire el JWT. El rol también se lee de BD en cada request (sincronización
  en tiempo real).
- **Rate limiting y protección contra fuerza bruta en login (`LoginRateLimitService`):**
  defensa en profundidad sin dependencias pesadas: (1) límite de 10 peticiones/minuto por IP a `/api/v1/auth/login` con HTTP 429 y `Retry-After`; (2) bloqueo temporal de 15 minutos al acumular 5 fallos consecutivos por email, rechazando solicitudes con HTTP 429 sin ejecutar el costoso cálculo de BCrypt; (3) reseteo de contador tras login exitoso; (4) limpieza periódica de registros vencidos cada 5 minutos.
- **Pruebas en verde tras mejoras de auth:** `LoginRateLimitIntegrationTest` (4/4), `AuthRefreshIntegrationTest` (5/5) y `JwtSecurityIntegrationTest` (7/7).

### Fase 2 — Pacientes e historia clínica base (completada, FASE2-01–10)

Módulo `patient`:
- `Patient` (FASE2-01/02/03): CRUD, baja lógica con `is_active`, paginación y búsqueda insensible a acentos/mayúsculas con PostgreSQL `pg_trgm` y `immutable_unaccent()`.
- Manejo de excepciones centralizado con `GlobalExceptionHandler` y `ApiErrorResponse`.
- `ClinicalRecord` (FASE2-05/06): modelo de historia clínica y endpoints (`POST /{id}/clinical-records`, `GET /{id}/clinical-records`).
- `OdontogramEntry` (FASE2-07/08): modelo de odontograma no destructivo, validación de notación FDI (11 a 48), 4 tipos de entrada y endpoints (`POST /{id}/odontogram`, `GET /{id}/odontogram` agrupado por pieza y tipo).
- Almacenamiento S3 y archivos (FASE2-09/10): `PatientFile` con Flyway `V9`, cliente S3 SDK v2 (`software.amazon.awssdk:s3`), subida multipart `POST /{id}/files` con compensación automática de borrado en S3 si falla la base de datos, listado `GET /{id}/files` y generación de URLs prefirmadas temporales (15 min) `GET /{id}/files/{fileId}/download-url` con `S3Presigner`.
- **Control de acceso por rol (`@PreAuthorize`) en `PatientController`:**
  - Historia clínica (`POST /{id}/clinical-records`): restringida a personal facultativo (`PROPIETARIO`, `ODONTOLOGO`, `ESPECIALISTA_EXTERNO`).
  - Historia clínica (`GET /{id}/clinical-records`): confidencial para personal asistencial (`PROPIETARIO`, `ODONTOLOGO`, `ESPECIALISTA_EXTERNO`, `AUXILIAR`). Recepción bloqueada con 403.
  - Odontograma (`POST /{id}/odontogram`): reservado a `PROPIETARIO`, `ODONTOLOGO`, `ESPECIALISTA_EXTERNO`.
  - Baja lógica (`DELETE /{id}`): acción destructiva reservada exclusivamente a `PROPIETARIO`.
  - Gestión demográfica (`POST /patients`, `PATCH /patients/{id}`): `PROPIETARIO`, `RECEPCION`, `ODONTOLOGO`, `AUXILIAR`.
  - 11 pruebas de autorización en `PatientRoleAuthorizationIntegrationTest` (11/11 en verde).
- Total de pruebas del proyecto: **124/124 pruebas en verde** en `./mvnw.cmd clean verify`.

### Fase 3 — Agenda y citas (completada, FASE3-01–07)

Módulo `appointment`:
- `Professional` y `Room` (FASE3-01): catálogo de recursos profesionales y consultorios/sillones por tenant con validación de existencia y estado activo. Migración Flyway `V11__create_professionals_and_rooms.sql`.
- `Appointment` y prevención de solapamiento (FASE3-02): modelo de citas con migración `V12__create_appointments.sql`. Restricción `EXCLUDE USING gist` en PostgreSQL sobre `(tenant_id WITH =, professional_id WITH =, tstzrange(starts_at, ends_at) WITH &&)` para evitar doble-agendamiento físico a nivel de motor. Citas canceladas y no_show quedan excluidas del constraint (`WHERE status NOT IN ('cancelada', 'no_show')`), liberando el espacio automáticamente. Manejo de error en `GlobalExceptionHandler` traduciendo a HTTP 409 Conflict.
- Endpoints de consulta de agenda (FASE3-03): `GET /api/v1/appointments` con filtros opcionales por rango (`from`, `to`) y por profesional (`professionalId`), ordenados por `starts_at ASC`. Aislamiento cross-tenant verificado.
- Máquina de estados y transiciones válidas (FASE3-04): `PATCH /api/v1/appointments/{id}/status` soportando ciclo `programada` → `confirmada` → `atendida` / `no_show` / `cancelada`. Transiciones ilegales rechazadas con HTTP 400.
- Valor económico de la cita (FASE3-05): campo `estimated_value_cop` y endpoint de agregación `GET /api/v1/appointments/summary-value` que suma y cuenta citas del rango excluyendo estados terminales liberados (`cancelada`, `no_show`).
- Lista de espera (FASE3-06): modelo `WaitlistEntry` con migración `V13__create_waitlist_entries.sql` y endpoint `POST /api/v1/waitlist` para registrar demanda insatisfecha con ventana de disponibilidad y procedimiento deseado.
- Recuperación de espacio al cancelar una cita (FASE3-07):
  - Al transicionar una cita a `cancelada` vía `PATCH /api/v1/appointments/{id}/status`, el backend calcula automáticamente los candidatos compatibles de la lista de espera y los anexa en `AppointmentResponse.waitlistCandidates`.
  - Endpoint dedicado de consulta: `GET /api/v1/appointments/{id}/waitlist-candidates`.
  - Lógica de compatibilidad: mismo tenant, estado activo (`WaitlistStatus.activa`), procedimiento compatible (mismo `procedureId` o comodín `null`), ventana horaria solapada con el intervalo liberado, exclusión del paciente cancelador y orden FIFO por fecha de registro.
  - Verificado con `SlotRecoveryIntegrationTest` (7/7 en verde).
- Total de pruebas del proyecto: **170/170 pruebas en verde** en `./mvnw.cmd clean verify`.

### Fase 4 — (siguiente)

Planes de tratamiento y facturación básica. Modelado de `TreatmentPlan` (FASE4-01), ítems de tratamiento (FASE4-02), máquina de estados de planes (FASE4-03) y facturación básica con pagos (FASE4-04/05).

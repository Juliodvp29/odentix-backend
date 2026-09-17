# Roadmap por Fases — Backend

## Sistema de Gestión y Productividad para Clínicas Odontológicas

**Alcance de este documento:** solo backend (Java 25 LTS + Spring Boot).
El frontend Angular se abordará en un roadmap separado una vez el
backend tenga una base estable.

**Complementa a:** `documentacion_sistema_gestion_odontologica_v1.1.md`
(documento de arquitectura y producto).

**Cómo leer este documento:**

- **Todas las fases (0 a 12)** están desglosadas en formato de
  tickets/issues de GitHub (título, tipo, estimación, dependencias,
  descripción, tareas, criterios de aceptación) — puedes copiar cada
  ticket directamente como un Issue.
- Se agregó una sección de **pricing y límites por plan**, ya que
  varias fases (especialmente la Fase 11) dependen directamente de qué
  límite tiene cada plan.
- Los checkboxes marcados `[x]` reflejan avance real del proyecto, no
  solo la plantilla — este documento se actualiza a medida que se
  completan tickets, no es estático.

---

# Pricing y límites por plan

Precios base definidos: **Esencial $99.900 COP/mes**, **Profesional
$169.900 COP/mes**, **Clínica $259.900 COP/mes** (facturación anual
con descuento, a definir el %).

Regla general: **el plan Clínica tiene todo sin límites artificiales**
(salvo límites técnicos razonables de plataforma). Los planes
Esencial y Profesional necesitan límites concretos — no solo
funcionalidades bloqueadas — porque un límite numérico es lo que hace
tangible por qué el precio es menor, y es lo que empuja a un cliente
que crece a subir de plan.

## Tabla comparativa

| Límite / Funcionalidad                   | Esencial                       | Profesional                | Clínica                           |
| ---------------------------------------- | ------------------------------ | -------------------------- | --------------------------------- |
| Sedes/consultorios                       | 1                              | Hasta 2                    | Ilimitadas                        |
| Usuarios internos                        | Hasta 2                        | Hasta 6                    | Ilimitados                        |
| Pacientes activos                        | Hasta 150                      | Hasta 800                  | Ilimitados                        |
| Citas/mes                                | Ilimitadas                     | Ilimitadas                 | Ilimitadas                        |
| Historia clínica y odontograma           | Sí                             | Sí                         | Sí                                |
| Facturación y pagos simples              | Sí                             | Sí                         | Sí                                |
| Notificaciones por email                 | Sí                             | Sí                         | Sí                                |
| Conversaciones WhatsApp incluidas/mes    | No incluido (add-on)           | 300                        | 1,000                             |
| CRM de leads                             | No                             | Sí                         | Sí                                |
| Automatizaciones (tareas, recordatorios) | Básicas (recordatorio de cita) | Completas                  | Completas                         |
| Cartera y planes de pago en cuotas       | No (pago único)                | Sí                         | Sí                                |
| Especialistas externos                   | No                             | Hasta 2                    | Ilimitados                        |
| Inventario                               | No                             | Básico (sin alertas)       | Completo (con alertas)            |
| Motor de oportunidades                   | No                             | Alertas básicas (2 reglas) | Completo (todas las reglas)       |
| IA administrativa (asistente/mensajes)   | No                             | No                         | Sí                                |
| Reportes y analítica                     | Básicos                        | Intermedios                | Avanzados                         |
| Soporte                                  | Email                          | Email prioritario          | Prioritario + onboarding asistido |

_(Los números de esta tabla son un punto de partida razonable, no una
decisión cerrada — se pueden ajustar con datos reales de los primeros
clientes, pero sirven para diseñar el feature-gating de la Fase 11
desde ya.)_

## Cómo se traduce esto a diseño técnico

Para que estos límites sean aplicables por el backend (Fase 11.1), se
necesitan dos tipos de control distintos, y conviene modelarlos por
separado desde el inicio:

1. **Feature flags booleanos** (¿el tenant tiene acceso al módulo o
   no?): CRM de leads, cartera, especialistas, inventario, motor de
   oportunidades, IA. Se resuelven con una tabla `plan_features`
   (plan_id, feature_key, enabled) y un filtro/anotación que valide
   antes de ejecutar el endpoint.
2. **Límites numéricos** (¿cuántos puede tener/usar?): usuarios,
   pacientes, sedes, especialistas, conversaciones de WhatsApp por
   mes. Se resuelven con una tabla `plan_limits` (plan_id, limit_key,
   max_value — `null`/`-1` como convención para "ilimitado") y una
   validación en el service layer antes de crear el recurso o antes de
   consumir el recurso (ej. contador mensual para WhatsApp).

Este diseño se detalla como tickets cuando lleguemos a la Fase 11; se
deja anotado aquí porque afecta cómo se modelan algunas entidades desde
fases más tempranas (ej. `TenantSubscription` debería existir, aunque
sea con datos mínimos, desde la Fase 1, para no tener que migrar datos
de tenants existentes después).

---

# Mapa general de fases

```text
FASE 0  → Fundamentos y esqueleto del proyecto
FASE 1  → Identidad, autenticación y multi-tenancy
FASE 2  → Pacientes e historia clínica base
FASE 3  → Agenda y citas
FASE 4  → Planes de tratamiento y facturación básica
FASE 5  → CRM de leads
FASE 6  → Cartera y pagos por etapas
FASE 7  → Especialistas externos e inventario
FASE 8  → Automatizaciones, notificaciones y tareas
FASE 9  → Motor de oportunidades
FASE 10 → IA administrativa
FASE 11 → Suscripciones y planes (billing del propio SaaS)
FASE 12 → Endurecimiento, observabilidad y preparación para producción
```

**Nota sobre planes de precio:** las fases 0–4 son el mínimo funcional
que sostiene el plan **Esencial**. Las fases 5–6 y parte de la 8
sostienen el plan **Profesional**. Las fases 7, 9 y 10 sostienen el plan
**Clínica**. Esto es orientativo — se puede ajustar si desde el negocio
se quiere adelantar o mover alguna funcionalidad entre planes, pero
sirve como guía de prioridad de construcción.

---

# FASE 0 — Fundamentos y esqueleto del proyecto

**Objetivo de la fase:** tener un proyecto Spring Boot corriendo,
versionado, dockerizado y desplegado, antes de escribir cualquier
lógica de negocio real.

**Labels sugeridas para todos los tickets de esta fase:** `backend`,
`fase-0`, `setup`

---

### 🎫 FASE0-01 — Inicializar proyecto Spring Boot

**Tipo:** setup
**Estimación:** S (1–2h)
**Depende de:** —

**Descripción:**
Crear el proyecto base con Spring Initializr y dejarlo corriendo
localmente sin lógica de negocio.

**Tareas:**

- [x] Generar proyecto con dependencias: Spring Web, Spring Data JPA,
      Spring Security, Validation, Actuator, PostgreSQL Driver, Flyway.
- [x] Decidir Maven vs Gradle (sugerido: Maven, por ser el más común en
      tutoriales/documentación de Spring si es tu primera vez).
- [x] Evaluar si usar Lombok o no (mientras aprendes, puede ser más
      claro escribir getters/setters explícitos al inicio).
- [x] Subir el proyecto vacío a GitHub con `.gitignore` apropiado para
      Java/Maven/IDE.

**Criterios de aceptación:**

- [x] `./mvnw spring-boot:run` levanta la app sin errores.
- [x] `GET /actuator/health` responde `200 OK` con `{"status":"UP"}`.

**Temas de Spring Boot para investigar:** estructura de un proyecto
Spring Boot, `@SpringBootApplication`, autoconfiguración.

---

### 🎫 FASE0-02 — Configurar perfiles de entorno

**Tipo:** setup
**Estimación:** S (1h)
**Depende de:** FASE0-01

**Descripción:**
Separar configuración de `dev`, `test` y `prod` para no mezclar
credenciales ni comportamientos entre entornos.

**Tareas:**

- [x] Crear `application.yml` base + `application-dev.yml`,
      `application-test.yml`, `application-prod.yml`.
- [x] Mover valores sensibles (credenciales de BD) a variables de
      entorno con sintaxis `${VAR:valor_default}`.
- [x] Documentar en el `README.md` cómo correr el proyecto en cada
      perfil.

**Criterios de aceptación:**

- [x] La app puede iniciar con `-Dspring.profiles.active=dev` sin
      errores.
- [x] Ningún secreto real está commiteado en el repositorio.

**Temas de Spring Boot:** `@Profile`, `spring.profiles.active`,
resolución de propiedades por entorno.

---

### 🎫 FASE0-03 — Definir estructura de paquetes del proyecto

**Tipo:** decisión técnica / setup
**Estimación:** S (1–2h, incluye leer/pensar antes de codear)
**Depende de:** FASE0-01

**Descripción:**
Decidir y documentar cómo se van a organizar los paquetes antes de que
haya suficiente código como para que cambiarlo duela. Ver sección 26
del documento de arquitectura para el criterio (capas vs. por
módulo/feature).

**Tareas:**

- [x] Elegir el criterio de organización (sugerido para tu caso, dado
      que vienes de un mundo más orientado a módulos en Angular: por
      **módulo de negocio** — `patient`, `appointment`, `auth`, etc. —
      cada uno con sus propias sub-capas internas).
- [x] Crear la estructura de carpetas vacía con un `package-info.java`
      o README corto explicando la convención.
- [x] Anotar la decisión en el propio repositorio (ej. `ARCHITECTURE.md`)
      para no tener que volver a discutirla en cada módulo nuevo.

**Criterios de aceptación:**

- [x] Existe un documento corto en el repo que explica la convención
      de paquetes elegida.

---

### 🎫 FASE0-04 — Docker Compose con PostgreSQL local

**Tipo:** setup
**Estimación:** S (1–2h)
**Depende de:** FASE0-01

**Descripción:**
Tener una base de datos PostgreSQL reproducible en un solo comando,
igual para vos que para cualquier otra persona que clone el repo.

**Tareas:**

- [x] Crear `docker-compose.yml` con servicio `postgres` (versión
      fijada, ej. `postgres:16`).
- [x] Configurar `application-dev.yml` para apuntar a esa instancia.
- [x] Documentar en `README.md` el comando para levantarla
      (`docker compose up -d`).

**Criterios de aceptación:**

- [x] `docker compose up -d` levanta PostgreSQL y la app conecta sin
      configuración adicional.

---

### 🎫 FASE0-05 — Configurar Flyway y primera migración

**Tipo:** setup
**Estimación:** S (1h)
**Depende de:** FASE0-04

**Descripción:**
Establecer desde ya que **todo** cambio de esquema pasa por una
migración versionada, nunca por `ddl-auto: update`.

**Tareas:**

- [x] Configurar `spring.jpa.hibernate.ddl-auto=validate` (nunca
      `update` ni `create` en ningún perfil, ni siquiera `dev`).
- [x] Crear `V1__init.sql` (puede ser una tabla mínima de prueba).
- [x] Verificar que Flyway corre automáticamente al iniciar la app.

**Criterios de aceptación:**

- [x] La tabla `flyway_schema_history` existe después de iniciar la
      app y contiene el registro de `V1`.
- [x] `ddl-auto` está en `validate` en todos los perfiles.

**Temas de Spring Boot:** Flyway + Spring Boot, por qué `ddl-auto:
update` es una mala práctica en cualquier proyecto real.

---

### 🎫 FASE0-06 — Testcontainers para pruebas de integración

**Tipo:** setup / testing
**Estimación:** M (2–3h, primera vez con Testcontainers)
**Depende de:** FASE0-05

**Descripción:**
Configurar pruebas de integración contra una base de datos PostgreSQL
real y desechable, no H2 ni mocks, desde el primer test.

**Tareas:**

- [x] Agregar dependencia de Testcontainers para PostgreSQL.
- [x] Crear una clase base de test (`@SpringBootTest` +
      `@Testcontainers`) reutilizable para todos los tests de
      integración futuros.
- [x] Escribir un primer test trivial que solo valide que el contexto
      de Spring levanta correctamente con la base de Testcontainers.

**Criterios de aceptación:**

- [x] El test corre localmente y en el pipeline de CI sin Docker
      preinstalado manualmente (Testcontainers lo maneja).

**Temas de Spring Boot:** `@Testcontainers`, `@DynamicPropertySource`
para inyectar la URL de la base de datos de prueba.

---

### 🎫 FASE0-07 — Dockerfile multi-stage para la aplicación

**Tipo:** setup / infra
**Estimación:** M (2h)
**Depende de:** FASE0-01

**Descripción:**
Empaquetar la aplicación como imagen Docker liviana, separando el
proceso de build del runtime.

**Tareas:**

- [x] Escribir `Dockerfile` multi-stage: stage 1 con Maven+JDK para
      compilar, stage 2 con JRE 25 slim solo para ejecutar el `.jar`.
- [x] Construir la imagen localmente y correrla con `docker run`.
- [x] Verificar que las variables de entorno (perfil `prod`, conexión a
      BD) se pasan correctamente al contenedor.

**Criterios de aceptación:**

- [x] `docker build` genera la imagen y `docker run` levanta la app
      correctamente respondiendo `/actuator/health`.

---

### 🎫 FASE0-08 — Primer despliegue manual en Render/Railway

**Tipo:** infra
**Estimación:** M (2–3h, incluye aprender la plataforma)
**Depende de:** FASE0-07

**Descripción:**
Validar que la imagen Docker corre en el proveedor elegido, antes de
automatizar nada.

**Tareas:**

- [x] Crear cuenta y proyecto en Render **o** Railway (elegir uno para
      empezar).
- [x] Crear instancia de PostgreSQL administrada en el mismo proveedor.
- [x] Configurar variables de entorno del servicio (credenciales de
      BD, perfil activo) desde el panel del proveedor, no en el
      código.
- [x] Desplegar manualmente la imagen/proyecto.

**Criterios de aceptación:**

- [x] `GET /actuator/health` responde `UP` desde una URL pública.
- [x] Ningún secreto está en el repositorio de GitHub.

---

### 🎫 FASE0-09 — Pipeline de CI (build + test)

**Tipo:** infra / CI
**Estimación:** M (2h)
**Depende de:** FASE0-06

**Descripción:**
Que ningún Pull Request se pueda mergear si el código no compila o si
las pruebas fallan.

**Tareas:**

- [x] Crear workflow de GitHub Actions (`.github/workflows/ci.yml`)
      que se dispare en push/PR contra `dev` y `main`.
- [x] Pasos: checkout, setup JDK 25, build, test.
- [x] Configurar la rama `main` (y `dev` si aplica) como protegida,
      exigiendo que el pipeline pase antes de mergear.

**Criterios de aceptación:**

- [x] Un PR con un test que falla queda bloqueado automáticamente por
      GitHub.

**Nota:** el deploy automático (CD) se deja para la Fase 12, cuando ya
haya algo real que desplegar de forma continua.

---

# FASE 1 — Identidad, autenticación y multi-tenancy

**Objetivo de la fase:** ningún dato de negocio se crea antes de que
exista aislamiento por tenant y autenticación funcionando, porque
retrofitear multi-tenancy después es mucho más costoso que empezar con
todas las entidades ya preparadas.

**Labels sugeridas para todos los tickets de esta fase:** `backend`,
`fase-1`, `security`

---

### 🎫 FASE1-01 — Modelar entidad `Tenant`

**Tipo:** feature
**Estimación:** S (1h)
**Depende de:** FASE0-05

**Descripción:**
Crear la entidad raíz que representa a cada clínica cliente.

**Tareas:**

- [x] Entidad `Tenant`: id, nombre, estado (activo/suspendido/trial),
      fecha de creación.
- [x] Migración Flyway `V2__create_tenants.sql`.
- [x] Repositorio JPA básico (`TenantRepository`).

**Criterios de aceptación:**

- [x] Se puede persistir y recuperar un `Tenant` desde un test de
      integración con Testcontainers.

---

### 🎫 FASE1-02 — Modelar entidad `User` con relación a `Tenant`

**Tipo:** feature
**Estimación:** S (1–2h)
**Depende de:** FASE1-01

**Descripción:**
Usuario del sistema, siempre asociado a un único tenant en esta etapa.

**Tareas:**

- [x] Entidad `User`: id, tenant_id (`@ManyToOne`), email, password
      (hash), nombre, estado (activo/inactivo).
- [x] Constraint único de `email` **por tenant** (no global — dos
      clínicas distintas podrían tener un usuario con el mismo email
      en teoría, aunque en la práctica sea raro; no asumir lo
      contrario).
- [x] Migración Flyway correspondiente.

**Criterios de aceptación:**

- [x] No se puede crear dos usuarios con el mismo email dentro del
      mismo tenant (constraint de BD, no solo validación en código).

---

### 🎫 FASE1-03 — Modelar `Role` y asignación a `User`

**Tipo:** feature
**Estimación:** S (1h)
**Depende de:** FASE1-02

**Descripción:**
Catálogo de roles y su asignación a usuarios.

**Tareas:**

- [x] Definir roles como enum o tabla: `PROPIETARIO`, `ODONTOLOGO`,
      `RECEPCION`, `AUXILIAR`, `ESPECIALISTA_EXTERNO`.
- [x] Relación `User` ↔ `Role` (puede ser un solo rol por usuario en
      esta fase; permitir múltiples roles es una mejora futura, no
      bloqueante).
- [x] Migración Flyway correspondiente.

**Criterios de aceptación:**

- [x] Un `User` persistido tiene un rol válido consultable.

---

### 🎫 FASE1-04 — Convención `tenant_id` en tablas de negocio

**Tipo:** decisión técnica / documentación
**Estimación:** S (1h)
**Depende de:** FASE1-01

**Descripción:**
Dejar por escrito, antes de crear más entidades, que toda tabla de
negocio (no de catálogo/configuración global) debe tener `tenant_id`
como columna obligatoria e indexada desde su primera migración.

**Tareas:**

- [x] Documentar la convención en `ARCHITECTURE.md`.
- [x] Definir si se usará una clase base `TenantAwareEntity` con el
      campo `tenant_id` para que las entidades futuras hereden de ella
      (recomendado, evita olvidos).

**Criterios de aceptación:**

- [x] Existe la clase/convención base y está documentada, lista para
      usarse desde la Fase 2 en adelante.

---

### 🎫 FASE1-05 — Registro de usuario y hash de contraseña

**Tipo:** feature
**Estimación:** S (1–2h)
**Depende de:** FASE1-02

**Descripción:**
Servicio interno para crear usuarios con contraseña encriptada (no se
expone como registro público todavía; los usuarios los crea el
propietario o un proceso interno).

**Tareas:**

- [x] Configurar `BCryptPasswordEncoder` como bean.
- [x] Servicio `UserService.createUser(...)` que hashea la contraseña
      antes de persistir.
- [x] Test de integración con Testcontainers verificando que la contraseña
      nunca se guarda en texto plano (desviación del "test unitario":
      AGENTS.md §7 exige integración contra PG real, y solo así se puede
      inspeccionar la BD de verdad).

**Criterios de aceptación:**

- [x] Inspeccionando la BD directamente, el campo password nunca es
      texto plano.

---

### 🎫 FASE1-06 — Endpoint de login y emisión de JWT

**Tipo:** feature
**Estimación:** M (3–4h, primera vez con JWT en Spring)
**Depende de:** FASE1-05

**Descripción:**
Autenticación por credenciales que devuelve un JWT con la información
mínima necesaria para las siguientes fases.

**Tareas:**

- [x] Agregar librería JWT (ej. `jjwt`).
- [x] Endpoint `POST /api/v1/auth/login` (email + password).
- [x] JWT firmado que incluye: `user_id`, `tenant_id`, `role`,
      expiración.
- [x] Manejo de error claro si las credenciales son inválidas (sin
      filtrar si el error fue "usuario no existe" vs "password
      incorrecta", por seguridad).

**Criterios de aceptación:**

- [x] Login válido devuelve `200` con el token.
- [x] Login inválido devuelve `401` con mensaje genérico.

---

### 🎫 FASE1-07 — Spring Security: validar JWT en cada request

**Tipo:** feature / security
**Estimación:** M (3–4h)
**Depende de:** FASE1-06

**Descripción:**
Configurar la cadena de filtros de Spring Security para que valide el
JWT y rechace requests sin token válido.

**Tareas:**

- [x] Filtro personalizado (`OncePerRequestFilter`) que extrae y valida
      el JWT del header `Authorization: Bearer ...`.
- [x] `SecurityFilterChain` configurado: rutas públicas (`/auth/login`,
      `/actuator/health`) vs. protegidas (todo lo demás).
- [x] Endpoint de prueba `GET /api/v1/me` protegido que devuelve los
      datos del usuario autenticado extraídos del JWT.

**Criterios de aceptación:**

- [x] Request sin token a `/api/v1/me` → `401`.
- [x] Request con token válido → `200` con los datos correctos.
- [x] Request con token expirado/manipulado → `401`.

---

### 🎫 FASE1-08 — Contexto de tenant por request

**Tipo:** feature / arquitectura
**Estimación:** M (2–3h)
**Depende de:** FASE1-07

**Descripción:**
Hacer disponible el `tenant_id` del usuario autenticado en cualquier
punto del código durante esa request, sin tener que pasarlo manualmente
por cada método.

**Tareas:**

- [x] Componente `TenantContext` (`ThreadLocal` o `RequestScope` bean).
- [x] El filtro de JWT (FASE1-07) puebla el `TenantContext` al inicio
      de cada request y lo limpia al final (importante: evitar fugas
      entre requests si se reutilizan hilos).
- [x] Test que verifica que el contexto se limpia correctamente después
      de cada request.

**Criterios de aceptación:**

- [x] Cualquier componente Spring puede obtener el `tenant_id` actual
      sin recibirlo como parámetro explícito.

---

### 🎫 FASE1-09 — Filtro automático de `tenant_id` en repositorios

**Tipo:** feature / arquitectura crítica
**Estimación:** L (4–6h, es la pieza más delicada de la fase)
**Depende de:** FASE1-08, FASE1-04

**Descripción:**
Garantizar que **ninguna query** a una tabla de negocio pueda devolver
u modificar datos de un tenant distinto al del usuario autenticado,
sin que cada desarrollador tenga que acordarse de agregar el filtro a
mano en cada query.

**Tareas:**

- [x] Evaluar Hibernate `@Filter`/`@FilterDef` a nivel de entidad base
      vs. un `BaseRepository` que inyecte `tenant_id` en cada método
      (evaluado frente a Hibernate 6/7 `@TenantId` nativo y elegido este último por cubrir búsquedas directas por ID).
- [x] Implementar la opción elegida sobre `TenantAwareEntity`
      (FASE1-04).
- [x] Activar el filtro automáticamente al abrir la sesión de
      Hibernate, usando el `TenantContext` (FASE1-08) mediante `TenantIdentifierResolver`.

**Criterios de aceptación:**

- [x] Una query "ingenua" (sin agregar `tenant_id` manualmente) sobre
      una entidad de negocio filtra correctamente por el tenant activo.

---

### 🎫 FASE1-10 — Test crítico de aislamiento cross-tenant

**Tipo:** testing / seguridad
**Estimación:** M (2–3h)
**Depende de:** FASE1-09

**Descripción:**
Esta es la prueba más importante de toda la Fase 1. No se avanza a la
Fase 2 sin que este ticket esté cerrado y en verde.

**Tareas:**

- [x] Crear en el test dos tenants (A y B) con datos de una misma
      entidad de negocio simple (puede ser una entidad de prueba si
      todavía no existe ninguna real).
- [x] Autenticarse como usuario del tenant A e intentar leer/modificar
      un registro del tenant B **usando su ID directamente**.
- [x] Verificar que la respuesta es `404` (no `403`, para no confirmar
      siquiera que el recurso existe).

**Criterios de aceptación:**

- [x] El test falla intencionalmente si alguien comenta o rompe el
      filtro de tenant (para servir como red de seguridad futura).
- [x] El test corre en el pipeline de CI en cada PR.

---

### 🎫 FASE1-11 — Autorización por rol (`@PreAuthorize`)

**Tipo:** feature / security
**Estimación:** S (1–2h)
**Depende de:** FASE1-07, FASE1-03

**Descripción:**
Restringir endpoints según el rol del usuario autenticado.

**Tareas:**

- [x] Habilitar `@EnableMethodSecurity` en la configuración de
      seguridad.
- [x] Anotar al menos un endpoint de prueba con
      `@PreAuthorize("hasRole('PROPIETARIO')")`.
- [x] Mapear el `role` del JWT a las authorities de Spring Security.

**Criterios de aceptación:**

- [x] Un usuario con rol `RECEPCION` recibe `403` al llamar un endpoint
      restringido a `PROPIETARIO`.

---

### 🎫 FASE1-12 — Test de autorización por rol

**Tipo:** testing
**Estimación:** S (1h)
**Depende de:** FASE1-11

**Descripción:**
Cubrir con pruebas automáticas que la restricción por rol funciona,
igual que se hizo con el aislamiento de tenant.

**Tareas:**

- [x] Test que autentica usuarios con distintos roles y verifica
      `200` vs `403` según corresponda en el endpoint de prueba.
      (Cubierto por `RoleAuthorizationIntegrationTest`, creado en FASE1-11:
      propietario → 200, recepción/odontólogo → 403, sin token → 401.)

**Criterios de aceptación:**

- [x] El test corre en CI y falla si la restricción de rol se rompe.
      (Verificado con `clean verify`: 5/5 en verde; las aserciones 403
      fallarían si `@PreAuthorize` se rompe.)

---

### 🎫 FASE1-13 — Tabla y entidad de auditoría (`audit_log`)

**Tipo:** feature
**Estimación:** S (1–2h)
**Depende de:** FASE1-01

**Descripción:**
Base del sistema de auditoría que se irá ampliando en cada fase
posterior.

**Tareas:**

- [x] Entidad `AuditLog`: tenant_id, user_id, acción, entidad afectada,
      entidad_id, fecha, detalle (JSON o texto).
      (Módulo `audit/`, hereda `TenantAwareEntity`; `detail` JSONB con
      `Map<String, Object>`; desviaciones de `schema.sql` documentadas en
      `V4__create_audit_log.sql`: PK UUID en vez de BIGSERIAL y columna
      `updated_at` exigida por la clase base.)
- [x] Migración Flyway correspondiente.
- [x] Servicio simple `AuditService.log(...)` reutilizable desde
      cualquier módulo futuro.

**Criterios de aceptación:**

- [x] Se puede registrar una entrada de auditoría desde código y
      consultarla filtrada por tenant.
      (Verificado con `AuditServiceIntegrationTest`: registro, lectura
      filtrada con round-trip JSON y aislamiento cross-tenant con query
      ingenua.)

---

### 🎫 FASE1-14 — Registrar auditoría de login (éxito/fallo)

**Tipo:** feature
**Estimación:** S (1h)
**Depende de:** FASE1-13, FASE1-06

**Descripción:**
Primer caso de uso real del sistema de auditoría.

**Tareas:**

- [x] Llamar a `AuditService.log(...)` en login exitoso y en login
      fallido (sin registrar la contraseña, ni siquiera fallida).
      (`AuthService` registra `login_success` con usuario y `login_failed`
      cuando el tenant es atribuible; detalle solo con el email.)
- [x] `AuditService.log` con `REQUIRES_NEW` para que el rollback del login
      fallido no borre la propia entrada de auditoría.

**Criterios de aceptación:**

- [x] Un intento de login (exitoso o fallido) genera una entrada
      consultable en `audit_log`.
      (Verificado con `LoginAuditIntegrationTest` 4/4. Excepción
      documentada: sin tenant atribuible —email inexistente sin `tenantId`
      o colisión sin desambiguar— no se registra nada, porque inventar un
      tenant violaría el aislamiento y `tenant_id` es NOT NULL.)

---

### ✅ Checklist de salida de Fase 1

Antes de pasar a la Fase 2, verificar:

- [x] FASE1-10 (test de aislamiento cross-tenant) está en verde y
      corre en CI.
      (`CrossTenantIsolationIntegrationTest` 5/5 en verde con `clean verify`,
      que es lo que corre el workflow de CI en cada push/PR a `dev`/`main`.)
- [x] FASE1-12 (test de autorización por rol) está en verde y corre en
      CI.
- [x] La convención `TenantAwareEntity` (FASE1-04) está lista para que
      las entidades de la Fase 2 la usen desde su primera migración.
      (Clase base + `TenantAwareEntityTest` + sección en `ARCHITECTURE.md`
      + uso real probado con tabla y filtro automático en los tests de
      FASE1-09/10.)

---

# FASE 2 — Pacientes e historia clínica base

**Objetivo de la fase:** el primer módulo de negocio real, y el más
transversal (casi todo lo demás depende de `Patient`).

**Labels sugeridas para todos los tickets de esta fase:** `backend`,
`fase-2`

---

### 🎫 FASE2-01 — Modelar entidad `Patient`

**Tipo:** feature
**Estimación:** M (2h)
**Depende de:** FASE1-09 (filtro automático de tenant), FASE1-04 (`TenantAwareEntity`)

**Descripción:**
Primera entidad de negocio real del proyecto. Debe heredar de
`TenantAwareEntity` desde el día uno.

**Tareas:**

- [x] Entidad `Patient`: datos personales, contacto, contacto de
      emergencia (sección 8.2 del doc de arquitectura, alcance
      reducido — sin odontograma ni historia clínica todavía).
- [x] Migración Flyway correspondiente, con `tenant_id` indexado.
- [x] Repositorio JPA básico (`PatientRepository`).

**Criterios de aceptación:**

- [x] Se puede persistir y recuperar un `Patient` desde un test de
      integración con Testcontainers.
- [x] El filtro automático de tenant (Hibernate Filter/RLS) aplica sin
      configuración adicional, heredado de `TenantAwareEntity`.

---

### 🎫 FASE2-02 — Endpoints CRUD de pacientes

**Tipo:** feature
**Estimación:** M (3h)
**Depende de:** FASE2-01

**Descripción:**
Exponer pacientes vía REST con validación de entrada.

**Tareas:**

- [x] Endpoints: `GET /api/v1/patients/{id}`, `POST /api/v1/patients`,
      `PATCH /api/v1/patients/{id}`, `DELETE /api/v1/patients/{id}`
      (baja lógica vía `is_active`, no borrado físico).
- [x] DTOs de entrada/salida (no exponer la entidad JPA directamente).
- [x] Validación con Bean Validation (`@Valid`): campos obligatorios,
      formato de teléfono/email.

**Criterios de aceptación:**

- [x] Un `POST` con datos inválidos devuelve `400` con el detalle del
      campo que falló.
- [x] Un `DELETE` marca `is_active = false` sin eliminar la fila.

**Temas de Spring Boot:** DTOs vs. entidades, `@Valid`, `ResponseEntity`.

---

### 🎫 FASE2-03 — Paginación, búsqueda y manejo de errores centralizado

**Tipo:** feature
**Estimación:** M (2–3h)
**Depende de:** FASE2-02

**Descripción:**
Completar el listado de pacientes con paginación real y búsqueda, y
dejar listo el manejo de errores que reutilizarán todos los módulos
siguientes.

**Tareas:**

- [x] `GET /api/v1/patients` paginado (`Pageable`/`Page`) con búsqueda
      opcional por nombre o número de documento (usa el índice trigram
      y la extensión `unaccent` con función `immutable_unaccent`).
- [x] `@ControllerAdvice` (`@RestControllerAdvice`) global para errores de
      validación, "no encontrado" (`ResourceNotFoundException`), conflictos (`409`),
      acceso denegado (`403`) y errores no controlados — con formato estructurado
      estándar `ApiErrorResponse`.

**Criterios de aceptación:**

- [x] Buscar por un fragmento del nombre devuelve resultados sin
      distinguir mayúsculas/acentos exactos.
- [x] Un recurso inexistente devuelve `404` con el formato de error
      estándar, no un stacktrace.

**Temas de Spring Boot:** `Pageable`, `Specification`/query methods de
Spring Data, `@ControllerAdvice`.

---

### 🎫 FASE2-04 — Tests de aislamiento cross-tenant para pacientes

**Tipo:** testing
**Estimación:** S (1–2h)
**Depende de:** FASE2-03

**Descripción:**
Primer módulo de negocio real: aplica el patrón de prueba definido en
`AGENTS.md` §5 antes de seguir avanzando.

**Tareas:**

- [x] Test: crear pacientes en tenant A y B, verificar que un usuario
      de A no puede leer, actualizar ni eliminar un paciente de B
      (ni por listado ni por ID directo).
      (Cubierto por `PatientCrossTenantIsolationIntegrationTest` 6/6 en verde).
- [x] Test: búsqueda paginada solo devuelve resultados del tenant activo.

**Criterios de aceptación:**

- [x] Ambos tests corren en CI y fallan si el aislamiento se rompe.
      (Verificado con `./mvnw clean verify`: 70/70 tests en verde).

---

### 🎫 FASE2-05 — Modelar `ClinicalRecord`

**Tipo:** feature
**Estimación:** S (1–2h)
**Depende de:** FASE2-01

**Descripción:**
Historia clínica en su versión reducida (texto estructurado, sin
plantillas todavía).

**Tareas:**

- [x] Entidad `ClinicalRecord`: motivo de consulta, antecedentes,
      diagnóstico, evolución. Relación `Patient` 1—N `ClinicalRecord`.
- [x] Migración Flyway correspondiente.

**Criterios de aceptación:**

- [x] Se puede persistir una entrada clínica asociada a un paciente
      existente.

---

### 🎫 FASE2-06 — Endpoint de historia clínica

**Tipo:** feature
**Estimación:** S (1–2h)
**Depende de:** FASE2-05

**Descripción:**
Registrar y consultar evolución clínica de un paciente.

**Tareas:**

- [x] `POST /api/v1/patients/{id}/clinical-records` (agregar entrada).
- [x] `GET /api/v1/patients/{id}/clinical-records` (historial ordenado
      por fecha descendente).
- [x] Test de aislamiento cross-tenant (mismo patrón de FASE2-04).

**Criterios de aceptación:**

- [x] Se puede registrar y consultar el historial de entradas clínicas
      de un paciente específico, y no el de pacientes de otro tenant.
      (Verificado con `ClinicalRecordIntegrationTest`: 5/5 en verde;
      `./mvnw.cmd clean verify`: 78/78 pruebas sin fallos).

---

### 🎫 FASE2-07 — Modelar `OdontogramEntry`

**Tipo:** feature
**Estimación:** M (2h)
**Depende de:** FASE2-01

**Descripción:**
En esta fase el foco es el **modelo de datos y la API**, no la parte
visual (eso es frontend). Separación explícita entre estado actual,
diagnóstico, plan propuesto y tratamiento realizado (sección 8.4 del
doc de arquitectura) — evita mezclar información clínica de distintos
momentos en un mismo registro.

**Tareas:**

- [x] Entidad `OdontogramEntry`: pieza dental (notación FDI), superficie,
      `entry_type` (`estado_actual`/`diagnostico`/`plan_propuesto`/
      `tratamiento_realizado`), condición, quién y cuándo lo registró.
- [x] Migración Flyway correspondiente.

**Criterios de aceptación:**

- [x] Se puede registrar más de una entrada para la misma pieza dental
      sin que una sobreescriba a la otra (son momentos distintos, no un
      solo estado mutable).
      (Verificado con `OdontogramEntryRepositoryIntegrationTest`: 3/3 en verde;
      `./mvnw.cmd clean verify`: 81/81 pruebas sin fallos).

---

### 🎫 FASE2-08 — Endpoint de odontograma

**Tipo:** feature
**Estimación:** M (2h)
**Depende de:** FASE2-07

**Descripción:**
Exponer el odontograma de forma que el frontend pueda pintar cualquier
representación visual sobre él sin ambigüedad.

**Tareas:**

- [x] `GET /api/v1/patients/{id}/odontogram` — devuelve las entradas
      agrupadas por pieza dental y por `entry_type`.
- [x] `POST /api/v1/patients/{id}/odontogram` — registrar una entrada
      nueva.
- [x] Test de aislamiento cross-tenant.

**Criterios de aceptación:**

- [x] La respuesta del `GET` distingue claramente estado actual de
      diagnóstico/plan/tratamiento realizado, sin que el consumidor
      tenga que inferirlo.
      (Verificado con `OdontogramIntegrationTest`: 6/6 en verde;
      `./mvnw.cmd clean verify`: 87/87 pruebas sin fallos).

---

### 🎫 FASE2-09 — Integración de almacenamiento S3-compatible

**Tipo:** feature / infra
**Estimación:** M (3h, primera vez integrando storage externo)
**Depende de:** FASE2-01

**Descripción:**
PostgreSQL guarda solo metadatos y referencia (`storage_key`), nunca el
binario del archivo.

**Tareas:**

- [x] Entidad `PatientFile`: nombre, tipo de contenido, tamaño,
      `storage_key`, quién lo subió.
- [x] Cliente S3 (AWS SDK v2 o el compatible con el proveedor elegido
      para Render/Railway) configurado por variables de entorno.
- [x] `POST /api/v1/patients/{id}/files` con `MultipartFile`.

**Criterios de aceptación:**

- [x] Subir un archivo crea el objeto en el bucket y el registro de
      metadatos en la misma operación (si uno falla, no debe quedar el
      otro huérfano — usa una transacción o un mecanismo de limpieza).
      (Verificado con `PatientFileUploadIntegrationTest`: 5/5 en verde y
      `PatientFileRepositoryIntegrationTest`: 2/2 en verde;
      `./mvnw.cmd clean verify`: 94/94 pruebas sin fallos).

**Temas de Spring Boot:** `MultipartFile`, manejo de recursos externos
dentro/fuera de una transacción JPA.

---

### 🎫 FASE2-10 — Endpoint de descarga y tests

**Tipo:** feature
**Estimación:** S (1–2h)
**Depende de:** FASE2-09

**Descripción:**
Cerrar el módulo de archivos con la descarga y las pruebas de
aislamiento.

**Tareas:**

- [x] `GET /api/v1/patients/{id}/files` (listar) y una forma de obtener
      una URL de descarga (firmada o directa, según el proveedor).
- [x] Test de aislamiento cross-tenant: un usuario de otro tenant no
      puede obtener una URL de descarga de un archivo ajeno, aunque
      adivine el `storage_key`.

**Criterios de aceptación:**

- [x] Se puede subir un archivo y luego recuperar una URL válida para
      descargarlo.
      (Verificado con `PatientFileUploadIntegrationTest`: 10/10 en verde;
      `./mvnw.cmd clean verify`: 99/99 pruebas sin fallos).

---

### ✅ Checklist de salida de Fase 2

- [x] FASE2-04, FASE2-06 (test), FASE2-08 (test) y FASE2-10 (test) en
      verde en CI — todos los módulos de esta fase tienen su prueba de
      aislamiento cross-tenant.
      (Verificado con `clean verify`: 99/99 tests en verde, incluyendo
      `PatientCrossTenantIsolationIntegrationTest`, `ClinicalRecordIntegrationTest`,
      `OdontogramIntegrationTest` y `PatientFileUploadIntegrationTest`).
- [x] Se puede hacer una demo de punta a punta: crear paciente →
      agregar entrada clínica → registrar odontograma → subir archivo → listar y obtener URL de descarga.
      (Flujo cubierto integralmente por la suite de integración de pacientes).

---

# FASE 3 — Agenda y citas

**Objetivo de la fase:** habilitar el flujo operativo diario de la
clínica.

**Labels sugeridas para todos los tickets de esta fase:** `backend`,
`fase-3`

---

### 🎫 FASE3-01 — Modelar `Professional` y `Room`

**Tipo:** feature
**Estimación:** S (1–2h)
**Depende de:** FASE1-09

**Descripción:**
Base de la agenda: quién atiende y, opcionalmente, en qué consultorio.

**Tareas:**

- [x] Entidad `Professional`: nombre, especialidad, licencia,
      `is_external` (para diferenciar especialistas externos más
      adelante en la Fase 7), vínculo opcional a `User`.
- [x] Entidad `Room` (opcional en esta subfase, se puede simplificar).
- [x] Migraciones Flyway correspondientes (`V11__create_professionals_and_rooms.sql`,
      incluyendo constraints de FK diferidas en V7 y V8:
      `clinical_records.professional_id` y `odontogram_entries.recorded_by`).

**Criterios de aceptación:**

- [x] Se puede persistir un `Professional` y asociarlo opcionalmente a
      un `User` existente.
      (Verificado con `ProfessionalRepositoryIntegrationTest` 5/5 y
      `RoomRepositoryIntegrationTest` 4/4 en verde; suite global 133/133 en
      `clean verify`).

---

### 🎫 FASE3-02 — Modelar `Appointment` y evitar doble-agendamiento

**Tipo:** feature
**Estimación:** L (4h — incluye la restricción de solapamiento)
**Depende de:** FASE3-01, FASE2-01

**Descripción:**
La cita es la entidad más consultada de todo el sistema. Esta vez la
integridad de "no solapar" se garantiza en la propia base de datos, no
solo en el código Java.

**Tareas:**

- [x] Entidad `Appointment`: paciente, profesional, procedimiento
      (catálogo simple o texto libre por ahora), fecha/hora de inicio y
      fin, estado, notas.
- [x] Migración Flyway con una restricción `EXCLUDE USING gist` (ver
      `schema.sql`) que impida que un mismo profesional tenga dos citas
      activas solapadas (`V12__create_appointments.sql`).
- [x] `POST /api/v1/appointments` con manejo del error de la
      restricción (traducirlo a un `409 Conflict` claro, no un `500`).

**Criterios de aceptación:**

- [x] Crear dos citas solapadas para el mismo profesional devuelve
      `409`, no una excepción sin manejar.
      (Verificado con `AppointmentIntegrationTest` 7/7 en verde: solapamiento
      devuelve 409 Conflict con mensaje traducido, citas contiguas permitidas,
      horarios cancelados reutilizables; suite global 140/140 en `clean verify`).

**Temas de Spring Boot:** traducir una violación de constraint de
PostgreSQL (`DataIntegrityViolationException`) a una respuesta HTTP
significativa.

---

### 🎫 FASE3-03 — Endpoints de consulta de agenda

**Tipo:** feature
**Estimación:** M (2h)
**Depende de:** FASE3-02

**Descripción:**
Consultar la agenda es tan importante como crearla.

**Tareas:**

- [ ] `GET /api/v1/appointments` con filtros por rango de fechas y,
      opcionalmente, por profesional.
- [ ] Test de aislamiento cross-tenant.

**Criterios de aceptación:**

- [ ] Filtrar por un rango de fechas devuelve solo las citas de ese
      rango, ordenadas por hora de inicio.

---

### 🎫 FASE3-04 — Estados de la cita y transiciones válidas

**Tipo:** feature
**Estimación:** M (2h)
**Depende de:** FASE3-02

**Descripción:**
Estados: `programada` → `confirmada` → `atendida` / `no_show` /
`cancelada` (sección 8.6 del doc de arquitectura).

**Tareas:**

- [ ] Endpoint de cambio de estado (`PATCH /api/v1/appointments/{id}/status`).
- [ ] Reglas de transición válida en el service layer (ej. no se puede
      pasar de `cancelada` a `atendida`).
- [ ] Test de transiciones inválidas.

**Criterios de aceptación:**

- [ ] Las transiciones inválidas de estado devuelven `400` con un
      mensaje claro sobre qué transición se intentó y por qué no es
      válida.

**Temas de Spring Boot:** modelado de máquinas de estado simples (enum

- validación en el service layer, sin librerías de state machine
  todavía).

---

### 🎫 FASE3-05 — Valor económico de la cita

**Tipo:** feature
**Estimación:** S (1h)
**Depende de:** FASE3-02

**Descripción:**
Insumo directo para reportes futuros y para el motor de oportunidades
de la Fase 9 (agenda inteligente, sección 8.5 — aquí solo el dato, la
lógica de riesgo/priorización llega después).

**Tareas:**

- [ ] Campo `estimated_value_cop` en `Appointment` (ya existe en
      `schema.sql`, confirmar que el DTO lo expone).
- [ ] Endpoint de agregación: suma de valor estimado por rango de
      fechas.

**Criterios de aceptación:**

- [ ] El endpoint de agregación devuelve un total coherente con la
      suma manual de las citas del rango.

---

### 🎫 FASE3-06 — Modelar `WaitlistEntry`

**Tipo:** feature
**Estimación:** S (1h)
**Depende de:** FASE2-01

**Descripción:**
Lista de espera de pacientes interesados en un horario/tipo de
procedimiento.

**Tareas:**

- [ ] Entidad `WaitlistEntry`: paciente, procedimiento de interés,
      rango de fechas deseado, estado.
- [ ] `POST /api/v1/waitlist` para registrar un interesado.

**Criterios de aceptación:**

- [ ] Se puede registrar un paciente en lista de espera para un tipo de
      procedimiento y rango de fechas.

---

### 🎫 FASE3-07 — Recuperación de espacio al cancelar una cita

**Tipo:** feature
**Estimación:** M (2h)
**Depende de:** FASE3-04, FASE3-06

**Descripción:**
Al cancelarse una cita de alto valor, sugerir candidatos compatibles de
la lista de espera (el envío automático del mensaje llega en la
Fase 8, aquí solo la lógica de "quién calza").

**Tareas:**

- [ ] Al transicionar una cita a `cancelada`, calcular candidatos
      compatibles de `WaitlistEntry` (mismo tipo de procedimiento y
      disponibilidad declarada que se solapa con el horario liberado).
- [ ] Endpoint que devuelve esos candidatos para la cita cancelada.

**Criterios de aceptación:**

- [ ] Cancelar una cita marcada como de alto valor devuelve al menos
      la lista de candidatos compatibles, si existen.

---

### ✅ Checklist de salida de Fase 3

- [ ] Restricción `EXCLUDE` de solapamiento verificada con un test real
      (no solo revisada visualmente).
- [ ] Se puede hacer una demo: crear cita → confirmar → cancelar → ver
      candidatos de lista de espera sugeridos.

---

# FASE 4 — Planes de tratamiento y facturación básica

**Labels sugeridas para todos los tickets de esta fase:** `backend`,
`fase-4`

---

### 🎫 FASE4-01 — Modelar `TreatmentPlan`

**Tipo:** feature
**Estimación:** M (2h)
**Depende de:** FASE2-01, FASE3-01

**Descripción:**
Entidad central del negocio odontológico: qué se le propone a un
paciente y en qué estado va esa propuesta.

**Tareas:**

- [ ] Entidad `TreatmentPlan`: diagnóstico, precio total, paciente,
      profesional, estado (`borrador` → `presentado` → `en_decision` →
      `aceptado` → `en_ejecucion` → `completado` / `rechazado` /
      `pospuesto` / `abandonado` — sección 8.9).
- [ ] Entidad `TreatmentPlanItem` (procedimientos/piezas involucradas).
- [ ] Migraciones Flyway correspondientes.

**Criterios de aceptación:**

- [ ] Se puede crear un plan de tratamiento con varios ítems asociados.

---

### 🎫 FASE4-02 — Endpoints y transiciones de `TreatmentPlan`

**Tipo:** feature
**Estimación:** M (2–3h)
**Depende de:** FASE4-01

**Descripción:**
Mismo patrón de máquina de estados que las citas (FASE3-04), aplicado
aquí.

**Tareas:**

- [ ] CRUD de `TreatmentPlan` + ítems.
- [ ] Endpoint de cambio de estado con las mismas reglas de transición
      válida que en la Fase 3.2.
- [ ] Test de transiciones inválidas y de aislamiento cross-tenant.

**Criterios de aceptación:**

- [ ] Se puede crear un plan de tratamiento y avanzarlo por sus
      estados vía API.

---

### 🎫 FASE4-03 — Modelar facturación (`Invoice`, `InvoiceItem`, `Payment`)

**Tipo:** feature
**Estimación:** M (2h)
**Depende de:** FASE4-01

**Descripción:**
Facturación simple, sin facturación electrónica todavía (eso depende
de normativa y un proveedor externo — integración futura, sección 30
del doc de arquitectura).

**Tareas:**

- [ ] Entidades `Invoice`, `InvoiceItem`, `Payment` (ver `schema.sql`
      para la referencia exacta de columnas y constraints).
- [ ] Relación `Invoice` → `TreatmentPlan` (opcional, puede facturarse
      sin un plan asociado).
- [ ] Migraciones Flyway correspondientes.

**Criterios de aceptación:**

- [ ] Se puede persistir una factura con ítems y su total coincide con
      la suma de los ítems.

---

### 🎫 FASE4-04 — Endpoints de facturación y pagos

**Tipo:** feature
**Estimación:** M (2–3h)
**Depende de:** FASE4-03

**Descripción:**
Generar facturas y registrar pagos contra ellas.

**Tareas:**

- [ ] `POST /api/v1/invoices` (generar factura, opcionalmente desde un
      `TreatmentPlan`).
- [ ] `POST /api/v1/invoices/{id}/payments` (registrar pago parcial o
      total).
- [ ] Lógica que actualiza `status` de la factura (`pendiente` →
      `parcial` → `pagada`) según los pagos acumulados.
- [ ] Test de aislamiento cross-tenant.

**Criterios de aceptación:**

- [ ] Registrar pagos parciales hasta cubrir el total cambia el estado
      de la factura a `pagada` automáticamente.

---

### 🎫 FASE4-05 — Checkpoint del plan Esencial

**Tipo:** chore / validación
**Estimación:** S (medio día de revisión, no de código)
**Depende de:** FASE2 completa, FASE3 completa, FASE4-04

**Descripción:**
En este punto, el backend ya cubre lo mínimo necesario para el plan
**Esencial**: pacientes, historia clínica base, agenda, tratamientos y
facturación simple. Es un buen momento para una pausa de validación
antes de seguir construyendo funcionalidades de planes superiores.

**Tareas:**

- [ ] Demo de punta a punta: crear paciente → agendar cita → crear plan
      de tratamiento → aceptarlo → facturar → registrar pago.
- [ ] Revisar que los límites del plan Esencial (`max_patients`,
      `max_users`, etc. de `plan_limits`) tengan sentido con datos
      reales de esta demo, aunque el feature-gating recién se
      implemente en la Fase 11.

**Criterios de aceptación:**

- [ ] La demo completa corre sin intervención manual en la base de
      datos (todo vía API).

---

# FASE 5 — CRM de leads

**Labels sugeridas para todos los tickets de esta fase:** `backend`,
`fase-5`

---

### 🎫 FASE5-01 — Modelar `Lead` y su pipeline

**Tipo:** feature
**Estimación:** M (2h)
**Depende de:** FASE1-09

**Descripción:**
Pipeline: `nuevo` → `contactado` → `calificado` → `cita_propuesta` →
`cita_agendada` → `cita_asistida` → `tratamiento_propuesto` →
`tratamiento_aceptado` (o `perdido` en cualquier punto).

**Tareas:**

- [ ] Entidad `Lead`: nombre, contacto, fuente, campaña, procedimiento
      de interés, valor potencial, estado, responsable asignado
      (sección 8.7).
- [ ] Entidad `LeadActivity` (historial de contactos: llamada,
      WhatsApp, email, nota).
- [ ] Migraciones Flyway correspondientes.

**Criterios de aceptación:**

- [ ] Se puede registrar un lead y agregarle actividades de contacto.

---

### 🎫 FASE5-02 — Endpoints CRUD y cambio de estado de leads

**Tipo:** feature
**Estimación:** M (2h)
**Depende de:** FASE5-01

**Tareas:**

- [ ] CRUD de `Lead` + endpoint de registro de `LeadActivity`.
- [ ] Endpoint de cambio de estado, sin restricciones de transición
      estrictas (a diferencia de citas/tratamientos, un lead sí puede
      "retroceder" en el pipeline en casos reales).
- [ ] Test de aislamiento cross-tenant.

**Criterios de aceptación:**

- [ ] Se puede registrar un lead manualmente y moverlo por el pipeline
      vía API.

---

### 🎫 FASE5-03 — Conversión de lead a paciente/cita

**Tipo:** feature
**Estimación:** M (2–3h)
**Depende de:** FASE5-02, FASE2-01, FASE3-02

**Descripción:**
Evitar duplicar datos entre `Lead` y `Patient` al convertir.

**Tareas:**

- [ ] `POST /api/v1/leads/{id}/convert` — crea `Patient` (+
      opcionalmente `Appointment`) a partir de los datos del lead.
- [ ] El lead queda enlazado al paciente resultante
      (`converted_patient_id`) para trazabilidad de origen.
- [ ] Test: convertir un lead no crea un paciente duplicado si se
      llama dos veces por error (idempotencia razonable).

**Criterios de aceptación:**

- [ ] Convertir un lead crea correctamente el paciente y la cita
      asociada, y el lead queda enlazado a ese paciente.

---

### 🎫 FASE5-04 — Métricas de conversión

**Tipo:** feature
**Estimación:** M (2h)
**Depende de:** FASE5-03

**Descripción:**
Insumo para el dashboard de CRM (sección 5.4 del doc de producto) — el
frontend graficará esto después, aquí solo los endpoints de datos.

**Tareas:**

- [ ] Endpoint de conversión por fuente y por campaña (cuántos leads
      llegaron vs. cuántos se convirtieron).
- [ ] Endpoint de tiempo promedio de primera respuesta a un lead.

**Criterios de aceptación:**

- [ ] Los endpoints devuelven estos indicadores para un rango de fechas
      dado, filtrados correctamente por tenant.

---

# FASE 6 — Cartera y pagos por etapas

**Labels sugeridas para todos los tickets de esta fase:** `backend`,
`fase-6`

---

### 🎫 FASE6-01 — Modelar `PaymentPlan` e `Installment`

**Tipo:** feature
**Estimación:** M (2h)
**Depende de:** FASE4-01

**Tareas:**

- [ ] Entidad `PaymentPlan` (asociada a un `TreatmentPlan`), entidad
      `Installment` (cuotas con número, monto, fecha de vencimiento,
      estado).
- [ ] Migraciones Flyway correspondientes.

**Criterios de aceptación:**

- [ ] Se puede definir un plan de pago en cuotas y ver el estado de
      cada cuota (`pendiente`, `pagada`, `vencida`).

---

### 🎫 FASE6-02 — Endpoints de planes de pago

**Tipo:** feature
**Estimación:** M (2h)
**Depende de:** FASE6-01

**Tareas:**

- [ ] `POST /api/v1/treatment-plans/{id}/payment-plan` (crear plan de
      pago en N cuotas).
- [ ] `POST /api/v1/installments/{id}/pay` (marcar cuota como pagada,
      idealmente generando el `Payment`/`Invoice` correspondiente en
      vez de solo cambiar el estado).
- [ ] Test de aislamiento cross-tenant.

**Criterios de aceptación:**

- [ ] Pagar una cuota la marca como `pagada` y queda reflejada en el
      dashboard de cartera (FASE6-04).

---

### 🎫 FASE6-03 — Job de cuotas vencidas

**Tipo:** feature
**Estimación:** S (1–2h)
**Depende de:** FASE6-01

**Descripción:**
La función `mark_overdue_installments()` ya existe en `schema.sql`;
aquí se conecta a un scheduler de Spring.

**Tareas:**

- [ ] Job diario con `@Scheduled` que invoca la lógica de marcar cuotas
      vencidas (puede llamar la función SQL directamente o
      reimplementar la misma regla en Java — mantente consistente con
      lo que ya existe en la base para no duplicar lógica divergente).
- [ ] Log o métrica de cuántas cuotas se marcaron vencidas en cada
      corrida, útil para depurar en producción.

**Criterios de aceptación:**

- [ ] Una cuota con `due_date` en el pasado y estado `pendiente`
      cambia a `vencida` después de correr el job.

**Temas de Spring Boot:** `@Scheduled`, consideraciones de concurrencia
si en el futuro hay múltiples instancias corriendo el mismo job
(relevante recién cuando se escale más allá de una sola instancia).

---

### 🎫 FASE6-04 — Dashboard de cartera

**Tipo:** feature
**Estimación:** S (1–2h)
**Depende de:** FASE6-03

**Tareas:**

- [ ] `GET /api/v1/portfolio/summary` — cartera total, vencida, por
      vencer y al día.

**Criterios de aceptación:**

- [ ] El endpoint devuelve totales coherentes con los datos de
      `Installment` en ese momento.

---

# FASE 7 — Especialistas externos e inventario

**Labels sugeridas para todos los tickets de esta fase:** `backend`,
`fase-7`

---

### 🎫 FASE7-01 — Modelar `Specialist` y liquidaciones

**Tipo:** feature
**Estimación:** M (2h)
**Depende de:** FASE3-01

**Descripción:**
Extensión financiera de `Professional` para especialistas externos
(sección 8.13 del doc de arquitectura).

**Tareas:**

- [ ] Entidad `Specialist` (1—1 con `Professional`, porcentaje de
      honorarios). Reutiliza el trigger `check_specialist_is_external`
      de `schema.sql` — no reimplementes esa validación en Java como
      único mecanismo, es defensa en profundidad igual que con
      multi-tenancy.
- [ ] Entidad `SpecialistSettlement` (liquidación por periodo).
- [ ] Migraciones Flyway correspondientes.

**Criterios de aceptación:**

- [ ] Intentar crear un `Specialist` sobre un `Professional` con
      `is_external = false` falla (ya sea por el trigger de BD o por
      validación de aplicación — ambas deben estar presentes).

---

### 🎫 FASE7-02 — Cálculo y endpoint de liquidaciones

**Tipo:** feature
**Estimación:** M (2–3h)
**Depende de:** FASE7-01, FASE4-04

**Tareas:**

- [ ] Lógica de cálculo de producción bruta de un especialista en un
      periodo (a partir de tratamientos/citas asociadas ya facturadas).
- [ ] `POST /api/v1/specialists/{id}/settlements` (generar liquidación
      para un periodo).
- [ ] Test de aislamiento cross-tenant.

**Criterios de aceptación:**

- [ ] Se puede calcular cuánto se le debe liquidar a un especialista en
      un periodo dado, a partir de los tratamientos/citas asociadas.

---

### 🎫 FASE7-03 — Modelar inventario

**Tipo:** feature
**Estimación:** M (2h)
**Depende de:** FASE1-09

**Descripción:**
El trigger `apply_stock_movement` de `schema.sql` ya aplica los
movimientos automáticamente al stock — aquí solo la capa Java.

**Tareas:**

- [ ] Entidades `InventoryItem`, `StockMovement`.
- [ ] Migraciones Flyway correspondientes (reutilizando las de
      `schema.sql` si ya están escritas como referencia).

**Criterios de aceptación:**

- [ ] Insertar un `StockMovement` actualiza automáticamente la
      cantidad del `InventoryItem` (verificado por el trigger, no por
      lógica Java redundante).

---

### 🎫 FASE7-04 — Endpoints de inventario y alertas

**Tipo:** feature
**Estimación:** M (2h)
**Depende de:** FASE7-03

**Tareas:**

- [ ] CRUD de `InventoryItem`, endpoint de registro de
      `StockMovement`.
- [ ] `GET /api/v1/inventory/critical` — ítems con `quantity <=
    min_threshold` (usa el índice parcial ya definido en
      `schema.sql`).
- [ ] Test: un movimiento que dejaría el stock en negativo debe
      fallar (verificado por el `CHECK` de la base, capturado y
      traducido a un `409`/`400` claro en la API).

**Criterios de aceptación:**

- [ ] Al registrar un consumo de inventario, el stock se actualiza y
      se puede consultar qué ítems están por debajo del umbral.

---

# FASE 8 — Automatizaciones, notificaciones y tareas

**Labels sugeridas para todos los tickets de esta fase:** `backend`,
`fase-8`

---

### 🎫 FASE8-01 — Modelar `Task` y CRUD básico

**Tipo:** feature
**Estimación:** S (1–2h)
**Depende de:** FASE1-09

**Tareas:**

- [ ] Entidad `Task`: título, descripción, referencia polimórfica
      (`related_entity_type`/`related_entity_id`), responsable, fecha
      límite, prioridad, estado.
- [ ] CRUD básico de tareas + endpoint de "mis tareas" (filtrado por
      `assigned_to` = usuario autenticado).

**Criterios de aceptación:**

- [ ] Se puede crear, asignar y completar una tarea manualmente.

---

### 🎫 FASE8-02 — Reglas automáticas de creación de tareas

**Tipo:** feature
**Estimación:** M (2h)
**Depende de:** FASE8-01, FASE3-04

**Tareas:**

- [ ] Regla: cita sin confirmar a 24h de su horario crea
      automáticamente una tarea para recepción (job programado o
      evento al momento de crear la cita, lo que resulte más simple de
      mantener).

**Criterios de aceptación:**

- [ ] El sistema puede crear tareas automáticamente a partir de esta
      regla, sin intervención manual.

---

### 🎫 FASE8-03 — Interfaz de notificaciones + adaptador de email

**Tipo:** feature / arquitectura
**Estimación:** M (2–3h)
**Depende de:** FASE1-09

**Descripción:**
Servicio de notificaciones desacoplado (interfaz + adaptadores), para
no acoplar el dominio a un proveedor concreto desde el inicio (sección
30 del doc de arquitectura). Primer adaptador: email (más simple y sin
costos de aprobación, antes de integrar WhatsApp).

**Tareas:**

- [ ] Interfaz `NotificationSender` (o similar) con un método de envío
      genérico por canal.
- [ ] Adaptador de email (usando el proveedor SMTP/API que se decida).
- [ ] Entidad `Notification` para registrar cada intento (canal,
      destinatario, estado, error si falló).
- [ ] `@ConditionalOnProperty` para poder activar/desactivar
      adaptadores por configuración.

**Criterios de aceptación:**

- [ ] Se puede disparar una notificación de confirmación de cita por
      email de forma automática, y queda registrada en `Notification`
      con su resultado.

**Temas de Spring Boot:** patrón de interfaz + implementación
intercambiable, `@ConditionalOnProperty`.

---

### 🎫 FASE8-04 — Disparo de notificaciones desde eventos de negocio

**Tipo:** feature
**Estimación:** S (1–2h)
**Depende de:** FASE8-03, FASE3-04

**Tareas:**

- [ ] Al crear/confirmar una cita, disparar la notificación
      correspondiente vía el servicio de FASE8-03.
- [ ] Test: un fallo del adaptador de email no debe impedir que la
      cita se cree/confirme (el fallo se registra en `Notification`,
      no revierte la operación de negocio).

**Criterios de aceptación:**

- [ ] Confirmar una cita dispara una notificación, y si el envío falla,
      la confirmación de la cita igual queda guardada.

---

### 🎫 FASE8-05 — Adaptador WhatsApp Business API

**Tipo:** feature / integración externa
**Estimación:** L (bloqueado por el proceso de aprobación de Meta,
que puede tardar semanas — planifica esta ticket sabiendo que el
trabajo de código en sí es más corto que la espera administrativa)
**Depende de:** FASE8-03

**Descripción:**
Segundo adaptador de notificaciones. Recuerda la nota de costos de la
sección 8.8 del doc de arquitectura: son conversaciones facturadas por
Meta, no un canal gratuito.

**Tareas:**

- [ ] Adaptador `NotificationSender` para WhatsApp Business API.
- [ ] Timeout corto + fallback (si WhatsApp falla, no bloquear el flujo
      que lo originó — mismo principio de resiliencia que se aplicará
      a la IA en la Fase 10).
- [ ] Registro de conversaciones en `Notification`.

**Criterios de aceptación:**

- [ ] Se puede enviar y registrar un mensaje de confirmación por
      WhatsApp.
- [ ] Simular una caída del proveedor de WhatsApp no bloquea el flujo
      de negocio que disparó la notificación (test explícito de esto).

---

### 🎫 FASE8-06 — Automatización de recuperación de espacio

**Tipo:** feature
**Estimación:** M (2h)
**Depende de:** FASE3-07, FASE8-03

**Descripción:**
Conecta la detección de candidatos de la Fase 3.7 con el envío real de
la notificación.

**Tareas:**

- [ ] Al cancelarse una cita de alto valor y existir candidatos
      compatibles en lista de espera, disparar automáticamente una
      tarea (FASE8-01) o notificación (FASE8-03) de recuperación.

**Criterios de aceptación:**

- [ ] Una cancelación de cita de alto valor genera automáticamente una
      tarea o notificación de recuperación, sin intervención manual.

---

# FASE 9 — Motor de oportunidades

**Objetivo de la fase:** este es el diferenciador central del producto
(sección 9 del doc de arquitectura), por eso va después de tener los
módulos base sólidos — no se puede detectar oportunidades sobre datos
que no existen todavía.

**Labels sugeridas para todos los tickets de esta fase:** `backend`,
`fase-9`

---

### 🎫 FASE9-01 — Modelar `Opportunity` y primera regla de detección

**Tipo:** feature
**Estimación:** M (3h)
**Depende de:** FASE4-02

**Descripción:**
Empezar por la regla más simple de validar con datos ya existentes:
"tratamiento sin seguimiento" (usa el índice
`idx_treatment_plans_followup` ya definido en `schema.sql`).

**Tareas:**

- [ ] Entidad `Opportunity`: tipo, referencia polimórfica, valor
      estimado, prioridad, estado.
- [ ] Job programado que detecta `TreatmentPlan` en estado
      `presentado`/`en_decision` sin contacto reciente y genera una
      `Opportunity` de tipo `tratamiento_sin_seguimiento`.

**Criterios de aceptación:**

- [ ] Existe al menos una regla completa funcionando de punta a punta.
- [ ] Correr el job dos veces seguidas no duplica la misma oportunidad
      ya abierta para el mismo tratamiento.

---

### 🎫 FASE9-02 — Ampliar reglas de detección

**Tipo:** feature
**Estimación:** L (se puede dividir en sub-tickets por regla si
prefieres ir una por una)
**Depende de:** FASE9-01, FASE5-02, FASE3-04, FASE6-04, FASE7-04

**Descripción:**
Cada regla usa datos que ya existen de fases anteriores — no se
inventa nada nuevo, solo se conecta.

**Tareas:**

- [ ] Regla "lead sin respuesta" (usa `idx_leads_unresponded`).
- [ ] Regla "cita de alto riesgo" (usa `risk_level` de `Appointment`).
- [ ] Regla "espacio disponible" (conecta con la lógica de la Fase 3.7).
- [ ] Regla "paciente inactivo" (sin citas/tratamientos en X tiempo).
- [ ] Regla "saldo vencido" (usa el dashboard de cartera de la Fase 6).
- [ ] Regla "inventario crítico" (usa `idx_inventory_items_critical`).

**Criterios de aceptación:**

- [ ] Cada regla implementada tiene al menos un test que genera el
      escenario y verifica que la `Opportunity` correspondiente se crea.

---

### 🎫 FASE9-03 — Acción recomendada por oportunidad

**Tipo:** feature
**Estimación:** M (2–3h)
**Depende de:** FASE9-01, FASE8-03

**Descripción:**
Principio de la sección 5.2 del doc de producto: "detectar → entender
→ proponer → ejecutar" — no basta con mostrar el dato bruto.

**Tareas:**

- [ ] Entidad `OpportunityAction` (mensaje sugerido o tarea sugerida).
- [ ] Cada regla de FASE9-01/02 genera también su acción sugerida
      correspondiente.
- [ ] `POST /api/v1/opportunities/{id}/actions/{actionId}/execute` —
      ejecuta la acción (envía el mensaje vía FASE8-03 o crea la tarea
      vía FASE8-01).

**Criterios de aceptación:**

- [ ] Una oportunidad detectada trae asociada al menos una acción
      ejecutable directamente vía API.

---

### 🎫 FASE9-04 — Métrica de valor recuperado

**Tipo:** feature
**Estimación:** M (2h)
**Depende de:** FASE9-03

**Descripción:**
Sección 10 del doc de arquitectura. La regla de qué cuenta como
"recuperado" debe quedar documentada en el propio código, no solo aquí.

**Tareas:**

- [ ] Definir y documentar (en un comentario/Javadoc, no solo en este
      roadmap) el criterio de atribución: por ejemplo, una oportunidad
      cuenta como "recuperada" si se resuelve y el evento de negocio
      asociado ocurre dentro de una ventana de tiempo razonable después
      de ejecutar la acción.
- [ ] `GET /api/v1/opportunities/recovered-value` — valor recuperado
      por categoría en un periodo.

**Criterios de aceptación:**

- [ ] Existe un endpoint que devuelve el valor recuperado por categoría
      en un periodo, con la regla de cálculo documentada en el código.

---

# FASE 10 — IA administrativa

**Labels sugeridas para todos los tickets de esta fase:** `backend`,
`fase-10`

---

### 🎫 FASE10-01 — Asistente administrativo (preguntas simples)

**Tipo:** feature / integración externa
**Estimación:** M (3h)
**Depende de:** FASE9-04 (para tener datos ricos que consultar)

**Descripción:**
El endpoint resuelve la pregunta consultando datos ya expuestos por
endpoints existentes, usando un proveedor de LLM (recomendado: Groq,
por costo — mismo proveedor que se evaluó para Venti Shop).

**Tareas:**

- [ ] Cliente HTTP hacia el proveedor de LLM elegido, configurado por
      variable de entorno (API key nunca hardcodeada).
- [ ] `POST /api/v1/assistant/ask` — recibe una pregunta en lenguaje
      natural, arma el contexto a partir de datos del tenant activo, y
      devuelve la respuesta del proveedor.

**Criterios de aceptación:**

- [ ] Preguntas como "¿qué tratamientos están pendientes de
      seguimiento?" devuelven una respuesta correcta basada en datos
      reales del tenant, nunca de otro tenant.

---

### 🎫 FASE10-02 — Generación asistida de mensajes

**Tipo:** feature
**Estimación:** S (1–2h)
**Depende de:** FASE10-01, FASE8-03

**Tareas:**

- [ ] `POST /api/v1/assistant/suggest-message` — dado un contexto (ej.
      cita sin confirmar), genera un mensaje sugerido.
- [ ] El mensaje generado queda como sugerencia editable — nunca se
      envía automáticamente sin pasar por el flujo de notificaciones de
      la Fase 8 con confirmación humana cuando aplique.

**Criterios de aceptación:**

- [ ] El endpoint devuelve el mensaje sugerido sin efectos secundarios
      (no envía nada por sí solo).

---

### 🎫 FASE10-03 — Resiliencia de IA

**Tipo:** feature / hardening
**Estimación:** M (2h)
**Depende de:** FASE10-01

**Descripción:**
Mismo principio que ya aplicaste a WhatsApp en FASE8-05: timeout corto,
fallback a plantilla fija si el proveedor de IA falla, y registro del
fallo.

**Tareas:**

- [ ] Timeout corto configurado en el cliente HTTP del proveedor de IA.
- [ ] Fallback a una plantilla fija cuando el proveedor no responde o
      responde con error.
- [ ] Registro del fallo (log estructurado o tabla, según lo que ya
      exista para WhatsApp).

**Criterios de aceptación:**

- [ ] Si se apaga la clave de API de IA, el sistema sigue operando con
      plantillas fijas sin romper ningún flujo (test explícito
      simulando la caída del proveedor).

---

# FASE 11 — Suscripciones y planes (billing del propio SaaS)

**Objetivo de la fase:** habilitar el cobro recurrente a las clínicas y
el control de qué funcionalidades ve cada plan (Esencial, Profesional,
Clínica).

**Labels sugeridas para todos los tickets de esta fase:** `backend`,
`fase-11`, `billing`

---

### 🎫 FASE11-01 — Entidades de planes y suscripción

**Tipo:** feature
**Estimación:** S (1–2h, las tablas ya existen en `schema.sql`)
**Depende de:** FASE1-01

**Descripción:**
Las tablas `plans`, `plan_features`, `plan_limits` y
`tenant_subscriptions` ya están definidas y sembradas en `schema.sql`
— aquí se crea la capa de entidades JPA que las mapea.

**Tareas:**

- [ ] Entidades `Plan`, `PlanFeature`, `PlanLimit`,
      `TenantSubscription` mapeadas 1:1 a las tablas existentes (no
      generar nuevas migraciones si `schema.sql` ya las tiene — verifica
      primero qué migraciones Flyway ya existen en el repo).

**Criterios de aceptación:**

- [ ] Se puede leer, para un tenant dado, su plan activo, sus features
      habilitadas y sus límites numéricos.

---

### 🎫 FASE11-02 — Feature-gating por plan

**Tipo:** feature / arquitectura
**Estimación:** L (4h)
**Depende de:** FASE11-01

**Descripción:**
Mecanismo de feature-gating por plan, siguiendo el diseño de la
sección "Pricing y límites por plan" del roadmap.

**Tareas:**

- [ ] Anotación o aspecto (`@RequiresFeature("crm_leads")` o similar)
      aplicable a endpoints, que valida contra `plan_features` del
      tenant activo antes de ejecutar el método.
- [ ] Aplicar la anotación a los endpoints ya existentes que
      correspondan (CRM de leads, cartera, especialistas, inventario,
      motor de oportunidades, IA).

**Criterios de aceptación:**

- [ ] Un tenant en plan Esencial recibe `403` al intentar usar un
      endpoint exclusivo de Profesional/Clínica (ej. CRM de leads).

**Temas de Spring Boot:** AOP (`@Aspect`), anotaciones personalizadas,
`HandlerInterceptor` como alternativa si prefieres no usar AOP.

---

### 🎫 FASE11-03 — Límites numéricos y contador de uso mensual

**Tipo:** feature
**Estimación:** M (3h)
**Depende de:** FASE11-02

**Tareas:**

- [ ] Validación de límites numéricos (`max_patients`, `max_users`,
      etc.) antes de crear el recurso correspondiente.
- [ ] Contador de uso mensual para límites que se consumen (ej.
      conversaciones de WhatsApp), reseteable al inicio de cada ciclo
      de facturación.

**Criterios de aceptación:**

- [ ] Un tenant que alcanza su límite numérico (ej. 150 pacientes)
      recibe un error claro al intentar crear el recurso 151.

---

### 🎫 FASE11-04 — Integración de pasarela de pagos

**Tipo:** feature / integración externa
**Estimación:** L (a definir con más precisión una vez se elija la
pasarela — Wompi, Stripe, etc.)
**Depende de:** FASE11-01

**Tareas:**

- [ ] Integración con la pasarela elegida para cobro recurrente
      mensual/anual.
- [ ] Webhook de confirmación de pago que activa/suspende el acceso
      del tenant.

**Criterios de aceptación:**

- [ ] Un pago exitoso activa el tenant; un pago fallido/vencido
      suspende el acceso según la política definida (con periodo de
      gracia a definir).

---

### 🎫 FASE11-05 — Ciclo de facturación anual

**Tipo:** feature
**Estimación:** S (1–2h)
**Depende de:** FASE11-04

**Tareas:**

- [ ] Soporte de ciclo de facturación anual con el descuento ya
      definido en `plans.annual_price_cop`.
- [ ] Endpoint/flujo para que un tenant elija entre mensual o anual al
      suscribirse.

**Criterios de aceptación:**

- [ ] Un tenant puede elegir entre ciclo mensual o anual al momento de
      suscribirse, y el monto cobrado refleja el descuento correcto.

---

### ✅ Checklist de salida de Fase 11

- [ ] El propio SaaS puede cobrar y gestionar sus planes de punta a
      punta: suscribirse, ser cobrado, y ver su acceso ajustado según
      el estado del pago.

---

# FASE 12 — Endurecimiento, observabilidad y preparación para producción

**Labels sugeridas para todos los tickets de esta fase:** `backend`,
`fase-12`, `production-readiness`

---

### 🎫 FASE12-01 — Rate limiting y revisión de seguridad

**Tipo:** hardening
**Estimación:** M (2–3h)
**Depende de:** Fase 1 completa

**Tareas:**

- [ ] Rate limiting básico (por IP y/o por tenant) en los endpoints
      públicos y de autenticación.
- [ ] Revisión de protección contra inyección (ya mitigado en gran
      parte por usar JPA/queries parametrizadas — auditar cualquier
      query nativa escrita a mano).
- [ ] Revisión de CSRF/XSS según corresponda a una API REST pura
      (normalmente no aplica CSRF si no hay sesiones basadas en
      cookies, pero verifícalo explícitamente, no lo asumas).

**Criterios de aceptación:**

- [ ] Superar el límite de rate limiting en el endpoint de login
      devuelve `429`, no deja intentar indefinidamente.

---

### 🎫 FASE12-02 — Gestión segura de secretos en producción

**Tipo:** hardening / infra
**Estimación:** S (1–2h)
**Depende de:** FASE0-08

**Tareas:**

- [ ] Auditoría de que ningún secreto (API keys de IA, WhatsApp,
      pasarela de pago, credenciales de BD) esté hardcodeado en el
      código o en archivos versionados.
- [ ] Confirmar que el rol de base de datos de producción no tiene
      `BYPASSRLS` (ver sección 16 de `schema.sql`).

**Criterios de aceptación:**

- [ ] Un `grep` de patrones de secretos comunes sobre el repo no
      encuentra coincidencias.

---

### 🎫 FASE12-03 — Observabilidad

**Tipo:** feature / infra
**Estimación:** M (3h)
**Depende de:** FASE0-08

**Tareas:**

- [ ] Spring Actuator ampliado (más allá de `/health`) + OpenTelemetry.
- [ ] Integración con Sentry (o equivalente) para captura de errores.

**Criterios de aceptación:**

- [ ] Un error no controlado en producción genera una alerta/registro
      visible en Sentry, no solo en logs locales.

---

### 🎫 FASE12-04 — Logs estructurados por tenant

**Tipo:** hardening
**Estimación:** S (1–2h)
**Depende de:** FASE12-03

**Descripción:**
Poder depurar sin exponer datos entre clínicas.

**Tareas:**

- [ ] Formato de log estructurado (JSON) que incluya `tenant_id` como
      campo, nunca datos sensibles del paciente en texto plano.

**Criterios de aceptación:**

- [ ] Se puede filtrar los logs de producción por `tenant_id` para
      depurar un caso puntual sin exponer datos de otras clínicas.

---

### 🎫 FASE12-05 — CI/CD completo

**Tipo:** infra
**Estimación:** M (2–3h)
**Depende de:** FASE0-09

**Tareas:**

- [ ] Ampliar el pipeline de GitHub Actions para que despliegue
      automáticamente a Render/Railway en merges a `main` (más allá
      del build+test que ya existe desde la Fase 0).

**Criterios de aceptación:**

- [ ] Un merge a `main` que pasa los tests despliega automáticamente
      sin intervención manual.

---

### 🎫 FASE12-06 — Backups y prueba de restauración

**Tipo:** infra / validación
**Estimación:** M (medio día, incluye la prueba real de restauración)
**Depende de:** FASE0-08

**Tareas:**

- [ ] Confirmar que el proveedor elegido (Render/Railway) tiene
      backups automáticos habilitados para la instancia de PostgreSQL.
- [ ] Ejecutar una prueba real de restauración (no solo leer la
      documentación del proveedor) antes de tener el primer cliente
      pagando.

**Criterios de aceptación:**

- [ ] Se puede perder la base de datos de producción y restaurarla
      desde backup en un tiempo conocido, sin intervención manual
      compleja — verificado con una prueba real, no solo en teoría.

---

### ✅ Checklist de salida de Fase 12

- [ ] FASE12-06 ejecutada realmente (no solo planeada) antes de
      aceptar el primer cliente pagando.
- [ ] Backend listo para producción: seguridad, observabilidad, CI/CD
      y backups verificados.

---

# Resumen de checkpoints de validación

```text
Fin Fase 1  → Multi-tenancy y auth probados con tests automatizados
Fin Fase 4  → Backend mínimo del plan Esencial completo
Fin Fase 6  → Backend mínimo del plan Profesional completo
Fin Fase 9  → Diferenciador principal (motor de oportunidades) funcional
Fin Fase 11 → El propio SaaS puede cobrar y gestionar sus planes
Fin Fase 12 → Backend listo para el primer cliente real en producción
```

No se debe iniciar el frontend en profundidad antes del checkpoint de
Fin de Fase 4, salvo pantallas mínimas de login/pacientes/agenda
necesarias para probar el backend de extremo a extremo.

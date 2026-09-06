# Roadmap por Fases — Backend
## Sistema de Gestión y Productividad para Clínicas Odontológicas

**Alcance de este documento:** solo backend (Java 25 LTS + Spring Boot).
El frontend Angular se abordará en un roadmap separado una vez el
backend tenga una base estable.

**Complementa a:** `documentacion_sistema_gestion_odontologica_v1.1.md`
(documento de arquitectura y producto).

**Cómo leer este documento:**

- Las **Fases 0 y 1** están desglosadas en formato de tickets/issues de
  GitHub (título, descripción, tareas, criterios de aceptación,
  labels, estimación, dependencias) porque son las que vas a ejecutar
  primero. Puedes copiar cada ticket directamente como un Issue.
- Las **Fases 2 en adelante** se mantienen a nivel de subfase (menos
  granular). Te recomiendo pedirme el desglose en tickets de la
  siguiente fase justo antes de empezarla, no todas de una vez, porque
  el detalle fino tiende a cambiar a medida que el proyecto avanza.
- Se agregó una sección de **pricing y límites por plan**, ya que
  varias fases (especialmente la Fase 11) dependen directamente de qué
  límite tiene cada plan.

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

| Límite / Funcionalidad | Esencial | Profesional | Clínica |
|---|---|---|---|
| Sedes/consultorios | 1 | Hasta 2 | Ilimitadas |
| Usuarios internos | Hasta 2 | Hasta 6 | Ilimitados |
| Pacientes activos | Hasta 150 | Hasta 800 | Ilimitados |
| Citas/mes | Ilimitadas | Ilimitadas | Ilimitadas |
| Historia clínica y odontograma | Sí | Sí | Sí |
| Facturación y pagos simples | Sí | Sí | Sí |
| Notificaciones por email | Sí | Sí | Sí |
| Conversaciones WhatsApp incluidas/mes | No incluido (add-on) | 300 | 1,000 |
| CRM de leads | No | Sí | Sí |
| Automatizaciones (tareas, recordatorios) | Básicas (recordatorio de cita) | Completas | Completas |
| Cartera y planes de pago en cuotas | No (pago único) | Sí | Sí |
| Especialistas externos | No | Hasta 2 | Ilimitados |
| Inventario | No | Básico (sin alertas) | Completo (con alertas) |
| Motor de oportunidades | No | Alertas básicas (2 reglas) | Completo (todas las reglas) |
| IA administrativa (asistente/mensajes) | No | No | Sí |
| Reportes y analítica | Básicos | Intermedios | Avanzados |
| Soporte | Email | Email prioritario | Prioritario + onboarding asistido |

*(Los números de esta tabla son un punto de partida razonable, no una
decisión cerrada — se pueden ajustar con datos reales de los primeros
clientes, pero sirven para diseñar el feature-gating de la Fase 11
desde ya.)*

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
- [ ] Generar proyecto con dependencias: Spring Web, Spring Data JPA,
      Spring Security, Validation, Actuator, PostgreSQL Driver, Flyway.
- [ ] Decidir Maven vs Gradle (sugerido: Maven, por ser el más común en
      tutoriales/documentación de Spring si es tu primera vez).
- [ ] Evaluar si usar Lombok o no (mientras aprendes, puede ser más
      claro escribir getters/setters explícitos al inicio).
- [ ] Subir el proyecto vacío a GitHub con `.gitignore` apropiado para
      Java/Maven/IDE.

**Criterios de aceptación:**
- [ ] `./mvnw spring-boot:run` levanta la app sin errores.
- [ ] `GET /actuator/health` responde `200 OK` con `{"status":"UP"}`.

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
- [ ] Crear `application.yml` base + `application-dev.yml`,
      `application-test.yml`, `application-prod.yml`.
- [ ] Mover valores sensibles (credenciales de BD) a variables de
      entorno con sintaxis `${VAR:valor_default}`.
- [ ] Documentar en el `README.md` cómo correr el proyecto en cada
      perfil.

**Criterios de aceptación:**
- [ ] La app puede iniciar con `-Dspring.profiles.active=dev` sin
      errores.
- [ ] Ningún secreto real está commiteado en el repositorio.

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
- [ ] Elegir el criterio de organización (sugerido para tu caso, dado
      que vienes de un mundo más orientado a módulos en Angular: por
      **módulo de negocio** — `patient`, `appointment`, `auth`, etc. —
      cada uno con sus propias sub-capas internas).
- [ ] Crear la estructura de carpetas vacía con un `package-info.java`
      o README corto explicando la convención.
- [ ] Anotar la decisión en el propio repositorio (ej. `ARCHITECTURE.md`)
      para no tener que volver a discutirla en cada módulo nuevo.

**Criterios de aceptación:**
- [ ] Existe un documento corto en el repo que explica la convención
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
- [ ] Crear `docker-compose.yml` con servicio `postgres` (versión
      fijada, ej. `postgres:16`).
- [ ] Configurar `application-dev.yml` para apuntar a esa instancia.
- [ ] Documentar en `README.md` el comando para levantarla
      (`docker compose up -d`).

**Criterios de aceptación:**
- [ ] `docker compose up -d` levanta PostgreSQL y la app conecta sin
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
- [ ] Configurar `spring.jpa.hibernate.ddl-auto=validate` (nunca
      `update` ni `create` en ningún perfil, ni siquiera `dev`).
- [ ] Crear `V1__init.sql` (puede ser una tabla mínima de prueba).
- [ ] Verificar que Flyway corre automáticamente al iniciar la app.

**Criterios de aceptación:**
- [ ] La tabla `flyway_schema_history` existe después de iniciar la
      app y contiene el registro de `V1`.
- [ ] `ddl-auto` está en `validate` en todos los perfiles.

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
- [ ] Agregar dependencia de Testcontainers para PostgreSQL.
- [ ] Crear una clase base de test (`@SpringBootTest` +
      `@Testcontainers`) reutilizable para todos los tests de
      integración futuros.
- [ ] Escribir un primer test trivial que solo valide que el contexto
      de Spring levanta correctamente con la base de Testcontainers.

**Criterios de aceptación:**
- [ ] El test corre localmente y en el pipeline de CI sin Docker
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
- [ ] Escribir `Dockerfile` multi-stage: stage 1 con Maven+JDK para
      compilar, stage 2 con JRE 25 slim solo para ejecutar el `.jar`.
- [ ] Construir la imagen localmente y correrla con `docker run`.
- [ ] Verificar que las variables de entorno (perfil `prod`, conexión a
      BD) se pasan correctamente al contenedor.

**Criterios de aceptación:**
- [ ] `docker build` genera la imagen y `docker run` levanta la app
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
- [ ] Agregar librería JWT (ej. `jjwt`).
- [ ] Endpoint `POST /api/v1/auth/login` (email + password).
- [ ] JWT firmado que incluye: `user_id`, `tenant_id`, `role`,
      expiración.
- [ ] Manejo de error claro si las credenciales son inválidas (sin
      filtrar si el error fue "usuario no existe" vs "password
      incorrecta", por seguridad).

**Criterios de aceptación:**
- [ ] Login válido devuelve `200` con el token.
- [ ] Login inválido devuelve `401` con mensaje genérico.

---

### 🎫 FASE1-07 — Spring Security: validar JWT en cada request

**Tipo:** feature / security
**Estimación:** M (3–4h)
**Depende de:** FASE1-06

**Descripción:**
Configurar la cadena de filtros de Spring Security para que valide el
JWT y rechace requests sin token válido.

**Tareas:**
- [ ] Filtro personalizado (`OncePerRequestFilter`) que extrae y valida
      el JWT del header `Authorization: Bearer ...`.
- [ ] `SecurityFilterChain` configurado: rutas públicas (`/auth/login`,
      `/actuator/health`) vs. protegidas (todo lo demás).
- [ ] Endpoint de prueba `GET /api/v1/me` protegido que devuelve los
      datos del usuario autenticado extraídos del JWT.

**Criterios de aceptación:**
- [ ] Request sin token a `/api/v1/me` → `401`.
- [ ] Request con token válido → `200` con los datos correctos.
- [ ] Request con token expirado/manipulado → `401`.

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
- [ ] Componente `TenantContext` (`ThreadLocal` o `RequestScope` bean).
- [ ] El filtro de JWT (FASE1-07) puebla el `TenantContext` al inicio
      de cada request y lo limpia al final (importante: evitar fugas
      entre requests si se reutilizan hilos).
- [ ] Test que verifica que el contexto se limpia correctamente después
      de cada request.

**Criterios de aceptación:**
- [ ] Cualquier componente Spring puede obtener el `tenant_id` actual
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
- [ ] Evaluar Hibernate `@Filter`/`@FilterDef` a nivel de entidad base
      vs. un `BaseRepository` que inyecte `tenant_id` en cada método.
- [ ] Implementar la opción elegida sobre `TenantAwareEntity`
      (FASE1-04).
- [ ] Activar el filtro automáticamente al abrir la sesión de
      Hibernate, usando el `TenantContext` (FASE1-08).

**Criterios de aceptación:**
- [ ] Una query "ingenua" (sin agregar `tenant_id` manualmente) sobre
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
- [ ] Crear en el test dos tenants (A y B) con datos de una misma
      entidad de negocio simple (puede ser una entidad de prueba si
      todavía no existe ninguna real).
- [ ] Autenticarse como usuario del tenant A e intentar leer/modificar
      un registro del tenant B **usando su ID directamente**.
- [ ] Verificar que la respuesta es `404` (no `403`, para no confirmar
      siquiera que el recurso existe).

**Criterios de aceptación:**
- [ ] El test falla intencionalmente si alguien comenta o rompe el
      filtro de tenant (para servir como red de seguridad futura).
- [ ] El test corre en el pipeline de CI en cada PR.

---

### 🎫 FASE1-11 — Autorización por rol (`@PreAuthorize`)

**Tipo:** feature / security
**Estimación:** S (1–2h)
**Depende de:** FASE1-07, FASE1-03

**Descripción:**
Restringir endpoints según el rol del usuario autenticado.

**Tareas:**
- [ ] Habilitar `@EnableMethodSecurity` en la configuración de
      seguridad.
- [ ] Anotar al menos un endpoint de prueba con
      `@PreAuthorize("hasRole('PROPIETARIO')")`.
- [ ] Mapear el `role` del JWT a las authorities de Spring Security.

**Criterios de aceptación:**
- [ ] Un usuario con rol `RECEPCION` recibe `403` al llamar un endpoint
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
- [ ] Test que autentica usuarios con distintos roles y verifica
      `200` vs `403` según corresponda en el endpoint de prueba.

**Criterios de aceptación:**
- [ ] El test corre en CI y falla si la restricción de rol se rompe.

---

### 🎫 FASE1-13 — Tabla y entidad de auditoría (`audit_log`)

**Tipo:** feature
**Estimación:** S (1–2h)
**Depende de:** FASE1-01

**Descripción:**
Base del sistema de auditoría que se irá ampliando en cada fase
posterior.

**Tareas:**
- [ ] Entidad `AuditLog`: tenant_id, user_id, acción, entidad afectada,
      entidad_id, fecha, detalle (JSON o texto).
- [ ] Migración Flyway correspondiente.
- [ ] Servicio simple `AuditService.log(...)` reutilizable desde
      cualquier módulo futuro.

**Criterios de aceptación:**
- [ ] Se puede registrar una entrada de auditoría desde código y
      consultarla filtrada por tenant.

---

### 🎫 FASE1-14 — Registrar auditoría de login (éxito/fallo)

**Tipo:** feature
**Estimación:** S (1h)
**Depende de:** FASE1-13, FASE1-06

**Descripción:**
Primer caso de uso real del sistema de auditoría.

**Tareas:**
- [ ] Llamar a `AuditService.log(...)` en login exitoso y en login
      fallido (sin registrar la contraseña, ni siquiera fallida).

**Criterios de aceptación:**
- [ ] Un intento de login (exitoso o fallido) genera una entrada
      consultable en `audit_log`.

---

### ✅ Checklist de salida de Fase 1

Antes de pasar a la Fase 2, verificar:

- [ ] FASE1-10 (test de aislamiento cross-tenant) está en verde y
      corre en CI.
- [ ] FASE1-12 (test de autorización por rol) está en verde y corre en
      CI.
- [ ] La convención `TenantAwareEntity` (FASE1-04) está lista para que
      las entidades de la Fase 2 la usen desde su primera migración.

---

# FASE 2 — Pacientes e historia clínica base

**Objetivo de la fase:** el primer módulo de negocio real, y el más
transversal (casi todo lo demás depende de `Patient`).

## 2.1. CRUD de pacientes

- Entidad `Patient`: datos personales, contacto, contacto de
  emergencia (sección 8.2, alcance reducido — sin odontograma ni
  historia clínica todavía).
- Endpoints REST: `GET/POST/PATCH/DELETE /api/v1/patients`.
- Validación de entrada (Bean Validation: campos obligatorios, formato
  de teléfono/email).
- Paginación y búsqueda simple (por nombre/documento).

**Temas de Spring Boot:** `@Valid`, `Pageable`/`Page`, manejo de
errores centralizado (`@ControllerAdvice` + `@ExceptionHandler`).

**DoD:** se puede crear, listar (paginado), actualizar y eliminar
(lógicamente, no físicamente) un paciente vía API, respetando el
aislamiento de tenant de la Fase 1.

## 2.2. Historia clínica (versión reducida)

- Entidad `ClinicalRecord`: motivo de consulta, antecedentes,
  diagnóstico, evolución (texto estructurado, sin plantillas todavía).
- Relación `Patient` 1—N `ClinicalRecord`.
- Endpoint para agregar una entrada de evolución a un paciente.

**DoD:** se puede registrar y consultar el historial de entradas
clínicas de un paciente específico.

## 2.3. Odontograma (modelo de datos)

- Entidad `Odontogram` con estado por pieza dental y superficie
  (sección 8.4). En esta fase el foco es el **modelo de datos y la
  API**, no la parte visual (eso es frontend).
- Separación explícita en el modelo entre: estado actual, diagnóstico,
  plan propuesto y tratamiento realizado (tal como pide la sección 8.4
  del documento de arquitectura) — esto es más una decisión de modelo
  de datos que de UI, por eso va en el backend desde ya.

**DoD:** existe un endpoint que devuelve el estado del odontograma de
un paciente estructurado de forma que el frontend pueda pintar
cualquier representación visual sobre él sin ambigüedad.

## 2.4. Archivos del paciente

- Integración con almacenamiento S3-compatible (sección 16): subida de
  archivos/fotos asociados a un paciente.
- PostgreSQL guarda solo metadatos y referencia (`file_id`,
  `storage_key`), nunca el binario.

**Temas de Spring Boot:** `MultipartFile`, cliente S3 (AWS SDK v2 o
cliente compatible según el proveedor elegido para Render/Railway).

**DoD:** se puede subir un archivo asociado a un paciente y luego
recuperar una URL (firmada o directa) para descargarlo.

---

# FASE 3 — Agenda y citas

**Objetivo de la fase:** habilitar el flujo operativo diario de la
clínica.

## 3.1. Modelo de agenda

- Entidades: `Professional`, `Appointment`, `Room`/`Resource`
  (opcional en esta subfase, se puede simplificar al inicio).
- `Appointment`: paciente, profesional, procedimiento (aún como texto
  libre o catálogo simple), fecha/hora, duración, estado.

**DoD:** se puede crear una cita asociando paciente y profesional, con
validación de que no haya cruce de horario para el mismo profesional.

## 3.2. Estados de la cita y transición

- Estados: creada → confirmada → atendida / no-show / cancelada
  (sección 8.6).
- Endpoints para cambiar de estado, con reglas de negocio simples
  (ej. no se puede pasar de "cancelada" a "atendida").

**Temas de Spring Boot:** modelado de máquinas de estado simples
(enum + validación en el service layer, sin sobre-ingeniería con
librerías de state machine todavía).

**DoD:** las transiciones inválidas de estado devuelven error `400`
con mensaje claro.

## 3.3. Valor económico de la cita

- Campo de valor estimado en `Appointment` (sección 8.5, "agenda
  inteligente" — aquí solo el dato, la lógica de riesgo/priorización
  llega en la Fase 9).

**DoD:** el valor estimado de la cita es consultable y se puede sumar
por rango de fechas (insumo para reportes futuros).

## 3.4. Lista de espera y reasignación

- Entidad `WaitlistEntry`: paciente interesado en un horario/tipo de
  procedimiento.
- Endpoint que, al cancelarse una cita, devuelve pacientes compatibles
  en lista de espera (lógica simple por ahora: mismo tipo de
  procedimiento y disponibilidad declarada).

**DoD:** al cancelar una cita marcada como de alto valor, la API
devuelve al menos la lista de candidatos compatibles (el envío de
mensajes automático llega en la Fase 8).

---

# FASE 4 — Planes de tratamiento y facturación básica

## 4.1. Planes de tratamiento

- Entidad `TreatmentPlan`: diagnóstico, procedimientos, piezas
  involucradas, precio, profesional, estados (borrador → presentado →
  en decisión → aceptado → en ejecución → completado / rechazado /
  pospuesto / abandonado — sección 8.9).

**DoD:** se puede crear un plan de tratamiento y avanzarlo por sus
estados vía API, con las mismas reglas de transición válida que en la
Fase 3.2.

## 4.2. Facturación simple

- Entidades: `Invoice`, `Payment` (sección 8.11, alcance reducido: sin
  facturación electrónica todavía, eso depende de normativa y
  proveedor externo — se deja como integración futura, sección 30).
- Relación entre `TreatmentPlan`/`Appointment` y los pagos asociados.

**DoD:** se puede generar una factura simple asociada a un tratamiento
y registrar pagos parciales contra ella.

## 4.3. Cierre de la Fase 4 — checkpoint del plan Esencial

En este punto, el backend ya cubre lo mínimo necesario para el plan
**Esencial**: pacientes, historia clínica base, agenda, tratamientos y
facturación simple. Es un buen momento para una pausa de validación
antes de seguir construyendo funcionalidades de planes superiores.

---

# FASE 5 — CRM de leads

## 5.1. Modelo de leads

- Entidad `Lead`: nombre, contacto, fuente, campaña, procedimiento de
  interés, valor potencial, estado, responsable asignado (sección
  8.7).
- Pipeline de estados: nuevo → contactado → calificado → cita
  propuesta → cita agendada → cita asistida → tratamiento propuesto →
  tratamiento aceptado.

**DoD:** se puede registrar un lead manualmente y moverlo por el
pipeline vía API.

## 5.2. Conversión de lead a paciente/cita

- Endpoint que convierte un `Lead` calificado en un `Patient` +
  `Appointment`, evitando duplicar datos.

**DoD:** convertir un lead crea correctamente el paciente y la cita
asociada, y el lead queda enlazado a ese paciente (trazabilidad de
origen).

## 5.3. Métricas de conversión (backend)

- Endpoints de agregación: conversión por fuente, por campaña, tiempo
  promedio de respuesta a leads (sección 5.4 del doc de producto).

**DoD:** existen endpoints que devuelven estos indicadores para un
rango de fechas dado (el frontend los graficará después).

---

# FASE 6 — Cartera y pagos por etapas

## 6.1. Planes de pago

- Entidad `PaymentPlan` con cuotas (`Installment`), asociada a un
  `TreatmentPlan`.

**DoD:** se puede definir un plan de pago en cuotas y ver el estado de
cada cuota (pendiente, pagada, vencida).

## 6.2. Cartera y vencimientos

- Job programado (Spring Scheduling) que recalcula diariamente qué
  cuotas están vencidas y actualiza el dashboard de cartera (sección
  8.12).

**Temas de Spring Boot:** `@Scheduled`, consideraciones de
concurrencia si en el futuro hay múltiples instancias corriendo el
mismo job (relevante para cuando se escale más allá de una sola
instancia).

**DoD:** existe un endpoint que devuelve cartera total, vencida, por
vencer y al día, coherente con los datos de `PaymentPlan`.

---

# FASE 7 — Especialistas externos e inventario

## 7.1. Especialistas y liquidaciones

- Entidad `Specialist`, relación con `Appointment`/`TreatmentPlan`,
  porcentaje de honorarios, cálculo de producción y liquidación
  (sección 8.13).

**DoD:** se puede calcular cuánto se le debe liquidar a un especialista
en un periodo dado, a partir de los tratamientos/citas asociadas.

## 7.2. Inventario

- Entidades: `InventoryItem`, `StockMovement` (sección 8.14).
- Alertas simples de inventario crítico (umbral configurable).

**DoD:** al registrar un consumo de inventario, el stock se actualiza y
se puede consultar qué ítems están por debajo del umbral.

---

# FASE 8 — Automatizaciones, notificaciones y tareas

## 8.1. Motor de tareas internas

- Entidad `Task`: asociada a paciente/lead/cita, responsable, fecha
  límite, estado.

**DoD:** el sistema puede crear tareas automáticamente a partir de
reglas simples (ej. "cita sin confirmar a 24h" crea una tarea para
recepción).

## 8.2. Notificaciones

- Servicio de notificaciones desacoplado (interfaz + adaptadores),
  para no acoplar el dominio a un proveedor concreto desde el inicio
  (sección 30 del doc de arquitectura).
- Primer adaptador: email (más simple y sin costos de aprobación,
  antes de integrar WhatsApp).

**Temas de Spring Boot:** patrón de interfaz + implementación
intercambiable, `@ConditionalOnProperty` para activar/desactivar
adaptadores por configuración.

**DoD:** se puede disparar una notificación de confirmación de cita por
email de forma automática.

## 8.3. Integración WhatsApp (cuando el proceso de aprobación de Meta
     esté listo)

- Segundo adaptador de notificaciones sobre WhatsApp Business API.
- Registro de conversaciones (sección 8.8).

**DoD:** se puede enviar y registrar un mensaje de confirmación por
WhatsApp, con manejo de fallos que no bloquee el flujo (mismo principio
de resiliencia que se definió para la IA en el documento de
arquitectura).

## 8.4. Automatización de recuperación de citas

- Al cancelarse una cita, disparar automáticamente contacto con la
  lista de espera compatible (conecta con la Fase 3.4).

**DoD:** una cancelación de cita de alto valor genera automáticamente
una tarea o notificación de recuperación, sin intervención manual.

---

# FASE 9 — Motor de oportunidades

**Objetivo de la fase:** este es el diferenciador central del producto
(sección 9 del doc de arquitectura), por eso va después de tener los
módulos base sólidos — no se puede detectar oportunidades sobre datos
que no existen todavía.

## 9.1. Reglas de detección

- Job programado que recorre: leads sin respuesta, tratamientos sin
  seguimiento, citas de alto riesgo, espacios disponibles, pacientes
  inactivos, saldos vencidos, inventario crítico.
- Cada regla genera una entidad `Opportunity` con tipo, prioridad y
  valor estimado.

**DoD:** existe al menos una regla completa funcionando de punta a
punta (sugerido: "tratamiento sin seguimiento", por ser la más simple
de validar con datos ya existentes de la Fase 4 y 5).

## 9.2. Acción recomendada

- Cada `Opportunity` incluye una acción sugerida (mensaje propuesto o
  tarea), no solo el dato bruto (principio de la sección 5.2 del doc
  de producto: "detectar → entender → proponer → ejecutar").

**DoD:** una oportunidad detectada trae asociada al menos una acción
ejecutable directamente vía API (ej. "enviar este mensaje" o "crear
esta tarea").

## 9.3. Métrica de valor recuperado

- Cálculo de la métrica de la sección 10 del doc de arquitectura, con
  reglas transparentes de atribución (qué cuenta como "recuperado").

**DoD:** existe un endpoint que devuelve el valor recuperado por
categoría en un periodo, con la regla de cálculo documentada en el
código (no solo en este documento).

---

# FASE 10 — IA administrativa

## 10.1. Asistente administrativo (preguntas simples)

- Endpoint que recibe una pregunta en lenguaje natural y la resuelve
  consultando datos ya expuestos por endpoints existentes (sección
  8.18), usando un proveedor de LLM (recomendado: Groq, como ya
  vienes evaluando para Venti Shop, por costo).

**DoD:** preguntas como "¿qué tratamientos están pendientes de
seguimiento?" devuelven una respuesta correcta basada en datos reales
del tenant.

## 10.2. Generación asistida de mensajes

- Endpoint que, dado un contexto (ej. cita sin confirmar), genera un
  mensaje sugerido de confirmación.

**DoD:** el mensaje generado queda como sugerencia editable, nunca se
envía automáticamente sin pasar por el flujo de notificaciones de la
Fase 8 con confirmación humana cuando aplique.

## 10.3. Resiliencia de IA

- Implementar el principio ya definido en el doc de arquitectura:
  timeout corto, fallback a plantilla fija si el proveedor de IA falla,
  y registro del fallo.

**DoD:** si se apaga la clave de API de IA, el sistema sigue operando
con plantillas fijas sin romper ningún flujo.

---

# FASE 11 — Suscripciones y planes (billing del propio SaaS)

**Objetivo de la fase:** habilitar el cobro recurrente a las clínicas y
el control de qué funcionalidades ve cada plan (Esencial, Profesional,
Clínica).

## 11.1. Modelo de planes y feature flags

- Entidad `Plan` (Esencial/Profesional/Clínica) y `TenantSubscription`.
- Tablas `plan_features` (booleanos por módulo) y `plan_limits`
  (numéricos, con `-1`/`null` como convención de "ilimitado"), según
  el diseño definido en la sección "Pricing y límites por plan" al
  inicio de este documento, con los valores iniciales de esa tabla.
- Mecanismo de feature-gating por plan (ej. `@PreAuthorize` extendido o
  un filtro que valide si el tenant tiene acceso a un módulo antes de
  ejecutar el endpoint).
- Contador de uso mensual para límites que se consumen (ej.
  conversaciones de WhatsApp), reseteable al inicio de cada ciclo de
  facturación.

**DoD:** un tenant en plan Esencial recibe `403` al intentar usar un
endpoint exclusivo de Profesional/Clínica (ej. CRM de leads), y un
tenant que alcanza su límite numérico (ej. 150 pacientes) recibe un
error claro al intentar crear el recurso 151.

## 11.2. Integración de pasarela de pagos para el cobro de suscripción

- Integración con pasarela (a definir: Wompi, Stripe, etc.) para cobro
  recurrente mensual/anual.
- Webhook de confirmación de pago que activa/suspende el acceso del
  tenant.

**DoD:** un pago exitoso activa el tenant; un pago fallido/vencido
suspende el acceso según la política definida (con periodo de gracia a
definir).

## 11.3. Descuento por facturación anual

- Soporte de ciclo de facturación anual con el descuento definido en el
  documento de pricing.

**DoD:** un tenant puede elegir entre ciclo mensual o anual al momento
de suscribirse, y el monto cobrado refleja el descuento correcto.

---

# FASE 12 — Endurecimiento, observabilidad y preparación para producción

## 12.1. Seguridad

- Rate limiting (sección 18), revisión de protección contra inyección,
  CSRF/XSS según corresponda a una API REST pura.
- Gestión segura de secretos (variables de entorno del proveedor,
  nunca hardcodeadas).

## 12.2. Observabilidad

- Spring Actuator + OpenTelemetry, integración con Sentry para errores.
- Logs estructurados por tenant para poder depurar sin exponer datos
  entre clínicas.

## 12.3. CI/CD completo

- Pipeline de GitHub Actions que compila, corre pruebas y despliega
  automáticamente a Render/Railway en merges a `main`.

## 12.4. Backups y recuperación

- Backups automáticos de PostgreSQL (según lo que ofrezca el proveedor
  elegido) y prueba real de restauración al menos una vez antes de
  tener el primer cliente pagando.

**DoD de toda la fase:** se puede perder la base de datos de
producción y restaurarla desde backup en un tiempo conocido, sin
intervención manual compleja.

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

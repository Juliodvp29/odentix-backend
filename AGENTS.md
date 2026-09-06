# AGENTS.md — odentix-backend

Este archivo son las instrucciones para cualquier agente de código (Claude
Code, Cursor, Copilot Workspace, etc.) que trabaje en este repositorio.
Si estás leyendo esto como agente: estas reglas tienen prioridad sobre tu
criterio general por defecto. Si una instrucción del usuario en el chat
contradice algo crítico de aquí (sobre todo la sección de multi-tenancy),
señala la contradicción en vez de aplicarla en silencio.

---

## 1. Qué es este proyecto

Backend de un SaaS de gestión para clínicas odontológicas en Colombia
(multi-tenant: cada clínica es un `tenant`). El diferenciador del producto
no es solo llevar registros, sino detectar oportunidades de negocio
perdidas (leads sin responder, tratamientos sin seguimiento, cartera
vencida, etc.) y proponer una acción — no lo tengas presente para el
código de infraestructura, pero si tocas lógica de negocio, el criterio
de diseño es "esto ayuda a la clínica a actuar", no solo "esto registra
un dato".

Documentos de referencia en la raíz del repo (léelos antes de tocar algo
que no entiendas del todo):
- `docs/documentacion_sistema_gestion_odontologica_v1.1.md  ` — arquitectura y producto.
- `docs/roadmap_backend_fases.md` — roadmap por fases y tickets.
- `docs/schema.sql` — esquema de base de datos de referencia.

Este backend se construye **solo** de momento (el frontend Angular es un
proyecto/roadmap separado). No asumas que existe un cliente Angular
consumiendo estos endpoints todavía — no hay contrato de API congelado.

### Skills disponibles

Existe una carpeta `.agents/skills/` en la raíz del repo con instrucciones
específicas para tareas puntuales (ej. cómo generar un tipo de reporte,
una convención particular, un checklist para cierto tipo de cambio).
**Antes de empezar cualquier tarea, revisa si `.agents/skills/` contiene
un skill relevante para lo que vas a hacer y síguelo** — tiene prioridad
sobre tu criterio general por defecto, igual que el resto de este
archivo. Si la carpeta todavía no tiene ningún skill relacionado con la
tarea actual, simplemente sigue las reglas de este `AGENTS.md`.

---

## 2. Stack y versiones exactas

- **Java 25 (LTS)** — nunca sugerir ni usar versiones no-LTS (26, etc.).
- **Spring Boot 4.1.1** — ⚠️ IMPORTANTE: en esta versión los paquetes de
  autoconfiguración se reorganizaron respecto a Spring Boot 3.x. La
  inmensa mayoría de tutoriales, respuestas de Stack Overflow y del
  propio conocimiento de un modelo de lenguaje usan los paquetes viejos.
  **Antes de escribir cualquier import o referencia a una clase de
  autoconfiguración de Spring Boot, verifica el nombre real del paquete**
  (ejemplos ya confirmados en este proyecto):
    - `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`
    - `org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration`
    - `org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration`
      Si no estás seguro del paquete de una clase de autoconfiguración en
      4.1.1, dilo explícitamente en vez de asumir el paquete de Spring Boot 3.
- **Maven** (no Gradle).
- **PostgreSQL 16+** — nunca H2 ni ninguna base embebida, ni siquiera para
  pruebas rápidas (ver sección de testing).
- **Flyway** para todo cambio de esquema.
- **Spring Security** con JWT (implementación propia, sin OAuth2/Keycloak
  en este alcance).
- Despliegue objetivo: Docker + Render/Railway (no Kubernetes, no
  microservicios — es un monolito modular a propósito).

---

## 3. Comandos esenciales

```bash
# Levantar Postgres local (una vez exista docker-compose.yml, Fase 0.4)
docker compose up -d

# Compilar y correr tests
./mvnw clean verify

# Solo tests
./mvnw test

# Correr la app (perfil dev)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Si alguno de estos comandos no existe todavía en el estado actual del
repo (por ejemplo `docker-compose.yml` antes de la Fase 0.4), no lo
inventes ni lo simules: dilo y pregunta en qué fase del roadmap estamos.

---

## 4. Arquitectura y convenciones de paquetes

Organización **por módulo de negocio**, no por capa técnica (decisión de
FASE0-03, documentada porque Julio viene de un mundo más orientado a
módulos en Angular):

```
com.julio.odentix.odentix_backend/
├── shared/           # TenantAwareEntity, TenantContext, excepciones comunes
├── tenant/
├── auth/
├── patient/
├── appointment/
├── treatmentplan/
├── billing/
├── crm/
├── inventory/
└── ...
```

Cada módulo se organiza internamente por sub-capas (`controller`,
`service`, `repository`, `entity`, `dto`) **dentro de su propio paquete**,
no como paquetes top-level compartidos. Si vas a crear un módulo nuevo,
sigue este mismo patrón sin pedir permiso; si vas a *cambiar* el patrón,
pregunta primero — es una decisión ya tomada y documentada.

---

## 5. Multi-tenancy — reglas críticas (no negociables)

Este es el aspecto más importante de todo el proyecto. Un bug aquí filtra
datos de pacientes entre clínicas distintas, que es el peor escenario de
seguridad posible para este producto.

1. **Toda entidad de negocio nueva** (no catálogos globales como `plans`)
   debe extender/incluir `tenant_id` desde su primera migración, y la
   clase Java debe heredar de `TenantAwareEntity` (o el patrón equivalente
   que exista para ese momento del proyecto).
2. **Nunca escribas una query JPQL/nativa manual que toque una tabla de
   negocio sin filtrar por `tenant_id`**, incluso si crees que el filtro
   automático (Hibernate Filter / `TenantContext`) ya lo cubre. Defensa en
   profundidad: dos capas independientes, ninguna sustituye a la otra.
3. Row Level Security en PostgreSQL (ver `schema.sql`) es la segunda capa.
   Si escribes una migración Flyway que crea una tabla con `tenant_id`,
   **debe** quedar con RLS activado siguiendo el mismo patrón que las
   demás (política `tenant_isolation` sobre `current_tenant_id()`).
4. Si escribes un test de un endpoint o repositorio que toque una tabla
   de negocio, **incluye siempre un caso que verifique aislamiento
   cross-tenant** (crear datos en tenant A, autenticarte/simular como
   tenant B, verificar que no se ve ni se puede modificar). No es
   opcional ni "algo que se puede agregar después".
5. Nunca loguees ni expongas en mensajes de error el contenido de datos
   de un tenant distinto al de la request actual, ni siquiera en logs de
   debug.

---

## 6. Base de datos y migraciones

- **`ddl-auto` siempre en `validate`**, en todos los perfiles, sin
  excepción. Nunca `update` ni `create`, ni "solo para probar rápido".
- Todo cambio de esquema va en una migración Flyway nueva
  (`V{n}__descripcion.sql`), nunca editando una migración ya aplicada
  (aunque sea reciente) ni modificando el `schema.sql` de referencia
  directamente esperando que se aplique solo.
- Sigue las convenciones ya establecidas en `schema.sql`: UUID como PK
  (`gen_random_uuid()`), `snake_case`, tablas en plural, `TIMESTAMPTZ` (no
  `TIMESTAMP`), montos en `NUMERIC(12,2)` con sufijo `_cop`, `updated_at`
  gestionado por el trigger genérico `set_updated_at()` (no lo setees a
  mano desde Java).
- Antes de crear una tabla nueva, revisa si ya existe una definición de
  referencia para ella en `docs/schema.sql` y mantente consistente con
  esa definición salvo que el usuario pida explícitamente cambiarla.

---

## 7. Testing

- **Testcontainers con PostgreSQL real**, nunca H2 ni mocks de base de
  datos para pruebas de integración. Ya existe una clase base para esto
  (ver FASE0-06 del roadmap) — reutilízala en vez de crear una nueva.
- Todo endpoint o repositorio nuevo que toque una tabla de negocio
  necesita al menos: un test de comportamiento normal, un test de
  aislamiento cross-tenant (sección 5), y un test de autorización por rol
  si el endpoint tiene restricción de rol.
- No marques un ticket/tarea como terminado si el DoD (Definition of
  Done) descrito en `roadmap_backend_fases.md` para ese ticket no está
  cubierto por una prueba automatizada verificable, no solo "probado a
  mano una vez".

---

## 8. Seguridad

- Nunca hardcodees secretos, credenciales o claves de API en código ni en
  archivos versionados. Van en variables de entorno.
- El rol de base de datos que use la aplicación en producción no debe
  tener `BYPASSRLS` — si necesitas escribir una migración o script
  administrativo que sí lo requiera, dilo explícitamente en vez de
  asumir permisos elevados por defecto.
- Nunca implementes ni sugieras deshabilitar RLS o el filtro de tenant
  "temporalmente para probar algo más rápido". Si algo es difícil de
  probar con el aislamiento activo, el problema es del test, no una
  razón para bajar la guardia de seguridad.
- Los endpoints de IA o de integraciones externas (WhatsApp, proveedores
  de IA) deben implementarse con manejo de fallos que **nunca bloquee**
  el flujo operativo que los originó (ver sección 8.3/10.3 del roadmap:
  timeout corto + fallback a plantilla fija + registro del fallo).

---

## 9. Estilo de código

- Sin Lombok por ahora (decisión de FASE0-01, mientras se está
  aprendiendo el framework — código explícito). Si en algún momento se
  agrega Lombok al proyecto, esta regla queda obsoleta; verifica el
  `pom.xml` real antes de asumir.
- Nombres de variables, métodos y comentarios de código en español o
  inglés, mantente consistente con lo que ya exista en el archivo que
  estés editando en vez de mezclar.
- Prefiere código explícito y legible sobre "clever". Julio está
  aprendiendo Spring Boot activamente — si tomas una decisión no obvia
  (ej. un patrón de Hibernate Filters, una configuración de seguridad no
  trivial), agrega un comentario breve explicando el porqué, no solo el
  qué.

---

## 10. Qué NO hacer nunca

- No agregues Kubernetes, un segundo servicio, colas de mensajería
  pesadas (Kafka, RabbitMQ) u otra infraestructura no pedida "porque es
  buena práctica". El principio arquitectónico explícito del proyecto es
  no introducir complejidad de infraestructura antes de que la escala la
  justifique.
- No asumas que el frontend Angular ya consume un endpoint de una forma
  específica — no existe todavía.
- No inventes valores de configuración, endpoints de proveedores externos
  (WhatsApp, pasarelas de pago) ni credenciales de ejemplo como si fueran
  reales.
- No marques como resuelto un ticket del roadmap sin cumplir su DoD tal
  como está escrito en `roadmap_backend_fases.md`.
- No cambies la convención de paquetes, el modelo de multi-tenancy, o las
  claves de `plan_features`/`plan_limits` sin señalarlo explícitamente —
  son decisiones ya tomadas con su razonamiento documentado.

---

## 11. Flujo de trabajo obligatorio en cada tarea

Este flujo aplica a **cualquier cambio no trivial** (una fase completa,
un ticket del roadmap, o incluso un ajuste puntual dentro de un ticket ya
empezado). No lo saltes por parecer "obvio" o "rápido" — la idea es que
Julio pueda revisar y aprender de cada paso, no solo recibir código ya
hecho.

1. **Plan antes de código.** Antes de crear o modificar cualquier
   archivo, presenta un plan breve: qué vas a hacer, qué archivos vas a
   tocar o crear, y qué decisiones no triviales vas a tomar (si hay más
   de una forma razonable de resolverlo, dilo y explica cuál eliges y
   por qué). No hace falta un documento largo — unas pocas líneas
   claras bastan.
2. **Esperar aprobación.** No ejecutes el plan hasta que Julio lo
   confirme explícitamente. Si pide cambios al plan, ajústalo y vuelve a
   presentarlo antes de tocar código.
3. **Ejecutar el plan aprobado.** Implementa exactamente lo acordado. Si
   en el camino descubres que el plan no funciona o falta algo
   importante, detente y explica el problema en vez de improvisar una
   solución distinta sin avisar.
4. **Probar antes de dar por terminado.** Corre las pruebas relevantes
   (`./mvnw test` o `./mvnw clean verify` según el alcance) y verifica el
   DoD del ticket correspondiente en `roadmap_backend_fases.md` si
   aplica. Si algo fue difícil de probar automáticamente, dilo
   explícitamente en vez de dar el cambio por bueno sin evidencia.
5. **Commit solo al final, y solo si todo lo anterior pasó.** Un commit
   por tarea/ticket completado, con mensaje claro (ver convención más
   abajo). Nunca hagas commit de código que no compila o con pruebas en
   rojo, y nunca hagas commit sin que Julio haya visto el resultado
   final de los pasos 3 y 4.

Este ciclo (plan → aprobación → ejecución → pruebas → commit) se repite
en cada fase y en cada ajuste dentro de una fase, no solo una vez al
principio del proyecto.

### Convención de commits

Mensajes en español, formato corto: `[FASE0-01] Configurar proyecto base
Spring Boot`. Si el cambio no corresponde a un ticket del roadmap (un
ajuste menor, un fix), usa una descripción igual de clara sin el prefijo
de ticket.

---

## 12. Estado actual del proyecto / notas vivas

*(Actualiza esta sección a medida que el proyecto avanza — es más útil
que quede desactualizada visiblemente a que no exista.)*

- Fase completada: **Fase 0 — Fundamentos y esqueleto del proyecto** (todos los tickets FASE0-01 a FASE0-09 completados).
- Siguiente fase: **Fase 1 — Identidad, autenticación y multi-tenancy** (empezando por FASE1-01: modelar entidad `Tenant`).
- Despliegue en la nube: activo en Render (`https://odentix-backend.onrender.com/actuator/health`).
- Base de datos de producción: PostgreSQL 16 administrada en Render (`odentix-postgres` en región Ohio).
- Pipeline de CI: activo en GitHub Actions (`.github/workflows/ci.yml`) con Java 25 y Testcontainers sobre `dev` y `main`.
- Fase 1 en curso: FASE1-01 a FASE1-11 completadas (`Tenant`, `User`, `UserRole`, `TenantAwareEntity`, `UserService`, login JWT, `JwtAuthenticationFilter`, `TenantContext` por request, filtrado automático de tenant en repositorios con `@TenantId` y `TenantIdentifierResolver`, test crítico de aislamiento cross-tenant, autorización por rol con `@PreAuthorize`). Siguiente ticket: FASE1-12 (Test de autorización por rol).
- Las entidades reales de negocio (pacientes, citas, etc.) comienzan en Fase 2.
# Arquitectura — convención de paquetes (FASE0-03)

Decisión tomada: organización **por módulo de negocio**, no por capa técnica.

```
com.julio.odentix.odentix_backend/
├── shared/          # transversal: TenantAwareEntity, TenantContext, excepciones
├── tenant/          # clínica cliente (raíz del multi-tenancy)
├── auth/            # User, Role, login JWT
├── patient/         # Fase 2
├── appointment/     # Fase 3
├── treatmentplan/   # Fase 4
├── billing/         # Fase 4
├── crm/             # Fase 5
└── inventory/       # Fase 7
```

Reglas:

1. Cada módulo tiene sus subcapas internas: `controller`, `service`, `repository`,
   `entity`, `dto`. No existen paquetes top-level `controllers/`, `services/`, etc.
2. `shared` es lo único importable desde cualquier módulo. Entre módulos de negocio
   las dependencias van hacia `tenant`/`patient`, nunca al revés sin justificarlo.
3. Módulo nuevo = mismo patrón, sin pedir permiso. Cambiar el patrón requiere
   discusión explícita (decisión documentada porque Julio viene de Angular modular).
4. Sin Lombok por ahora (FASE0-01): getters/setters explícitos mientras se aprende
   el framework. Verificar `pom.xml` antes de asumirlo.

---

# Convención `tenant_id` en tablas de negocio (FASE1-04)

Regla: toda tabla de negocio lleva `tenant_id UUID NOT NULL` desde su primera
migración, y su entidad hereda de `shared.entity.TenantAwareEntity` (aporta
`id`, `tenant_id`, `created_at`, `updated_at`). Defensa en profundidad
(AGENTS.md §5): el filtro de aplicación (Fase 1.9) + RLS en Postgres son dos
capas independientes, ninguna sustituye a la otra.

Checklist de cada migración que cree una tabla de negocio:

1. Columna `tenant_id UUID NOT NULL REFERENCES tenants(id)` + índice.
2. RLS activado con la política `tenant_isolation` sobre `current_tenant_id()`
   (mismo patrón de `docs/schema.sql`).
3. La entidad hereda de `TenantAwareEntity`; si necesita navegar a `Tenant`,
   la asociación usa `insertable = false, updatable = false`.
4. El repositorio filtra por tenant en cada query (`findByTenantId...`), aunque
   exista filtro automático.
5. Test cross-tenant obligatorio (AGENTS.md §5.4).

Excepciones: `tenants` (es la raíz, no pertenece a ningún tenant), `users`
(modela el tenant vía asociación desde FASE1-02) y catálogos globales
(`plans`, `plan_features`, `plan_limits` en Fase 11).

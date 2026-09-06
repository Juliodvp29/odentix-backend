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

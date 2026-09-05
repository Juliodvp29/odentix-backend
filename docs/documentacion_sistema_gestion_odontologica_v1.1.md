# Sistema de Gestión y Productividad para Clínicas Odontológicas

**Documento de arquitectura, producto y alcance**

**Versión:** 1.1\
**Fecha:** septiembre de 2026\
**Estado:** Documento base para diseño y desarrollo

**Notas de la versión 1.1:**

- Se confirma el stack **Java 25 (LTS) + Spring Boot** como decisión
  definitiva (ver sección 39 para la justificación).
- Se agrega el modelo de negocio (suscripción) y la estrategia de
  migración de datos de clientes existentes.
- Se corrige la numeración de secciones y se elimina la duplicación
  entre las secciones de stack tecnológico.
- El **roadmap por fases / MVP** se desarrollará en un documento
  separado y no forma parte de este documento base.

---

## 1. Resumen ejecutivo

El proyecto consiste en construir una plataforma web para la gestión
integral de centros odontológicos, pero con un enfoque diferente al de
un software odontológico tradicional.

El objetivo no es únicamente digitalizar historias clínicas,
odontogramas, citas y facturación. El producto debe convertirse en una
**plataforma de productividad y operación para el centro odontologico**, capaz de
reducir trabajo administrativo, evitar pérdidas de oportunidades
comerciales, mejorar la ocupación de la agenda, acelerar la conversión
de pacientes y facilitar el control financiero.

La idea central del producto es:

> **No limitarse a registrar lo que ocurre en la clínica, sino ayudar a
> que ocurran las acciones correctas en el momento correcto.**

El sistema debe ayudar especialmente al odontólogo propietario que,
además de atender pacientes, participa en la administración del negocio.

El flujo de valor principal será:

```text
Publicidad
    ↓
Lead
    ↓
Respuesta rápida
    ↓
Calificación
    ↓
Cita
    ↓
Confirmación
    ↓
Atención
    ↓
Plan de tratamiento
    ↓
Aceptación
    ↓
Ejecución
    ↓
Cobro
    ↓
Seguimiento
    ↓
Fidelización / retorno
```

La plataforma deberá detectar puntos de fuga dentro de este flujo y
ayudar a corregirlos mediante automatización, alertas, tareas, métricas,
CRM e inteligencia artificial aplicada principalmente a tareas
administrativas y de productividad.

---

# 2. Problema que buscamos solucionar

## 2.1. Situación del cliente objetivo

El producto está dirigido inicialmente a centros odontológicos
particulares, especialmente aquellas que presentan características como:

- El propietario es también odontólogo.
- Existe un equipo interno pequeño.
- La recepción y administración recaen sobre pocas personas.
- Se utilizan especialistas externos según los procedimientos
  agendados.
- La adquisición de pacientes depende de Meta Ads, Google Ads,
  Instagram, WhatsApp y otros canales digitales.
- Se realizan tratamientos de alto valor.
- Se ofrecen pagos por etapas o financiación informal.
- La clínica necesita controlar tanto la operación clínica como la
  parte comercial y financiera.

En este contexto, el problema no es simplemente la ausencia de software.

El problema es que **la operación depende demasiado de personas
recordando, revisando, copiando, respondiendo y haciendo seguimiento
manualmente**.

---

# 3. Problemas principales identificados

## 3.1. El "Dr. Administrador"

El odontólogo propietario puede dedicar parte de su tiempo clínico a:

- Responder mensajes.
- Revisar agendas.
- Resolver cancelaciones.
- Revisar pagos.
- Conciliar información.
- Revisar inventario.
- Coordinar especialistas.
- Supervisar empleados.
- Revisar presupuestos.
- Hacer seguimiento a pacientes.

Cada hora administrativa que podría realizar otra persona representa un
costo de oportunidad cuando la hora clínica del propietario tiene un
valor elevado.

### Objetivo del producto

Reducir la cantidad de decisiones y tareas operativas que requieren
intervención directa del propietario.

---

## 3.2. Fuga de leads

Las clínicas invierten dinero en publicidad, pero el lead puede llegar a
WhatsApp y esperar horas para recibir una respuesta.

El problema puede representarse así:

```text
Meta / Google
     ↓
Lead
     ↓
WhatsApp
     ↓
Recepcionista ocupada
     ↓
Respuesta tardía
     ↓
Paciente pierde interés
     ↓
Dinero de publicidad desperdiciado
```

### Objetivo

Reducir el tiempo de respuesta y evitar que los leads de valor queden
olvidados.

El sistema deberá permitir:

- Centralizar leads.
- Registrar fuente y campaña.
- Automatizar respuestas iniciales.
- Calificar prospectos.
- Asignar leads.
- Detectar leads sin respuesta.
- Convertir conversaciones en citas.
- Medir conversión por fuente.
- Medir valor generado por campaña.

---

## 3.3. No-Shows y cancelaciones de citas de alto valor

Una inasistencia de 30 minutos no tiene el mismo impacto que perder una
cita de dos horas para un implante, preparación de carillas o
procedimiento realizado con un especialista externo.

El sistema debe comprender que **una cita tiene valor económico y
operativo**.

### Objetivo

Prevenir y recuperar espacios perdidos.

Debe contemplarse:

- Confirmación automática.
- Recordatorios.
- Detección de citas de alto impacto.
- Riesgo de no-show.
- Alertas internas.
- Lista de espera.
- Reasignación rápida de espacios.
- Contacto con pacientes compatibles.
- Registro de cancelaciones.
- Métricas de ocupación y pérdida.

---

## 3.4. Presupuestos sin seguimiento

Un paciente puede recibir un plan de tratamiento de varios millones de
pesos y no decidir inmediatamente.

En un sistema tradicional:

```text
Presupuesto creado
        ↓
Paciente se va
        ↓
Nadie recuerda hacer seguimiento
        ↓
Oportunidad perdida
```

El sistema debe convertir el presupuesto en una **oportunidad comercial
activa**.

### Objetivo

Aumentar la tasa de aceptación de tratamientos.

Debe permitir:

- Pipeline de tratamientos.
- Estados de oportunidad.
- Seguimientos programados.
- Recordatorios.
- Tareas.
- Historial de contactos.
- Automatizaciones.
- Identificación de oportunidades de alto valor.
- Métricas de conversión.

---

## 3.5. Cartera vencida

Las clínicas pueden permitir pagos por etapas sin disponer de un sistema
de financiación estructurado.

Esto genera:

- Saldos pendientes.
- Tratamientos interrumpidos.
- Dificultades de cobro.
- Falta de visibilidad sobre cartera.
- Riesgo de continuar procedimientos con pacientes morosos.

### Objetivo

Controlar el dinero pendiente y anticipar riesgos.

El sistema deberá:

- Registrar planes de pago.
- Registrar cuotas.
- Controlar vencimientos.
- Generar alertas.
- Mostrar saldos.
- Registrar pagos.
- Mantener historial.
- Relacionar pagos con tratamientos.
- Permitir políticas configurables para saldos vencidos.

---

## 3.6. Coordinación de especialistas externos

El modelo de especialistas por porcentaje crea necesidades específicas:

- Disponibilidad.
- Procedimientos.
- Agenda.
- Pacientes asignados.
- Honorarios.
- Porcentajes.
- Producción generada.
- Liquidaciones.

### Objetivo

Facilitar la coordinación y conocer la rentabilidad real de cada
procedimiento y especialista.

---

## 3.7. Sobrecarga administrativa y normativa

La clínica debe manejar información clínica, financiera y
administrativa, además de procesos relacionados con requisitos
regulatorios y de facturación.

El sistema debe reducir la duplicación de trabajo y centralizar
información.

**Importante:** las funcionalidades regulatorias deberán diseñarse y
validarse con asesoría especializada para garantizar cumplimiento de la
normativa colombiana vigente. El software no debe asumir que una
implementación técnica por sí sola garantiza cumplimiento legal.

---

## 3.8. Migración desde el software o método actual del cliente

Muchas clínicas objetivo ya usan otro software odontológico, hojas de
cálculo o registro en papel. Si migrar no es sencillo, es una barrera de
adopción tan grande como cualquier problema funcional.

### Objetivo

Reducir la fricción de entrada de un cliente nuevo.

El sistema deberá contemplar:

- Plantillas de importación (pacientes, historial básico, agenda futura).
- Herramientas o servicios de migración asistida para los primeros
  clientes.
- Validación y limpieza de datos importados.
- Comunicación clara de qué información se puede migrar y cuál no.

---

# 4. Visión del producto

## 4.1. Qué queremos construir

Una plataforma SaaS/web para clínicas odontológicas que combine:

- Gestión clínica.
- Gestión administrativa.
- CRM.
- Automatización.
- Inteligencia operativa.
- Gestión financiera.
- Analítica.
- Comunicación con pacientes.
- Control de especialistas.
- Asistencia mediante IA.

La plataforma debe funcionar como un **sistema operativo de la
clínica**.

---

# 5. Principios del producto

## 5.1. Productividad antes que cantidad de funcionalidades

No queremos ganar por tener más menús.

Queremos ganar porque el usuario puede hacer su trabajo con menos pasos.

---

## 5.2. Automatizar antes de notificar

Una alerta que simplemente dice:

> "Paciente sin seguimiento"

es útil.

Pero es mejor:

> "Paciente sin seguimiento desde hace 9 días. Aquí tienes el mensaje
> sugerido y el botón para enviarlo."

La regla será:

```text
Detectar
   ↓
Entender
   ↓
Proponer
   ↓
Ejecutar
```

---

## 5.3. El sistema debe ser proactivo

El usuario no debería tener que revisar todos los módulos para descubrir
problemas.

El sistema debe decirle:

- Qué necesita atención.
- Qué puede esperar.
- Qué representa mayor impacto.
- Qué oportunidad se puede recuperar.
- Qué riesgo está aumentando.

---

## 5.4. Medir impacto empresarial

Las métricas no deben limitarse a:

- Número de pacientes.
- Número de citas.
- Número de facturas.

También deben existir métricas como:

- Tiempo promedio de respuesta a leads.
- Conversión de lead a cita.
- Conversión de cita a tratamiento.
- Valor de tratamientos pendientes.
- Valor de oportunidades recuperadas.
- Tasa de no-show.
- Valor perdido por cancelaciones.
- Ocupación de agenda.
- Cartera vencida.
- Tiempo administrativo ahorrado.
- Producción por profesional.
- Rentabilidad por procedimiento.

---

# 6. Usuarios y roles

## 6.1. Propietario / administrador

Necesita:

- Visión general del negocio.
- Indicadores.
- Alertas.
- Finanzas.
- Producción.
- Rentabilidad.
- Personal.
- Especialistas.
- Configuración.

---

## 6.2. Odontólogo

Necesita:

- Agenda.
- Pacientes.
- Historia clínica.
- Odontograma.
- Diagnósticos.
- Evolución.
- Planes de tratamiento.
- Documentación clínica.
- Seguimiento.

La interfaz debe minimizar el tiempo de registro durante la consulta.

---

## 6.3. Recepción

Necesita:

- Agenda.
- Pacientes.
- Leads.
- WhatsApp/comunicación.
- Confirmaciones.
- Reprogramaciones.
- Pagos.
- Tareas.
- Seguimientos.

---

## 6.4. Auxiliar

Necesita acceso a las funciones necesarias para:

- Preparación de consulta.
- Apoyo clínico.
- Inventario.
- Procedimientos.
- Registro operativo.

---

## 6.5. Especialista externo

Debe poder acceder únicamente a la información y funciones necesarias
para su trabajo.

---

# 7. Arquitectura funcional

La aplicación estará organizada en capas funcionales.

```text
┌────────────────────────────────────────────┐
│              EXPERIENCIA                   │
│ Dashboard / Agenda / Pacientes / CRM      │
├────────────────────────────────────────────┤
│              PRODUCTIVIDAD                 │
│ Automatizaciones / Alertas / Tareas / IA  │
├────────────────────────────────────────────┤
│              OPERACIÓN                    │
│ Clínica / Agenda / Tratamientos / Cobros  │
├────────────────────────────────────────────┤
│              NEGOCIO                      │
│ CRM / Finanzas / Especialistas / Reportes │
├────────────────────────────────────────────┤
│              PLATAFORMA                   │
│ Seguridad / Auditoría / Notificaciones    │
│ Archivos / Configuración / Integraciones  │
└────────────────────────────────────────────┘
```

---

# 8. Módulos principales

## 8.1. Dashboard / Centro de control

Será la entrada principal para propietarios y administradores.

Debe responder:

> ¿Qué está pasando en mi clínica y qué debería atender ahora?

### Componentes

- Citas del día.
- Producción.
- Ingresos.
- Ocupación.
- Cancelaciones.
- No-shows.
- Leads.
- Tratamientos pendientes.
- Cartera.
- Alertas.
- Tareas.
- Oportunidades.

### Centro de oportunidades

Ejemplo:

```text
🔴 3 acciones de alta prioridad

$4.800.000
Implante mañana sin confirmar

$3.200.000
Tratamiento sin seguimiento desde hace 9 días

$850.000
Saldo vencido

🟡 7 leads pendientes

🟢 4 espacios disponibles esta semana
```

---

# 8.2. Pacientes

Debe centralizar toda la información del paciente.

### Información

- Datos personales.
- Información de contacto.
- Contactos de emergencia.
- Información administrativa.
- Historia clínica.
- Antecedentes.
- Documentos.
- Fotografías.
- Odontograma.
- Tratamientos.
- Citas.
- Pagos.
- Presupuestos.
- Comunicaciones.
- Consentimientos.

---

# 8.3. Historia clínica

Debe permitir registrar la información clínica de forma rápida y
estructurada.

### Funciones

- Motivo de consulta.
- Antecedentes.
- Anamnesis.
- Examen.
- Diagnóstico.
- Evolución.
- Procedimientos.
- Indicaciones.
- Archivos.
- Firmas/confirmaciones cuando correspondan.
- Plantillas clínicas.

### Productividad

Se deberán incluir:

- Plantillas.
- Campos reutilizables.
- Atajos.
- Autocompletado.
- Dictado/transcripción mediante IA cuando sea viable.
- Resumen del historial.

La IA debe ser asistiva y requerir revisión profesional antes de guardar
información clínica generada o modificada.

---

# 8.4. Odontograma

El odontograma será uno de los componentes especializados del producto.

Debe permitir:

- Visualización dental.
- Estado por pieza.
- Superficies.
- Tratamientos.
- Diagnósticos.
- Historial.
- Plan de tratamiento.
- Evolución.
- Interacción gráfica.

Debe existir separación entre:

```text
Estado actual
Historial
Diagnóstico
Plan propuesto
Tratamiento realizado
```

Esto evita mezclar información clínica de diferentes momentos.

---

# 8.5. Agenda

Debe manejar:

- Profesionales.
- Especialistas.
- Consultorios.
- Equipos/recursos.
- Duración.
- Procedimientos.
- Bloqueos.
- Disponibilidad.
- Citas.
- Reprogramaciones.
- Cancelaciones.
- Lista de espera.

### Agenda inteligente

Cada cita podrá tener:

```text
Paciente
Procedimiento
Duración
Valor estimado
Profesional
Especialista
Consultorio
Recursos
Nivel de riesgo
Estado de confirmación
```

---

# 8.6. Confirmación y recuperación de citas

Sistema de automatización:

```text
Cita creada
    ↓
Confirmación
    ↓
Recordatorio
    ↓
Paciente confirma
    ↓
Cita atendida
```

Si no confirma:

```text
No confirma
    ↓
Recordatorio adicional
    ↓
Tarea para recepción
    ↓
Contacto manual
```

Si cancela:

```text
Cancelación
    ↓
Liberar espacio
    ↓
Buscar pacientes compatibles
    ↓
Contactarlos
    ↓
Recuperar espacio
```

---

# 8.7. CRM de leads

El CRM debe conectar marketing con agenda.

### Pipeline

```text
Nuevo lead
   ↓
Contactado
   ↓
Calificado
   ↓
Cita propuesta
   ↓
Cita agendada
   ↓
Cita asistida
   ↓
Tratamiento propuesto
   ↓
Tratamiento aceptado
```

### Datos

- Nombre.
- Contacto.
- Fuente.
- Campaña.
- Procedimiento.
- Valor potencial.
- Estado.
- Responsable.
- Último contacto.
- Próxima acción.
- Historial de conversaciones.

---

# 8.8. Automatización de leads

Objetivo:

> Responder rápido y evitar que una oportunidad dependa de que una
> persona esté disponible.

Funciones:

- Respuestas automáticas.
- Preguntas de calificación.
- Captura de datos.
- Agendamiento.
- Asignación.
- Seguimiento.
- Alertas.
- Integración con WhatsApp mediante proveedores/APIs autorizadas.
- Registro de conversaciones.

Las integraciones de mensajería deberán cumplir las políticas y
condiciones de los respectivos proveedores.

**Nota de costos y tiempos:** la API oficial de WhatsApp Business (Meta)
tiene costos por conversación y un proceso de verificación/aprobación
que puede tardar semanas. Estos costos y tiempos deben dimensionarse y
comunicarse al cliente desde el inicio, y no deben asumirse como
"gratis" al calcular el modelo de suscripción.

---

# 8.9. Planes de tratamiento

Debe permitir crear:

- Diagnóstico.
- Procedimientos.
- Piezas involucradas.
- Precio.
- Descuentos.
- Duración estimada.
- Profesional.
- Especialista.
- Etapas.
- Condiciones de pago.

### Estados

```text
Borrador
   ↓
Presentado
   ↓
En decisión
   ↓
Aceptado
   ↓
En ejecución
   ↓
Completado
```

También:

```text
Rechazado
Pospuesto
Abandonado
```

---

# 8.10. Seguimiento de tratamientos

El sistema debe detectar oportunidades que necesitan seguimiento.

Ejemplo:

```text
Paciente: Carlos Rodríguez

Tratamiento: Rehabilitación oral
Valor: $6.200.000

Presentado: 01/09
Último contacto: 05/09

⚠ Sin seguimiento durante 9 días
```

El sistema puede:

- Crear tarea.
- Sugerir mensaje.
- Programar seguimiento.
- Registrar respuesta.
- Cambiar estado.
- Medir conversión.

---

# 8.11. Facturación y pagos

Debe permitir:

- Facturas.
- Recibos.
- Pagos.
- Métodos de pago.
- Abonos.
- Saldos.
- Devoluciones.
- Descuentos.
- Planes de pago.
- Cartera.
- Vencimientos.

La integración con facturación electrónica y requisitos tributarios
deberá implementarse según la normativa vigente y mediante los
mecanismos/proveedores correspondientes.

---

# 8.12. Cartera

Dashboard:

```text
Cartera total
$18.500.000

Vencida
$3.200.000

Por vencer
$5.800.000

Al día
$9.500.000
```

Debe poder identificar:

- Pacientes con saldo.
- Cuotas vencidas.
- Tratamientos con riesgo financiero.
- Historial de pagos.
- Compromisos de pago.

---

# 8.13. Especialistas externos

Funciones:

- Registro de especialistas.
- Especialidades.
- Porcentaje acordado.
- Disponibilidad.
- Procedimientos.
- Agenda.
- Pacientes.
- Producción.
- Honorarios.
- Liquidaciones.

### Indicadores

```text
Producción
Honorarios
Materiales
Margen
Número de procedimientos
```

---

# 8.14. Inventario

Debe permitir:

- Productos.
- Categorías.
- Proveedores.
- Entradas.
- Salidas.
- Ajustes.
- Lotes cuando aplique.
- Vencimientos.
- Existencias.
- Stock mínimo.

### Automatización

Ejemplo:

```text
Producto X
Stock actual: 12
Consumo promedio: 2/día

⚠ Posible agotamiento en 6 días
```

---

# 8.15. Reportes y analítica

Debe permitir visualizar:

### Operación

- Citas.
- Ocupación.
- Cancelaciones.
- No-shows.
- Productividad.

### Comercial

- Leads.
- Conversión.
- Fuentes.
- Campañas.
- Tratamientos propuestos.
- Tratamientos aceptados.

### Financiero

- Ingresos.
- Cartera.
- Pagos.
- Producción.
- Rentabilidad.

### Profesional

- Producción.
- Procedimientos.
- Horas.
- Pacientes.

---

# 8.16. Automatizaciones

El sistema debe disponer de un motor de automatización.

Ejemplo:

```text
EVENTO
Paciente no confirma cita
       ↓
REGLA
Cita de alto valor
       ↓
ACCIÓN
Enviar recordatorio
       ↓
ESPERAR
X horas
       ↓
CONDICIÓN
¿Confirmó?
       ↓
NO
       ↓
Crear tarea para recepción
```

Otros eventos:

- Lead creado.
- Lead sin respuesta.
- Cita creada.
- Cita cancelada.
- Cita no confirmada.
- Tratamiento presentado.
- Tratamiento sin seguimiento.
- Pago vencido.
- Inventario bajo.
- Paciente inactivo.

---

# 8.17. Notificaciones

Canales potenciales:

- WhatsApp.
- Email.
- SMS.
- Notificaciones internas.
- Push/web notifications.

La plataforma deberá mantener un historial de comunicaciones.

---

# 8.18. Inteligencia artificial

La IA debe utilizarse donde produzca ahorro de tiempo o mejor
información.

## Casos iniciales

### Asistente administrativo

Preguntas como:

> "¿Qué pacientes de alto valor necesitan seguimiento?"

> "¿Cuánto dinero tenemos en tratamientos pendientes?"

> "¿Qué citas de mañana tienen mayor riesgo de no-show?"

> "¿Qué leads llegaron ayer y todavía no tienen una cita?"

---

### Resumen de paciente

Generar un resumen de información clínica y administrativa relevante
para revisión del profesional.

---

### Dictado clínico

El odontólogo puede dictar una evolución y el sistema puede convertirla
en una estructura editable.

Siempre debe existir revisión profesional antes de guardar.

---

### Generación asistida de mensajes

Ejemplo:

```text
Paciente no confirmó cita.

IA propone:

"Hola, Carlos. Queríamos confirmar tu cita
para mañana a las 10:00 a. m. Si necesitas
reprogramarla, podemos ayudarte por este medio."
```

---

### Resiliencia y fallback de IA

Los flujos operativos (confirmaciones, recordatorios, tareas) **no deben
depender** de que el proveedor de IA esté disponible.

Si el servicio de IA falla, no responde o entrega una respuesta de baja
calidad, el sistema debe:

- Continuar el flujo con un mensaje/plantilla predeterminada sin IA.
- Registrar el fallo para revisión posterior.
- Nunca bloquear una acción operativa (confirmar cita, alertar cartera
  vencida, etc.) esperando una respuesta de IA.

---

# 8.19. Onboarding y soporte inicial

La adopción de un "Dr. Administrador" no técnico depende tanto del
producto como del proceso de implementación.

Debe contemplarse:

- Checklist de configuración inicial (tenant, usuarios, agenda,
  procedimientos, precios).
- Migración asistida de datos (ver sección 3.8).
- Capacitación breve orientada a tareas (ver principio de la sección 35).
- Canal de soporte definido para las primeras semanas de uso.
- Métrica de "tiempo hasta el primer valor" (ej. primera cita confirmada
  automáticamente, primer lead recuperado).

---

# 9. Motor de oportunidades

Este será uno de los elementos diferenciales del producto.

El sistema debe analizar eventos y datos para detectar:

- Leads sin respuesta.
- Tratamientos sin seguimiento.
- Citas de alto riesgo.
- Espacios disponibles.
- Pacientes inactivos.
- Saldos vencidos.
- Inventario crítico.
- Oportunidades comerciales.
- Problemas operativos.

La salida no debe ser únicamente un reporte.

Debe ser una **acción recomendada**.

```text
DATOS
  ↓
REGLAS / ANALÍTICA
  ↓
OPORTUNIDAD
  ↓
PRIORIDAD
  ↓
ACCIÓN RECOMENDADA
  ↓
RESULTADO
```

---

# 10. Métrica diferencial: valor recuperado

Se propone implementar una métrica orientada a demostrar ROI.

Ejemplo:

```text
Valor recuperado este mes

Leads convertidos       $2.100.000
Cancelaciones recuperadas
                         $1.800.000
Tratamientos reactivados
                         $3.200.000
Cobranza recuperada       $750.000
────────────────────────────────────
Total                    $7.850.000
```

Esta métrica debe implementarse con reglas transparentes y configurables
para evitar atribuciones engañosas.

---

# 11. Arquitectura técnica

## 11.1. Arquitectura general

```text
                         INTERNET
                             │
                           HTTPS
                             │
                             ▼
                    ┌─────────────────┐
                    │     NGINX       │
                    │ Reverse Proxy   │
                    └────────┬────────┘
                             │
              ┌──────────────┴──────────────┐
              │                             │
              ▼                             ▼
       ┌─────────────┐               ┌─────────────┐
       │   Angular   │               │ Spring Boot │
       │   Frontend  │◄─────────────►│   Backend   │
       └─────────────┘     REST      └──────┬──────┘
                                             │
                    ┌────────────────────────┼───────────────────┐
                    │                        │                   │
                    ▼                        ▼                   ▼
             ┌────────────┐           ┌────────────┐      ┌────────────┐
             │ PostgreSQL │           │   Redis    │      │ S3 / MinIO │
             │   Datos    │           │ Cache/Jobs │      │  Archivos  │
             └────────────┘           └────────────┘      └────────────┘
```

---

# 12. Stack tecnológico definitivo

## 12.1. Frontend

### Angular 22

Responsabilidades:

- Interfaz.
- Navegación.
- Formularios.
- Estado de UI.
- Visualizaciones.
- Agenda.
- Odontograma.
- Dashboard.
- Comunicación con API.

### Tecnologías complementarias

- TypeScript.
- Angular Router.
- Angular HttpClient.
- Signals.
- RxJS cuando sea apropiado.
- Angular Material o una solución UI equivalente.
- Tailwind CSS si se decide utilizarlo.
- Librería de gráficos.
- Librería especializada para calendario si resulta necesaria.

---

# 13. Backend

## Java 25 (LTS)

Java será el lenguaje principal del backend. Se fija la versión LTS
(25) para evitar migraciones forzadas por soporte de corto plazo (ver
justificación completa en la sección 39).

## Spring Boot

Será el framework principal para:

- API REST.
- Inyección de dependencias.
- Configuración.
- Seguridad.
- Persistencia.
- Validación.
- Integraciones.
- Procesos en segundo plano.

### Componentes

- Spring Web.
- Spring Security.
- Spring Data JPA.
- Hibernate.
- Bean Validation.
- Spring Actuator.
- Spring Scheduling cuando sea suficiente.
- Integraciones externas mediante clientes HTTP.

---

# 14. Base de datos

## PostgreSQL

Será la base de datos principal.

Se utilizará para:

- Pacientes.
- Usuarios.
- Clínicas.
- Citas.
- Historias clínicas.
- Odontogramas.
- Tratamientos.
- Presupuestos.
- Pagos.
- Facturas.
- Inventario.
- Leads.
- Tareas.
- Automatizaciones.
- Auditoría.
- Configuración.

### Migraciones

Se recomienda:

**Flyway**

Las modificaciones de esquema deben realizarse mediante migraciones
versionadas.

---

# 15. Cache y tareas

## Redis

Redis no es obligatorio para la primera versión, pero se recomienda como
componente preparado para:

- Cache.
- Rate limiting.
- Datos temporales.
- Locks distribuidos cuando sean necesarios.
- Colas simples.
- Procesamiento de tareas.

No debe introducirse simplemente por moda.

Cuando el sistema requiera procesamiento de eventos más complejo, se
puede evaluar una tecnología de mensajería dedicada.

---

# 16. Archivos

Los archivos clínicos no deberían almacenarse directamente dentro de
PostgreSQL como blobs en la mayoría de los casos.

Se recomienda:

**S3-compatible storage**

Opciones:

- AWS S3.
- Cloudflare R2.
- MinIO.
- Otro proveedor compatible.

PostgreSQL almacena los metadatos y referencias.

```text
PostgreSQL
   ↓
file_id
   ↓
storage_key
   ↓
S3 / MinIO
```

---

# 17. API

La comunicación entre Angular y Spring Boot será principalmente
mediante:

**REST + JSON + HTTPS**

Se recomienda utilizar:

**OpenAPI**

para documentar y mantener el contrato de la API.

Ejemplo:

```text
GET    /api/v1/patients
POST   /api/v1/patients
GET    /api/v1/patients/{id}
PATCH  /api/v1/patients/{id}
DELETE /api/v1/patients/{id}
```

La versión de API deberá gestionarse de manera consistente desde el
principio.

---

# 18. Seguridad

La seguridad es crítica debido a la naturaleza de la información
manejada.

Se deberán contemplar:

- HTTPS.
- Autenticación.
- Autorización.
- Roles.
- Permisos.
- Gestión de sesiones/tokens.
- Protección contra ataques comunes.
- Rate limiting.
- Validación de entrada.
- Auditoría.
- Gestión segura de secretos.
- Backups.
- Recuperación ante desastres.
- Cifrado donde corresponda.
- Separación de datos entre clínicas.

---

# 19. Multi-tenancy

Si el producto será SaaS para múltiples clínicas, el sistema debe
diseñarse desde el inicio pensando en multi-tenancy.

Conceptualmente:

```text
Tenant
 ├── Usuarios
 ├── Pacientes
 ├── Profesionales
 ├── Citas
 ├── Tratamientos
 ├── Facturas
 └── Configuración
```

Cada registro sensible debe estar asociado de manera segura al tenant
correspondiente.

El backend debe impedir que una clínica pueda consultar o modificar
información de otra.

Una estrategia inicial viable es:

**Base de datos compartida + `tenant_id` + aislamiento obligatorio a
nivel de aplicación**, acompañado de controles adicionales y pruebas
específicas.

Para una evolución futura se puede evaluar aislamiento por esquema o
base de datos según requisitos de seguridad, escala y cumplimiento.

---

# 20. Auditoría

Debe existir un registro de acciones importantes.

Ejemplo:

```text
Usuario: Dr. García
Acción: Modificación de historia clínica
Paciente: #12345
Fecha: 2026-09-04 15:42
Campos modificados: ...
```

También deberán auditarse acciones como:

- Acceso a información sensible.
- Modificación de historia clínica.
- Creación/eliminación lógica.
- Cambios de permisos.
- Cambios financieros.
- Facturación.
- Pagos.
- Configuración crítica.

---

# 21. Docker

Todo el entorno debe ser reproducible mediante Docker.

En desarrollo:

```text
docker compose
├── PostgreSQL
├── Redis
├── MinIO
└── servicios auxiliares
```

La aplicación Angular y el backend Spring Boot deberán poder construirse
mediante imágenes reproducibles.

---

# 22. Infraestructura recomendada

### Desarrollo

- Docker.
- Docker Compose.
- Git.
- IDE.
- PostgreSQL.
- Herramientas de API.

### Producción

Inicialmente:

- Linux.
- Docker.
- Reverse proxy.
- HTTPS.
- PostgreSQL gestionado o servidor dedicado.
- Object Storage.
- Backups automáticos.
- Monitorización.

No es necesario comenzar con Kubernetes.

---

# 23. CI/CD

Se recomienda incorporar:

**GitHub Actions** o plataforma equivalente.

Pipeline:

```text
Push
 ↓
Tests
 ↓
Lint
 ↓
Build
 ↓
Security checks
 ↓
Docker image
 ↓
Deploy
```

---

# 24. Observabilidad

Se recomienda desde etapas tempranas:

- Logs estructurados.
- Métricas.
- Health checks.
- Trazabilidad.
- Monitoreo de errores.

Tecnologías que pueden incorporarse según el nivel de madurez:

- Spring Boot Actuator.
- OpenTelemetry.
- Prometheus.
- Grafana.
- Sentry u otra plataforma de errores.

No es necesario implementar todo el ecosistema de observabilidad desde
el primer día.

---

# 25. Arquitectura interna del backend

Se recomienda una arquitectura modular orientada a dominios.

Ejemplo:

```text
backend/
├── auth/
├── tenants/
├── users/
├── patients/
├── clinical/
├── odontogram/
├── appointments/
├── treatments/
├── crm/
├── billing/
├── payments/
├── inventory/
├── specialists/
├── notifications/
├── automation/
├── reports/
├── files/
└── audit/
```

La aplicación debe comenzar como un **monolito modular**, no como
microservicios.

---

# 26. Capas internas

Dentro de cada módulo se puede utilizar una separación como:

```text
Controller
    ↓
Application / Service
    ↓
Domain
    ↓
Repository
    ↓
Infrastructure
```

Ejemplo:

```text
TreatmentController
       ↓
TreatmentService
       ↓
Treatment domain
       ↓
TreatmentRepository
       ↓
PostgreSQL
```

El objetivo es evitar que los controllers contengan lógica de negocio
compleja.

---

# 27. Frontend Angular

La aplicación debe organizarse por funcionalidades.

Ejemplo:

```text
src/app/
├── core/
├── shared/
├── auth/
├── dashboard/
├── patients/
├── clinical/
├── odontogram/
├── appointments/
├── treatments/
├── crm/
├── billing/
├── inventory/
├── specialists/
├── reports/
└── settings/
```

### Core

Servicios globales:

- Autenticación.
- HTTP.
- Interceptores.
- Configuración.
- Guards.
- Manejo de errores.

### Shared

Componentes y utilidades reutilizables.

---

# 28. Modelo conceptual de datos

Entidades principales:

```text
Tenant
User
Role
Permission
Professional
Specialist
Patient
MedicalHistory
ClinicalNote
Tooth
Odontogram
Diagnosis
TreatmentPlan
Treatment
Appointment
AppointmentStatus
Lead
LeadInteraction
Task
Automation
Quote
Invoice
Payment
PaymentPlan
AccountReceivable
Product
InventoryMovement
Notification
Communication
File
AuditEvent
```

Relaciones principales:

```text
Tenant
 ├── Users
 ├── Patients
 │    ├── Clinical History
 │    ├── Odontogram
 │    ├── Treatments
 │    ├── Appointments
 │    ├── Quotes
 │    ├── Payments
 │    └── Communications
 │
 ├── Professionals
 ├── Specialists
 ├── Leads
 ├── Inventory
 └── Financial records
```

---

# 29. Flujo principal del negocio

## Lead → paciente → tratamiento → ingreso

```text
Lead
 ↓
Calificación
 ↓
Cita
 ↓
Asistencia
 ↓
Valoración
 ↓
Diagnóstico
 ↓
Plan de tratamiento
 ↓
Presupuesto
 ↓
Seguimiento
 ↓
Aceptación
 ↓
Tratamiento
 ↓
Pago
 ↓
Seguimiento posterior
 ↓
Retorno
```

Este flujo debe estar conectado entre módulos.

No deben existir módulos aislados.

---

# 30. Integraciones futuras

El sistema deberá diseñarse para poder integrar:

- WhatsApp Business / proveedores autorizados.
- Meta.
- Google Ads.
- Email.
- SMS.
- Pasarelas de pago.
- Facturación electrónica.
- Servicios de IA.
- Calendarios externos.
- Firma electrónica.
- Almacenamiento externo.

Las integraciones deberán aislarse mediante módulos/adaptadores para
evitar acoplar el dominio principal a un proveedor concreto.

---

# 31. Modelo de negocio

El producto se comercializará mediante **suscripción (SaaS)**.

### Estructura propuesta (a refinar)

- Cobro recurrente (mensual o anual) por clínica/tenant.
- Planes diferenciados según variables como número de usuarios,
  número de sedes/consultorios o volumen de citas.
- Periodo de prueba gratuito para reducir la fricción de adopción
  inicial.
- Sin costo de licencia perpetua ni instalación on-premise en esta
  etapa.

### Pendiente de definir

- Precios exactos por plan.
- Qué funcionalidades (ej. IA, WhatsApp, especialistas externos) van en
  cada plan vs. como add-on.
- Política de facturación y medios de pago para el cobro de la
  suscripción misma (distinto de los pagos que la clínica cobra a sus
  pacientes, sección 8.11).

Estos detalles de pricing se desarrollarán en un documento comercial
aparte, una vez exista validación con clientes reales.

---

# 32. Migración de datos de clientes existentes

Ver sección 3.8 para el problema. Esta sección resume el alcance
técnico de la solución.

- Definir un formato estándar de importación (CSV/Excel) para
  pacientes y agenda futura como primer alcance.
- Ofrecer migración asistida manual para los primeros clientes
  (antes de automatizar el proceso).
- Automatizar la importación solo cuando exista un patrón repetible
  entre varios clientes.
- No migrar historia clínica completa como requisito de v1; priorizar
  que el cliente pueda operar hacia adelante rápidamente.

---

# 33. Diferenciadores del producto

Los principales diferenciadores propuestos son:

## 1. Centro de oportunidades

El sistema identifica qué necesita atención.

## 2. CRM odontológico conectado con tratamientos

No solo administra leads: conecta marketing, citas y producción.

## 3. Agenda orientada a valor

Las citas tienen impacto económico y operativo.

## 4. Recuperación automática

El sistema intenta recuperar:

- Leads.
- Cancelaciones.
- Tratamientos.
- Cartera.

## 5. Automatización operativa

Reduce tareas manuales.

## 6. Gestión de especialistas externos

Adaptada al modelo de clínicas o centros odontologicos pequeñas.

## 7. IA administrativa

Utilizada para ahorrar tiempo, no simplemente como elemento de
marketing.

## 8. Métricas de impacto

El sistema intenta demostrar cuánto valor genera.

---

# 34. Principales KPIs del producto

## Comercial

- Leads nuevos.
- Tiempo de primera respuesta.
- Lead → cita.
- Cita → asistencia.
- Asistencia → tratamiento.
- Tratamiento aceptado.
- Valor potencial.
- Valor convertido.

## Agenda

- Ocupación.
- No-show.
- Cancelación.
- Recuperación de espacios.
- Horas improductivas.

## Financiero

- Producción.
- Ingresos.
- Cartera.
- Cartera vencida.
- Ticket promedio.
- Cobranza.
- Tratamientos pendientes de pago.

## Productividad

- Tareas automatizadas.
- Tiempo administrativo estimado ahorrado.
- Acciones realizadas desde recomendaciones.
- Oportunidades recuperadas.

---

# 35. Principio de diseño UX

El usuario no debería necesitar entender la arquitectura interna del
software.

La interfaz debe estar orientada a tareas.

En lugar de:

```text
CRM
Agenda
Facturación
Pacientes
Reportes
```

el sistema debe permitir flujos como:

```text
"Quiero llenar este espacio de agenda."

"Quiero recuperar este tratamiento."

"Quiero saber qué debo hacer hoy."

"Quiero cobrar lo pendiente."

"Quiero preparar mi consulta."
```

La aplicación debe llevar al usuario directamente a la acción.

---

# 36. Seguridad y privacidad

Debido a que se manejarán datos personales y clínicos, la seguridad será
un requisito fundamental.

Se deberá contemplar:

- Principio de mínimo privilegio.
- Control de acceso por roles.
- Segregación por tenant.
- Auditoría.
- Backups.
- Cifrado.
- Gestión segura de secretos.
- Políticas de retención.
- Recuperación ante desastres.
- Protección de APIs.
- Validación de entrada.
- Protección contra inyección.
- Protección contra CSRF/XSS según arquitectura.
- Rate limiting.
- Gestión de sesiones.
- Registro de accesos relevantes.

Para comercialización en Colombia se deberá realizar revisión jurídica y
técnica de las obligaciones aplicables a datos personales, historia
clínica, facturación, RIPS y demás requisitos correspondientes.

---

# 37. Estrategia de desarrollo

El desarrollo deberá seguir un enfoque incremental.

```text
Problema
   ↓
Hipótesis
   ↓
Funcionalidad mínima
   ↓
Usuario real
   ↓
Medición
   ↓
Aprendizaje
   ↓
Mejora
```

No se debe asumir que una funcionalidad es valiosa simplemente porque
parece interesante técnicamente.

> **Nota:** el roadmap detallado por fases (qué módulos de la sección 8
> entran en el MVP vs. en versiones posteriores) se definirá en un
> documento separado, para no mezclar la visión de producto de largo
> plazo (este documento) con la planificación de ejecución a corto
> plazo.

---

# 38. Principio arquitectónico clave

El sistema debe diferenciar claramente entre:

### Sistema de registro

Guarda:

> "Qué ocurrió."

### Sistema operativo

Ayuda a decidir:

> "Qué debería ocurrir ahora."

El producto debe evolucionar hacia el segundo.

---

# 39. Justificación de decisiones tecnológicas y despliegue

## 39.1. Por qué Java 25 (LTS) + Spring Boot

La decisión de usar Java + Spring Boot en lugar de continuar con el
stack Angular + Supabase (usado en proyectos anteriores) es intencional
y responde a objetivos personales y de portafolio, no solo técnicos:

- Ya se domina Angular; el reto pendiente es el backend con Spring Boot.
- No existen proyectos con este stack en el portafolio/GitHub actual;
  diversificarlo tiene valor profesional.
- Cambiar de foco frente a varios proyectos consecutivos de
  Angular + Supabase es deseado explícitamente.

**Corrección de versión:** se utilizará **Java 25 (LTS)**, no Java 26.
Java 26 es una versión no-LTS de corta vida; iniciar un producto
comercial nuevo sobre una versión no-LTS obligaría a migraciones de
versión cada pocos meses sin necesidad real.

## 39.2. Estrategia de despliegue

Al usar **OpenJDK + Docker** desde el inicio (sección 21), el backend
queda empaquetado como una imagen estándar, lo que permite desplegar en
plataformas administradas tipo **Render** o **Railway** sin necesitar
Kubernetes ni infraestructura propia en esta etapa:

```text
GitHub (push a main/dev)
    ↓
GitHub Actions (build + test)
    ↓
Imagen Docker (Spring Boot + OpenJDK)
    ↓
Render / Railway (despliegue administrado)
    ↓
PostgreSQL administrado (Render/Railway) + storage S3-compatible
```

Esto mantiene coherencia con la decisión #13 (sección 41): no introducir
Kubernetes ni microservicios prematuramente, aprovechando que estas
plataformas administran el runtime, TLS y la base de datos por el
desarrollador.

---

# 40. Stack final resumido

```text
FRONTEND
Angular 22
TypeScript
Angular Material / UI system
RxJS
Signals

BACKEND
Java 25 (LTS)
Spring Boot
Spring Security
Spring Data JPA
Hibernate
Bean Validation
Flyway
OpenAPI

DATABASE
PostgreSQL

STORAGE
S3 / MinIO (o storage administrado del proveedor de despliegue)

CACHE / ASYNC
Redis

DESPLIEGUE
Docker
Render / Railway
GitHub Actions (CI/CD)

OBSERVABILITY
Spring Actuator
OpenTelemetry
Sentry
Prometheus/Grafana según necesidad

TESTING
JUnit
Mockito
Testcontainers
Playwright

VERSION CONTROL
Git
```

*(Nota: esta tabla es un resumen de referencia rápida; el detalle
completo del stack y sus responsabilidades está en la sección 12.)*

---

# 41. Decisiones arquitectónicas iniciales

1.  **Monolito modular antes que microservicios.**
2.  **PostgreSQL como base de datos principal.**
3.  **REST como mecanismo inicial de comunicación.**
4.  **Angular separado del backend mediante API.**
5.  **Spring Boot como núcleo del backend.**
6.  **Docker desde el inicio.**
7.  **Multi-tenancy considerado desde el diseño inicial.**
8.  **Auditoría desde las primeras versiones.**
9.  **Migraciones de base de datos mediante Flyway.**
10. **Archivos fuera de PostgreSQL, usando object storage.**
11. **Redis solamente cuando exista una necesidad real.**
12. **IA inicialmente enfocada en productividad administrativa y
    asistencia.**
13. **No introducir Kubernetes ni microservicios prematuramente.**
14. **Java 25 (LTS), no versiones no-LTS de corta vida.**
15. **Despliegue mediante Docker en plataformas administradas (Render
    o Railway) mientras la escala no justifique infraestructura propia.**

---

# 42. Visión a largo plazo

El objetivo final no es que la clínica simplemente tenga todos sus datos
digitalizados.

El objetivo es que el software se convierta progresivamente en una capa
inteligente sobre la operación de la clínica.

La evolución conceptual será:

```text
ETAPA 1
Digitalizar
    ↓
ETAPA 2
Centralizar
    ↓
ETAPA 3
Automatizar
    ↓
ETAPA 4
Detectar oportunidades
    ↓
ETAPA 5
Recomendar acciones
    ↓
ETAPA 6
Ejecutar acciones automáticamente
```

El producto ideal será aquel en el que el propietario pueda entrar al
sistema y entender en pocos segundos:

> **Qué está pasando, qué está en riesgo, qué oportunidad existe y qué
> debería hacer ahora.**

---

# 43. Objetivo final del producto

La plataforma debe ser capaz de responder continuamente cuatro
preguntas:

### 1. ¿Dónde estamos perdiendo dinero?

Ejemplos:

- Leads sin respuesta.
- No-shows.
- Cancelaciones.
- Tratamientos no aceptados.
- Cartera vencida.

### 2. ¿Dónde estamos perdiendo tiempo?

Ejemplos:

- Tareas administrativas.
- Seguimientos manuales.
- Confirmaciones.
- Revisión de información.
- Coordinación de especialistas.

### 3. ¿Qué oportunidad podemos aprovechar?

Ejemplos:

- Espacio libre.
- Paciente interesado.
- Tratamiento pendiente.
- Paciente inactivo.
- Lead de alto valor.

### 4. ¿Qué debería hacer ahora?

Esta última pregunta es la más importante.

El sistema debe pasar de:

> **"Aquí están tus datos."**

a:

> **"Esto es lo que está ocurriendo y estas son las acciones que pueden
> generar mayor impacto."**

---

# 44. Resumen final

El producto será una plataforma web de gestión y productividad
odontológica basada en:

**Angular 22 + TypeScript**

para la experiencia de usuario,

**Java 25 (LTS) + Spring Boot**

para la lógica de negocio y API, desplegado mediante **Docker** en
plataformas administradas como **Render o Railway**,

**PostgreSQL**

para persistencia,

**Docker**

para empaquetado y despliegue,

y componentes complementarios como:

**Flyway, Redis, S3/MinIO, OpenAPI, Nginx, GitHub Actions,
Testcontainers y herramientas de observabilidad.**

El núcleo diferenciador no será la existencia de funciones tradicionales
como pacientes, citas o facturación.

El verdadero valor estará en conectar toda la operación:

```text
CAPTACIÓN
    ↓
LEAD
    ↓
CONVERSIÓN
    ↓
AGENDA
    ↓
ASISTENCIA
    ↓
TRATAMIENTO
    ↓
SEGUIMIENTO
    ↓
COBRO
    ↓
RETENCIÓN
```

y utilizar automatización, datos e inteligencia artificial para reducir
pérdidas y trabajo administrativo.

> **La meta no es construir otro software para odontólogos.**
>
> **La meta es construir una plataforma que ayude a un centro
> odontológico a operar mejor, perder menos oportunidades y generar más
> valor con el mismo equipo.**

El producto se comercializará bajo un modelo de **suscripción (SaaS)**
(detalle en sección 31), y su alcance de construcción se ejecutará de
forma incremental mediante un roadmap por fases que se definirá en un
documento separado (ver nota en sección 37).

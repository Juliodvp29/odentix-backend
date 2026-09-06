# odentix-backend

Backend SaaS para clínicas odontológicas (Java 25 + Spring Boot 4.1.1 + PostgreSQL 16+).

## Requisitos

- Java 25 (LTS)
- Maven wrapper incluido (`./mvnw`)
- Docker + Docker Compose (para PostgreSQL local, FASE0-04)

## Base de datos local

```bash
docker compose up -d
```

Levanta PostgreSQL 16 en `localhost:5434` con base `odentix`, usuario `odentix`, clave `odentix`
(solo defaults locales, ver `docker-compose.yml`). Para detener: `docker compose down`.
Los datos persisten en el volumen `pgdata`.

> Puerto `5434` (no el habitual `5432`) porque en esta máquina de desarrollo hay
> Postgres nativos ocupando el `5432` y el `5433` (servicios `postgresql-x64-17/18`).

## Cómo correr por perfil

```bash
# Desarrollo (usa docker local por defecto, o sobreescribe con env vars)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Tests de integración (Testcontainers: levanta su propio PostgreSQL, solo exige el daemon de Docker)
./mvnw test

# Producción (SIN defaults: exige DB_URL, DB_USERNAME, DB_PASSWORD; puerto vía $PORT)
DB_URL=jdbc:postgresql://<host>:5432/<db> DB_USERNAME=<user> DB_PASSWORD=<pass> ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

Variables soportadas (sintaxis `${VAR:default}`):

| Variable      | Default local (dev)                              | Prod |
|---------------|--------------------------------------------------|------|
| `DB_URL`      | `jdbc:postgresql://localhost:5434/odentix`       | sin default (obligatoria) |
| `DB_USERNAME` | `odentix`                                        | sin default (obligatoria) |
| `DB_PASSWORD` | `odentix`                                        | sin default (obligatoria) |
| `PORT`        | `8081` (fijo en local)                           | `8080` si no se define |

Ningún secreto real está commiteado: solo hay defaults locales sin valor productivo.

## Salud

```bash
curl http://localhost:8081/actuator/health
# {"status":"UP"}
```

Puerto local `8081` (el `8080` lo ocupa `AgentService.exe` en la máquina de desarrollo).

# FASE0-07: imagen multi-stage.
# Stage 1 compila con Maven + JDK 25; stage 2 solo ejecuta el jar con JRE 25
# (liviana, sin toolchain de build).
# Tags verificados con `docker manifest inspect` (no asumidos).

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
# El pom primero: la capa de descarga de dependencias se reutiliza
# mientras no cambie el pom.
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw mvnw.cmd ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# Perfil prod por defecto (la plataforma puede sobreescribirlo con
# SPRING_PROFILES_ACTIVE). Prod exige DB_URL/DB_USERNAME/DB_PASSWORD
# (sin defaults: falla al arrancar antes que conectarse a una BD
# equivocada) y lee el puerto de $PORT (default 8080).
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "app.jar"]

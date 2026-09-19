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
# Memoria JVM dimensionada al contenedor (Render free = 512MB).
# La app (Spring Boot + Hibernate, 240 clases) no cabe con defaults: el heap
# solo ya superaba el 25% (~128MB) y con 75% el RSS total excedía los 512MB
# (diagnosticado 2026-09-19: OOM-kill de Render tras inicializar Hibernate).
# Esta combinación apunta a ~450MB totales: heap 50%, metaspace acotado, GC
# serial (menor huella que G1) y stacks reducidos. Sobre-escribible con JAVA_OPTS.
# Si Render sigue matando el proceso, el camino es subir de plan (Starter 1GB+).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50.0 -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -Xss512k"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

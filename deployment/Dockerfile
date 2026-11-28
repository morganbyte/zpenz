# Imagen de dos etapas: compila desde el código fuente, así sirve tanto en local
# como en plataformas que construyen directamente desde el repositorio (Render).
# Se construye desde la raíz del repositorio:
#   docker build -f deployment/Dockerfile -t zpenz .

# --- Etapa 1: compilar el jar ---
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src

# Primero solo lo necesario para descargar Gradle y las dependencias,
# para que Docker reutilice esa capa mientras no cambien los build.gradle.
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle/ gradle/
COPY applications/app-service/build.gradle applications/app-service/
COPY domain/model/build.gradle domain/model/
COPY domain/usecase/build.gradle domain/usecase/
COPY infrastructure/entry-points/api-rest/build.gradle infrastructure/entry-points/api-rest/
COPY infrastructure/driven-adapters/ledger-store/build.gradle infrastructure/driven-adapters/ledger-store/
RUN ./gradlew --no-daemon --no-configuration-cache help

# Las pruebas corren en el CI; aquí solo se empaqueta.
COPY . .
RUN ./gradlew --no-daemon --no-configuration-cache :app-service:bootJar -x test

# --- Etapa 2: imagen de ejecución, solo con el JRE ---
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=build --chown=appuser:appgroup /src/applications/app-service/build/libs/zpenz.jar zpenz.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
USER appuser
EXPOSE 8080
# exec reemplaza al shell, así la JVM recibe SIGTERM y se apaga ordenadamente.
ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /app/zpenz.jar"]

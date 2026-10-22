# --- build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY server/pom.xml server/pom.xml
RUN mvn -B -q -pl server -am dependency:go-offline

COPY server server
RUN mvn -B -pl server -am package -DskipTests

# --- run ---
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --create-home --uid 10001 mc
RUN mkdir -p /data/screenshots && chown -R mc:mc /data

COPY --from=build /build/server/target/management-control-server-*.jar app.jar

USER mc
EXPOSE 8080

ENV MC_SCREENSHOTS_PATH=/data/screenshots

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

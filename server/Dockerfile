# Build stage: compile the backend jar.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN mvn -B -ntp -DskipTests package

# Runtime stage: JRE only, non-root, with curl for the container healthcheck.
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 ride
WORKDIR /app
COPY --from=build /workspace/target/ride-dispatch-server-*.jar app.jar
RUN mkdir -p /app/data/brand && chown -R ride:ride /app
USER ride
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

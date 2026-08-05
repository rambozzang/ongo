# Stage 1: Build
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew .
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY onGo-common onGo-common
COPY onGo-domain onGo-domain
COPY onGo-application onGo-application
COPY onGo-infrastructure onGo-infrastructure
COPY onGo-api onGo-api
RUN chmod +x gradlew && ./gradlew :onGo-api:bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:25-jre
RUN groupadd -r ongo && useradd -r -g ongo -s /bin/false ongo
WORKDIR /app
COPY --from=build /app/onGo-api/build/libs/ongo-api-*.jar app.jar
RUN mkdir -p /app/logs && chown -R ongo:ongo /app
USER root
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
USER ongo
EXPOSE 8070
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8070/actuator/health || exit 1
# JSON-form ENTRYPOINT does not expand environment variables. Use a tiny shell
# wrapper so SPRING_PROFILES_ACTIVE is actually honored by the container.
ENTRYPOINT ["sh", "-c", "exec java -Xms512m -Xmx2g -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} -jar app.jar"]

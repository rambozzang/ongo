# Stage 1: Build
FROM eclipse-temurin:21-jdk AS build
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
FROM eclipse-temurin:21-jre
RUN groupadd -r ongo && useradd -r -g ongo -s /bin/false ongo
WORKDIR /app
COPY --from=build /app/onGo-api/build/libs/ongo-api-*.jar app.jar
RUN mkdir -p /app/logs && chown -R ongo:ongo /app
USER ongo
EXPOSE 8777
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8777/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

plugins {
    kotlin("plugin.spring")
}

dependencies {
    api(project(":onGo-domain"))
    api(project(":onGo-common"))
    // TODO: Move StorageService, PlatformUploadService interfaces to domain layer
    // and TusUploadController to onGo-api to remove this dependency.
    implementation(project(":onGo-application"))

    // jOOQ
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation(libs.jooq)

    // Database
    implementation("org.postgresql:postgresql")
    implementation("com.zaxxer:HikariCP")

    // Flyway
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Spring AI
    implementation("org.springframework.ai:spring-ai-anthropic-spring-boot-starter")
    implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter")
    implementation("org.springframework.ai:spring-ai-vertex-ai-gemini-spring-boot-starter")

    // Cache
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation(libs.caffeine)

    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Storage (MinIO / S3)
    implementation(libs.minio)
    implementation(libs.aws.s3)

    // Encryption
    implementation("org.springframework.security:spring-security-crypto")

    // Security + OAuth2
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Reactive WebClient (Google Drive OAuth + streaming download — Task 10/17)
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // JWT
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // Resilience4j (Circuit Breaker + Retry)
    implementation(libs.resilience4j.spring)
    implementation(libs.resilience4j.retry)

    // AOP (Resilience4j 어노테이션 지원)
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Testcontainers (for @SpringBootTest integration tests with real PostgreSQL)
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")

    // MockWebServer (Google Drive OAuth 클라이언트 단위 테스트용)
    // — okhttp 4.12.0이 minio를 통해 이미 classpath에 있으므로 동일 버전 사용
    testImplementation("com.squareup.okhttp3:mockwebserver:5.1.0")
}

// Test classpath에 onGo-api의 Flyway 마이그레이션만 공유 (db/migration/*.sql)
// 프로덕션 application.yml은 제외 — test용 application-test.yml 사용
// srcDir은 classpath root로 복사되므로 src/main/resources 자체를 가리키되,
// includes로 db/migration 만 포함시켜 JWT_SECRET 등이 요구되는 application.yml 제외
//
// Test classpath에 onGo-api의 Flyway 마이그레이션 공유 (V1~V43 전체)
tasks.named<Copy>("processTestResources") {
    from(project(":onGo-api").file("src/main/resources")) {
        include("db/migration/**")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

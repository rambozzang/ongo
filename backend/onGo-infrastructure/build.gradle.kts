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
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

// Test classpath에 onGo-api의 Flyway 마이그레이션만 공유 (db/migration/*.sql)
// 프로덕션 application.yml은 제외 — test용 application-test.yml 사용
// srcDir은 classpath root로 복사되므로 src/main/resources 자체를 가리키되,
// includes로 db/migration 만 포함시켜 JWT_SECRET 등이 요구되는 application.yml 제외
//
// V38~V41 제외 사유: V38 은 과거 AI 콘텐츠 스튜디오 기능 일부 제거 후 남은 고아
// 마이그레이션으로 `revenue_alert_configs` / `revenue_insights` 테이블에 의존하는데
// CREATE 스크립트가 없어 깨끗한 DB 에서 실행되지 않는다 (기존 dev/prod 는 baseline).
// V39~V41 은 V38 뒤에 붙어있고 현재 테스트 대상 (ContentSource) 과 무관.
// Testcontainers 신규 DB 에서는 V1~V37 + V42 만으로 필요한 스키마가 구성된다.
tasks.named<Copy>("processTestResources") {
    from(project(":onGo-api").file("src/main/resources")) {
        include("db/migration/**")
        exclude("db/migration/V38__*.sql")
        exclude("db/migration/V39__*.sql")
        exclude("db/migration/V40__*.sql")
        exclude("db/migration/V41__*.sql")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

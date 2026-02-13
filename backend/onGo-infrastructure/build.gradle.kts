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

    // JWT
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
}

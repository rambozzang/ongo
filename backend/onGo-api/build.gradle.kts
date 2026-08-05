plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":onGo-common"))
    implementation(project(":onGo-domain"))
    implementation(project(":onGo-application"))
    implementation(project(":onGo-infrastructure"))

    // Web
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-jackson2")

    // AOP (for @Aspect-based permission checks)
    implementation("org.springframework.boot:spring-boot-starter-aspectj")

    // Security + OAuth2 (configured here at API boundary)
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")

    // Swagger / OpenAPI
    implementation(libs.springdoc.webmvc)

    // WebSocket
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    // Actuator
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // DevTools
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("ongo-api")
}

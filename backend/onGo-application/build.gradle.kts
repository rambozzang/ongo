plugins {
    kotlin("plugin.spring")
}

dependencies {
    api(project(":onGo-domain"))
    api(project(":onGo-common"))

    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")

    // Spring AI (ChatClient, Structured Output, Audio Transcription)
    implementation("org.springframework.ai:spring-ai-core")
    implementation("org.springframework.ai:spring-ai-openai")

    // Rate Limiting (Bucket4j)
    implementation(libs.bucket4j)

    // Caching (Caffeine)
    implementation(libs.caffeine)
}

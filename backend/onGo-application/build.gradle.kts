plugins {
    kotlin("plugin.spring")
}

dependencies {
    api(project(":onGo-domain"))
    api(project(":onGo-common"))

    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")
    implementation("org.springframework:spring-web")

    // Spring AI (ChatClient, Structured Output, Audio Transcription)
    implementation("org.springframework.ai:spring-ai-client-chat")
    implementation("org.springframework.ai:spring-ai-openai")

    // Rate Limiting (Bucket4j)
    implementation(libs.bucket4j)

    // Caching (Caffeine)
    implementation(libs.caffeine)

    // Excel (쇼츠 업로드 예약표 내보내기/가져오기)
    implementation(libs.poi.ooxml)
}

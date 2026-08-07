plugins {
    kotlin("plugin.spring")
}

dependencies {
    api(project(":onGo-domain"))
    api(project(":onGo-common"))

    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-tx")

    // Spring AI (ChatClient, Structured Output, Audio Transcription)
    // BOM 2.0.0 부터 spring-ai-core 가 client-chat 등으로 쪼개졌다. core 를 그대로 두면
    // 버전이 해석되지 않아 "Could not find org.springframework.ai:spring-ai-core:." 로 실패한다.
    // BOM 버전(루트 build.gradle.kts)과 이 아티팩트명은 항상 같이 움직여야 한다.
    implementation("org.springframework.ai:spring-ai-client-chat")
    implementation("org.springframework.ai:spring-ai-openai")

    // Rate Limiting (Bucket4j)
    implementation(libs.bucket4j)

    // Caching (Caffeine)
    implementation(libs.caffeine)

    // Excel (쇼츠 업로드 예약표 내보내기/가져오기)
    implementation(libs.poi.ooxml)
}

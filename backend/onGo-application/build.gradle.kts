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
    // spring-ai-bom 1.0.0-M5 기준 아티팩트다. client-chat 으로 분리된 것은 이후 버전이라
    // BOM 을 올리기 전에는 core 를 써야 버전이 해석된다.
    implementation("org.springframework.ai:spring-ai-core")
    implementation("org.springframework.ai:spring-ai-openai")

    // Rate Limiting (Bucket4j)
    implementation(libs.bucket4j)

    // Caching (Caffeine)
    implementation(libs.caffeine)

    // Excel (쇼츠 업로드 예약표 내보내기/가져오기)
    implementation(libs.poi.ooxml)
}

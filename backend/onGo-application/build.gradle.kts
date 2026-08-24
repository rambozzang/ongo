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

/*
 * `FunnelMeasurementDocTest` 가 읽는 운영 문서를 테스트 입력으로 등록한다.
 *
 * ## 왜 필요한가
 *
 * 그 테스트는 저장소의 마크다운을 직접 읽어 SQL 의 집계 단위와 action 목록을 검사한다.
 * 소스가 아니라서 Gradle 이 변경을 모르고, 문서만 고친 뒤 테스트를 돌리면 태스크가
 * UP-TO-DATE 로 스킵돼 **옛 결과가 그대로 통과한다.** 실제로 이 가드의 변이 검증에서
 * 한 번 거짓 통과했고, `--rerun-tasks` 를 붙여야 잡혔다.
 *
 * 사람이 매번 플래그를 기억하는 것에 의존하면 언젠가 잊는다. 입력으로 선언해 Gradle 이
 * 알게 한다.
 *
 * ## 왜 파일 하나만 넣는가
 *
 * `docs/` 전체를 넣으면 무관한 문서 한 줄만 고쳐도 이 모듈 테스트 전체가 다시 돈다.
 * 테스트가 실제로 읽는 파일은 하나뿐이므로 그것만 선언한다.
 *
 * ## 경로
 *
 * Gradle 루트 프로젝트는 `backend/` 이고 저장소 루트는 그 부모다. 실행 디렉터리에
 * 좌우되지 않도록 `rootProject` 기준으로 계산한다.
 *
 * `PathSensitivity.RELATIVE` 는 절대 경로가 아니라 상대 경로 + 내용으로 판정하게 해,
 * 체크아웃 위치가 다른 CI 에서도 캐시가 유효하게 한다.
 */
tasks.named<Test>("test") {
    inputs.file(
        rootProject.layout.projectDirectory
            .dir("..")
            .file("docs/operations/FUNNEL_MEASUREMENT_QUERIES.md"),
    )
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("funnelMeasurementDoc")
}

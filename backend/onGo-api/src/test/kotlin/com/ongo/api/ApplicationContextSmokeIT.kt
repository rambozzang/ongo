package com.ongo.api

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * **기본 프로필**에서 스프링 컨텍스트가 실제로 뜨는지 확인한다.
 *
 * 목적은 하나다. **모듈을 지웠을 때 빈 그래프가 깨지지 않는지** 보는 것.
 * 지운 빈을 누군가 주입받고 있으면 컨텍스트가 죽는데, 그 의존이 `wip` 게이트 뒤에 있으면
 * 정적 분석으로는 놓칠 수 있다. 컴파일은 통과하고 기동에서만 터진다.
 *
 * ## 이 테스트가 **잡지 못하는** 것 (실측)
 *
 * 의존성 버전 조합 문제는 여기서 안 잡힌다. 확인해봤다 — `springdoc` 을 어긋난 버전으로
 * 되돌리고 이 테스트를 돌려도 통과한다(`NONE`, `MOCK` 둘 다).
 *
 * 이유는 층이 다르기 때문이다. Gradle 테스트 런타임은 의존성 충돌을 단일 버전으로
 * 해석하지만, 운영 장애는 **패키징된 jar 안에 서로 다른 메이저 버전이 섞여서** 났다.
 * BOM 이 관리하지 않는 모듈이 전이로 딸려오면 그렇게 된다.
 *
 * 그래서 이 테스트를 "런타임 호환성 증거"로 쓰지 않는다. 빈 그래프 조립까지가 범위다.
 * 패키징 층은 `bootJar` 산출물의 버전 분포를 따로 봐야 한다.
 *
 * ## 이 테스트가 운영 동작을 유발하지 않게 하는 장치
 *
 * | 장치 | 이유 |
 * |---|---|
 * | `webEnvironment = MOCK` | 포트를 열지 않는다. `NONE` 대신 쓰는 이유는 삭제 대상이 `@RestController` 라 웹 계층 배선까지 봐야 하기 때문이다 |
 * | 기본 프로필 (`wip` 켜지 않음) | **운영과 같은 빈 조합**을 본다. wip 을 켜면 실제와 다른 그래프가 된다 |
 * | Testcontainers 전용 DB | 운영·개발 DB 를 건드리지 않는다 |
 * | `@Scheduled` 등록 차단 | `ScheduleExecutor`(fixedRate 60s), `WebhookRetryScheduler`(fixedDelay 60s)는 **기동 즉시 발화**한다. 막지 않으면 이 테스트가 실제 배치를 돌린다 |
 * | 더미 시크릿 | 실제 자격증명을 쓰지 않는다 |
 *
 * 검증하는 것은 **컨텍스트 refresh 성공** 하나다. 동작 검증은 각 기능의 테스트가 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
class ApplicationContextSmokeIT {

    @Autowired lateinit var context: ApplicationContext

    /**
     * `@Scheduled` 메서드가 스케줄러에 등록되지 않게 한다.
     *
     * 빈 자체는 그대로 만들어진다 — 우리가 보려는 것이 빈 그래프이기 때문이다.
     * 등록만 막아서 컨텍스트가 뜨는 순간 배치가 도는 것을 방지한다.
     */
    @TestConfiguration
    class NoScheduledTasks {
        @Bean
        @Primary
        fun noOpScheduledProcessor(): ScheduledAnnotationBeanPostProcessor =
            object : ScheduledAnnotationBeanPostProcessor() {
                override fun postProcessAfterInitialization(bean: Any, beanName: String): Any = bean
            }
    }

    companion object {
        @Container @JvmStatic
        val pg = PostgreSQLContainer("postgres:16").apply {
            withDatabaseName("ongo_smoke")
            withUsername("test"); withPassword("test")
        }

        @JvmStatic @DynamicPropertySource
        fun props(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url") { pg.jdbcUrl }
            r.add("spring.datasource.username") { pg.username }
            r.add("spring.datasource.password") { pg.password }

            // application.yml 이 요구하는 값들. 전부 더미다.
            r.add("JWT_SECRET") { "smoke-test-jwt-secret-not-a-real-credential-0123456789" }
            r.add("DB_PASSWORD") { "test" }
            // AES-256 이라 Base64 디코딩 결과가 정확히 32바이트여야 한다.
            r.add("PLATFORM_TOKEN_ENCRYPTION_KEY") { "MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA=" }
            r.add("MINIO_ACCESS_KEY") { "smoke" }
            r.add("MINIO_SECRET_KEY") { "smoke-secret" }
        }
    }

    @Test
    @DisplayName("기본 프로필에서 컨텍스트가 뜬다")
    fun contextLoads() {
        assertNotNull(context) { "컨텍스트가 만들어지지 않았다" }

        // 빈이 몇 개 없으면 컨텍스트가 반쪽만 뜬 것이다. 통과의 의미를 지킨다.
        assertTrue(context.beanDefinitionCount > 100) {
            "빈이 ${context.beanDefinitionCount}개뿐이다. 컨텍스트가 온전히 뜨지 않았다"
        }
    }

    @Test
    @DisplayName("wip 프로필이 켜져 있지 않다 — 운영과 같은 조합을 봐야 한다")
    fun wipProfileIsNotActive() {
        // wip 을 켜면 미완성 기능의 빈까지 조립돼 실제 운영과 다른 그래프를 검증하게 된다.
        // 그러면 "운영에서 뜬다"는 근거가 되지 못한다.
        assertTrue("wip" !in context.environment.activeProfiles) {
            "wip 프로필이 활성화됐다: ${context.environment.activeProfiles.joinToString()}"
        }
    }
}

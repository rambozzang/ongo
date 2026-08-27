package com.ongo.api.admin

import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 파일럿 등록은 운영자 전용이다.
 *
 * 이 경로가 일반 사용자에게 열리면 코호트를 아무나 늘릴 수 있고, 그 순간 "유료 파일럿
 * 참여자 수"가 사업 판단 근거로서 무의미해진다.
 *
 * 여기서는 두 가지 방어선이 **선언되어 있는지**를 고정한다:
 *   1) 클래스에 `hasRole('ADMIN')`
 *   2) admin 하위 경로 — `SecurityConfig` 의 URL 매처가 같은 역할을 다시 요구한다
 * 어느 한쪽을 나중에 지워도 이 테스트가 먼저 깨진다.
 *
 * 실제 HTTP 403 은 Spring 컨텍스트가 필요하고 그 스모크 테스트는 Docker 를 요구한다.
 * 이 테스트는 그 앞단에서 선언 누락을 잡는 용도이며, 런타임 검증을 대체한다고 주장하지 않는다.
 */
class ShortsPilotEnrollmentRouteTest {

    private val controller = ShortsPilotEnrollmentController::class

    @Test
    fun `등록 컨트롤러는 ADMIN 역할을 요구한다`() {
        val preAuthorize = controller.findAnnotation<PreAuthorize>()

        assertNotNull(preAuthorize, "ShortsPilotEnrollmentController must declare @PreAuthorize")
        assertEquals("hasRole('ADMIN')", preAuthorize.value)
    }

    @Test
    fun `등록 경로는 SecurityConfig 가 막는 admin 하위에 있다`() {
        val mapping = controller.findAnnotation<RequestMapping>()

        assertNotNull(mapping, "ShortsPilotEnrollmentController must declare @RequestMapping")
        assertTrue(
            mapping.value.single().startsWith("/api/v1/admin/"),
            "등록 경로가 admin 하위가 아니면 URL 매처 방어선이 사라진다: ${mapping.value.toList()}",
        )
    }

    @Test
    fun `승인된 경로 외에는 열지 않는다`() {
        /*
         * 목록은 이름으로 고정한다. 개수만 세면 라우트를 하나 지우고 하나 더한 변경이
         * 그대로 통과해, 권한 검토를 거치지 않은 경로가 조용히 들어온다.
         *
         * 후보 조회(candidates)는 등록 화면이 runId 를 스스로 찾게 하려고 이번에 더했다.
         * 리포트·투입시간·매출은 측정 컨트롤러 몫이며 여기서는 열지 않는다.
         */
        val routes = controller.declaredFunctions
            .filter { fn -> fn.annotations.any { it.annotationClass.simpleName?.endsWith("Mapping") == true } }
            .map { it.name }
            .toSet()

        assertEquals(setOf("enroll", "candidates"), routes, "예상하지 못한 라우트가 있다: $routes")
    }

    /**
     * 후보 조회는 **읽기**다. GET 이 아니면 CSRF·캐시·재시도 취급이 달라지고, 운영자가
     * 새로고침할 때마다 쓰기로 오해될 여지가 생긴다.
     */
    @Test
    fun `후보 조회는 GET 이다`() {
        val candidates = controller.declaredFunctions.single { it.name == "candidates" }

        assertTrue(
            candidates.annotations.any { it.annotationClass.simpleName == "GetMapping" },
            "후보 조회가 GET 이 아니다: ${candidates.annotations.map { it.annotationClass.simpleName }}",
        )
    }
}

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
    fun `등록 외의 경로를 추가로 열지 않는다`() {
        // 리포트·운영자 시간 입력·결제 연결은 다음 작업이다. 이번에 곁다리로 열리면
        // 권한 검토 없이 표면이 넓어진다.
        val routes = controller.declaredFunctions
            .filter { fn -> fn.annotations.any { it.annotationClass.simpleName?.endsWith("Mapping") == true } }

        assertEquals(1, routes.size, "예상하지 못한 라우트가 있다: ${routes.map { it.name }}")
        assertEquals("enroll", routes.single().name)
    }
}

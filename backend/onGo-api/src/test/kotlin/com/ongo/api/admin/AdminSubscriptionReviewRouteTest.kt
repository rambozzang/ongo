package com.ongo.api.admin

import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.valueParameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 갱신 확인 대상 화면은 운영자 전용이다.
 *
 * 이 경로가 일반 사용자에게 열리면 남의 결제 상태를 조회할 수 있고, 재조회는 구독 기간과
 * 크레딧을 실제로 움직인다.
 *
 * 여기서는 두 방어선이 **선언되어 있는지**를 고정한다:
 *   1) 클래스에 `hasRole('ADMIN')`
 *   2) `/api/v1/admin` 하위 — `SecurityConfig` 의 admin 하위 URL 매처가 다시 요구한다
 *
 * 실제 HTTP 403 검증은 Spring 컨텍스트가 필요하고 그 스모크 테스트는 Docker 를 요구한다.
 * 이 테스트는 선언 누락을 앞단에서 잡는 용도이며 런타임 검증을 대체하지 않는다.
 */
class AdminSubscriptionReviewRouteTest {

    private val controller = AdminSubscriptionReviewController::class

    @Test
    fun `확인 대상 컨트롤러는 ADMIN 역할을 요구한다`() {
        val preAuthorize = controller.findAnnotation<PreAuthorize>()

        assertNotNull(preAuthorize, "AdminSubscriptionReviewController must declare @PreAuthorize")
        assertEquals("hasRole('ADMIN')", preAuthorize.value)
    }

    @Test
    fun `확인 대상 경로는 SecurityConfig 가 막는 admin 하위에 있다`() {
        val mapping = controller.findAnnotation<RequestMapping>()

        assertNotNull(mapping, "AdminSubscriptionReviewController must declare @RequestMapping")
        assertTrue(
            mapping.value.single().startsWith("/api/v1/admin"),
            "경로가 admin 하위가 아니면 URL 매처 방어선이 사라진다: ${mapping.value.toList()}",
        )
    }

    @Test
    fun `승인된 경로 외에는 열지 않는다`() {
        // 개수만 세면 라우트를 하나 지우고 하나 더한 변경이 통과한다. 이름으로 고정한다.
        val routes = controller.declaredFunctions
            .filter { fn -> fn.annotations.any { it.annotationClass.simpleName?.endsWith("Mapping") == true } }
            .map { it.name }
            .toSet()

        assertEquals(setOf("list", "recheck"), routes)
    }

    /**
     * **재조회는 결과를 인자로 받지 않는다.**
     *
     * 요청 본문으로 outcome 을 받을 수 있게 되는 순간 "확인했다고 치고 성공 처리" 가
     * 가능해지고, 그러면 이 원장으로 이중 청구를 막았다고 말할 수 없다. 결과는 PG 재조회가
     * 정한다. 본문 파라미터가 하나라도 생기면 여기서 먼저 깨진다.
     */
    @Test
    fun `재조회는 요청 본문을 받지 않는다`() {
        val recheck = controller.declaredFunctions.single { it.name == "recheck" }

        val bodyParams = recheck.valueParameters.filter { it.findAnnotation<RequestBody>() != null }

        assertTrue(bodyParams.isEmpty(), "재조회가 본문을 받으면 임의 확정 경로가 열린다: $bodyParams")
    }
}

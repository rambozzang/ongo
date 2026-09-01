package com.ongo.api.admin

import com.ongo.application.admin.AdminDeadLetterWebhookItem
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.valueParameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * DEAD_LETTER 웹훅 화면은 운영자 전용이다.
 *
 * 이 경로가 일반 사용자에게 열리면 남의 결제 이벤트 이력을 볼 수 있고, 재큐잉은 결제·환불
 * 반영을 실제로 다시 돌린다.
 *
 * 두 방어선이 **선언되어 있는지**를 고정한다:
 *   1) 클래스에 `hasRole('ADMIN')`
 *   2) `/api/v1/admin` 하위 — `SecurityConfig` 의 admin 하위 URL 매처가 다시 요구한다
 *
 * 실제 HTTP 403 검증은 Spring 컨텍스트가 필요하고 그 스모크 테스트는 Docker 를 요구한다.
 * 이 테스트는 선언 누락을 앞단에서 잡는 용도이며 런타임 검증을 대체하지 않는다.
 */
class AdminWebhookDeadLetterRouteTest {

    private val controller = AdminWebhookDeadLetterController::class

    @Test
    fun `웹훅 복구 컨트롤러는 ADMIN 역할을 요구한다`() {
        val preAuthorize = controller.findAnnotation<PreAuthorize>()

        assertNotNull(preAuthorize, "AdminWebhookDeadLetterController must declare @PreAuthorize")
        assertEquals("hasRole('ADMIN')", preAuthorize.value)
    }

    @Test
    fun `웹훅 복구 경로는 SecurityConfig 가 막는 admin 하위에 있다`() {
        val mapping = controller.findAnnotation<RequestMapping>()

        assertNotNull(mapping, "AdminWebhookDeadLetterController must declare @RequestMapping")
        assertTrue(
            mapping.value.single().startsWith("/api/v1/admin"),
            "경로가 admin 하위가 아니면 URL 매처 방어선이 사라진다: ${mapping.value.toList()}",
        )
    }

    @Test
    fun `승인된 경로 외에는 열지 않는다`() {
        val routes = controller.declaredFunctions
            .filter { fn -> fn.annotations.any { it.annotationClass.simpleName?.endsWith("Mapping") == true } }
            .map { it.name }
            .toSet()

        assertEquals(setOf("list", "requeue"), routes)
    }

    /**
     * **재큐잉은 상태를 인자로 받지 않는다.**
     *
     * 본문으로 상태를 받을 수 있게 되는 순간 임의 상태 전이가 가능해진다. PROCESSED 로
     * 바꿔버리면 처리되지 않은 결제가 완료로 남고, 아무도 그것을 다시 보지 않는다.
     * 허용되는 전이는 서버가 정한 `DEAD_LETTER → FAILED` 하나뿐이다.
     */
    @Test
    fun `재큐잉은 요청 본문을 받지 않는다`() {
        val requeue = controller.declaredFunctions.single { it.name == "requeue" }

        val bodyParams = requeue.valueParameters.filter { it.findAnnotation<RequestBody>() != null }

        assertTrue(bodyParams.isEmpty(), "재큐잉이 본문을 받으면 임의 상태 전이 경로가 열린다: $bodyParams")
    }

    /**
     * 응답 DTO 에 본문을 담는 필드가 생기면 결제 식별자·고객 정보가 관리자 화면과
     * 스크린샷을 통해 퍼진다. 되돌릴 수 없는 유출이라 필드 이름 수준에서 막는다.
     */
    @Test
    fun `응답 DTO 에 원문 본문이나 서명 필드가 없다`() {
        val forbidden = listOf("payload", "signature", "rawBody", "secret", "body")

        val fields = AdminDeadLetterWebhookItem::class.memberProperties.map { it.name }

        forbidden.forEach { banned ->
            assertTrue(
                fields.none { it.equals(banned, ignoreCase = true) },
                "응답에 '$banned' 필드가 생겼다 — 원문 유출 경로다: $fields",
            )
        }
        // 전체 멱등 키도 내보내지 않는다. 마스킹된 것만 담는다.
        assertTrue("eventId" !in fields, "멱등 키 전체가 응답에 있다: $fields")
        assertTrue("maskedEventId" in fields, "대조용 마스킹 키가 없으면 운영자가 이벤트를 특정하지 못한다")
    }
}

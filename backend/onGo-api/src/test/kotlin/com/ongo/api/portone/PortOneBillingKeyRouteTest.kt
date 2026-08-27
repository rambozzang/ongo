package com.ongo.api.portone

import com.ongo.api.config.CurrentUser
import org.springframework.web.bind.annotation.PostMapping
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 정기결제 수단 등록 경로.
 *
 * 빌링키는 이 값 하나로 고객에게 반복 청구가 가능하다. 경로·쿼리에 실리면 접근 로그와
 * 브라우저 기록에 평문으로 남고, 대상 구독을 요청에서 지정할 수 있으면 남의 결제 수단을
 * 덮어쓸 수 있다. 둘 다 선언 단계에서 고정한다.
 *
 * 실제 인증 강제는 Spring 컨텍스트가 필요하고 그 스모크 테스트는 Docker 를 요구한다.
 * 이 테스트는 그 앞단에서 선언 누락을 잡는 용도이며 런타임 검증을 대체하지 않는다.
 */
class PortOneBillingKeyRouteTest {

    private val controller = PortOneController::class

    private val route = controller.declaredFunctions.single { it.name == "registerBillingKey" }

    /** GET 이면 빌링키가 쿼리에 실린다. 본문으로만 받아야 한다. */
    @Test
    fun `등록은 POST 다`() {
        assertTrue(
            route.annotations.any { it.annotationClass.simpleName == "PostMapping" },
            "등록이 POST 가 아니다: ${route.annotations.map { it.annotationClass.simpleName }}",
        )
    }

    /** 경로에 빌링키가 들어가면 접근 로그에 평문으로 남는다. */
    @Test
    fun `경로에 빌링키를 넣지 않는다`() {
        val path = route.findAnnotation<PostMapping>()?.value?.single()

        assertEquals("/billing-key", path)
        assertTrue(path?.contains("{") != true, "경로에 변수가 있다: $path")
    }

    /**
     * `@CurrentUser` 가 없으면 대상 사용자를 요청에서 받게 되고, 그 순간 남의 구독을
     * 지정할 수 있다.
     */
    @Test
    fun `대상 사용자를 인증 토큰에서만 받는다`() {
        val currentUserParam = route.parameters.singleOrNull { param ->
            param.annotations.any { it.annotationClass == CurrentUser::class }
        }

        assertTrue(currentUserParam != null, "@CurrentUser 파라미터가 없다")
        assertEquals("userId", currentUserParam.name)
    }

    /** 본문에 userId·subscriptionId 가 있으면 소유권 검사가 무의미해진다. */
    @Test
    fun `요청 본문은 빌링키 하나만 받는다`() {
        val properties = RegisterBillingKeyRequest::class.declaredMemberProperties.map { it.name }

        assertEquals(listOf("billingKey"), properties)
    }
}

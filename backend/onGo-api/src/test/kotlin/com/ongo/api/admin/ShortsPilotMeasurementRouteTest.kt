package com.ongo.api.admin

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 측정 보고와 투입 시간 입력은 운영자 전용이다.
 *
 * 이 경로가 일반 사용자에게 열리면 다른 고객의 실행 ID·리드타임이 노출되고, 투입 시간을
 * 아무나 넣어 원가 판단 근거가 망가진다.
 *
 * 실제 HTTP 403 은 Spring 컨텍스트가 필요하고 그 스모크 테스트는 Docker 를 요구한다.
 * 이 테스트는 선언 누락을 그 앞단에서 잡는 용도이며, 런타임 검증을 대체한다고 주장하지 않는다.
 */
class ShortsPilotMeasurementRouteTest {

    private val controller = ShortsPilotMeasurementController::class

    @Test
    fun `측정 컨트롤러는 ADMIN 역할을 요구한다`() {
        val preAuthorize = controller.findAnnotation<PreAuthorize>()

        assertNotNull(preAuthorize, "ShortsPilotMeasurementController must declare @PreAuthorize")
        assertEquals("hasRole('ADMIN')", preAuthorize.value)
    }

    @Test
    fun `측정 경로는 SecurityConfig 가 막는 admin 하위에 있다`() {
        val mapping = controller.findAnnotation<RequestMapping>()

        assertNotNull(mapping, "ShortsPilotMeasurementController must declare @RequestMapping")
        assertTrue(
            mapping.value.single().startsWith("/api/v1/admin/"),
            "경로가 admin 하위가 아니면 URL 매처 방어선이 사라진다: ${mapping.value.toList()}",
        )
    }

    @Test
    fun `보고 조회와 운영자 입력 세 경로만 연다`() {
        // 결제 연결·고객 조회는 이 표면에 없다. 곁다리로 열리면 권한 검토 없이 넓어진다.
        val routes = controller.declaredFunctions
            .filter { fn -> fn.annotations.any { it.annotationClass.simpleName?.endsWith("Mapping") == true } }

        assertEquals(
            setOf("report", "logOperatorTime", "logRevenue", "logExternalCost"),
            routes.map { it.name }.toSet(),
            "예상하지 못한 라우트가 있다: ${routes.map { it.name }}",
        )
    }

    /*
     * 0 원과 자릿수 오타는 유스케이스도 막지만, HTTP 경계에서 먼저 걸러야 잘못된 값이
     * append-only 원장까지 내려가지 않는다.
     */
    @Test
    fun `금액 요청 본문에 1원에서 1억원 범위 제약이 걸려 있다`() {
        val amount = ShortsPilotMeasurementController.AmountRequest::class
            .declaredMemberProperties
            .single { it.name == "amountKrw" }
            .javaField

        assertNotNull(amount, "amountKrw 필드를 찾을 수 없다")
        assertEquals(1L, amount.getAnnotation(Min::class.java)?.value, "@Min(1) 이 없다")
        assertEquals(100_000_000L, amount.getAnnotation(Max::class.java)?.value, "@Max(1억) 이 없다")
    }

    /* 금액 하나뿐이다. 결제수단·고객·영상 자료가 이 경로로 들어오면 안 된다. */
    @Test
    fun `금액 요청 본문은 금액 하나만 받는다`() {
        val properties = ShortsPilotMeasurementController.AmountRequest::class
            .declaredMemberProperties
            .map { it.name }

        assertEquals(listOf("amountKrw"), properties)
    }

    /*
     * 0 분과 하루 초과는 유스케이스도 막지만, HTTP 경계에서 먼저 걸러야 잘못된 값이
     * 트랜잭션까지 내려가지 않는다.
     */
    @Test
    fun `투입 시간 요청 본문에 1에서 1440 범위 제약이 걸려 있다`() {
        val minutes = ShortsPilotMeasurementController.OperatorTimeRequest::class
            .declaredMemberProperties
            .single { it.name == "minutes" }
            .javaField

        assertNotNull(minutes, "minutes 필드를 찾을 수 없다")
        assertEquals(1L, minutes.getAnnotation(Min::class.java)?.value, "@Min(1) 이 없다")
        assertEquals(1440L, minutes.getAnnotation(Max::class.java)?.value, "@Max(1440) 이 없다")
    }

    /* 요청 본문은 분 하나뿐이다. 고객·영상·결제 자료가 이 경로로 들어오면 안 된다. */
    @Test
    fun `투입 시간 요청 본문은 분 하나만 받는다`() {
        val properties = ShortsPilotMeasurementController.OperatorTimeRequest::class
            .declaredMemberProperties
            .map { it.name }

        assertEquals(listOf("minutes"), properties)
    }
}

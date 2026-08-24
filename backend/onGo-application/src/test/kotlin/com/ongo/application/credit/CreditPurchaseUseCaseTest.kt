package com.ongo.application.credit

import com.ongo.common.enums.CreditPackage
import com.ongo.common.exception.BusinessException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 레거시 크레딧 구매 경로가 **거절되고 아무것도 남기지 않는지** 고정한다.
 *
 * 예전 구현은 PG 를 부르지 않고 PENDING 결제 행을 만든 뒤 `creditsAdded = 500` 과
 * `expiresAt` 을 돌려줬다. 크레딧은 지급된 적이 없다. 응답만 보면 충전이 끝난 것처럼
 * 보이므로, 이 테스트가 지키는 것은 "성공을 주장하지 않는다"다.
 */
class CreditPurchaseUseCaseTest {

    private val useCase = CreditPurchaseUseCase()

    /* 성공 응답 자체가 없어야 한다. 200 이 나가는 순간 화면은 충전됐다고 말한다. */
    @Test
    fun `레거시 구매 경로는 성공을 주장하지 않고 거절한다`() {
        val ex = assertFailsWith<BusinessException> { useCase.rejectLegacyPurchase() }

        assertEquals("CREDIT_PURCHASE_PATH_UNSUPPORTED", ex.code)
    }

    /*
     * 거절 문구는 사용자가 다음에 무엇을 할지 알려줘야 한다. "지원하지 않습니다"만으로는
     * 결제하려던 사람이 갈 곳이 없다.
     */
    @Test
    fun `거절 안내는 정상 결제 화면으로 유도한다`() {
        val ex = assertFailsWith<BusinessException> { useCase.rejectLegacyPurchase() }

        assertContains(ex.message!!, "결제 버튼")
        // 지급을 시사하는 표현이 남아 있으면 안 된다.
        assertFalse(ex.message!!.contains("완료"), "거절 문구가 충전 완료를 시사한다: ${ex.message}")
    }

    /**
     * **구조적 보장.** 이 유스케이스는 결제·크레딧 리포지터리를 하나도 주입받지 않는다.
     * 위 [rejectLegacyPurchase] 의 예외를 누가 지워도 여기서 결제 행이 생기거나 크레딧이
     * 지급될 수 없다. 생성자가 비어 있다는 사실이 그 보장이므로 그것을 고정한다.
     *
     * 리포지터리가 다시 들어오면 이 테스트가 먼저 깨진다.
     */
    @Test
    fun `유스케이스는 쓰기 수단을 갖지 않는다`() {
        val params = CreditPurchaseUseCase::class.java.declaredConstructors.single().parameterTypes

        assertTrue(params.isEmpty(), "쓰기 가능한 협력자가 주입됐다: ${params.map { it.simpleName }}")
    }

    /* 패키지 조회는 구매와 무관하게 계속 동작해야 한다 — 결제 화면이 목록을 그린다. */
    @Test
    fun `패키지 목록은 그대로 제공한다`() {
        val packages = useCase.getPackages()

        assertEquals(CreditPackage.entries.size, packages.size)
        val starter = packages.first { it.name == CreditPackage.STARTER.name }
        assertEquals(CreditPackage.STARTER.credits, starter.credits)
        assertEquals(CreditPackage.STARTER.price, starter.price)
        assertEquals(
            CreditPackage.STARTER.price.toDouble() / CreditPackage.STARTER.credits,
            starter.pricePerCredit,
        )
    }
}

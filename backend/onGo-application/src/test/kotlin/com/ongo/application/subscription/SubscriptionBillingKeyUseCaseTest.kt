package com.ongo.application.subscription

import com.ongo.application.portone.PortOneBillingKey
import com.ongo.application.portone.PortOnePaymentGateway
import com.ongo.common.enums.BillingCycle
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 정기결제 수단 등록.
 *
 * 브라우저가 PortOne 에서 받은 빌링키를 서버가 저장한다. 여기서 고정하는 것은 셋이다.
 *
 *  1. **받은 값을 믿지 않는다** — PortOne 에 되물어 실제로 발급됐고 쓸 수 있는지 확인
 *  2. **평문을 남기지 않는다** — 암호화해 저장하고 응답·예외에 값이 없다
 *  3. **자기 구독만 고친다** — userId 로만 조회하므로 남의 구독을 지정할 경로가 없다
 *
 * 실제 PortOne 호출은 하지 않는다. gateway 계약만 mock 으로 검증한다.
 */
class SubscriptionBillingKeyUseCaseTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val gateway = mockk<PortOnePaymentGateway>()
    private val tokenEncryptionPort = mockk<TokenEncryptionPort>()

    private val useCase = SubscriptionBillingKeyUseCase(
        subscriptionRepository = subscriptionRepository,
        gateway = gateway,
        tokenEncryptionPort = tokenEncryptionPort,
    )

    private val userId = 7L
    private val rawKey = "billing-key-from-portone"

    private fun subscription(billingKeyEncrypted: String? = null) = Subscription(
        id = 5L,
        userId = userId,
        planType = PlanType.PRO,
        status = SubscriptionStatus.ACTIVE,
        price = 19_900,
        billingCycle = BillingCycle.MONTHLY,
        billingKeyEncrypted = billingKeyEncrypted,
    )

    private fun issued() {
        every { gateway.findBillingKey(rawKey) } returns PortOneBillingKey(status = "ISSUED")
        every { tokenEncryptionPort.encrypt(PlainToken(rawKey)) } returns EncryptedToken("enc:$rawKey")
    }

    /* ---- 정상 ---- */

    @Test
    fun `발급된 빌링키를 암호화해 현재 사용자의 구독에 저장한다`() {
        every { subscriptionRepository.findByUserId(userId) } returns subscription()
        issued()
        val saved = slot<Subscription>()
        every { subscriptionRepository.update(capture(saved)) } answers { saved.captured }

        useCase.register(userId, rawKey)

        assertEquals("enc:$rawKey", saved.captured.billingKeyEncrypted)
        assertEquals(5L, saved.captured.id)
        // 평문이 저장되면 안 된다.
        assertTrue(saved.captured.billingKeyEncrypted != rawKey)
    }

    /** 앞뒤 공백은 브라우저·복사에서 흔히 붙는다. 그대로 저장하면 조회가 어긋난다. */
    @Test
    fun `앞뒤 공백을 제거한 뒤 검증하고 저장한다`() {
        every { subscriptionRepository.findByUserId(userId) } returns subscription()
        issued()
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        useCase.register(userId, "  $rawKey  ")

        verify(exactly = 1) { gateway.findBillingKey(rawKey) }
        verify(exactly = 1) { tokenEncryptionPort.encrypt(PlainToken(rawKey)) }
    }

    /** 카드를 바꾼 사용자가 새 키를 보낸다. 옛 키를 남기면 해지된 카드로 청구한다. */
    @Test
    fun `이미 등록된 키가 있어도 새 키로 교체한다`() {
        every { subscriptionRepository.findByUserId(userId) } returns subscription(billingKeyEncrypted = "enc:old")
        issued()
        val saved = slot<Subscription>()
        every { subscriptionRepository.update(capture(saved)) } answers { saved.captured }

        useCase.register(userId, rawKey)

        assertEquals("enc:$rawKey", saved.captured.billingKeyEncrypted)
    }

    /* ---- 검증 실패 ---- */

    /**
     * 인증된 사용자라도 임의의 문자열을 보낼 수 있다. 검증 없이 저장하면 정기 청구 때가
     * 되어서야 실패하는데, 그때는 이미 고객이 첫 달 구독료를 낸 뒤다.
     */
    @Test
    fun `PortOne 이 모르는 키는 저장하지 않는다`() {
        every { subscriptionRepository.findByUserId(userId) } returns subscription()
        every { gateway.findBillingKey(rawKey) } returns null

        val e = assertFailsWith<BusinessException> { useCase.register(userId, rawKey) }

        assertEquals("BILLING_KEY_NOT_FOUND", e.code)
        verify(exactly = 0) { subscriptionRepository.update(any()) }
        verify(exactly = 0) { tokenEncryptionPort.encrypt(any()) }
    }

    @Test
    fun `ISSUED 가 아닌 상태의 키는 저장하지 않는다`() {
        every { subscriptionRepository.findByUserId(userId) } returns subscription()
        every { gateway.findBillingKey(rawKey) } returns PortOneBillingKey(status = "DELETED")

        val e = assertFailsWith<BusinessException> { useCase.register(userId, rawKey) }

        assertEquals("BILLING_KEY_NOT_USABLE", e.code)
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    /** 조회 자체가 실패하면 상태를 모른다. 모르는 채로 저장하면 안 된다. */
    @Test
    fun `PortOne 조회가 실패하면 저장하지 않고 예외를 올린다`() {
        every { subscriptionRepository.findByUserId(userId) } returns subscription()
        every { gateway.findBillingKey(rawKey) } throws IllegalStateException("portone down")

        assertFailsWith<IllegalStateException> { useCase.register(userId, rawKey) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    @Test
    fun `빈 값은 조회조차 하지 않고 거절한다`() {
        for (blank in listOf("", "   ")) {
            val e = assertFailsWith<BusinessException> { useCase.register(userId, blank) }
            assertEquals("BILLING_KEY_INVALID", e.code)
        }
        verify(exactly = 0) { gateway.findBillingKey(any()) }
        verify(exactly = 0) { subscriptionRepository.update(any()) }
    }

    @Test
    fun `구독이 없으면 등록할 수 없다`() {
        every { subscriptionRepository.findByUserId(userId) } returns null

        assertFailsWith<NotFoundException> { useCase.register(userId, rawKey) }
        verify(exactly = 0) { gateway.findBillingKey(any()) }
    }

    /* ---- 평문 노출 금지 ---- */

    /**
     * 예외 메시지가 사용자 화면과 로그로 흘러간다. 여기에 빌링키가 들어가면 그 값 하나로
     * 반복 청구가 가능해진다.
     */
    @Test
    fun `예외 메시지에 빌링키가 들어가지 않는다`() {
        every { subscriptionRepository.findByUserId(userId) } returns subscription()

        every { gateway.findBillingKey(rawKey) } returns null
        val notFound = assertFailsWith<BusinessException> { useCase.register(userId, rawKey) }

        every { gateway.findBillingKey(rawKey) } returns PortOneBillingKey(status = "DELETED")
        val notUsable = assertFailsWith<BusinessException> { useCase.register(userId, rawKey) }

        for (message in listOfNotNull(notFound.message, notUsable.message)) {
            assertTrue(rawKey !in message, "예외 메시지에 빌링키가 새어 나왔다: $message")
        }
    }

    /**
     * 조회 결과 객체가 로그·응답으로 흘러도 결제 수단이 새지 않아야 한다. 호출자는 이미
     * 자기가 조회한 키를 알고 있으므로 되돌려 받을 이유가 없다.
     */
    @Test
    fun `빌링키 조회 응답에 키 값 필드가 없다`() {
        val fields = PortOneBillingKey::class.members.map { it.name }.toSet()

        for (forbidden in listOf("billingKey", "key", "value")) {
            assertTrue(forbidden !in fields, "조회 응답에 키 값이 실렸다: $forbidden")
        }
    }

    /* ---- 소유권 ---- */

    /**
     * 대상 구독을 userId 로만 찾는다. 요청에서 subscriptionId 를 받지 않으므로 남의 구독을
     * 지정할 경로 자체가 없다 — 이 호출 형태가 곧 방어선이다.
     */
    @Test
    fun `대상 구독을 인증된 사용자로만 조회한다`() {
        every { subscriptionRepository.findByUserId(userId) } returns subscription()
        issued()
        every { subscriptionRepository.update(any()) } answers { firstArg() }

        useCase.register(userId, rawKey)

        verify(exactly = 1) { subscriptionRepository.findByUserId(userId) }
    }

    /**
     * 호출 형태 자체가 방어선이다. subscriptionId 를 받는 순간 남의 구독을 지정할 수 있게
     * 되므로, 파라미터가 userId 와 빌링키 둘뿐임을 고정한다.
     */
    @Test
    fun `등록은 사용자와 빌링키만 받는다`() {
        val register = SubscriptionBillingKeyUseCase::class.members.single { it.name == "register" }
        // 첫 파라미터는 수신 객체(this)다.
        val params = register.parameters.drop(1).map { it.name }

        assertEquals(listOf("userId", "billingKey"), params)
    }
}

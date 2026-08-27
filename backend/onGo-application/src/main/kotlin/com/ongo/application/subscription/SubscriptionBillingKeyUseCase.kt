package com.ongo.application.subscription

import com.ongo.application.portone.PortOnePaymentGateway
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.subscription.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 브라우저가 발급받은 정기결제 수단을 저장한다.
 *
 * ## 왜 브라우저가 발급하는가
 *
 * 카드 정보는 우리 서버를 지나지 않는다. PortOne 발급 창이 카드사와 직접 주고받고,
 * 우리는 그 결과인 빌링키 문자열만 받는다. 서버가 카드번호를 만지지 않는 것이
 * 이 구조의 요점이다.
 *
 * ## 왜 받은 값을 검증하는가
 *
 * 인증된 사용자라도 임의의 문자열을 보낼 수 있다. 검증 없이 저장하면 **정기 청구 때가
 * 되어서야** 실패하는데, 그때는 이미 고객이 첫 달 구독료를 낸 뒤다. 그래서 저장 전에
 * PortOne 에 되물어 실제로 발급된 키이고 쓸 수 있는 상태인지 확인한다.
 *
 * ## 무엇을 남기지 않는가
 *
 * 빌링키 평문은 **어디에도** 남기지 않는다 — 응답, 예외 메시지, 로그 전부. 이 값 하나로
 * 고객에게 반복 청구가 가능해서, 유출 시 피해가 액세스 토큰보다 크다.
 */
@Service
class SubscriptionBillingKeyUseCase(
    private val subscriptionRepository: SubscriptionRepository,
    private val gateway: PortOnePaymentGateway,
    private val tokenEncryptionPort: TokenEncryptionPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 현재 사용자의 구독에 정기결제 수단을 등록한다.
     *
     * 이미 등록된 키가 있으면 **교체한다.** 카드를 바꾼 사용자가 새로 발급받은 키를
     * 보내는 것이 정상 흐름이고, 옛 키를 남겨 두면 해지된 카드로 청구하게 된다.
     *
     * @param userId 인증된 사용자. 호출자가 경로·본문에서 받지 않고 토큰에서 꺼내야 한다.
     * @param billingKey 브라우저가 PortOne 에서 받은 값. 여기서만 평문으로 존재한다.
     */
    @Transactional
    fun register(userId: Long, billingKey: String) {
        val trimmed = billingKey.trim()
        if (trimmed.isEmpty()) {
            throw BusinessException("BILLING_KEY_INVALID", "정기결제 수단 정보가 올바르지 않습니다.")
        }

        /*
         * **사용자의 구독을 먼저 찾는다.** userId 로 조회하므로 남의 구독을 건드릴 경로가
         * 없다 — 요청 본문에 subscriptionId 를 받지 않는 이유다.
         */
        val subscription = subscriptionRepository.findByUserId(userId)
            ?: throw NotFoundException("구독", userId)

        /*
         * PortOne 에 되묻는다. 조회 실패는 "없음"과 다르므로 삼키지 않고 그대로 올린다 —
         * 모르는 상태에서 저장하면 청구 시점에야 깨진다.
         */
        val verified = gateway.findBillingKey(trimmed)
            ?: throw BusinessException(
                "BILLING_KEY_NOT_FOUND",
                "정기결제 수단을 확인할 수 없습니다. 다시 등록해 주세요.",
            )

        if (!verified.status.equals(ISSUED_STATUS, ignoreCase = true)) {
            // 상태는 우리 로그에만 남긴다. 빌링키 값은 넣지 않는다.
            log.warn("사용할 수 없는 빌링키 상태. userId={} status={}", userId, verified.status)
            throw BusinessException(
                "BILLING_KEY_NOT_USABLE",
                "정기결제 수단을 사용할 수 없는 상태입니다. 다시 등록해 주세요.",
            )
        }

        val encrypted = tokenEncryptionPort.encrypt(PlainToken(trimmed))
        subscriptionRepository.update(
            subscription.copy(
                billingKeyEncrypted = encrypted.value,
                updatedAt = LocalDateTime.now(),
            ),
        )
        // 등록 사실만 남긴다. 값도, 마스킹한 값도 남기지 않는다.
        log.info("정기결제 수단을 등록했다. userId={}", userId)
    }

    private companion object {
        /** PortOne 이 사용 가능한 빌링키로 보는 상태. */
        const val ISSUED_STATUS = "ISSUED"
    }
}

package com.ongo.application.subscription

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 지금 이 배포에서 **구독이 자동으로 결제되는가**. 결제 화면의 동의 문구가 이 값을 따른다.
 *
 * 결제 모달은 오래 "매월 자동으로 결제됩니다" 라고 약속했지만, 정기 청구는 기본값이 꺼짐이다
 * ([BillingScheduler] 참고). 꺼진 동안 기간이 끝나면 Free 로 내려가므로, 화면이 자동 결제를
 * 약속하면 카드를 등록한 사용자가 아무 예고 없이 강등당한 것으로 느낀다. 반대로 켜져 있는데
 * "자동 결제 안 됨" 이라고 말하면 동의 없는 청구가 된다. 그래서 문구를 서버 설정 하나에 묶는다.
 *
 * 스케줄러와 **같은 설정 키**를 [RENEWAL_ENABLED] 로 공유한다. 둘이 다른 키를 보면 화면과 청구가 갈라진다.
 */
@Component
class SubscriptionBillingPolicy(
    @param:Value(RENEWAL_ENABLED)
    val autoRenewalEnabled: Boolean,
) {
    fun current(): BillingPolicyResponse =
        BillingPolicyResponse(autoRenewal = autoRenewalEnabled, expiryNoticeDays = EXPIRY_NOTICE_DAYS)

    companion object {
        const val RENEWAL_ENABLED = "\${subscription.renewal.enabled:false}"

        /** 자동 결제가 꺼져 있을 때 기간 종료를 미리 알리는 날 수. */
        const val EXPIRY_NOTICE_DAYS = 3
    }
}

data class BillingPolicyResponse(
    /** true 면 기간이 끝날 때 등록한 수단으로 자동 청구한다. false 면 기간이 끝나면 Free 로 내려간다. */
    val autoRenewal: Boolean,
    val expiryNoticeDays: Int,
)

package com.ongo.common.exception

import com.ongo.common.enums.PlanType

/**
 * 이미 이용 중인 유료 구독이 있는데 같거나 더 낮은 등급의 구독 결제를 새로 만들려 할 때.
 *
 * 상위 등급 전환(업그레이드)은 이 예외의 대상이 아니다. 판정 기준은 `SubscriptionUseCase`와
 * 동일한 가격 비교이며, 상위 등급 결제는 그대로 통과한다.
 */
class DuplicateSubscriptionPaymentException(
    val currentPlan: PlanType,
    val requestedPlan: PlanType,
) : BusinessException(
    "SUBSCRIPTION_ALREADY_ACTIVE",
    "이미 ${currentPlan.displayName} 구독을 이용 중입니다. " +
        "${requestedPlan.displayName} 구독 결제를 새로 만들 수 없습니다.",
)

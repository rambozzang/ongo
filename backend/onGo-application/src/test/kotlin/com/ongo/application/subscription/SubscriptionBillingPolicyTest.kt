package com.ongo.application.subscription

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value

/**
 * 결제 화면이 말하는 것(자동 결제 여부)과 스케줄러가 하는 것(청구 또는 만료)이 **같은 설정**을
 * 보는지 고정한다. 한쪽 키만 바뀌면 "자동 결제" 를 약속하고 Free 로 내리거나, "자동 결제 없음" 이라
 * 말하고 청구하게 된다.
 */
class SubscriptionBillingPolicyTest {

    private fun valueOf(type: Class<*>, parameterType: Class<*>): String =
        type.declaredConstructors.single { it.parameterCount > 0 }
            .let { ctor ->
                ctor.parameters.withIndex()
                    .single { (i, p) -> p.type == parameterType && ctor.parameterAnnotations[i].any { it is Value } }
                    .let { (i, _) -> ctor.parameterAnnotations[i].filterIsInstance<Value>().single().value }
            }

    @Test
    @DisplayName("결제 정책과 정기 청구 스케줄러가 같은 설정 키를 본다")
    fun schedulerAndPolicyShareRenewalKey() {
        assertEquals(SubscriptionBillingPolicy.RENEWAL_ENABLED, valueOf(BillingScheduler::class.java, Boolean::class.javaPrimitiveType!!))
        assertEquals(SubscriptionBillingPolicy.RENEWAL_ENABLED, valueOf(SubscriptionBillingPolicy::class.java, Boolean::class.javaPrimitiveType!!))
        assertEquals("\${subscription.renewal.enabled:false}", SubscriptionBillingPolicy.RENEWAL_ENABLED, "기본값은 꺼짐이어야 한다")
    }

    @Test
    @DisplayName("설정값을 그대로 화면에 알린다")
    fun reportsConfiguredValue() {
        assertEquals(BillingPolicyResponse(autoRenewal = false, expiryNoticeDays = 3), SubscriptionBillingPolicy(false).current())
        assertEquals(true, SubscriptionBillingPolicy(true).current().autoRenewal)
    }
}

import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

describe('SubscriptionView billing-cycle pricing contract', () => {
  const source = readFileSync(resolve(process.cwd(), 'src/views/SubscriptionView.vue'), 'utf-8')

  it('유료 결제 주기를 바꾸면 결제 모달로 보내고 연간 금액을 사용한다', () => {
    expect(source).toContain('const targetCheckoutPrice = computed')
    expect(source).toContain("targetPlanInfo.value.yearlyPrice : targetPlanInfo.value.price")
    expect(source).toContain('period: targetPeriod')
    expect(source).toContain("watch(() => subscription.value?.billingCycle")
    expect(source).toContain('pendingPlanInfo')
    expect(source).toContain("subscription.value.billingCycle === 'YEARLY'")
    expect(source).toContain('연간 구독의 업그레이드는 연간 결제 주기로 진행해 주세요.')
    expect(source).toContain('changesPaidBillingCycle')
    expect(source).toContain('targetIdx >= currentIdx')
    expect(source).toContain('isPlanUpgrade || changesPaidBillingCycle')
  })

  it('연간 구독의 월간 전환은 결제 모달이 아닌 종료 후 예약 흐름으로 보낸다', () => {
    const start = source.indexOf('const changesPaidBillingCycle = Boolean(')
    const end = source.indexOf('\n  )', start)
    const block = start >= 0 && end >= 0 ? source.slice(start, end) : ''

    expect(block).not.toBe('')
    expect(block).toContain("subscription.value.billingCycle === 'MONTHLY'")
    expect(block).toContain("billingCycle.value === 'YEARLY'")
  })
})

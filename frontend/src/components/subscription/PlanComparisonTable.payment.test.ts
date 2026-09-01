import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PlanComparisonTable from './PlanComparisonTable.vue'
import type { Plan } from '@/types/subscription'

const plans: Plan[] = [
  {
    type: 'FREE', name: 'Free', price: 0, yearlyPrice: 0, maxPlatforms: 1,
    maxUploadsPerMonth: 5, maxScheduleDays: 0, analyticsPeriodDays: 7, storageMb: 1024,
    commentManagement: false, teamMembers: 0, freeAiCredits: 30, support: '커뮤니티',
  },
  {
    type: 'PRO', name: 'Pro', price: 19900, yearlyPrice: 199000, maxPlatforms: 4,
    maxUploadsPerMonth: 100, maxScheduleDays: 30, analyticsPeriodDays: 365, storageMb: 51200,
    commentManagement: true, teamMembers: 2, freeAiCredits: 300, support: '우선 이메일',
  },
  {
    type: 'BUSINESS', name: 'Business', price: 49900, yearlyPrice: 499000, maxPlatforms: 4,
    maxUploadsPerMonth: -1, maxScheduleDays: 90, analyticsPeriodDays: -1, storageMb: 204800,
    commentManagement: true, teamMembers: 10, freeAiCredits: 1000, support: '전담 매니저',
  },
]

describe('PlanComparisonTable 결제 상태 게이팅', () => {
  it('결제가 불가하면 유료 업그레이드 버튼을 비활성화한다', () => {
    const wrapper = mount(PlanComparisonTable, {
      props: {
        plans,
        currentPlan: 'FREE',
        paymentEnabled: false,
        paymentDisabledReason: '결제 설정을 확인할 수 없습니다.',
      },
    })

    const paidButton = wrapper.findAll('button').find((button) => button.text() === '업그레이드')
    expect(paidButton?.attributes('disabled')).toBeDefined()
    expect(paidButton?.attributes('title')).toBe('결제 설정을 확인할 수 없습니다.')
  })

  it('결제가 불가해도 다운그레이드 버튼은 사용할 수 있다', () => {
    const wrapper = mount(PlanComparisonTable, {
      props: {
        plans,
        currentPlan: 'BUSINESS',
        paymentEnabled: false,
      },
    })

    const downgradeButtons = wrapper.findAll('button').filter((button) => button.text() === '다운그레이드')
    expect(downgradeButtons).toHaveLength(2)
    expect(downgradeButtons.every((button) => button.attributes('disabled') === undefined)).toBe(true)
  })

  it('결제 capability를 전달받지 못하면 유료 업그레이드를 열지 않는다', () => {
    const wrapper = mount(PlanComparisonTable, {
      props: {
        plans,
        currentPlan: 'FREE',
      },
    })

    const paidButton = wrapper.findAll('button').find((button) => button.text() === '업그레이드')
    expect(paidButton?.attributes('disabled')).toBeDefined()
  })

  it('서버가 준 분석 보관 기간만 표시하고 플랜별 가짜 차등값은 표시하지 않는다', () => {
    const wrapper = mount(PlanComparisonTable, {
      props: { plans, currentPlan: 'FREE' },
    })

    expect(wrapper.text()).toContain('분석 데이터 보관')
    expect(wrapper.text()).toContain('365일')
    expect(wrapper.text()).toContain('무제한')
    expect(wrapper.text()).not.toContain('동시 업로드')
    expect(wrapper.text()).not.toContain('우선 지원')
    expect(wrapper.text()).not.toContain('API 접근')
  })
})

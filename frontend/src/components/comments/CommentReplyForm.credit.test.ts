import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import CommentReplyForm from '@/components/comments/CommentReplyForm.vue'
import { aiApi } from '@/api/ai'
import { useCreditStore } from '@/stores/credit'
import CreditPurchaseModal from '@/components/subscription/CreditPurchaseModal.vue'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/ai', () => ({
  aiApi: { generateReply: vi.fn() },
}))

vi.mock('@/api/credit', () => ({
  creditApi: {
    getBalance: vi.fn(),
    getTransactions: vi.fn(),
    getPackages: vi.fn().mockResolvedValue([]),
    list: vi.fn(),
    purchase: vi.fn(),
  },
}))

function creditError(code: string) {
  return { response: { data: { success: false, message: '크레딧이 부족합니다', error: code } } }
}

function render() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }],
  })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  const wrapper = mount(CommentReplyForm, {
    props: { commentContent: '어떤 댓글이세요?' },
    global: {
      plugins: [pinia, router, i18n],
    },
  })
  return { wrapper, router }
}

describe('CommentReplyForm 크레딧 차단 CTA (COMMENT_REPLY)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('CREDIT_INSUFFICIENT면 인라인 안내 + 충전 CTA + 모달 노출', async () => {
    vi.mocked(aiApi.generateReply).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    const { wrapper } = render()
    const vm = wrapper.vm as unknown as { creditBlocked: boolean }

    await wrapper.find('[data-testid="reply-ai-button"]').trigger('click')
    await flushPromises()

    expect(vm.creditBlocked).toBe(true)
    const cta = wrapper.find('[data-testid="reply-credit-cta"]')
    expect(cta.exists()).toBe(true)
    expect(cta.text()).toContain('크레딧 충전하기')
    // 아직 모달은 닫혀 있다.
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(false)

    await cta.trigger('click')
    await flushPromises()
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(true)
  })

  it('일반 오류(비 Error 객체)면 CTA 없이 기존 실패 안내를 보인다', async () => {
    vi.mocked(aiApi.generateReply).mockRejectedValue({ notAnError: true })

    const { wrapper } = render()
    const vm = wrapper.vm as unknown as { creditBlocked: boolean }

    await wrapper.find('[data-testid="reply-ai-button"]').trigger('click')
    await flushPromises()

    expect(vm.creditBlocked).toBe(false)
    expect(wrapper.find('[data-testid="reply-credit-cta"]').exists()).toBe(false)
    const err = wrapper.find('[data-testid="reply-error"]')
    expect(err.exists()).toBe(true)
    expect(err.text()).toContain('AI 답글 생성에 실패했습니다')
  })

  it('정상이면 추천을 노출하고 CTA/에러는 없다', async () => {
    vi.mocked(aiApi.generateReply).mockResolvedValue({
      replies: [{ tone: 'friendly', reply: '감사합니다!' }],
    } as never)

    const { wrapper } = render()
    const vm = wrapper.vm as unknown as { creditBlocked: boolean }

    await wrapper.find('[data-testid="reply-ai-button"]').trigger('click')
    await flushPromises()

    expect(vm.creditBlocked).toBe(false)
    expect(wrapper.find('[data-testid="reply-credit-cta"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="reply-error"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('감사합니다!')
  })

  it('추천 선택 시 replyText에 반영된다 (정상 동작 보존)', async () => {
    vi.mocked(aiApi.generateReply).mockResolvedValue({
      replies: [{ tone: 'friendly', reply: '고마워요' }],
    } as never)

    const { wrapper } = render()
    const vm = wrapper.vm as unknown as { replyText: string }

    await wrapper.find('[data-testid="reply-ai-button"]').trigger('click')
    await flushPromises()
    await wrapper.find('.cursor-pointer').trigger('click')
    await flushPromises()

    expect(vm.replyText).toBe('고마워요')
  })

  it('purchase 뒤 fetchBalance 호출 + 차단 해제, 자동 재실행 없음', async () => {
    vi.mocked(aiApi.generateReply).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    const { wrapper } = render()
    const vm = wrapper.vm as unknown as { creditBlocked: boolean }

    await wrapper.find('[data-testid="reply-ai-button"]').trigger('click')
    await flushPromises()
    expect(vm.creditBlocked).toBe(true)

    const creditStore = useCreditStore()
    const fetchBalanceSpy = vi
      .spyOn(creditStore, 'fetchBalance')
      .mockResolvedValue(undefined as never)
    const callsBefore = vi.mocked(aiApi.generateReply).mock.calls.length

    await wrapper
      .findComponent(CreditPurchaseModal)
      .vm.$emit('purchase', { key: 'STARTER', name: 'Starter', pricePerCredit: 100, validDays: 30 })
    await flushPromises()

    expect(fetchBalanceSpy).toHaveBeenCalled()
    expect(vm.creditBlocked).toBe(false)
    // 자동 재실행 없음 — purchase 전후 generateReply 호출 횟수 동일.
    expect(vi.mocked(aiApi.generateReply).mock.calls.length).toBe(callsBefore)
  })

  it('PLAN_LIMIT_EXCEEDED는 크레딧 CTA를 띄우지 않고 기존 안내를 유지한다', async () => {
    vi.mocked(aiApi.generateReply).mockRejectedValue(creditError('PLAN_LIMIT_EXCEEDED'))

    const { wrapper } = render()
    const vm = wrapper.vm as unknown as { creditBlocked: boolean }

    await wrapper.find('[data-testid="reply-ai-button"]').trigger('click')
    await flushPromises()

    expect(vm.creditBlocked).toBe(false)
    expect(wrapper.find('[data-testid="reply-credit-cta"]').exists()).toBe(false)
  })
})

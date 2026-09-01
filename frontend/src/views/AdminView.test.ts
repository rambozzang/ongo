import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import AdminView from './AdminView.vue'
import { adminApi } from '@/api/admin'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/admin', () => ({
  adminApi: {
    getUsers: vi.fn(),
    getPublishQueue: vi.fn(),
    getAccountDeletionJobs: vi.fn(),
    getDeadLetterWebhooks: vi.fn(),
    requeueDeadLetterWebhook: vi.fn(),
  },
}))

const deadLetter = (overrides: Record<string, unknown> = {}) => ({
  id: 7,
  provider: 'PADDLE',
  eventType: 'transaction.completed',
  maskedEventId: 'evt_01HQ…S9T0',
  retryCount: 5,
  maxRetries: 5,
  nextRetryAt: null,
  errorMessage: '크레딧 패키지를 식별할 수 없습니다',
  createdAt: '2026-08-28T01:00:00Z',
  processedAt: null,
  ...overrides,
})

async function mountAdminView() {
  setActivePinia(createPinia())
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div/>' } }] })
  await router.push('/')
  await router.isReady()

  const wrapper = mount(AdminView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return wrapper
}

describe('AdminView dead-letter webhook recovery', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(adminApi.getUsers).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, page: 0, size: 20,
    } as never)
    vi.mocked(adminApi.getPublishQueue).mockResolvedValue({ totalPending: 0, items: [] } as never)
    vi.mocked(adminApi.getAccountDeletionJobs).mockResolvedValue([] as never)
    vi.mocked(adminApi.getDeadLetterWebhooks).mockResolvedValue([deadLetter()] as never)
  })

  it('loads dead-letter webhooks on mount and renders the operator row', async () => {
    const wrapper = await mountAdminView()

    expect(adminApi.getDeadLetterWebhooks).toHaveBeenCalled()
    const text = wrapper.text()
    expect(text).toContain('PADDLE')
    expect(text).toContain('transaction.completed')
    expect(text).toContain('5 / 5')
  })

  /**
   * 마스킹은 서버가 한다. 화면이 원문 멱등 키나 본문을 어떤 경로로도 보여주면 안 된다 —
   * 스크린샷 한 장으로 결제 식별자가 퍼진다.
   */
  it('shows only the masked key and never a raw payload', async () => {
    const wrapper = await mountAdminView()

    const html = wrapper.html()
    expect(html).toContain('evt_01HQ…S9T0')
    expect(html).not.toContain('payload')
    expect(html).not.toContain('signature')
  })

  it('renders an empty state instead of a fabricated count when nothing needs recovery', async () => {
    vi.mocked(adminApi.getDeadLetterWebhooks).mockResolvedValue([] as never)

    const wrapper = await mountAdminView()

    expect(wrapper.text()).toContain(koMessages.admin.deadLetterEmpty)
    expect(adminApi.requeueDeadLetterWebhook).not.toHaveBeenCalled()
  })

  /**
   * 재큐잉은 결제 반영을 다시 돌린다. 확인 모달을 거치지 않고 바로 서버를 부르면
   * 오조작 한 번이 그대로 반영된다.
   */
  it('requires an explicit confirmation before requeueing', async () => {
    const wrapper = await mountAdminView()

    const requeueButton = wrapper.findAll('button').find((b) => b.text() === koMessages.admin.requeueWebhook)
    expect(requeueButton).toBeDefined()

    await requeueButton!.trigger('click')
    await flushPromises()

    // 모달만 열렸을 뿐 아직 아무것도 보내지 않았다.
    expect(adminApi.requeueDeadLetterWebhook).not.toHaveBeenCalled()
    // ConfirmModal 은 body 로 teleport 되므로 문서에서 찾는다.
    expect(document.body.textContent).toContain('evt_01HQ…S9T0')
  })

  it('requeues by surrogate id and reloads the list from the server', async () => {
    vi.mocked(adminApi.requeueDeadLetterWebhook).mockResolvedValue({
      id: 7, status: 'FAILED', nextRetryAt: '2026-08-28T04:00:00Z',
    } as never)
    const wrapper = await mountAdminView()

    const requeueButton = wrapper.findAll('button').find((b) => b.text() === koMessages.admin.requeueWebhook)
    await requeueButton!.trigger('click')
    await flushPromises()

    // ConfirmModal 은 body 로 teleport 되므로 wrapper 안에서는 찾을 수 없다.
    const confirmButton = Array.from(document.body.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === koMessages.action.confirm)
    expect(confirmButton, '확인 버튼이 없으면 재큐잉을 실행할 수 없다').toBeDefined()
    confirmButton!.click()
    await flushPromises()

    // 멱등 키가 아니라 대리 키로 지정한다.
    expect(adminApi.requeueDeadLetterWebhook).toHaveBeenCalledWith(7)
    // 응답으로 행 상태를 지어내지 않고 서버에서 다시 읽는다.
    expect(adminApi.getDeadLetterWebhooks).toHaveBeenCalledTimes(2)
  })

  /**
   * 우리가 재처리할 수 없는 이벤트는 서버가 거부한다. 버튼을 주면 운영자가 눌러보고
   * 실패를 받는데, 그 실패는 시스템 오류처럼 보인다.
   */
  it('offers no requeue action for events no reprocessor owns', async () => {
    vi.mocked(adminApi.getDeadLetterWebhooks).mockResolvedValue([
      deadLetter({ provider: 'UNKNOWN', eventType: 'Something.Unknown' }),
    ] as never)

    const wrapper = await mountAdminView()

    const requeueButton = wrapper.findAll('button').find((b) => b.text() === koMessages.admin.requeueWebhook)
    expect(requeueButton).toBeUndefined()
  })
})

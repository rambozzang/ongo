import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import RecycleModal from './RecycleModal.vue'
import { videoApi } from '@/api/video'
import { channelApi } from '@/api/channel'

/**
 * 재게시 접수 결과를 **일어난 그대로** 말하는지 고정한다.
 *
 * ## 무엇이 깨져 있었나
 *
 * 서버 응답은 **접수 확인**이지 게시 완료가 아니다. `PublishVideoUseCase` 는 모든 플랫폼을
 * `UPLOADING` 으로 돌려주고 실제 전송은 `VideoPublishEvent` 로 비동기 처리된다. 그런데
 * 화면은 그 응답 직후 "콘텐츠가 성공적으로 재게시되었습니다" 를 **과거형으로** 단언하고
 * 모달을 닫았다.
 *
 * 결과는 두 가지로 나빴다. 예약 재게시는 아직 시작조차 안 했는데 완료라고 했고, 나중에
 * 플랫폼이 거절해도 사용자는 알 방법이 없었다 — 며칠 뒤 게시물이 없다는 것을 스스로
 * 발견해야 했다.
 */

const success = vi.fn()

vi.mock('@/api/video', () => ({ videoApi: { recycle: vi.fn() } }))
vi.mock('@/api/channel', () => ({ channelApi: { list: vi.fn() } }))
vi.mock('@/composables/useNotification', () => ({
  useNotification: () => ({ success }),
}))
vi.mock('@/stores/notification', () => ({
  useNotificationStore: () => ({ error: vi.fn() }),
}))

const VIDEO = {
  id: 10,
  title: '원본 영상',
  description: '설명',
  tags: ['tag'],
  category: 'VLOG',
  uploads: [],
} as never

function accepted(uploads: Array<Record<string, unknown>>) {
  return { videoId: 11, uploads }
}

async function renderModal() {
  setActivePinia(createPinia())
  vi.mocked(channelApi.list).mockResolvedValue({
    channels: [
      { id: 101, platform: 'YOUTUBE', channelName: '내 채널', isActive: true },
      { id: 202, platform: 'TIKTOK', channelName: '틱톡', isActive: true },
    ],
    maxAllowed: 4,
    currentCount: 2,
  } as never)

  const wrapper = mount(RecycleModal, {
    props: { modelValue: true, video: VIDEO },
    global: { stubs: { teleport: true, PlatformBadge: true } },
  })
  await flushPromises()
  return wrapper
}

/** 폼을 유효하게 채우고 제출한다. 제목·플랫폼이 있어야 버튼이 열린다. */
async function submit(
  wrapper: Awaited<ReturnType<typeof renderModal>>,
  options: { scheduledAt?: string } = {},
) {
  const vm = wrapper.vm as unknown as {
    formData: { title: string; platforms: string[]; scheduledAt: string }
  }
  vm.formData.title = '재게시 제목'
  vm.formData.platforms = ['YOUTUBE#101']
  vm.formData.scheduledAt = options.scheduledAt ?? ''
  await wrapper.vm.$nextTick()

  const button = wrapper.findAll('button').find((b) => b.text().includes('재게시'))
  if (!button) throw new Error('"재게시" 버튼을 찾지 못했습니다')
  await button.trigger('click')
  await flushPromises()
}

describe('RecycleModal 접수 결과 처리', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  /* ── 접수 성공 ─────────────────────────────────────────────────────── */

  /**
   * **핵심 회귀.** 서버가 `UPLOADING` 을 돌려준 시점에 "완료"라고 말하면 안 된다.
   * 아직 아무 플랫폼에도 올라가지 않았다.
   */
  it('즉시 재게시는 완료가 아니라 시작을 알린다', async () => {
    vi.mocked(videoApi.recycle).mockResolvedValue(
      accepted([{ platform: 'YOUTUBE', status: 'UPLOADING' }]) as never,
    )
    const wrapper = await renderModal()

    await submit(wrapper)

    expect(success).toHaveBeenCalledTimes(1)
    const message = success.mock.calls[0][0] as string
    expect(message).toContain('시작했습니다')
    expect(message).not.toContain('재게시되었습니다')
    expect(wrapper.emitted('confirm')).toBeTruthy()
    const closeEvents = wrapper.emitted('update:modelValue')
    expect(closeEvents?.[closeEvents.length - 1]).toEqual([false])
  })

  /** 예약이면 아직 게시가 시작되지도 않았다. 시각과 함께 예약 사실만 말한다. */
  it('예약 재게시는 예약 사실과 시각을 알린다', async () => {
    vi.mocked(videoApi.recycle).mockResolvedValue(
      accepted([{ platform: 'YOUTUBE', status: 'UPLOADING' }]) as never,
    )
    const wrapper = await renderModal()

    await submit(wrapper, { scheduledAt: '2026-12-24T10:30' })

    const message = success.mock.calls[0][0] as string
    expect(message).toContain('예약했습니다')
    expect(message).not.toContain('시작했습니다')
    // 언제인지 말해야 사용자가 확인할 수 있다.
    expect(message).toContain('2026')
  })

  /* ── 일부 거절 ─────────────────────────────────────────────────────── */

  /**
   * **핵심 회귀.** 거절된 플랫폼이 있는데 닫아 버리면 사용자는 무엇이 빠졌는지 모른 채
   * 끝나고, 고칠 기회도 사라진다.
   */
  it('거절된 플랫폼이 있으면 모달을 닫지 않고 사유를 보여준다', async () => {
    vi.mocked(videoApi.recycle).mockResolvedValue(
      accepted([
        { platform: 'YOUTUBE', status: 'UPLOADING' },
        { platform: 'TIKTOK', status: 'FAILED', errorMessage: '채널 연결이 만료되었습니다' },
      ]) as never,
    )
    const wrapper = await renderModal()

    await submit(wrapper)

    expect(success).not.toHaveBeenCalled()
    expect(wrapper.emitted('confirm')).toBeFalsy()
    expect(wrapper.emitted('update:modelValue')).toBeFalsy()

    const rejected = wrapper.find('[data-testid="recycle-rejected"]')
    expect(rejected.exists()).toBe(true)
    expect(rejected.text()).toContain('TikTok')
    expect(rejected.text()).toContain('채널 연결이 만료되었습니다')
  })

  /** REJECTED 도 같은 취급이다 — 서버 enum 그대로 본다. */
  it('REJECTED 상태도 거절로 취급한다', async () => {
    vi.mocked(videoApi.recycle).mockResolvedValue(
      accepted([{ platform: 'TIKTOK', status: 'REJECTED' }]) as never,
    )
    const wrapper = await renderModal()

    await submit(wrapper)

    expect(success).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="recycle-rejected"]').exists()).toBe(true)
  })

  /* ── 요청 자체 실패 ────────────────────────────────────────────────── */

  /**
   * 예전에는 `handleSubmit` 이 예외를 잡지 않아 unhandled rejection 이 되고, 모달은
   * 아무 설명 없이 열린 채 남았다.
   */
  it('요청이 실패하면 성공 토스트도 닫기도 없이 사유를 보여준다', async () => {
    vi.mocked(videoApi.recycle).mockRejectedValue(new Error('서버가 응답하지 않습니다'))
    const wrapper = await renderModal()

    await submit(wrapper)

    expect(success).not.toHaveBeenCalled()
    expect(wrapper.emitted('confirm')).toBeFalsy()
    expect(wrapper.emitted('update:modelValue')).toBeFalsy()
    expect(wrapper.find('[data-testid="recycle-error"]').text()).toContain(
      '서버가 응답하지 않습니다',
    )
  })

  /** 재시도 시 이전 실패 표시가 남아 있으면 방금 결과로 오해한다. */
  it('다시 시도하면 이전 실패 표시를 지운다', async () => {
    vi.mocked(videoApi.recycle).mockRejectedValue(new Error('일시적 오류'))
    const wrapper = await renderModal()
    await submit(wrapper)
    expect(wrapper.find('[data-testid="recycle-error"]').exists()).toBe(true)

    vi.mocked(videoApi.recycle).mockResolvedValue(
      accepted([{ platform: 'YOUTUBE', status: 'UPLOADING' }]) as never,
    )
    await submit(wrapper)

    expect(wrapper.find('[data-testid="recycle-error"]').exists()).toBe(false)
    expect(success).toHaveBeenCalledTimes(1)
  })
})

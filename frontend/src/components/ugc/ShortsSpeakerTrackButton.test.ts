import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import koMessages from '@/locales/ko/common.json'
import ShortsSpeakerTrackButton from './ShortsSpeakerTrackButton.vue'
import { ugcShortsTrackingApi } from '@/api/ugcShortsTracking'

vi.mock('@/api/ugcShortsTracking', () => ({
  ugcShortsTrackingApi: { cost: vi.fn(), track: vi.fn() },
}))

function render(props: Record<string, unknown> = {}) {
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  return mount(ShortsSpeakerTrackButton, {
    props: { workspaceId: 2, runId: 5, clipId: 9, ...props },
    global: { plugins: [i18n] },
  })
}

describe('ShortsSpeakerTrackButton', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(ugcShortsTrackingApi.cost).mockResolvedValue({ credits: 4 })
  })

  it('shows the server credit cost and emits the applied result', async () => {
    const result = { edit: { editor: {}, needsRerender: true, uncoveredSubtitleSpan: false }, foundTiles: 10, totalTiles: 12, creditsCharged: 4 }
    vi.mocked(ugcShortsTrackingApi.track).mockResolvedValue(result as never)
    const wrapper = render()
    await flushPromises()
    expect(wrapper.get('[data-testid="speaker-track"]').text()).toContain('4 크레딧')

    await wrapper.get('[data-testid="speaker-track"]').trigger('click')
    await flushPromises()

    expect(ugcShortsTrackingApi.track).toHaveBeenCalledWith(2, 5, 9)
    expect(wrapper.emitted('applied')?.[0]).toEqual([result])
    expect(wrapper.get('[data-testid="speaker-track-result"]').text()).toContain('12개 중 10개')
  })

  it('shows the server message on failure and does not emit', async () => {
    vi.mocked(ugcShortsTrackingApi.track).mockRejectedValue(new Error('장면에서 인물을 충분히 찾지 못해 화자 추적을 적용하지 않았습니다. 크레딧은 돌려드렸습니다.'))
    const wrapper = render()
    await flushPromises()

    await wrapper.get('[data-testid="speaker-track"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('applied')).toBeUndefined()
    expect(wrapper.get('[data-testid="speaker-track-error"]').text()).toContain('크레딧은 돌려드렸습니다')
  })

  it('hides the credit number when the cost cannot be loaded and respects disabled', async () => {
    vi.mocked(ugcShortsTrackingApi.cost).mockRejectedValue(new Error('x'))
    const wrapper = render({ disabled: true })
    await flushPromises()

    expect(wrapper.get('[data-testid="speaker-track"]').text()).not.toContain('크레딧')
    expect(wrapper.get('[data-testid="speaker-track"]').attributes('disabled')).toBeDefined()
  })
})

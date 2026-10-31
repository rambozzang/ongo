import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import koMessages from '@/locales/ko/common.json'
import NaverClipManualUpload from './NaverClipManualUpload.vue'

function render(props: Record<string, unknown> = {}) {
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  return mount(NaverClipManualUpload, {
    props: { title: '김치찌개 레시피', description: '10분이면 됩니다', tags: ['#요리', '요리', '집 밥', ''], fileUrl: 'https://cdn.test/v.mp4', ...props },
    global: { plugins: [i18n] },
  })
}

describe('NaverClipManualUpload', () => {
  const writeText = vi.fn()

  beforeEach(() => {
    writeText.mockReset().mockResolvedValue(undefined)
    Object.assign(navigator, { clipboard: { writeText } })
  })

  it('honestly says it is manual and links download and the Clip Creators upload page', () => {
    const wrapper = render()

    expect(wrapper.text()).toContain('자동으로 올릴 수 없습니다')
    expect(wrapper.get('[data-testid="naver-clip-download"]').attributes('href')).toBe('https://cdn.test/v.mp4')
    expect(wrapper.get('[data-testid="naver-clip-open"]').attributes('href')).toBe('https://clipcreators.naver.com')
  })

  it('copies the title and a description with cleaned, de-duplicated hashtags', async () => {
    const wrapper = render()

    await wrapper.get('[data-testid="naver-clip-copy-title"]').trigger('click')
    await wrapper.get('[data-testid="naver-clip-copy-body"]').trigger('click')
    await flushPromises()

    expect(writeText).toHaveBeenNthCalledWith(1, '김치찌개 레시피')
    expect(writeText).toHaveBeenNthCalledWith(2, '10분이면 됩니다\n\n#요리 #집밥')
  })

  it('shows a hint instead of a dead link when there is no file, and reports copy failure', async () => {
    writeText.mockRejectedValue(new Error('denied'))
    const wrapper = render({ fileUrl: null })

    expect(wrapper.find('[data-testid="naver-clip-download"]').exists()).toBe(false)
    await wrapper.get('[data-testid="naver-clip-copy-title"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('복사하지 못했습니다')
  })
})

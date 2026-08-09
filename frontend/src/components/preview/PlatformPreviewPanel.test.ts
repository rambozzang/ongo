import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import PlatformPreviewPanel from './PlatformPreviewPanel.vue'
import koMessages from '@/locales/ko/common.json'

describe('PlatformPreviewPanel', () => {
  it('renders separate previews for two accounts on the same platform', () => {
    const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
    const wrapper = mount(PlatformPreviewPanel, {
      props: {
        platforms: ['YOUTUBE'],
        comparisonMode: true,
        targets: [
          {
            key: 'YOUTUBE#11',
            platform: 'YOUTUBE',
            channelName: '브랜드 A',
            metadata: { title: 'A 제목', description: 'A 설명', tags: ['a'] },
          },
          {
            key: 'YOUTUBE#12',
            platform: 'YOUTUBE',
            channelName: '브랜드 B',
            metadata: { title: 'B 제목', description: 'B 설명', tags: ['b'] },
          },
        ],
      },
      global: {
        plugins: [i18n],
        stubs: {
          YouTubePreview: {
            props: ['title', 'channelName'],
            template: '<div data-testid="youtube-preview">{{ channelName }} / {{ title }}</div>',
          },
        },
      },
    })

    expect(wrapper.findAll('[data-testid="youtube-preview"]')).toHaveLength(2)
    expect(wrapper.text()).toContain('브랜드 A')
    expect(wrapper.text()).toContain('브랜드 B')
    expect(wrapper.text()).toContain('A 제목')
    expect(wrapper.text()).toContain('B 제목')
  })

  it('shows the composed caption limit for caption-based platforms', () => {
    const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
    const wrapper = mount(PlatformPreviewPanel, {
      props: {
        platforms: ['TIKTOK'],
        targets: [{
          key: 'TIKTOK#11',
          platform: 'TIKTOK',
          metadata: { title: '제목', description: '설명', tags: ['태그'] },
        }],
        platformLimits: { TIKTOK: { caption: 3 } },
      },
      global: {
        plugins: [i18n],
        stubs: {
          TikTokPreview: { template: '<div />' },
        },
      },
    })

    const badge = wrapper.find('[title*="캡션"]')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toContain('초과')
  })

  it('shows a preview warning when the server declares tags unsupported', () => {
    const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
    const wrapper = mount(PlatformPreviewPanel, {
      props: {
        platforms: ['INSTAGRAM'],
        targets: [{
          key: 'INSTAGRAM#11',
          platform: 'INSTAGRAM',
          metadata: { title: '제목', description: '설명', tags: ['태그'] },
        }],
        platformLimits: { INSTAGRAM: { tags: 0 } },
      },
      global: {
        plugins: [i18n],
        stubs: { InstagramPreview: { template: '<div />' } },
      },
    })

    const badge = wrapper.find('[title*="태그"]')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toContain('초과')
  })
})

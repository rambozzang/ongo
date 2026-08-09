import { describe, expect, it } from 'vitest'
import { composePublishCaption, parsePublishHashtags, validatePublishDrafts } from './publishValidation'

const capabilities = [
  { platform: 'YOUTUBE' as const, maxTitleLength: 5, maxDescriptionLength: 10, maxTagCount: 2 },
  { platform: 'TIKTOK' as const, maxTitleLength: 0, maxDescriptionLength: 0, maxTagCount: 0 },
]

describe('publish validation', () => {
  it('normalizes hash, whitespace, and comma separated tags', () => {
    expect(parsePublishHashtags(' #one two, #three\n')).toEqual(['one', 'two', 'three'])
  })

  it('reports title, description, and tag limits per channel', () => {
    const issues = validatePublishDrafts(
      [{ platform: 'YOUTUBE', channelName: '메인 채널' }],
      capabilities,
      { YOUTUBE: { title: 'abcdef', description: '12345678901', hashtags: '#one #two #three' } },
    )
    expect(issues.map((issue) => issue.field)).toEqual(['title', 'description', 'tags'])
    expect(issues[2]).toMatchObject({ current: 3, limit: 2, channelName: '메인 채널' })
  })

  it('treats zero description as no limit but zero tag limit as unsupported', () => {
    const issues = validatePublishDrafts([{ platform: 'TIKTOK', channelName: '틱톡' }], capabilities, {
      TIKTOK: {
        title: 'a'.repeat(3000),
        description: 'a'.repeat(10000),
        hashtags: '#one #two #three',
      },
    })

    expect(issues).toEqual([
      expect.objectContaining({ field: 'tags', current: 3, limit: 0 }),
    ])
    expect(
      validatePublishDrafts([{ platform: 'TIKTOK', channelName: '틱톡' }], capabilities, {
        TIKTOK: { title: '제목', description: '설명', hashtags: '' },
      }),
    ).toEqual([])
  })

  it('skips a target when the server has not declared its capability', () => {
    expect(
      validatePublishDrafts([{ platform: 'FACEBOOK', channelName: '페이지' }], capabilities, {
        FACEBOOK: { title: 'a'.repeat(500), description: '', hashtags: '' },
      }),
    ).toEqual([])
  })

  it('validates the composed caption for providers with one text field', () => {
    expect(
      composePublishCaption('TWITTER', {
        title: '제목',
        description: '설명',
        hashtags: '#하나',
      }),
    ).toBe('제목\n\n설명\n\n#하나')

    const issues = validatePublishDrafts(
      [{ platform: 'TIKTOK', channelName: '틱톡' }],
      [{ ...capabilities[1], maxTagCount: 10, maxCaptionLength: 10 }],
      { TIKTOK: { title: '제목', description: '설명이 아주 깁니다', hashtags: '#하나' } },
    )
    expect(issues).toEqual([expect.objectContaining({ field: 'caption', limit: 10 })])
  })
})

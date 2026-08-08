import { describe, expect, it } from 'vitest'
import { parsePublishHashtags, validatePublishDrafts } from './publishValidation'

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

  it('treats zero description and tag limits as no limit', () => {
    expect(
      validatePublishDrafts([{ platform: 'TIKTOK', channelName: '틱톡' }], capabilities, {
        TIKTOK: {
          title: 'a'.repeat(3000),
          description: 'a'.repeat(10000),
          hashtags: '#one #two #three',
        },
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
})

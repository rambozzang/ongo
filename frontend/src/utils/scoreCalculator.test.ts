import { describe, expect, it } from 'vitest'
import { calculateVideoScore } from '@/utils/scoreCalculator'
import type { Video } from '@/types/video'
import type { Platform } from '@/types/channel'

function videoWithUploads(platforms: Platform[]): Video {
  return {
    id: 1,
    userId: 1,
    title: '테스트 영상',
    description: null,
    tags: [],
    category: null,
    mediaType: 'VIDEO',
    fileUrl: 'https://cdn.example.com/video.mp4',
    thumbnailUrl: null,
    thumbnailCandidates: [],
    fileSize: 100,
    status: 'PUBLISHED',
    visibility: 'PUBLIC',
    createdAt: '2026-08-01T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
    uploads: platforms.map((platform, index) => ({
      id: index + 1,
      videoId: 1,
      platform,
      status: 'PUBLISHED',
      platformVideoId: `${platform}-${index}`,
      platformUrl: null,
      description: null,
      tags: [],
      errorMessage: null,
      publishedAt: '2026-08-01T00:00:00Z',
      createdAt: '2026-08-01T00:00:00Z',
    })),
  }
}

describe('calculateVideoScore', () => {
  it('counts distinct supported platforms instead of duplicate upload rows', () => {
    const result = calculateVideoScore(videoWithUploads(['YOUTUBE', 'YOUTUBE', 'NAVER_CLIP']))

    expect(result.coverage).toBe(25)
  })

  it('caps coverage after four distinct supported platforms', () => {
    const result = calculateVideoScore(videoWithUploads(['YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'FACEBOOK']))

    expect(result.coverage).toBe(100)
  })

  it('does not count an unconfirmed processing upload as published coverage', () => {
    const video = videoWithUploads(['YOUTUBE'])
    video.uploads[0].status = 'PROCESSING'

    expect(calculateVideoScore(video).coverage).toBe(0)
  })
})

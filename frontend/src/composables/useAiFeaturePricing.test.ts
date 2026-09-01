import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { aiApi } from '@/api/ai'
import { useAiFeaturePricing } from './useAiFeaturePricing'

vi.mock('@/api/ai', () => ({
  aiApi: { getFeatures: vi.fn() },
}))

describe('useAiFeaturePricing', () => {
  beforeEach(() => vi.clearAllMocks())

  it('stores the server-owned cost by feature key', async () => {
    vi.mocked(aiApi.getFeatures).mockResolvedValue([
      { key: 'META_GENERATION', displayName: '제목/설명 생성', creditCost: 7 },
    ] as never)

    const pricing = useAiFeaturePricing()
    await pricing.load()

    expect(pricing.costOf('META_GENERATION')).toBe(7)
    expect(pricing.hasCostsFor(['META_GENERATION'])).toBe(true)
    expect(pricing.error.value).toBeNull()
  })

  it('does not expose a cost when the server request fails', async () => {
    vi.mocked(aiApi.getFeatures).mockRejectedValue(new Error('offline'))

    const pricing = useAiFeaturePricing()
    await pricing.load()

    expect(pricing.costOf('META_GENERATION')).toBeNull()
    expect(pricing.hasCostsFor(['META_GENERATION'])).toBe(false)
    expect(pricing.error.value).toContain('AI 기능 비용')
  })

  it('rejects malformed pricing instead of opening a paid action', async () => {
    vi.mocked(aiApi.getFeatures).mockResolvedValue([
      { key: 'META_GENERATION', displayName: '제목/설명 생성', creditCost: Number.NaN },
    ] as never)

    const pricing = useAiFeaturePricing()
    await pricing.load()
    await flushPromises()

    expect(pricing.costs.value).toBeNull()
    expect(pricing.hasCostsFor(['META_GENERATION'])).toBe(false)
  })
})

import { afterEach, describe, expect, it, vi } from 'vitest'
import { useShortsCreditEstimate } from './useShortsCreditEstimate'

describe('useShortsCreditEstimate', () => {
  const created: HTMLVideoElement[] = []
  let originalCreateElement: typeof document.createElement

  function stubVideoElement(behaviour: (video: HTMLVideoElement) => void) {
    originalCreateElement = document.createElement.bind(document)
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const element = originalCreateElement(tag) as HTMLVideoElement
      if (tag !== 'video') return element
      created.push(element)
      Object.defineProperty(element, 'src', {
        configurable: true,
        set() { queueMicrotask(() => behaviour(element)) },
        get: () => '',
      })
      return element
    })
  }

  afterEach(() => {
    vi.restoreAllMocks()
    created.length = 0
  })

  it('duration metadata를 서버에 보내 서버 산출 크레딧을 그대로 표시한다', async () => {
    stubVideoElement((video) => {
      Object.defineProperty(video, 'duration', { configurable: true, value: 1800.25 })
      video.onloadedmetadata?.(new Event('loadedmetadata'))
    })
    const quote = vi.fn(async (durationMs: number) => durationMs > 1_800_000 ? 83 : 50)

    const { estimate, measure } = useShortsCreditEstimate(quote)
    await measure('https://cdn.example.com/signed/source.mp4?token=secret')

    expect(quote).toHaveBeenCalledWith(1_800_250)
    expect(estimate.value.durationSeconds).toBe(1800.25)
    expect(estimate.value.credits).toBe(83)
  })

  it('메타데이터만 요청하고 서버 견적 API를 사용한다', async () => {
    stubVideoElement((video) => {
      Object.defineProperty(video, 'duration', { configurable: true, value: 60 })
      video.onloadedmetadata?.(new Event('loadedmetadata'))
    })
    const quote = vi.fn(async () => 51)

    await useShortsCreditEstimate(quote).measure('https://cdn.example.com/source.mp4')

    expect(created[0].preload).toBe('metadata')
    expect(quote).toHaveBeenCalledWith(60_000)
  })

  it('길이 읽기 실패 시 값이나 서버 견적을 지어내지 않는다', async () => {
    stubVideoElement((video) => video.onerror?.(new Event('error')))
    const quote = vi.fn(async () => 37)

    const { estimate, measure } = useShortsCreditEstimate(quote)
    await measure('https://cdn.example.com/broken.mp4')

    expect(estimate.value.credits).toBeNull()
    expect(estimate.value.durationSeconds).toBeNull()
    expect(quote).not.toHaveBeenCalled()
  })

  it('서명 URL을 로그에 남기지 않고 측정 뒤 src 요청을 정리한다', async () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {})
    stubVideoElement((video) => {
      Object.defineProperty(video, 'duration', { configurable: true, value: 60 })
      video.onloadedmetadata?.(new Event('loadedmetadata'))
    })

    await useShortsCreditEstimate(async () => 47).measure('https://cdn.example.com/a.mp4?token=super-secret')

    expect(created[0].getAttribute('src')).toBeNull()
    for (const spy of [errorSpy, warnSpy, logSpy]) expect(spy).not.toHaveBeenCalled()
  })

  it('늦게 끝난 이전 견적 응답은 현재 영상을 덮지 않는다', async () => {
    const metadataResolvers: Array<(seconds: number) => void> = []
    stubVideoElement((video) => {
      metadataResolvers.push((seconds) => {
        Object.defineProperty(video, 'duration', { configurable: true, value: seconds })
        video.onloadedmetadata?.(new Event('loadedmetadata'))
      })
    })
    const quoteResolvers: Array<(credits: number) => void> = []
    const quote = vi.fn(() => new Promise<number>((resolve) => quoteResolvers.push(resolve)))

    const { estimate, measure } = useShortsCreditEstimate(quote)
    const first = measure('https://cdn.example.com/long.mp4')
    await Promise.resolve()
    metadataResolvers[0](3600)
    await vi.waitFor(() => expect(quoteResolvers).toHaveLength(1))

    const second = measure('https://cdn.example.com/short.mp4')
    await Promise.resolve()
    metadataResolvers[1](600)
    await vi.waitFor(() => expect(quoteResolvers).toHaveLength(2))
    quoteResolvers[1](51)
    await second
    quoteResolvers[0](120)
    await first

    expect(estimate.value.credits).toBe(51)
    expect(estimate.value.durationSeconds).toBe(600)
  })

  it('영상을 지우면 이전 예상치를 비운다', async () => {
    stubVideoElement((video) => {
      Object.defineProperty(video, 'duration', { configurable: true, value: 600 })
      video.onloadedmetadata?.(new Event('loadedmetadata'))
    })
    const { estimate, measure, reset } = useShortsCreditEstimate(async () => 64)
    await measure('https://cdn.example.com/a.mp4')
    expect(estimate.value.credits).toBe(64)
    reset()
    expect(estimate.value.credits).toBeNull()
    expect(estimate.value.durationSeconds).toBeNull()
  })
})

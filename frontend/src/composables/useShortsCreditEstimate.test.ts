import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  SHORTS_MIN_CREDITS,
  shortsCreditsForDuration,
  useShortsCreditEstimate,
} from './useShortsCreditEstimate'

/**
 * 예상 크레딧의 계약.
 *
 * 두 가지가 핵심이다.
 *
 * 1. **서버 규칙과 같은 값이 나와야 한다.** 화면이 37 이라 하고 서버가 87 을 부르면,
 *    조용히 다르게 청구하는 것과 같다.
 * 2. **길이를 모르면 지어내지 않는다.** 목록 API 에 길이가 없으므로 못 읽는 경우가
 *    정상 경로다. 그때는 규칙 안내로 내려가야지 최소값을 확정치처럼 보이면 안 된다.
 */
describe('shortsCreditsForDuration', () => {
  /** 서버 `totalCreditsForRun`: 고정 27 + 10분 구간마다 10 */
  it('서버 과금 규칙과 같은 금액을 낸다', () => {
    expect(shortsCreditsForDuration(60)).toBe(37) // 1분 → 1구간
    expect(shortsCreditsForDuration(600)).toBe(37) // 정확히 10분 → 1구간
    expect(shortsCreditsForDuration(601)).toBe(47) // 10분 1초 → 2구간
    expect(shortsCreditsForDuration(1200)).toBe(47) // 20분 → 2구간
    expect(shortsCreditsForDuration(3600)).toBe(87) // 60분 → 6구간
    expect(shortsCreditsForDuration(10800)).toBe(207) // 3시간 → 18구간
  })

  it('최소값은 10분 이하 영상의 실제 금액과 같다', () => {
    expect(SHORTS_MIN_CREDITS).toBe(37)
  })

  /** 길이를 못 믿을 때 0원이나 음수가 나오면 안 된다. */
  it('비정상 길이는 최소값으로 떨어진다', () => {
    expect(shortsCreditsForDuration(0)).toBe(SHORTS_MIN_CREDITS)
    expect(shortsCreditsForDuration(-1)).toBe(SHORTS_MIN_CREDITS)
    expect(shortsCreditsForDuration(Number.NaN)).toBe(SHORTS_MIN_CREDITS)
    expect(shortsCreditsForDuration(Number.POSITIVE_INFINITY)).toBe(SHORTS_MIN_CREDITS)
  })
})

describe('useShortsCreditEstimate', () => {
  const created: HTMLVideoElement[] = []
  let originalCreateElement: typeof document.createElement

  function stubVideoElement(behaviour: (video: HTMLVideoElement) => void) {
    originalCreateElement = document.createElement.bind(document)
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const element = originalCreateElement(tag) as HTMLVideoElement
      if (tag !== 'video') return element
      created.push(element)
      // src 가 실제로 설정되는 시점에 브라우저 동작을 흉내낸다.
      Object.defineProperty(element, 'src', {
        configurable: true,
        set() {
          queueMicrotask(() => behaviour(element))
        },
        get: () => '',
      })
      return element
    })
  }

  afterEach(() => {
    vi.restoreAllMocks()
    created.length = 0
  })

  it('메타데이터를 읽으면 예상 크레딧을 낸다', async () => {
    stubVideoElement((video) => {
      Object.defineProperty(video, 'duration', { configurable: true, value: 1800 })
      video.onloadedmetadata?.(new Event('loadedmetadata'))
    })

    const { estimate, measure } = useShortsCreditEstimate()
    await measure('https://cdn.example.com/signed/source.mp4?token=secret')

    expect(estimate.value.durationSeconds).toBe(1800)
    expect(estimate.value.credits).toBe(57) // 30분 → 3구간
  })

  /** **전체 파일을 받지 않는다.** 1GB 원본에 이 값이 빠지면 목록을 여는 것만으로 폭발한다. */
  it('메타데이터만 요청한다', async () => {
    stubVideoElement((video) => {
      Object.defineProperty(video, 'duration', { configurable: true, value: 60 })
      video.onloadedmetadata?.(new Event('loadedmetadata'))
    })

    await useShortsCreditEstimate().measure('https://cdn.example.com/source.mp4')

    expect(created[0].preload).toBe('metadata')
  })

  it('읽기에 실패하면 금액을 지어내지 않는다', async () => {
    stubVideoElement((video) => video.onerror?.(new Event('error')))

    const { estimate, measure } = useShortsCreditEstimate()
    await measure('https://cdn.example.com/broken.mp4')

    // null 이어야 화면이 규칙 안내로 내려간다. 최소값을 넣으면 확정치처럼 보인다.
    expect(estimate.value.credits).toBeNull()
    expect(estimate.value.durationSeconds).toBeNull()
  })

  /** 스트리밍 원본은 duration 이 Infinity 다. 그건 길이를 아는 것이 아니다. */
  it('무한 길이는 모르는 것으로 본다', async () => {
    stubVideoElement((video) => {
      Object.defineProperty(video, 'duration', {
        configurable: true,
        value: Number.POSITIVE_INFINITY,
      })
      video.onloadedmetadata?.(new Event('loadedmetadata'))
    })

    const { estimate, measure } = useShortsCreditEstimate()
    await measure('https://cdn.example.com/live.m3u8')

    expect(estimate.value.credits).toBeNull()
  })

  it('영상이 없으면 아무것도 요청하지 않는다', async () => {
    stubVideoElement(() => {
      throw new Error('요청하면 안 된다')
    })

    const { estimate, measure } = useShortsCreditEstimate()
    await measure(undefined)

    expect(estimate.value.credits).toBeNull()
    expect(created).toHaveLength(0)
  })

  /** fileUrl 은 서명 URL 이라 접근 권한이 실려 있다. 콘솔에도 남기면 안 된다. */
  it('fileUrl 을 로그에 남기지 않는다', async () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {})
    stubVideoElement((video) => video.onerror?.(new Event('error')))

    await useShortsCreditEstimate().measure('https://cdn.example.com/x.mp4?token=super-secret')

    for (const spy of [errorSpy, warnSpy, logSpy]) {
      expect(spy).not.toHaveBeenCalled()
    }
  })

  /** 선택을 바꿀 때마다 이전 요청이 남으면 연결이 쌓인다. */
  it('측정이 끝나면 src 를 비워 요청을 끊는다', async () => {
    stubVideoElement((video) => {
      Object.defineProperty(video, 'duration', { configurable: true, value: 60 })
      video.onloadedmetadata?.(new Event('loadedmetadata'))
    })

    await useShortsCreditEstimate().measure('https://cdn.example.com/a.mp4')

    expect(created[0].getAttribute('src')).toBeNull()
  })

  /**
   * **늦게 끝난 이전 측정이 지금 값을 덮으면 안 된다.**
   *
   * 영상을 빠르게 바꾸면 앞선 요청이 나중에 끝날 수 있다. 그때 결과를 쓰면 지금 고른
   * 영상 옆에 이전 영상의 금액이 붙는다 — 틀린 금액을 확정치로 보여주는 것이라 아예
   * 안 보여주는 것보다 나쁘다.
   */
  it('늦게 도착한 이전 측정 결과를 버린다', async () => {
    const resolvers: Array<(seconds: number) => void> = []
    stubVideoElement((video) => {
      resolvers.push((seconds) => {
        Object.defineProperty(video, 'duration', { configurable: true, value: seconds })
        video.onloadedmetadata?.(new Event('loadedmetadata'))
      })
    })

    const { estimate, measure } = useShortsCreditEstimate()
    const first = measure('https://cdn.example.com/long.mp4')
    await Promise.resolve()
    const second = measure('https://cdn.example.com/short.mp4')
    await Promise.resolve()

    // 두 번째(현재 선택)가 먼저 끝나고, 첫 번째가 뒤늦게 끝난다.
    resolvers[1](600) // 10분 → 37
    await second
    resolvers[0](3600) // 60분 → 87. 이미 버려진 요청이다.
    await first

    expect(estimate.value.credits).toBe(37)
    expect(estimate.value.durationSeconds).toBe(600)
  })

  it('선택을 지우면 이전 예상치가 남지 않는다', async () => {
    stubVideoElement((video) => {
      Object.defineProperty(video, 'duration', { configurable: true, value: 600 })
      video.onloadedmetadata?.(new Event('loadedmetadata'))
    })

    const { estimate, measure, reset } = useShortsCreditEstimate()
    await measure('https://cdn.example.com/a.mp4')
    expect(estimate.value.credits).toBe(37)

    reset()

    expect(estimate.value.credits).toBeNull()
    expect(estimate.value.durationSeconds).toBeNull()
  })
})

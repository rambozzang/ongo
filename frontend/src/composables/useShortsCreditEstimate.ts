import { ref } from 'vue'

/**
 * 쇼츠 완주 크레딧의 **예상치**.
 *
 * ## 가격은 서버가 정한다
 *
 * 크레딧은 모델 단가에서 나오는 원가 보장 규칙(`AiUnitEconomics`·`ShortsPipelineCreditRequirements`)으로 계산된다.
 * 예전에는 이 파일이 규칙의 사본(27 + 10분당 10)을 들고 있었는데, 단가가 바뀌면 화면만 옛 값을 보여 조용히
 * 다르게 청구하는 것과 같았다. 이제 화면은 길이만 재고 금액은 `GET .../credit-estimate` 가 준다.
 *
 * ## 왜 길이를 브라우저에서 재는가
 *
 * 영상 목록 API 에는 길이가 없다. 전사 크레딧은 길이에 비례하므로 `preload="metadata"` 로 **헤더만** 읽어
 * 길이를 잰다. 전체 파일을 받지 않는다. 판정은 언제나 서버가 한다 — 화면 예상치는 안내다.
 */

export interface ShortsCreditEstimate {
  /** 길이를 읽었을 때만 채워진다. 못 읽으면 null 이고 규칙 안내만 보여준다. */
  credits: number | null
  durationSeconds: number | null
}

/**
 * `fileUrl` 의 메타데이터만 읽어 예상 크레딧을 낸다.
 *
 * 실패(CORS·형식 미지원·네트워크)는 **오류가 아니다.** 길이를 모르는 것뿐이므로 조용히
 * null 로 두고, 화면은 규칙 안내만 보여준다. 여기서 오류를 띄우면 멀쩡한 생성 흐름이
 * 부가 기능 때문에 막힌 것처럼 보인다.
 */
export function useShortsCreditEstimate(quoteCredits: (durationMs: number) => Promise<number>) {
  const estimate = ref<ShortsCreditEstimate>({ credits: null, durationSeconds: null })
  const measuring = ref(false)

  /*
   * 마지막 요청만 결과를 쓴다.
   *
   * 사용자가 영상을 빠르게 바꾸면 앞선 측정이 나중에 끝날 수 있다. 순서를 확인하지 않으면
   * **지금 고른 영상 옆에 이전 영상의 금액**이 붙는다 — 틀린 금액을 확정치처럼 보여주는
   * 것이라 아예 안 보여주는 것보다 나쁘다.
   */
  let requestId = 0

  function reset() {
    requestId += 1
    estimate.value = { credits: null, durationSeconds: null }
    measuring.value = false
  }

  async function measure(fileUrl: string | null | undefined): Promise<void> {
    reset()
    if (!fileUrl) return

    const current = requestId
    measuring.value = true
    try {
      const durationSeconds = await readDurationSeconds(fileUrl)
      // 그 사이 다른 영상으로 바뀌었으면 이 결과는 버린다.
      if (current !== requestId) return
      if (durationSeconds != null) {
        const credits = await quoteCredits(Math.ceil(durationSeconds * 1000))
        if (current !== requestId) return
        estimate.value = {
          credits,
          durationSeconds,
        }
      }
    } finally {
      if (current === requestId) measuring.value = false
    }
  }

  return { estimate, measuring, measure, reset }
}

/**
 * 헤더만 읽어 재생 길이를 얻는다. **전체 파일을 내려받지 않는다.**
 *
 * `preload = 'metadata'` 는 브라우저에게 재생에 필요한 최소 바이트만 요청하게 한다.
 * 1GB 원본이라도 수십 KB 수준이다. 끝나면 `src` 를 비우고 `load()` 로 진행 중인 요청을
 * 취소해, 사용자가 영상을 여러 번 바꿔도 연결이 쌓이지 않게 한다.
 *
 * **fileUrl 은 로그에 남기지 않는다.** 서명된 스토리지 URL 이라 접근 권한이 실려 있다.
 */
function readDurationSeconds(fileUrl: string): Promise<number | null> {
  return new Promise((resolve) => {
    let settled = false
    const video = document.createElement('video')

    const finish = (value: number | null) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      video.onloadedmetadata = null
      video.onerror = null
      // 진행 중인 요청을 끊는다. 남겨두면 선택을 바꿀 때마다 연결이 늘어난다.
      video.removeAttribute('src')
      video.load()
      resolve(value)
    }

    // 응답이 오지 않는 URL 에 매달리지 않는다. 예상치는 없어도 생성은 진행돼야 한다.
    const timer = setTimeout(() => finish(null), 8000)

    video.preload = 'metadata'
    // 자격증명을 붙이지 않는다. 서명 URL 은 쿼리로 인증하며, 쿠키를 보내면 CORS 가 막힌다.
    video.crossOrigin = 'anonymous'
    video.muted = true
    video.onloadedmetadata = () => {
      const duration = video.duration
      // 스트리밍 원본은 Infinity 가 나온다. 그건 길이를 모르는 것과 같다.
      finish(Number.isFinite(duration) && duration > 0 ? duration : null)
    }
    // 실패 사유는 남기지 않는다 — 메시지에 URL 이 섞여 로그로 새는 경로가 된다.
    video.onerror = () => finish(null)
    video.src = fileUrl
  })
}

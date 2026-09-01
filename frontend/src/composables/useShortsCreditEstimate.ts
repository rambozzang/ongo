import { ref } from 'vue'

/**
 * 쇼츠 완주 크레딧의 **예상치**.
 *
 * ## 서버 과금 규칙을 그대로 옮긴다
 *
 * 백엔드 `ShortsPipelineCreditRequirements` 가 진실이다. 여기 숫자는 그 규칙을 사용자에게
 * 설명하기 위한 사본이며, **판정은 언제나 서버가 한다.** 화면이 통과시켜도 서버가 거절할
 * 수 있고 그 반대도 가능하다(다른 요청이 그 사이 크레딧을 쓰면).
 *
 * ## 왜 길이를 브라우저에서 재는가
 *
 * 영상 목록 API(`Video`)에는 **길이가 없다.** 그런데 전사 크레딧은 길이에 비례하므로,
 * 길이를 모르면 정확한 금액을 말할 수 없다. 그렇다고 최소값(37)만 보여주면 60분 영상을
 * 고른 사용자에게 87 이 나올 때 "왜 다르냐"는 문제가 된다 — 조용히 다르게 청구하는 것과
 * 같다.
 *
 * 그래서 `preload="metadata"` 로 **헤더만** 읽는다. 전체 파일을 받지 않는다.
 */

/** 단계 정액 합계: REFRAME 3 + SEGMENT 8 + SUBTITLE 5 + HOOK 5 + TEMPLATE 3 + VALIDATE 3 */
export const SHORTS_FIXED_CREDITS = 27

/** 전사 단가. 10분 구간이 **시작될 때마다** 한 번씩 붙는다. */
export const SHORTS_TRANSCRIBE_CREDITS_PER_WINDOW = 10

/** 전사 과금 구간(분). 서버의 `TRANSCRIBE_BILLING_WINDOW_MS` 와 같아야 한다. */
export const SHORTS_TRANSCRIBE_WINDOW_MINUTES = 10

/** 길이를 모를 때의 하한. 10분 이하 영상의 실제 금액과 같다. */
export const SHORTS_MIN_CREDITS = SHORTS_FIXED_CREDITS + SHORTS_TRANSCRIBE_CREDITS_PER_WINDOW

/**
 * 서버 `totalCreditsForRun` 과 같은 계산.
 *
 * 시작된 구간은 전부 센다 — 10분 1초는 두 번째 구간을 시작한 것이므로 2단위다.
 */
export function shortsCreditsForDuration(durationSeconds: number): number {
  if (!Number.isFinite(durationSeconds) || durationSeconds <= 0) return SHORTS_MIN_CREDITS
  const windowSeconds = SHORTS_TRANSCRIBE_WINDOW_MINUTES * 60
  const windows = Math.ceil(durationSeconds / windowSeconds)
  return SHORTS_FIXED_CREDITS + windows * SHORTS_TRANSCRIBE_CREDITS_PER_WINDOW
}

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
export function useShortsCreditEstimate() {
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
        estimate.value = {
          credits: shortsCreditsForDuration(durationSeconds),
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

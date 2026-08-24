import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { ResData } from '@/types/api'

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

let isRefreshing = false
let isLoggingOut = false

function forceLogout() {
  if (isLoggingOut) return
  isLoggingOut = true
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  sessionStorage.setItem('sessionExpired', '1')
  window.location.href = '/login'
}
let failedQueue: Array<{
  resolve: (token: string) => void
  reject: (error: unknown) => void
}> = []

function processQueue(error: unknown, token: string | null) {
  failedQueue.forEach((promise) => {
    if (error) {
      promise.reject(error)
    } else {
      promise.resolve(token!)
    }
  })
  failedQueue = []
}

function isSessionEstablishingRequest(config: InternalAxiosRequestConfig): boolean {
  const url = config.url ?? ''
  // These endpoints establish or inspect the session itself. A 401 here is
  // the response the caller needs to render; refreshing with the same stale
  // session would recurse and hide the real authentication error.
  return /\/auth\/(?:login|dev-login|refresh)(?:\/|$)/.test(url)
    || /\/auth\/(?:google|kakao)\/state(?:\/|$)/.test(url)
}

apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('accessToken')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

apiClient.interceptors.response.use(
  (response) => {
    const resData = response.data as ResData<unknown>
    if (resData.success === false) {
      return Promise.reject(new ApiError(resData.message ?? resData.error ?? 'Unknown error', response.status))
    }
    return response
  },
  async (error: AxiosError<ResData<unknown>>) => {
    const originalRequest = error.config
    if (!originalRequest) return Promise.reject(error)

    // Login failures must reach the login UI as-is. Retrying them with a
    // stale refresh token hides the provider's real error and can redirect
    // users away from the sign-in flow before the message is shown.
    if (
      error.response?.status === 401 &&
      !isSessionEstablishingRequest(originalRequest) &&
      !('_retry' in originalRequest)
    ) {
      if (isRefreshing) {
        return new Promise<string>((resolve, reject) => {
          failedQueue.push({ resolve, reject })
          // 대기 중인 요청이 영원히 stuck되지 않도록 15초 타임아웃
          setTimeout(() => reject(new Error('Token refresh timeout')), 15000)
        }).then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`
          return apiClient(originalRequest)
        })
      }

      Object.assign(originalRequest, { _retry: true })
      isRefreshing = true

      try {
        const refreshToken = localStorage.getItem('refreshToken')
        if (!refreshToken) {
          throw new Error('No refresh token')
        }

        const { data } = await axios.post<ResData<{ accessToken: string; refreshToken: string }>>(
          `${import.meta.env.VITE_API_BASE_URL}/auth/refresh`,
          { refreshToken },
          { timeout: 10000 },
        )

        if (data.success && data.data) {
          localStorage.setItem('accessToken', data.data.accessToken)
          localStorage.setItem('refreshToken', data.data.refreshToken)

          processQueue(null, data.data.accessToken)
          originalRequest.headers.Authorization = `Bearer ${data.data.accessToken}`
          return apiClient(originalRequest)
        }

        throw new Error('Token refresh failed')
      } catch (refreshError) {
        processQueue(refreshError, null)
        forceLogout()
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    enrichWithServerMessage(error)
    return Promise.reject(error)
  },
)

/** 공백이 아닌 문자열이면 다듬어서 돌려주고, 그 밖의 모든 값은 무시한다. */
function readableText(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

/** `PLAN_LIMIT_EXCEEDED` 처럼 대문자·숫자·밑줄로만 이뤄진 식별자. 사용자에게 보일 문장이 아니다. */
const MACHINE_CODE = /^[A-Z][A-Z0-9_]*$/

/**
 * 서버가 내려보낸 사용자용 사유를 오류 메시지로 끌어올린다.
 *
 * 백엔드는 거절 사유를 두 자리에 나눠 담는다. `handleBusiness` 만 message 와 error(코드)를
 * 함께 채우고, IllegalArgumentException·IllegalStateException·검증 실패를 비롯한 나머지 4xx 는
 * message 를 비운 채 **error 에 사람이 읽을 문장**을 넣는다. message 만 보던 동안에는
 * `require(...)` 로 막히는 입력·예약·게시 오류가 전부 `Request failed with status code 400`
 * 으로 보였다.
 *
 * 그래서 message 를 먼저 쓰고 없을 때만 error 로 내려간다. 이 우선순위가 1차 안전장치다 —
 * handleBusiness 는 error 에 PLAN_LIMIT_EXCEEDED 같은 기계 코드를 넣는데, message 가 항상
 * 함께 있으므로 코드가 사용자에게 노출되지 않는다.
 *
 * error 단독 값에는 2차 안전장치를 둔다. 대문자·숫자·밑줄로만 이뤄진 값은 사람이 읽을 문장이
 * 아니라 식별자이므로 무시하고 기존 Axios 문구를 남긴다. 사유를 못 보여주는 것보다 정체불명의
 * 코드를 보여주는 쪽이 더 나쁘고, 실제로 보여주고 싶은 건 require/검증 실패의 한국어 문장이다.
 *
 * 401 은 손대지 않는다. 세션을 만드는 요청(소셜 로그인·dev-login·state·refresh)의 401 은
 * refresh 분기를 건너뛰고 여기까지 오는데, handleUnauthorized/handleTokenExpired 가 error 에
 * 예외 메시지를 담기 때문에 보강하면 로그인 화면 문구가 서버 내부 사유로 바뀐다. 로그인·토큰
 * 흐름은 지금처럼 원본 오류를 그대로 UI 에 넘긴다.
 *
 * AxiosError 객체는 **그대로 두고 message 만** 채운다. response/status/config/isAxiosError 에
 * 의존하는 곳(useErrorHandler 의 상태코드별 분기, comments 스토어의 PLAN_LIMIT_EXCEEDED 판정)
 * 이 있어서 다른 오류 타입으로 감싸면 조용히 깨진다.
 */
function enrichWithServerMessage(error: AxiosError<ResData<unknown>>): void {
  if (error.response?.status === 401) return

  // data 는 서버 JSON 이 아닐 수 있다(HTML 오류 페이지 문자열, Blob 응답, 배열 등).
  // 객체가 아니면 읽지 않고, 객체여도 문자열 필드가 아니면 그대로 흘려보낸다.
  const data = error.response?.data
  if (typeof data !== 'object' || data === null) return

  const fromMessage = readableText(data.message)
  const fromError = readableText(data.error)
  const reason = fromMessage ?? (fromError && !MACHINE_CODE.test(fromError) ? fromError : null)
  if (reason) error.message = reason
}

export class ApiError extends Error {
  constructor(
    message: string,
    public statusCode: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export function unwrapResponse<T>(response: { data: ResData<T> }): T {
  if (response.data.success) {
    return response.data.data as T
  }
  throw new ApiError(response.data.message ?? response.data.error ?? 'No data', 0)
}

export default apiClient

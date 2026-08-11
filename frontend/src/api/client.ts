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
  return /\/auth\/(?:login|dev-login)(?:\/|$)/.test(url)
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
      return Promise.reject(new ApiError(resData.error ?? 'Unknown error', response.status))
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

    return Promise.reject(error)
  },
)

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
  throw new ApiError(response.data.error ?? 'No data', 0)
}

export default apiClient

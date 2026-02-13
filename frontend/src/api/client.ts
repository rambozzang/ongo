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

    if (error.response?.status === 401 && !('_retry' in originalRequest)) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
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
        // 현재 토큰 캡처 — 새 로그인이 발생했는지 비교용
        const failedToken = localStorage.getItem('accessToken')
        import('@/stores/auth').then(({ useAuthStore }) => {
          const authStore = useAuthStore()
          // 새 로그인으로 토큰이 교체된 경우 logout 하지 않음 (레이스 컨디션 방지)
          const currentToken = localStorage.getItem('accessToken')
          if (!currentToken || currentToken === failedToken) {
            authStore.logout()
          }
        }).catch(() => {
          const currentToken = localStorage.getItem('accessToken')
          if (!currentToken || currentToken === failedToken) {
            localStorage.removeItem('accessToken')
            localStorage.removeItem('refreshToken')
            window.location.href = '/login'
          }
        })
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

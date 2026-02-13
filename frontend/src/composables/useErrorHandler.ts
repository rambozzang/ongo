import { useNotificationStore } from '@/stores/notification'
import type { AxiosError } from 'axios'
import { ApiError } from '@/api/client'

export interface ErrorContext {
  action?: string
  silent?: boolean
}

export interface HandledError {
  message: string
  statusCode?: number
  isNetworkError: boolean
  isAuthError: boolean
  originalError: unknown
}

export function useErrorHandler() {
  const notification = useNotificationStore()

  function handleApiError(error: unknown, context?: ErrorContext): HandledError {
    const result: HandledError = {
      message: '',
      isNetworkError: false,
      isAuthError: false,
      originalError: error,
    }

    // Handle network errors (no internet connection)
    if (error instanceof Error && error.message === 'Network Error') {
      result.message = '인터넷 연결을 확인해주세요'
      result.isNetworkError = true

      if (!context?.silent) {
        notification.error(result.message, '네트워크 오류')
      }

      console.error('[ErrorHandler] Network error:', { context, error })
      return result
    }

    // Handle Axios errors
    if (isAxiosError(error)) {
      const statusCode = error.response?.status
      result.statusCode = statusCode

      // Network error (no response received)
      if (!error.response) {
        result.message = '서버에 연결할 수 없습니다. 인터넷 연결을 확인해주세요.'
        result.isNetworkError = true

        if (!context?.silent) {
          notification.error(result.message, '연결 오류')
        }

        console.error('[ErrorHandler] No response from server:', { context, error })
        return result
      }

      // Handle specific status codes
      switch (statusCode) {
        case 401:
          result.message = '로그인이 필요합니다'
          result.isAuthError = true

          if (!context?.silent) {
            notification.warning(result.message)
            // Redirect to login (handled by API interceptor)
            // router.push('/login')
          }
          break

        case 403:
          result.message = '접근 권한이 없습니다'
          result.isAuthError = true

          if (!context?.silent) {
            notification.error(result.message, '권한 오류')
          }
          break

        case 404:
          result.message = context?.action
            ? `${context.action} 중 요청한 리소스를 찾을 수 없습니다`
            : '요청한 페이지를 찾을 수 없습니다'

          if (!context?.silent) {
            notification.error(result.message, '404 오류')
          }
          break

        case 429:
          result.message = '요청이 너무 많습니다. 잠시 후 다시 시도해주세요'

          if (!context?.silent) {
            notification.warning(result.message, '요청 제한')
          }
          break

        case 500:
        case 502:
        case 503:
        case 504:
          result.message = '서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요'

          if (!context?.silent) {
            notification.error(result.message, '서버 오류')
          }
          break

        default:
          // Try to extract error message from response
          const responseData = error.response?.data as { error?: string; message?: string }
          result.message =
            responseData?.error ||
            responseData?.message ||
            (context?.action
              ? `${context.action} 중 오류가 발생했습니다`
              : '알 수 없는 오류가 발생했습니다')

          if (!context?.silent) {
            notification.error(result.message)
          }
      }

      console.error('[ErrorHandler] API error:', {
        context,
        statusCode,
        message: result.message,
        error,
      })
      return result
    }

    // Handle ApiError from our client
    if (error instanceof ApiError) {
      result.statusCode = error.statusCode
      result.message = error.message

      if (!context?.silent) {
        notification.error(result.message)
      }

      console.error('[ErrorHandler] ApiError:', { context, error })
      return result
    }

    // Handle generic errors
    if (error instanceof Error) {
      result.message = context?.action
        ? `${context.action} 중 오류가 발생했습니다: ${error.message}`
        : error.message

      if (!context?.silent) {
        notification.error(result.message)
      }

      console.error('[ErrorHandler] Generic error:', { context, error })
      return result
    }

    // Unknown error type
    result.message = context?.action
      ? `${context.action} 중 알 수 없는 오류가 발생했습니다`
      : '알 수 없는 오류가 발생했습니다'

    if (!context?.silent) {
      notification.error(result.message)
    }

    console.error('[ErrorHandler] Unknown error:', { context, error })
    return result
  }

  // Type guard for AxiosError
  function isAxiosError(error: unknown): error is AxiosError {
    return (
      typeof error === 'object' &&
      error !== null &&
      'isAxiosError' in error &&
      (error as AxiosError).isAxiosError === true
    )
  }

  return {
    handleApiError,
  }
}

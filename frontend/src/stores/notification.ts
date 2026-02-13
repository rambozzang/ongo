import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ToastType = 'success' | 'error' | 'warning' | 'info'

export interface Toast {
  id: string
  type: ToastType
  title?: string
  message: string
  duration: number
  createdAt: number
  timeoutId?: ReturnType<typeof setTimeout>
}

export interface ShowToastOptions {
  type: ToastType
  title?: string
  message: string
  duration?: number
}

const MAX_TOASTS = 5

export const useNotificationStore = defineStore('notification', () => {
  const toasts = ref<Toast[]>([])

  function showToast(options: ShowToastOptions) {
    const id = `toast-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
    const duration = options.duration ?? 4000

    const toast: Toast = {
      id,
      type: options.type,
      title: options.title,
      message: options.message,
      duration,
      createdAt: Date.now(),
    }

    // Add toast to the end (newest at bottom)
    toasts.value.push(toast)

    // Remove oldest toasts if exceeding max
    while (toasts.value.length > MAX_TOASTS) {
      const oldestToast = toasts.value.shift()
      if (oldestToast?.timeoutId) {
        clearTimeout(oldestToast.timeoutId)
      }
    }

    // Auto-remove after duration
    if (duration > 0) {
      toast.timeoutId = setTimeout(() => removeToast(id), duration)
    }

    return id
  }

  function removeToast(id: string) {
    const index = toasts.value.findIndex((t) => t.id === id)
    if (index !== -1) {
      const toast = toasts.value[index]
      if (toast.timeoutId) {
        clearTimeout(toast.timeoutId)
      }
      toasts.value.splice(index, 1)
    }
  }

  // Legacy method for backward compatibility
  function addToast(type: ToastType, message: string, duration = 3000) {
    showToast({ type, message, duration })
  }

  function success(message: string, title?: string) {
    showToast({ type: 'success', message, title, duration: 3000 })
  }

  function error(message: string, title?: string) {
    showToast({ type: 'error', message, title, duration: 5000 })
  }

  function warning(message: string, title?: string) {
    showToast({ type: 'warning', message, title, duration: 4000 })
  }

  function info(message: string, title?: string) {
    showToast({ type: 'info', message, title, duration: 3000 })
  }

  return {
    toasts,
    showToast,
    addToast,
    removeToast,
    success,
    error,
    warning,
    info,
  }
})

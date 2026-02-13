import { useNotificationStore } from '@/stores/notification'

export function useNotification() {
  const store = useNotificationStore()

  return {
    showToast: store.showToast,
    success: store.success,
    error: store.error,
    warning: store.warning,
    info: store.info,
    toasts: store.toasts,
    removeToast: store.removeToast,
  }
}

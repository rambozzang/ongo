import { ref, onMounted, onUnmounted, type Ref } from 'vue'

export interface InfiniteScrollOptions {
  threshold?: number
  rootMargin?: string
  debounceMs?: number
}

export function useInfiniteScroll(
  loadMore: () => void | Promise<void>,
  options: InfiniteScrollOptions = {},
) {
  const {
    threshold = 0.1,
    rootMargin = '0px',
    debounceMs = 300,
  } = options

  const sentinelRef = ref<HTMLElement | null>(null)
  const isLoading = ref(false)
  const hasMore = ref(true)

  let observer: IntersectionObserver | null = null
  let debounceTimeout: ReturnType<typeof setTimeout> | null = null

  const handleIntersection = async (entries: IntersectionObserverEntry[]) => {
    const entry = entries[0]

    if (!entry.isIntersecting || isLoading.value || !hasMore.value) {
      return
    }

    // Debounce rapid scroll events
    if (debounceTimeout) {
      clearTimeout(debounceTimeout)
    }

    debounceTimeout = setTimeout(async () => {
      isLoading.value = true
      try {
        await loadMore()
      } catch {
        // silently ignore load errors
      } finally {
        isLoading.value = false
      }
    }, debounceMs)
  }

  const setupObserver = () => {
    if (!sentinelRef.value) return

    observer = new IntersectionObserver(handleIntersection, {
      threshold,
      rootMargin,
    })

    observer.observe(sentinelRef.value)
  }

  const cleanup = () => {
    if (debounceTimeout) {
      clearTimeout(debounceTimeout)
      debounceTimeout = null
    }

    if (observer) {
      observer.disconnect()
      observer = null
    }
  }

  const reset = () => {
    hasMore.value = true
    isLoading.value = false
    cleanup()
    setupObserver()
  }

  onMounted(() => {
    setupObserver()
  })

  onUnmounted(() => {
    cleanup()
  })

  return {
    sentinelRef: sentinelRef as Ref<HTMLElement | null>,
    isLoading,
    hasMore,
    reset,
  }
}

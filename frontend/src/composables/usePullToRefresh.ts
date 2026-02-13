import { ref, onMounted, onUnmounted, type Ref } from 'vue'

export interface UsePullToRefreshOptions {
  threshold?: number
  maxDistance?: number
  onRefresh?: () => void | Promise<void>
}

export function usePullToRefresh(
  containerRef: Ref<HTMLElement | null>,
  options: UsePullToRefreshOptions = {}
) {
  const { threshold = 80, maxDistance = 150, onRefresh } = options

  const pullDistance = ref(0)
  const isRefreshing = ref(false)
  const canPull = ref(false)

  let startY = 0
  let isDragging = false
  let isVerticalPull = false

  const handleTouchStart = (e: TouchEvent) => {
    const container = containerRef.value
    if (!container) return

    // Only allow pull-to-refresh when scrolled to top
    if (container.scrollTop === 0) {
      const touch = e.touches[0]
      startY = touch.clientY
      isDragging = true
      canPull.value = true
    }
  }

  const handleTouchMove = (e: TouchEvent) => {
    if (!isDragging || !canPull.value || isRefreshing.value) return

    const container = containerRef.value
    if (!container) return

    const touch = e.touches[0]
    const deltaY = touch.clientY - startY

    // Only pull down
    if (deltaY <= 0) {
      pullDistance.value = 0
      return
    }

    // Check if still at top
    if (container.scrollTop > 0) {
      pullDistance.value = 0
      canPull.value = false
      return
    }

    isVerticalPull = true

    // Apply resistance
    if (deltaY > maxDistance) {
      const excess = deltaY - maxDistance
      pullDistance.value = maxDistance + excess * 0.3
    } else {
      pullDistance.value = deltaY
    }

    // Prevent default scroll when pulling
    if (pullDistance.value > 10) {
      e.preventDefault()
    }
  }

  const handleTouchEnd = async () => {
    if (!isDragging || !isVerticalPull) {
      reset()
      return
    }

    isDragging = false
    isVerticalPull = false

    // Trigger refresh if threshold reached
    if (pullDistance.value >= threshold && onRefresh && !isRefreshing.value) {
      isRefreshing.value = true
      pullDistance.value = threshold

      try {
        await onRefresh()
      } finally {
        await new Promise((resolve) => setTimeout(resolve, 300))
        reset()
        isRefreshing.value = false
      }
    } else {
      reset()
    }
  }

  const reset = () => {
    pullDistance.value = 0
    isDragging = false
    isVerticalPull = false
    canPull.value = false
  }

  onMounted(() => {
    const container = containerRef.value
    if (container) {
      container.addEventListener('touchstart', handleTouchStart, { passive: true })
      container.addEventListener('touchmove', handleTouchMove, { passive: false })
      container.addEventListener('touchend', handleTouchEnd, { passive: true })
    }
  })

  onUnmounted(() => {
    const container = containerRef.value
    if (container) {
      container.removeEventListener('touchstart', handleTouchStart)
      container.removeEventListener('touchmove', handleTouchMove)
      container.removeEventListener('touchend', handleTouchEnd)
    }
  })

  return {
    pullDistance,
    isRefreshing,
    canPull,
  }
}

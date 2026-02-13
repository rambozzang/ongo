import { ref, onMounted, onUnmounted, type Ref } from 'vue'

export interface UseSwipeOptions {
  threshold?: number
  maxSwipeDistance?: number
  onSwipeLeft?: () => void
  onSwipeRight?: () => void
}

export function useSwipe(elementRef: Ref<HTMLElement | null>, options: UseSwipeOptions = {}) {
  const {
    threshold = 80,
    maxSwipeDistance = 120,
    onSwipeLeft,
    onSwipeRight,
  } = options

  const offsetX = ref(0)
  const isSwiping = ref(false)

  let startX = 0
  let startY = 0
  let currentX = 0
  let isDragging = false
  let isHorizontalSwipe = false

  const handleTouchStart = (e: TouchEvent) => {
    const touch = e.touches[0]
    startX = touch.clientX
    startY = touch.clientY
    currentX = touch.clientX
    isDragging = true
    isHorizontalSwipe = false
  }

  const handleTouchMove = (e: TouchEvent) => {
    if (!isDragging) return

    const touch = e.touches[0]
    const deltaX = touch.clientX - startX
    const deltaY = touch.clientY - startY

    // Determine if this is a horizontal swipe
    if (!isHorizontalSwipe && Math.abs(deltaX) < 10 && Math.abs(deltaY) < 10) {
      return
    }

    if (!isHorizontalSwipe) {
      // Check if horizontal movement > vertical movement
      if (Math.abs(deltaX) > Math.abs(deltaY)) {
        isHorizontalSwipe = true
        isSwiping.value = true
      } else {
        // This is a vertical scroll, cancel the swipe
        isDragging = false
        return
      }
    }

    if (isHorizontalSwipe) {
      // Prevent vertical scroll when swiping horizontally
      e.preventDefault()

      currentX = touch.clientX
      const distance = currentX - startX

      // Apply resistance at the edges
      if (Math.abs(distance) > maxSwipeDistance) {
        const sign = distance > 0 ? 1 : -1
        const excess = Math.abs(distance) - maxSwipeDistance
        offsetX.value = sign * (maxSwipeDistance + excess * 0.3)
      } else {
        offsetX.value = distance
      }
    }
  }

  const handleTouchEnd = () => {
    if (!isDragging || !isHorizontalSwipe) {
      reset()
      return
    }

    isDragging = false
    isHorizontalSwipe = false

    const distance = offsetX.value

    // Check if threshold is reached
    if (distance < -threshold && onSwipeLeft) {
      onSwipeLeft()
    } else if (distance > threshold && onSwipeRight) {
      onSwipeRight()
    } else {
      reset()
    }
  }

  const reset = () => {
    offsetX.value = 0
    isSwiping.value = false
    isDragging = false
    isHorizontalSwipe = false
  }

  onMounted(() => {
    const element = elementRef.value
    if (element) {
      element.addEventListener('touchstart', handleTouchStart, { passive: true })
      element.addEventListener('touchmove', handleTouchMove, { passive: false })
      element.addEventListener('touchend', handleTouchEnd, { passive: true })
    }
  })

  onUnmounted(() => {
    const element = elementRef.value
    if (element) {
      element.removeEventListener('touchstart', handleTouchStart)
      element.removeEventListener('touchmove', handleTouchMove)
      element.removeEventListener('touchend', handleTouchEnd)
    }
  })

  return {
    offsetX,
    isSwiping,
    reset,
  }
}

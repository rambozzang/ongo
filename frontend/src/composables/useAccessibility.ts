import { onMounted, onUnmounted, ref, type Ref } from 'vue'

/**
 * Focus trap for modal/dialog components
 * Traps keyboard focus within a container element
 */
export function useFocusTrap(containerRef: Ref<HTMLElement | null>) {
  const firstFocusableElement = ref<HTMLElement | null>(null)
  const lastFocusableElement = ref<HTMLElement | null>(null)

  const focusableSelector = 'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'

  function updateFocusableElements() {
    if (!containerRef.value) return

    const focusables = Array.from(
      containerRef.value.querySelectorAll<HTMLElement>(focusableSelector)
    ).filter(el => !el.hasAttribute('inert') && el.offsetParent !== null)

    firstFocusableElement.value = focusables[0] || null
    lastFocusableElement.value = focusables[focusables.length - 1] || null
  }

  function handleKeyDown(e: KeyboardEvent) {
    if (e.key !== 'Tab') return
    if (!firstFocusableElement.value || !lastFocusableElement.value) return

    if (e.shiftKey) {
      // Shift + Tab
      if (document.activeElement === firstFocusableElement.value) {
        e.preventDefault()
        lastFocusableElement.value.focus()
      }
    } else {
      // Tab
      if (document.activeElement === lastFocusableElement.value) {
        e.preventDefault()
        firstFocusableElement.value.focus()
      }
    }
  }

  function activate() {
    updateFocusableElements()
    if (firstFocusableElement.value) {
      firstFocusableElement.value.focus()
    }
    document.addEventListener('keydown', handleKeyDown)
  }

  function deactivate() {
    document.removeEventListener('keydown', handleKeyDown)
  }

  return {
    activate,
    deactivate,
    updateFocusableElements,
  }
}

/**
 * Arrow key navigation for lists/menus
 * Handles Up/Down arrow keys for vertical navigation
 */
export function useArrowNavigation(
  containerRef: Ref<HTMLElement | null>,
  options: {
    selector?: string
    orientation?: 'vertical' | 'horizontal'
    loop?: boolean
  } = {}
) {
  const {
    selector = '[role="menuitem"], [role="option"], button, a',
    orientation = 'vertical',
    loop = true,
  } = options

  function getNavigableElements(): HTMLElement[] {
    if (!containerRef.value) return []
    return Array.from(
      containerRef.value.querySelectorAll<HTMLElement>(selector)
    ).filter(el => !el.hasAttribute('disabled') && !el.hasAttribute('inert') && el.offsetParent !== null)
  }

  function handleKeyDown(e: KeyboardEvent) {
    const items = getNavigableElements()
    if (items.length === 0) return

    const currentIndex = items.findIndex(item => item === document.activeElement)
    let nextIndex = -1

    if (orientation === 'vertical') {
      if (e.key === 'ArrowDown') {
        e.preventDefault()
        nextIndex = currentIndex === -1 ? 0 : currentIndex + 1
        if (nextIndex >= items.length) {
          nextIndex = loop ? 0 : currentIndex
        }
      } else if (e.key === 'ArrowUp') {
        e.preventDefault()
        nextIndex = currentIndex === -1 ? items.length - 1 : currentIndex - 1
        if (nextIndex < 0) {
          nextIndex = loop ? items.length - 1 : 0
        }
      } else if (e.key === 'Home') {
        e.preventDefault()
        nextIndex = 0
      } else if (e.key === 'End') {
        e.preventDefault()
        nextIndex = items.length - 1
      }
    } else {
      // horizontal
      if (e.key === 'ArrowRight') {
        e.preventDefault()
        nextIndex = currentIndex === -1 ? 0 : currentIndex + 1
        if (nextIndex >= items.length) {
          nextIndex = loop ? 0 : currentIndex
        }
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault()
        nextIndex = currentIndex === -1 ? items.length - 1 : currentIndex - 1
        if (nextIndex < 0) {
          nextIndex = loop ? items.length - 1 : 0
        }
      }
    }

    if (nextIndex >= 0 && items[nextIndex]) {
      items[nextIndex].focus()
    }
  }

  function activate() {
    if (!containerRef.value) return
    containerRef.value.addEventListener('keydown', handleKeyDown)
  }

  function deactivate() {
    if (!containerRef.value) return
    containerRef.value.removeEventListener('keydown', handleKeyDown)
  }

  onMounted(() => {
    activate()
  })

  onUnmounted(() => {
    deactivate()
  })

  return {
    activate,
    deactivate,
  }
}

/**
 * Announce message to screen readers via aria-live region
 * Must be used with AriaLiveRegion component
 */
let announceCallback: ((message: string, priority?: 'polite' | 'assertive') => void) | null = null

export function registerAnnouncer(callback: (message: string, priority?: 'polite' | 'assertive') => void) {
  announceCallback = callback
}

export function announce(message: string, priority: 'polite' | 'assertive' = 'polite') {
  if (announceCallback) {
    announceCallback(message, priority)
  } else {
    console.warn('[useAccessibility] AriaLiveRegion not registered. Cannot announce:', message)
  }
}

/**
 * Check if user prefers reduced motion
 */
export function usePrefersReducedMotion() {
  const prefersReducedMotion = ref(false)

  function updatePreference() {
    prefersReducedMotion.value = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  }

  onMounted(() => {
    updatePreference()
    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
    mediaQuery.addEventListener('change', updatePreference)

    onUnmounted(() => {
      mediaQuery.removeEventListener('change', updatePreference)
    })
  })

  return { prefersReducedMotion }
}

/**
 * Manage inert attribute for elements outside modal
 * Useful for preventing interaction with background content when modal is open
 */
export function useInert(enabled: Ref<boolean>, excludeSelector?: string) {
  const inertElements = ref<HTMLElement[]>([])

  function setInert() {
    if (!enabled.value) return

    const bodyChildren = Array.from(document.body.children) as HTMLElement[]
    bodyChildren.forEach(child => {
      if (excludeSelector && child.matches(excludeSelector)) {
        return
      }
      if (!child.hasAttribute('inert')) {
        child.setAttribute('inert', '')
        inertElements.value.push(child)
      }
    })
  }

  function removeInert() {
    inertElements.value.forEach(el => {
      el.removeAttribute('inert')
    })
    inertElements.value = []
  }

  onMounted(() => {
    if (enabled.value) {
      setInert()
    }
  })

  onUnmounted(() => {
    removeInert()
  })

  return {
    setInert,
    removeInert,
  }
}

import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'

export const showShortcutHelp = ref(false)

export function useKeyboardShortcuts() {
  const router = useRouter()

  // Sequential key tracking
  const sequentialBuffer = ref<string[]>([])
  let sequentialTimeout: number | null = null

  // Check if user is typing in an input field
  function isTyping(event: KeyboardEvent): boolean {
    const target = event.target as HTMLElement
    const tagName = target.tagName.toLowerCase()
    const isInput = tagName === 'input' || tagName === 'textarea'
    const isContentEditable = target.isContentEditable
    return isInput || isContentEditable
  }

  // Clear sequential buffer
  function clearSequentialBuffer() {
    sequentialBuffer.value = []
    if (sequentialTimeout !== null) {
      window.clearTimeout(sequentialTimeout)
      sequentialTimeout = null
    }
  }

  // Handle sequential keys (like "G D" for navigation)
  function handleSequentialKey(key: string, event: KeyboardEvent) {
    if (isTyping(event)) return

    sequentialBuffer.value.push(key.toLowerCase())

    // Clear buffer after 1 second
    if (sequentialTimeout !== null) {
      window.clearTimeout(sequentialTimeout)
    }
    sequentialTimeout = window.setTimeout(() => {
      clearSequentialBuffer()
    }, 1000)

    // Check for navigation shortcuts (G + second key)
    if (sequentialBuffer.value.length === 2 && sequentialBuffer.value[0] === 'g') {
      const sequence = sequentialBuffer.value.join('')

      switch (sequence) {
        case 'gd':
          router.push('/dashboard')
          break
        case 'gv':
          router.push('/videos')
          break
        case 'gu':
          router.push('/upload')
          break
        case 'ga':
          router.push('/analytics')
          break
        case 'gs':
          router.push('/settings')
          break
      }

      clearSequentialBuffer()
    }

    // Clear if buffer gets too long
    if (sequentialBuffer.value.length > 2) {
      clearSequentialBuffer()
    }
  }

  // Main keyboard event handler
  function handleKeydown(event: KeyboardEvent) {
    // Don't handle shortcuts when typing
    if (isTyping(event)) return

    const key = event.key.toLowerCase()

    // ? key - Show shortcut help
    if (key === '?') {
      event.preventDefault()
      showShortcutHelp.value = true
      return
    }

    // Escape - Close modals
    if (key === 'escape') {
      showShortcutHelp.value = false
      return
    }

    // / key - Focus search (if search input exists)
    if (key === '/') {
      event.preventDefault()
      const searchInput = document.querySelector<HTMLInputElement>('input[type="search"], input[placeholder*="검색"]')
      if (searchInput) {
        searchInput.focus()
      }
      return
    }

    // Sequential keys (G + another key)
    if (key === 'g' || (sequentialBuffer.value.length > 0 && sequentialBuffer.value[0] === 'g')) {
      event.preventDefault()
      handleSequentialKey(key, event)
      return
    }
  }

  // Register global keyboard shortcuts
  onMounted(() => {
    window.addEventListener('keydown', handleKeydown)
  })

  // Cleanup
  onUnmounted(() => {
    window.removeEventListener('keydown', handleKeydown)
    if (sequentialTimeout !== null) {
      window.clearTimeout(sequentialTimeout)
    }
  })

  return {
    showShortcutHelp,
  }
}

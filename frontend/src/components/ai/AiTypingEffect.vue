<template>
  <span class="inline">
    {{ displayedText }}<span v-if="showCursor" class="cursor">|</span>
  </span>
</template>

<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue'

const props = withDefaults(
  defineProps<{
    text: string
    speed?: number
    startDelay?: number
  }>(),
  {
    speed: 30,
    startDelay: 0,
  }
)

const emit = defineEmits<{
  complete: []
}>()

const displayedText = ref('')
const showCursor = ref(false)
let currentIndex = 0
let animationTimer: number | null = null
let cursorTimer: number | null = null

function startTyping() {
  // Reset state
  displayedText.value = ''
  currentIndex = 0
  showCursor.value = true

  // Clear any existing timers
  if (animationTimer) clearTimeout(animationTimer)
  if (cursorTimer) clearInterval(cursorTimer)

  // Start cursor blinking
  cursorTimer = window.setInterval(() => {
    showCursor.value = !showCursor.value
  }, 530)

  // Start typing after delay
  setTimeout(() => {
    typeNextCharacter()
  }, props.startDelay)
}

function typeNextCharacter() {
  if (currentIndex < props.text.length) {
    displayedText.value = props.text.slice(0, currentIndex + 1)
    currentIndex++
    animationTimer = window.setTimeout(() => {
      typeNextCharacter()
    }, props.speed)
  } else {
    // Typing complete - blink cursor 3 more times then hide
    if (cursorTimer) clearInterval(cursorTimer)

    let blinkRemaining = 6 // 3 full blinks (on/off = 2 states per blink)
    cursorTimer = window.setInterval(() => {
      showCursor.value = !showCursor.value
      blinkRemaining--

      if (blinkRemaining <= 0) {
        if (cursorTimer) clearInterval(cursorTimer)
        showCursor.value = false
        emit('complete')
      }
    }, 530)
  }
}

// Watch for text changes
watch(() => props.text, () => {
  startTyping()
}, { immediate: true })

// Cleanup on unmount
onUnmounted(() => {
  if (animationTimer) clearTimeout(animationTimer)
  if (cursorTimer) clearInterval(cursorTimer)
})
</script>

<style scoped>
.cursor {
  @apply inline-block text-primary-600 dark:text-primary-400;
  animation: cursor-blink 1.06s step-end infinite;
}

@keyframes cursor-blink {
  0%, 50% {
    opacity: 1;
  }
  50.01%, 100% {
    opacity: 0;
  }
}
</style>

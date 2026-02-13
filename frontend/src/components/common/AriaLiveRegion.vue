<template>
  <div class="sr-only">
    <!-- Polite announcements (non-interrupting) -->
    <div
      role="status"
      aria-live="polite"
      aria-atomic="true"
    >
      {{ politeMessage }}
    </div>

    <!-- Assertive announcements (interrupting) -->
    <div
      role="alert"
      aria-live="assertive"
      aria-atomic="true"
    >
      {{ assertiveMessage }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { registerAnnouncer } from '@/composables/useAccessibility'

const politeMessage = ref('')
const assertiveMessage = ref('')

let politeTimeout: ReturnType<typeof setTimeout> | null = null
let assertiveTimeout: ReturnType<typeof setTimeout> | null = null

function announce(message: string, priority: 'polite' | 'assertive' = 'polite') {
  if (priority === 'assertive') {
    // Clear any existing timeout
    if (assertiveTimeout) {
      clearTimeout(assertiveTimeout)
    }

    assertiveMessage.value = message

    // Clear after 5 seconds
    assertiveTimeout = setTimeout(() => {
      assertiveMessage.value = ''
    }, 5000)
  } else {
    // Clear any existing timeout
    if (politeTimeout) {
      clearTimeout(politeTimeout)
    }

    politeMessage.value = message

    // Clear after 5 seconds
    politeTimeout = setTimeout(() => {
      politeMessage.value = ''
    }, 5000)
  }
}

onMounted(() => {
  registerAnnouncer(announce)
})

onUnmounted(() => {
  if (politeTimeout) clearTimeout(politeTimeout)
  if (assertiveTimeout) clearTimeout(assertiveTimeout)
})
</script>

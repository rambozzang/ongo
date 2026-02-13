<template>
  <Transition
    enter-active-class="transition-all duration-300"
    enter-from-class="opacity-0 translate-y-4"
    enter-to-class="opacity-100 translate-y-0"
    leave-active-class="transition-all duration-200"
    leave-from-class="opacity-100 translate-y-0"
    leave-to-class="opacity-0 translate-y-4"
  >
    <button
      v-if="isVisible"
      class="fixed bottom-20 right-6 z-30 rounded-full bg-primary-600 p-3 text-white shadow-lg hover:bg-primary-700 dark:bg-primary-500 dark:hover:bg-primary-600 transition-colors focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"
      @click="scrollToTop"
      aria-label="맨 위로 스크롤"
      title="맨 위로"
    >
      <ChevronUpIcon class="h-6 w-6" />
    </button>
  </Transition>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ChevronUpIcon } from '@heroicons/vue/24/outline'

const isVisible = ref(false)
const scrollThreshold = 300

const handleScroll = () => {
  isVisible.value = window.scrollY > scrollThreshold
}

const scrollToTop = () => {
  window.scrollTo({
    top: 0,
    behavior: 'smooth',
  })
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})
</script>

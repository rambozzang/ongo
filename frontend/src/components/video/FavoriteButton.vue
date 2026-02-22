<template>
  <button
    type="button"
    class="inline-flex items-center justify-center rounded-full transition-all duration-200"
    :class="[
      sizeClasses,
      isFavorited
        ? 'text-amber-500 hover:text-amber-600'
        : 'text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300',
      isAnimating && 'animate-pulse-scale',
    ]"
    :title="isFavorited ? '즐겨찾기 해제' : '즐겨찾기 추가'"
    :aria-label="isFavorited ? '즐겨찾기 해제' : '즐겨찾기 추가'"
    :aria-pressed="isFavorited"
    @click.stop="handleToggle"
  >
    <StarIconSolid v-if="isFavorited" :class="iconSizeClasses" aria-hidden="true" />
    <StarIconOutline v-else :class="iconSizeClasses" aria-hidden="true" />
  </button>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { StarIcon as StarIconOutline } from '@heroicons/vue/24/outline'
import { StarIcon as StarIconSolid } from '@heroicons/vue/24/solid'
import { useFavoritesStore } from '@/stores/favorites'
import { useNotification } from '@/composables/useNotification'

const props = withDefaults(
  defineProps<{
    videoId: number
    size?: 'sm' | 'md'
  }>(),
  {
    size: 'md',
  }
)

const favoritesStore = useFavoritesStore()
const { success, error } = useNotification()

const isAnimating = ref(false)
const hasShownFirstToast = ref(false)

const isFavorited = computed(() => favoritesStore.isFavorite(props.videoId))

const sizeClasses = computed(() => {
  return props.size === 'sm' ? 'p-1' : 'p-1.5'
})

const iconSizeClasses = computed(() => {
  return props.size === 'sm' ? 'h-4 w-4' : 'h-5 w-5'
})

let animationTimeout: ReturnType<typeof setTimeout> | null = null

function handleToggle() {
  try {
    const added = favoritesStore.toggleFavorite(props.videoId)

    // Trigger animation
    isAnimating.value = true
    if (animationTimeout) clearTimeout(animationTimeout)
    animationTimeout = setTimeout(() => {
      isAnimating.value = false
    }, 300)

    // Show toast on first favorite
    if (added && !hasShownFirstToast.value) {
      success('즐겨찾기에 추가되었습니다')
      hasShownFirstToast.value = true
    }
  } catch (err) {
    if (err instanceof Error) {
      error(err.message)
    }
  }
}

onUnmounted(() => {
  if (animationTimeout) clearTimeout(animationTimeout)
})
</script>

<style scoped>
@keyframes pulse-scale {
  0%,
  100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.2);
  }
}

.animate-pulse-scale {
  animation: pulse-scale 0.3s ease-in-out;
}
</style>

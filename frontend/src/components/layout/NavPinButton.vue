<template>
  <button
    type="button"
    class="inline-flex shrink-0 items-center justify-center rounded-lg transition-colors hover:bg-gray-500/20"
    :class="[
      sizeClasses,
      isPinned ? 'text-amber-500 hover:text-amber-600' : 'text-gray-400 hover:text-amber-500',
    ]"
    :title="isPinned ? t('nav.removeFavorite') : t('nav.addFavorite')"
    :aria-label="`${label} — ${isPinned ? t('nav.removeFavorite') : t('nav.addFavorite')}`"
    :aria-pressed="isPinned"
    @click.stop.prevent="handleToggle"
  >
    <StarIconSolid v-if="isPinned" :class="iconSizeClasses" aria-hidden="true" />
    <StarIconOutline v-else :class="iconSizeClasses" aria-hidden="true" />
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { StarIcon as StarIconOutline } from '@heroicons/vue/24/outline'
import { StarIcon as StarIconSolid } from '@heroicons/vue/24/solid'
import { useLocale } from '@/composables/useLocale'
import { useNotification } from '@/composables/useNotification'
import { useNavFavoritesStore, MAX_NAV_FAVORITES } from '@/stores/navFavorites'

const props = withDefaults(
  defineProps<{
    path: string
    label: string
    size?: 'sm' | 'md'
  }>(),
  {
    size: 'sm',
  },
)

const { t } = useLocale()
const { error } = useNotification()
const navFavoritesStore = useNavFavoritesStore()

const isPinned = computed(() => navFavoritesStore.isFavorite(props.path))

const sizeClasses = computed(() => (props.size === 'sm' ? 'h-8 w-8' : 'h-11 w-11'))
const iconSizeClasses = computed(() => (props.size === 'sm' ? 'h-4 w-4' : 'h-5 w-5'))

function handleToggle() {
  const result = navFavoritesStore.toggleFavorite(props.path)
  if (result === 'limit') {
    error(t('nav.favoriteLimit', { max: MAX_NAV_FAVORITES }))
  }
}
</script>

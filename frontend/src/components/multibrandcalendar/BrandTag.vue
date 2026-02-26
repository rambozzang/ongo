<script setup lang="ts">
import { computed } from 'vue'
import { XMarkIcon } from '@heroicons/vue/24/outline'
import type { BrandColor } from '@/types/multiBrandCalendar'

interface Props {
  name: string
  color: BrandColor
  removable?: boolean
  active?: boolean
}

interface Emits {
  (e: 'click'): void
  (e: 'remove'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const colorMap: Record<BrandColor, string> = {
  RED: '#EF4444',
  BLUE: '#3B82F6',
  GREEN: '#10B981',
  PURPLE: '#8B5CF6',
  ORANGE: '#F97316',
  PINK: '#EC4899',
  TEAL: '#14B8A6',
  YELLOW: '#EAB308',
}

const dotColor = computed(() => colorMap[props.color])
</script>

<template>
  <span
    :class="[
      'inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-sm font-medium transition-all cursor-pointer',
      active
        ? 'bg-gray-900 text-white dark:bg-white dark:text-gray-900 shadow-md'
        : 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700',
    ]"
    @click="emit('click')"
  >
    <span
      class="w-2.5 h-2.5 rounded-full flex-shrink-0"
      :style="{ backgroundColor: dotColor }"
    />
    {{ name }}
    <button
      v-if="removable"
      @click.stop="emit('remove')"
      class="ml-0.5 p-0.5 rounded-full hover:bg-gray-300 dark:hover:bg-gray-600 transition-colors"
    >
      <XMarkIcon class="w-3 h-3" />
    </button>
  </span>
</template>

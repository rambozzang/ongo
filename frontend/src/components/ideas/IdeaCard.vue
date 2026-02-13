<template>
  <div
    class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-4 cursor-pointer hover:shadow-md transition-shadow"
    draggable="true"
    @dragstart="handleDragStart"
    @click="$emit('click')"
  >
    <!-- Priority Badge -->
    <div class="flex items-center justify-between mb-2">
      <span
        :class="priorityClasses"
        class="text-xs font-medium px-2 py-1 rounded"
      >
        {{ priorityLabel }}
      </span>
      <div class="flex gap-1">
        <component
          v-for="platform in idea.platform"
          :key="platform"
          :is="platformIcon(platform)"
          class="w-4 h-4 text-gray-500 dark:text-gray-400"
        />
      </div>
    </div>

    <!-- Title -->
    <h3 class="font-semibold text-gray-900 dark:text-gray-100 mb-2 line-clamp-2">
      {{ idea.title }}
    </h3>

    <!-- Description -->
    <p class="text-sm text-gray-600 dark:text-gray-400 mb-3 line-clamp-2">
      {{ idea.description }}
    </p>

    <!-- Tags -->
    <div v-if="idea.tags.length > 0" class="flex flex-wrap gap-1 mb-3">
      <span
        v-for="tag in idea.tags"
        :key="tag"
        class="text-xs bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 px-2 py-1 rounded"
      >
        #{{ tag }}
      </span>
    </div>

    <!-- Due Date -->
    <div v-if="idea.dueDate" class="flex items-center text-xs text-gray-500 dark:text-gray-400">
      <CalendarIcon class="w-4 h-4 mr-1" />
      {{ formatDate(idea.dueDate) }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ContentIdea } from '@/types/idea'
import { CalendarIcon } from '@heroicons/vue/24/outline'

const props = defineProps<{
  idea: ContentIdea
}>()

const emit = defineEmits<{
  (e: 'click'): void
  (e: 'dragstart', event: DragEvent): void
}>()

const priorityClasses = computed(() => {
  switch (props.idea.priority) {
    case 'high':
      return 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400'
    case 'medium':
      return 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400'
    case 'low':
      return 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400'
    default:
      return 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300'
  }
})

const priorityLabel = computed(() => {
  switch (props.idea.priority) {
    case 'high':
      return '높음'
    case 'medium':
      return '보통'
    case 'low':
      return '낮음'
    default:
      return ''
  }
})

const platformIcon = (platform: string) => {
  // Using simple shapes as placeholders for platform icons
  // In a real app, you'd use actual platform logos
  const icons: Record<string, string> = {
    YOUTUBE: 'div',
    TIKTOK: 'div',
    INSTAGRAM: 'div',
    NAVER_CLIP: 'div'
  }
  return icons[platform] || 'div'
}

const formatDate = (dateString: string) => {
  const date = new Date(dateString)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}/${day}`
}

const handleDragStart = (event: DragEvent) => {
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('ideaId', String(props.idea.id))
  }
  emit('dragstart', event)
}
</script>

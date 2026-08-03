<template>
  <div
    class="cursor-pointer rounded-[11px] border border-line bg-surface-card p-[13px] shadow-none transition-colors hover:border-line-hover"
    draggable="true"
    @dragstart="handleDragStart"
    @click="$emit('click')"
  >
    <!-- Priority Badge -->
    <div class="flex items-center justify-between mb-2">
      <StatusPill :variant="priorityVariant">{{ priorityLabel }}</StatusPill>
      <div class="flex items-center gap-1">
        <template v-for="platform in idea.platform" :key="platform">
          <PlatformChip
            v-if="platformCode(platform)"
            :platform="platformCode(platform)!"
            size="sm"
          />
        </template>
        <button
          type="button"
          class="ml-1 rounded p-1 text-gray-400 transition-colors hover:bg-error-subtle hover:text-error-strong"
          aria-label="아이디어 삭제"
          title="아이디어 삭제"
          @click.stop="$emit('delete')"
        >
          <TrashIcon class="h-4 w-4" />
        </button>
      </div>
    </div>

    <!-- Title -->
    <h3 class="mb-2 line-clamp-2 text-h3 text-content">
      {{ idea.title }}
    </h3>

    <!-- Description -->
    <p class="mb-3 line-clamp-2 text-body-sm text-content-secondary">
      {{ idea.description }}
    </p>

    <!-- Tags -->
    <div v-if="idea.tags.length > 0" class="flex flex-wrap gap-1 mb-3">
      <span
        v-for="tag in idea.tags"
        :key="tag"
        class="rounded-md bg-surface-raised px-2 py-1 text-body-xs text-content-secondary"
      >
        #{{ tag }}
      </span>
    </div>

    <!-- Due Date -->
    <div v-if="idea.dueDate" class="flex items-center text-body-xs text-content-tertiary">
      <CalendarIcon class="w-4 h-4 mr-1" />
      {{ formatDate(idea.dueDate) }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ContentIdea } from '@/types/idea'
import { CalendarIcon, TrashIcon } from '@heroicons/vue/24/outline'
import PlatformChip from '@/components/redesign/PlatformChip.vue'
import StatusPill from '@/components/redesign/StatusPill.vue'

const props = defineProps<{
  idea: ContentIdea
}>()

const { t } = useI18n({ useScope: 'global' })

const emit = defineEmits<{
  (e: 'click'): void
  (e: 'dragstart', event: DragEvent): void
  (e: 'delete'): void
}>()

const priorityVariant = computed<'success' | 'warning' | 'error' | 'muted'>(() => {
  switch (props.idea.priority) {
    case 'high':
      return 'error'
    case 'medium':
      return 'warning'
    case 'low':
      return 'success'
    default:
      return 'muted'
  }
})

const priorityLabel = computed(() => {
  switch (props.idea.priority) {
    case 'high':
      return t('ideas.priority.high')
    case 'medium':
      return t('ideas.priority.medium')
    case 'low':
      return t('ideas.priority.low')
    default:
      return ''
  }
})

type RedesignPlatform = 'YT' | 'IG' | 'TT' | 'FB' | 'NV' | 'TH'

const platformCode = (platform: string): RedesignPlatform | undefined => ({
  YOUTUBE: 'YT',
  INSTAGRAM: 'IG',
  TIKTOK: 'TT',
  FACEBOOK: 'FB',
  NAVER_CLIP: 'NV',
  THREADS: 'TH',
}[platform] as RedesignPlatform | undefined)

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

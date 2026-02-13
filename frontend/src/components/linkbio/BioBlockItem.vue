<template>
  <div
    class="group flex items-center gap-3 rounded-lg border border-gray-200 bg-white p-3 transition-all hover:border-gray-300 dark:border-gray-700 dark:bg-gray-800 dark:hover:border-gray-600"
    :class="{ 'opacity-50': !block.isVisible }"
    draggable="true"
    @dragstart="$emit('dragstart', $event)"
    @dragend="$emit('dragend', $event)"
    @dragover.prevent
    @drop="$emit('drop', $event)"
  >
    <!-- Drag Handle -->
    <button
      class="cursor-move text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300"
      @mousedown.stop
    >
      <Bars3Icon class="h-5 w-5" />
    </button>

    <!-- Type Icon -->
    <div class="flex h-8 w-8 items-center justify-center rounded bg-gray-100 dark:bg-gray-700">
      <span class="text-sm">{{ typeIcon }}</span>
    </div>

    <!-- Content -->
    <div class="flex-1 min-w-0">
      <div v-if="block.type === 'link'" class="space-y-1">
        <input
          v-model="localTitle"
          @blur="updateBlock"
          type="text"
          placeholder="링크 제목"
          class="w-full border-0 bg-transparent p-0 text-sm font-medium text-gray-900 placeholder-gray-400 focus:ring-0 dark:text-gray-100"
        />
        <input
          v-model="localUrl"
          @blur="updateBlock"
          type="url"
          placeholder="https://"
          class="w-full border-0 bg-transparent p-0 text-xs text-gray-500 placeholder-gray-400 focus:ring-0 dark:text-gray-400"
        />
        <div class="text-xs text-gray-400 dark:text-gray-500">클릭 {{ block.clickCount }}회</div>
      </div>

      <div v-else-if="block.type === 'header'" class="space-y-1">
        <input
          v-model="localText"
          @blur="updateBlock"
          type="text"
          placeholder="헤더 텍스트"
          class="w-full border-0 bg-transparent p-0 text-sm font-semibold text-gray-900 placeholder-gray-400 focus:ring-0 dark:text-gray-100"
        />
      </div>

      <div v-else-if="block.type === 'social'" class="space-y-1">
        <select
          v-model="localPlatform"
          @change="updateBlock"
          class="w-full border-0 bg-transparent p-0 text-sm font-medium text-gray-900 focus:ring-0 dark:text-gray-100"
        >
          <option value="instagram">Instagram</option>
          <option value="twitter">Twitter</option>
          <option value="youtube">YouTube</option>
          <option value="tiktok">TikTok</option>
          <option value="facebook">Facebook</option>
        </select>
        <input
          v-model="localUrl"
          @blur="updateBlock"
          type="url"
          placeholder="https://"
          class="w-full border-0 bg-transparent p-0 text-xs text-gray-500 placeholder-gray-400 focus:ring-0 dark:text-gray-400"
        />
      </div>

      <div v-else-if="block.type === 'video'" class="space-y-1">
        <input
          v-model="localTitle"
          @blur="updateBlock"
          type="text"
          placeholder="영상 제목"
          class="w-full border-0 bg-transparent p-0 text-sm font-medium text-gray-900 placeholder-gray-400 focus:ring-0 dark:text-gray-100"
        />
        <input
          v-model="localVideoUrl"
          @blur="updateBlock"
          type="url"
          placeholder="영상 URL"
          class="w-full border-0 bg-transparent p-0 text-xs text-gray-500 placeholder-gray-400 focus:ring-0 dark:text-gray-400"
        />
      </div>

      <div v-else-if="block.type === 'text'" class="space-y-1">
        <textarea
          v-model="localContent"
          @blur="updateBlock"
          placeholder="텍스트 내용"
          rows="2"
          class="w-full resize-none border-0 bg-transparent p-0 text-sm text-gray-700 placeholder-gray-400 focus:ring-0 dark:text-gray-300"
        />
      </div>

      <div v-else-if="block.type === 'divider'" class="text-sm text-gray-500 dark:text-gray-400">
        구분선
      </div>
    </div>

    <!-- Actions -->
    <div class="flex items-center gap-2">
      <button
        @click="$emit('toggle-visibility')"
        class="text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300"
      >
        <EyeIcon v-if="block.isVisible" class="h-5 w-5" />
        <EyeSlashIcon v-else class="h-5 w-5" />
      </button>
      <button
        @click="handleDelete"
        class="text-gray-400 hover:text-red-500 dark:text-gray-500 dark:hover:text-red-400"
      >
        <TrashIcon class="h-5 w-5" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Bars3Icon, EyeIcon, EyeSlashIcon, TrashIcon } from '@heroicons/vue/24/outline'
import type { BioBlock } from '@/types/linkbio'

const props = defineProps<{
  block: BioBlock
}>()

const emit = defineEmits<{
  update: [updates: Partial<BioBlock>]
  'toggle-visibility': []
  delete: []
  dragstart: [event: DragEvent]
  dragend: [event: DragEvent]
  drop: [event: DragEvent]
}>()

const localTitle = ref('')
const localUrl = ref('')
const localText = ref('')
const localPlatform = ref('')
const localVideoUrl = ref('')
const localContent = ref('')

watch(
  () => props.block,
  (block) => {
    if (block.type === 'link') {
      localTitle.value = block.title
      localUrl.value = block.url
    } else if (block.type === 'header') {
      localText.value = block.text
    } else if (block.type === 'social') {
      localPlatform.value = block.platform
      localUrl.value = block.url
    } else if (block.type === 'video') {
      localTitle.value = block.title
      localVideoUrl.value = block.videoUrl
    } else if (block.type === 'text') {
      localContent.value = block.content
    }
  },
  { immediate: true }
)

const typeIcon = (() => {
  switch (props.block.type) {
    case 'link':
      return '🔗'
    case 'header':
      return '📝'
    case 'social':
      return '📱'
    case 'video':
      return '🎬'
    case 'divider':
      return '➖'
    case 'text':
      return '💬'
    default:
      return '❓'
  }
})()

const updateBlock = () => {
  if (props.block.type === 'link') {
    emit('update', { title: localTitle.value, url: localUrl.value })
  } else if (props.block.type === 'header') {
    emit('update', { text: localText.value })
  } else if (props.block.type === 'social') {
    emit('update', { platform: localPlatform.value, url: localUrl.value })
  } else if (props.block.type === 'video') {
    emit('update', { title: localTitle.value, videoUrl: localVideoUrl.value })
  } else if (props.block.type === 'text') {
    emit('update', { content: localContent.value })
  }
}

const handleDelete = () => {
  if (confirm('이 블록을 삭제하시겠습니까?')) {
    emit('delete')
  }
}
</script>

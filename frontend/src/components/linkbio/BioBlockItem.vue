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
      <span class="text-body">{{ typeIcon }}</span>
    </div>

    <!-- Content -->
    <div class="flex-1 min-w-0">
      <div v-if="block.type === 'link'" class="space-y-1">
        <input
          v-model="localTitle"
          type="text"
          placeholder="링크 제목"
          class="w-full border-0 bg-transparent p-0 text-body font-medium text-gray-900 placeholder-gray-400 focus:ring-0 dark:text-gray-100"
          @blur="updateBlock"
        />
        <input
          v-model="localUrl"
          type="url"
          placeholder="https://example.com/내-페이지"
          class="w-full border-0 bg-transparent p-0 text-body-xs text-gray-500 placeholder-gray-400 focus:ring-0 dark:text-gray-400"
          @blur="updateBlock"
        />
        <!-- 저장하기 전에 어디를 고쳐야 하는지 그 자리에서 알려준다. -->
        <div v-if="!isValidLinkUrl(localUrl)" class="text-body-xs text-error-strong">
          주소를 입력해주세요. 저장하려면 http:// 또는 https:// 로 시작해야 합니다.
        </div>
        <div class="text-body-xs text-gray-400 dark:text-gray-500">클릭 {{ block.clickCount }}회</div>
      </div>

      <div v-else-if="block.type === 'header'" class="space-y-1">
        <input
          v-model="localText"
          type="text"
          placeholder="헤더 텍스트"
          class="w-full border-0 bg-transparent p-0 text-body font-semibold text-gray-900 placeholder-gray-400 focus:ring-0 dark:text-gray-100"
          @blur="updateBlock"
        />
      </div>

      <div v-else-if="block.type === 'social'" class="space-y-1">
        <select
          v-model="localPlatform"
          class="w-full border-0 bg-transparent p-0 text-body font-medium text-gray-900 focus:ring-0 dark:text-gray-100"
          @change="updateBlock"
        >
          <option value="instagram">Instagram</option>
          <option value="twitter">Twitter</option>
          <option value="youtube">YouTube</option>
          <option value="tiktok">TikTok</option>
          <option value="facebook">Facebook</option>
        </select>
        <input
          v-model="localUrl"
          type="url"
          placeholder="https://"
          class="w-full border-0 bg-transparent p-0 text-body-xs text-gray-500 placeholder-gray-400 focus:ring-0 dark:text-gray-400"
          @blur="updateBlock"
        />
      </div>

      <div v-else-if="block.type === 'video'" class="space-y-1">
        <input
          v-model="localTitle"
          type="text"
          placeholder="영상 제목"
          class="w-full border-0 bg-transparent p-0 text-body font-medium text-gray-900 placeholder-gray-400 focus:ring-0 dark:text-gray-100"
          @blur="updateBlock"
        />
        <input
          v-model="localVideoUrl"
          type="url"
          placeholder="영상 URL"
          class="w-full border-0 bg-transparent p-0 text-body-xs text-gray-500 placeholder-gray-400 focus:ring-0 dark:text-gray-400"
          @blur="updateBlock"
        />
      </div>

      <div v-else-if="block.type === 'text'" class="space-y-1">
        <textarea
          v-model="localContent"
          placeholder="텍스트 내용"
          rows="2"
          class="w-full resize-none border-0 bg-transparent p-0 text-body text-gray-700 placeholder-gray-400 focus:ring-0 dark:text-gray-300"
          @blur="updateBlock"
        />
      </div>

      <div v-else-if="block.type === 'divider'" class="text-body text-gray-500 dark:text-gray-400">
        구분선
      </div>
    </div>

    <!-- Actions -->
    <div class="flex items-center gap-2">
      <button
        class="text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300"
        @click="$emit('toggle-visibility')"
      >
        <EyeIcon v-if="block.isVisible" class="h-5 w-5" />
        <EyeSlashIcon v-else class="h-5 w-5" />
      </button>
      <button
        class="text-gray-400 hover:text-error-strong dark:text-gray-500"
        @click="handleDelete"
      >
        <TrashIcon class="h-5 w-5" />
      </button>
    </div>

    <!-- 블록 삭제 확인 -->
    <ConfirmModal
      v-model="showDeleteModal"
      :title="$t('linkBioView.block.deleteTitle')"
      :message="$t('linkBioView.block.deleteMessage')"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="confirmDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Bars3Icon, EyeIcon, EyeSlashIcon, TrashIcon } from '@heroicons/vue/24/outline'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import { isValidLinkUrl } from '@/types/linkbio'
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
const showDeleteModal = ref(false)

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
  showDeleteModal.value = true
}

const confirmDelete = () => {
  emit('delete')
}
</script>

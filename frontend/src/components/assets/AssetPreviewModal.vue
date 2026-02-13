<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAssetsStore } from '@/stores/assets'
import type { Asset } from '@/types/asset'
import {
  XMarkIcon,
  ArrowDownTrayIcon,
  TrashIcon,
  TagIcon,
  VideoCameraIcon,
  PhotoIcon,
  MusicalNoteIcon,
  DocumentIcon,
  CalendarIcon,
  CubeIcon,
  ClockIcon,
  FolderIcon,
} from '@heroicons/vue/24/outline'

const props = defineProps<{
  modelValue: boolean
  asset: Asset | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'delete', id: number): void
}>()

const assetsStore = useAssetsStore()
const tagInput = ref('')

const typeIcon = computed(() => {
  if (!props.asset) return DocumentIcon
  const icons: Record<string, any> = {
    VIDEO: VideoCameraIcon,
    IMAGE: PhotoIcon,
    AUDIO: MusicalNoteIcon,
    TEMPLATE: DocumentIcon,
  }
  return icons[props.asset.type] ?? DocumentIcon
})

const typeLabel = computed(() => {
  if (!props.asset) return ''
  const labels: Record<string, string> = {
    VIDEO: '영상',
    IMAGE: '이미지',
    AUDIO: '오디오',
    TEMPLATE: '템플릿',
  }
  return labels[props.asset.type] ?? '기타'
})

const folderName = computed(() => {
  if (!props.asset || !props.asset.folderId) return '미분류'
  const folder = assetsStore.folders.find((f) => f.id === props.asset!.folderId)
  return folder?.name ?? '미분류'
})

function close() {
  emit('update:modelValue', false)
  tagInput.value = ''
}

function addTag() {
  const trimmed = tagInput.value.trim()
  if (trimmed && props.asset) {
    assetsStore.addTag(props.asset.id, trimmed)
  }
  tagInput.value = ''
}

function removeTag(tag: string) {
  if (props.asset) {
    assetsStore.removeTag(props.asset.id, tag)
  }
}

function onTagKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' || e.key === ',') {
    e.preventDefault()
    addTag()
  }
}

function handleDelete() {
  if (props.asset) {
    emit('delete', props.asset.id)
    close()
  }
}

function handleDownload() {
  if (!props.asset) return
  const link = document.createElement('a')
  link.href = props.asset.fileUrl
  link.download = props.asset.name
  link.click()
}

function formatFileSize(bytes: number): string {
  if (bytes >= 1_073_741_824) return (bytes / 1_073_741_824).toFixed(1) + ' GB'
  if (bytes >= 1_048_576) return (bytes / 1_048_576).toFixed(1) + ' MB'
  if (bytes >= 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return bytes + ' B'
}

function formatDuration(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  if (h > 0) {
    return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
  }
  return `${m}:${s.toString().padStart(2, '0')}`
}

function formatDate(dateStr: string): string {
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(dateStr))
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div
        v-if="modelValue && asset"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="asset-preview-modal-title"
      >
        <!-- Backdrop -->
        <div class="absolute inset-0 bg-black/50" aria-hidden="true" @click="close" />

        <!-- Modal -->
        <div
          class="relative flex w-full max-w-4xl flex-col overflow-hidden rounded-2xl bg-white shadow-xl dark:bg-gray-800 lg:flex-row"
          style="max-height: 90vh"
          @keydown.escape="close"
        >
          <!-- Preview Area (Left) -->
          <div class="flex w-full items-center justify-center bg-gray-100 dark:bg-gray-900 lg:w-3/5">
            <!-- Image Preview -->
            <img
              v-if="asset.type === 'IMAGE' && asset.fileUrl"
              :src="asset.fileUrl"
              :alt="asset.name"
              class="max-h-[50vh] w-full object-contain lg:max-h-[80vh]"
            />

            <!-- Video Preview -->
            <div
              v-else-if="asset.type === 'VIDEO'"
              class="flex w-full items-center justify-center"
            >
              <div v-if="asset.thumbnail" class="relative w-full">
                <img
                  :src="asset.thumbnail"
                  :alt="asset.name"
                  class="max-h-[50vh] w-full object-contain lg:max-h-[80vh]"
                />
                <div class="absolute inset-0 flex items-center justify-center">
                  <div class="rounded-full bg-black/60 p-4">
                    <VideoCameraIcon class="h-8 w-8 text-white" />
                  </div>
                </div>
              </div>
              <div v-else class="flex flex-col items-center gap-2 p-16">
                <VideoCameraIcon class="h-16 w-16 text-gray-300 dark:text-gray-600" />
                <span class="text-sm text-gray-400 dark:text-gray-500">영상 미리보기</span>
              </div>
            </div>

            <!-- Audio Preview -->
            <div
              v-else-if="asset.type === 'AUDIO'"
              class="flex flex-col items-center gap-4 p-16"
            >
              <div class="rounded-full bg-purple-100 p-6 dark:bg-purple-900/30">
                <MusicalNoteIcon class="h-12 w-12 text-purple-600 dark:text-purple-400" />
              </div>
              <span class="text-sm text-gray-500 dark:text-gray-400">
                {{ asset.duration ? formatDuration(asset.duration) : '오디오 파일' }}
              </span>
            </div>

            <!-- Template/Other Preview -->
            <div v-else class="flex flex-col items-center gap-4 p-16">
              <div v-if="asset.thumbnail">
                <img
                  :src="asset.thumbnail"
                  :alt="asset.name"
                  class="max-h-[50vh] w-full object-contain lg:max-h-[80vh]"
                />
              </div>
              <template v-else>
                <div class="rounded-full bg-orange-100 p-6 dark:bg-orange-900/30">
                  <DocumentIcon class="h-12 w-12 text-orange-600 dark:text-orange-400" />
                </div>
                <span class="text-sm text-gray-500 dark:text-gray-400">템플릿 파일</span>
              </template>
            </div>
          </div>

          <!-- Info Panel (Right) -->
          <div class="flex w-full flex-col lg:w-2/5" style="max-height: 90vh">
            <!-- Header -->
            <div class="flex items-start justify-between border-b border-gray-200 p-5 dark:border-gray-700">
              <div class="min-w-0 pr-2">
                <h2 id="asset-preview-modal-title" class="text-lg font-bold text-gray-900 dark:text-white" :title="asset.name">
                  {{ asset.name }}
                </h2>
                <p class="mt-0.5 text-sm text-gray-500 dark:text-gray-400">{{ typeLabel }}</p>
              </div>
              <button
                class="flex-shrink-0 rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
                aria-label="모달 닫기"
                @click="close"
              >
                <XMarkIcon class="h-5 w-5" />
              </button>
            </div>

            <!-- Details -->
            <div class="flex-1 overflow-y-auto p-5">
              <!-- File Info -->
              <div class="mb-5 space-y-3">
                <h3 class="text-xs font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
                  파일 정보
                </h3>

                <div class="space-y-2">
                  <div class="flex items-center gap-2 text-sm">
                    <CubeIcon class="h-4 w-4 flex-shrink-0 text-gray-400" />
                    <span class="text-gray-500 dark:text-gray-400">크기:</span>
                    <span class="font-medium text-gray-900 dark:text-gray-100">
                      {{ formatFileSize(asset.fileSize) }}
                    </span>
                  </div>

                  <div class="flex items-center gap-2 text-sm">
                    <component :is="typeIcon" class="h-4 w-4 flex-shrink-0 text-gray-400" />
                    <span class="text-gray-500 dark:text-gray-400">형식:</span>
                    <span class="font-medium text-gray-900 dark:text-gray-100">
                      {{ asset.mimeType }}
                    </span>
                  </div>

                  <div v-if="asset.width && asset.height" class="flex items-center gap-2 text-sm">
                    <PhotoIcon class="h-4 w-4 flex-shrink-0 text-gray-400" />
                    <span class="text-gray-500 dark:text-gray-400">해상도:</span>
                    <span class="font-medium text-gray-900 dark:text-gray-100">
                      {{ asset.width }} x {{ asset.height }}
                    </span>
                  </div>

                  <div v-if="asset.duration" class="flex items-center gap-2 text-sm">
                    <ClockIcon class="h-4 w-4 flex-shrink-0 text-gray-400" />
                    <span class="text-gray-500 dark:text-gray-400">재생 시간:</span>
                    <span class="font-medium text-gray-900 dark:text-gray-100">
                      {{ formatDuration(asset.duration) }}
                    </span>
                  </div>

                  <div class="flex items-center gap-2 text-sm">
                    <CalendarIcon class="h-4 w-4 flex-shrink-0 text-gray-400" />
                    <span class="text-gray-500 dark:text-gray-400">업로드:</span>
                    <span class="font-medium text-gray-900 dark:text-gray-100">
                      {{ formatDate(asset.createdAt) }}
                    </span>
                  </div>

                  <div class="flex items-center gap-2 text-sm">
                    <component :is="FolderIcon" class="h-4 w-4 flex-shrink-0 text-gray-400" />
                    <span class="text-gray-500 dark:text-gray-400">폴더:</span>
                    <span class="font-medium text-gray-900 dark:text-gray-100">
                      {{ folderName }}
                    </span>
                  </div>
                </div>
              </div>

              <!-- Tags -->
              <div class="mb-5">
                <h3 class="mb-2 text-xs font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
                  태그
                </h3>
                <div class="flex flex-wrap items-center gap-1.5">
                  <span
                    v-for="tag in asset.tags"
                    :key="tag"
                    class="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2.5 py-1 text-xs font-medium text-gray-700 dark:bg-gray-700 dark:text-gray-300"
                  >
                    <TagIcon class="h-3 w-3" />
                    {{ tag }}
                    <button
                      class="ml-0.5 text-gray-400 hover:text-red-500 dark:text-gray-500 dark:hover:text-red-400"
                      @click="removeTag(tag)"
                    >
                      <XMarkIcon class="h-3 w-3" />
                    </button>
                  </span>
                  <input
                    v-model="tagInput"
                    type="text"
                    placeholder="+ 태그 추가"
                    class="w-24 rounded-full border border-dashed border-gray-300 bg-transparent px-2.5 py-1 text-xs text-gray-600 placeholder-gray-400 focus:border-primary-500 focus:outline-none dark:border-gray-600 dark:text-gray-400 dark:placeholder-gray-500 dark:focus:border-primary-400"
                    @keydown="onTagKeydown"
                    @blur="addTag"
                  />
                </div>
              </div>

              <!-- Usage Info (mock) -->
              <div>
                <h3 class="mb-2 text-xs font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
                  사용 정보
                </h3>
                <p class="text-sm text-gray-500 dark:text-gray-400">
                  이 에셋은 아직 영상에 사용되지 않았습니다.
                </p>
              </div>
            </div>

            <!-- Actions Footer -->
            <div class="flex items-center gap-2 border-t border-gray-200 p-4 dark:border-gray-700">
              <button
                class="inline-flex flex-1 items-center justify-center gap-2 rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
                @click="handleDownload"
              >
                <ArrowDownTrayIcon class="h-4 w-4" />
                다운로드
              </button>
              <button
                class="inline-flex flex-1 items-center justify-center gap-2 rounded-lg bg-red-50 px-4 py-2 text-sm font-medium text-red-600 hover:bg-red-100 dark:bg-red-900/20 dark:text-red-400 dark:hover:bg-red-900/30"
                @click="handleDelete"
              >
                <TrashIcon class="h-4 w-4" />
                삭제
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

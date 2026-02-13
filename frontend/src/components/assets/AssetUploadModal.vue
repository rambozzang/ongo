<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAssetsStore } from '@/stores/assets'
import type { AssetFolder } from '@/types/asset'
import {
  XMarkIcon,
  CloudArrowUpIcon,
  DocumentPlusIcon,
  TagIcon,
} from '@heroicons/vue/24/outline'

defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'uploaded'): void
}>()

const assetsStore = useAssetsStore()

const isDragging = ref(false)
const selectedFiles = ref<File[]>([])
const tagInput = ref('')
const tags = ref<string[]>([])
const selectedFolderId = ref<number | null>(null)
const uploading = ref(false)
const uploadProgress = ref(0)

const folders = computed<AssetFolder[]>(() => assetsStore.folders)

const acceptedTypes = 'video/*,image/*,audio/*,.psd,.aep,.prproj,.mogrt'

function close() {
  emit('update:modelValue', false)
  resetForm()
}

function resetForm() {
  selectedFiles.value = []
  tags.value = []
  tagInput.value = ''
  selectedFolderId.value = null
  uploading.value = false
  uploadProgress.value = 0
}

function onDragEnter(e: DragEvent) {
  e.preventDefault()
  isDragging.value = true
}

function onDragLeave(e: DragEvent) {
  e.preventDefault()
  isDragging.value = false
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  isDragging.value = false
  if (e.dataTransfer?.files) {
    addFiles(Array.from(e.dataTransfer.files))
  }
}

function onFileInput(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files) {
    addFiles(Array.from(input.files))
  }
  input.value = ''
}

function addFiles(files: File[]) {
  selectedFiles.value.push(...files)
}

function removeFile(index: number) {
  selectedFiles.value.splice(index, 1)
}

function addTag() {
  const trimmed = tagInput.value.trim()
  if (trimmed && !tags.value.includes(trimmed)) {
    tags.value.push(trimmed)
  }
  tagInput.value = ''
}

function removeTagItem(tag: string) {
  tags.value = tags.value.filter((t) => t !== tag)
}

function onTagKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' || e.key === ',') {
    e.preventDefault()
    addTag()
  }
  if (e.key === 'Backspace' && tagInput.value === '' && tags.value.length > 0) {
    tags.value.pop()
  }
}

function formatFileSize(bytes: number): string {
  if (bytes >= 1_073_741_824) return (bytes / 1_073_741_824).toFixed(1) + ' GB'
  if (bytes >= 1_048_576) return (bytes / 1_048_576).toFixed(1) + ' MB'
  if (bytes >= 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return bytes + ' B'
}

async function handleUpload() {
  if (selectedFiles.value.length === 0) return

  uploading.value = true
  uploadProgress.value = 0

  const totalFiles = selectedFiles.value.length
  for (let i = 0; i < totalFiles; i++) {
    const file = selectedFiles.value[i]
    // Simulate upload delay
    await new Promise((resolve) => setTimeout(resolve, 300 + Math.random() * 500))
    assetsStore.uploadAsset(file, selectedFolderId.value, [...tags.value])
    uploadProgress.value = Math.round(((i + 1) / totalFiles) * 100)
  }

  uploading.value = false
  emit('uploaded')
  close()
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div
        v-if="modelValue"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="asset-upload-modal-title"
      >
        <!-- Backdrop -->
        <div
          class="absolute inset-0 bg-black/50"
          aria-hidden="true"
          @click="close"
        />

        <!-- Modal -->
        <div
          class="relative w-full max-w-xl rounded-2xl bg-white p-6 shadow-xl dark:bg-gray-800"
          @keydown.escape="close"
        >
          <!-- Header -->
          <div class="mb-5 flex items-center justify-between">
            <h2 id="asset-upload-modal-title" class="text-lg font-bold text-gray-900 dark:text-white">
              에셋 업로드
            </h2>
            <button
              class="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
              aria-label="모달 닫기"
              @click="close"
            >
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <!-- Drop Zone -->
          <div
            class="mb-4 flex flex-col items-center justify-center rounded-xl border-2 border-dashed p-8 transition-colors"
            :class="
              isDragging
                ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                : 'border-gray-300 bg-gray-50 hover:border-gray-400 dark:border-gray-600 dark:bg-gray-900 dark:hover:border-gray-500'
            "
            @dragenter="onDragEnter"
            @dragover.prevent
            @dragleave="onDragLeave"
            @drop="onDrop"
          >
            <CloudArrowUpIcon
              class="mb-3 h-10 w-10"
              :class="isDragging ? 'text-primary-500' : 'text-gray-400 dark:text-gray-500'"
            />
            <p class="mb-1 text-sm font-medium text-gray-700 dark:text-gray-300">
              파일을 여기에 드래그하거나
            </p>
            <label
              class="cursor-pointer text-sm font-medium text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
            >
              파일 선택
              <input
                type="file"
                multiple
                :accept="acceptedTypes"
                class="hidden"
                @change="onFileInput"
              />
            </label>
            <p class="mt-2 text-xs text-gray-400 dark:text-gray-500">
              영상, 이미지, 오디오, 템플릿 파일 지원
            </p>
          </div>

          <!-- Selected Files -->
          <div
            v-if="selectedFiles.length > 0"
            class="mb-4 max-h-36 space-y-2 overflow-y-auto"
          >
            <div
              v-for="(file, i) in selectedFiles"
              :key="i"
              class="flex items-center justify-between rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-900"
            >
              <div class="flex min-w-0 items-center gap-2">
                <DocumentPlusIcon class="h-4 w-4 flex-shrink-0 text-gray-400" />
                <span class="truncate text-sm text-gray-700 dark:text-gray-300">
                  {{ file.name }}
                </span>
                <span class="flex-shrink-0 text-xs text-gray-400 dark:text-gray-500">
                  {{ formatFileSize(file.size) }}
                </span>
              </div>
              <button
                class="ml-2 flex-shrink-0 rounded p-0.5 text-gray-400 hover:text-red-500 dark:hover:text-red-400"
                @click="removeFile(i)"
              >
                <XMarkIcon class="h-4 w-4" />
              </button>
            </div>
          </div>

          <!-- Folder Selection -->
          <div class="mb-4">
            <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
              저장 폴더
            </label>
            <select
              v-model="selectedFolderId"
              class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            >
              <option :value="null">전체 (루트)</option>
              <option v-for="folder in folders" :key="folder.id" :value="folder.id">
                {{ folder.name }}
              </option>
            </select>
          </div>

          <!-- Tag Input -->
          <div class="mb-5">
            <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
              태그
            </label>
            <div
              class="flex flex-wrap items-center gap-1.5 rounded-lg border border-gray-300 bg-white px-3 py-2 focus-within:border-primary-500 focus-within:ring-1 focus-within:ring-primary-500 dark:border-gray-600 dark:bg-gray-900"
            >
              <span
                v-for="tag in tags"
                :key="tag"
                class="inline-flex items-center gap-1 rounded-full bg-primary-100 px-2 py-0.5 text-xs font-medium text-primary-700 dark:bg-primary-900/30 dark:text-primary-400"
              >
                <TagIcon class="h-3 w-3" />
                {{ tag }}
                <button
                  class="ml-0.5 hover:text-primary-900 dark:hover:text-primary-200"
                  @click="removeTagItem(tag)"
                >
                  <XMarkIcon class="h-3 w-3" />
                </button>
              </span>
              <input
                v-model="tagInput"
                type="text"
                placeholder="태그 입력 후 Enter"
                class="min-w-[120px] flex-1 border-0 bg-transparent p-0 text-sm text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-0 dark:text-gray-100 dark:placeholder-gray-500"
                @keydown="onTagKeydown"
                @blur="addTag"
              />
            </div>
          </div>

          <!-- Upload Progress -->
          <div v-if="uploading" class="mb-4">
            <div class="mb-1 flex items-center justify-between text-sm">
              <span class="text-gray-600 dark:text-gray-400">업로드 중...</span>
              <span class="font-medium text-gray-900 dark:text-white">{{ uploadProgress }}%</span>
            </div>
            <div class="h-2 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
              <div
                class="h-full rounded-full bg-primary-600 transition-all duration-300"
                :style="{ width: uploadProgress + '%' }"
              />
            </div>
          </div>

          <!-- Actions -->
          <div class="flex items-center justify-end gap-3">
            <button
              class="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
              :disabled="uploading"
              @click="close"
            >
              취소
            </button>
            <button
              class="rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50"
              :disabled="selectedFiles.length === 0 || uploading"
              @click="handleUpload"
            >
              {{ uploading ? '업로드 중...' : `업로드 (${selectedFiles.length}개)` }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

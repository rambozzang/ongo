<script setup lang="ts">
import { ref, computed } from 'vue'
import { PlusIcon, TrashIcon, ArrowDownTrayIcon, EyeIcon, PhotoIcon, VideoCameraIcon, DocumentIcon } from '@heroicons/vue/24/outline'
import type { BrandAsset } from '@/types/brandkit'
import { assetsApi } from '@/api/assets'

const props = defineProps<{
  assets: BrandAsset[]
}>()

const emit = defineEmits<{
  add: [asset: Omit<BrandAsset, 'id'>]
  remove: [id: number]
}>()

const selectedAsset = ref<BrandAsset | null>(null)
const filterType = ref<string>('all')
const fileInputRef = ref<HTMLInputElement | null>(null)
const uploading = ref(false)

const assetTypeLabels: Record<BrandAsset['type'], string> = {
  logo: '로고',
  watermark: '워터마크',
  intro: '인트로',
  outro: '아웃트로',
  overlay: '오버레이',
  thumbnail_template: '썸네일 템플릿',
}

const assetTypes = [
  { value: 'all', label: '전체' },
  { value: 'logo', label: '로고' },
  { value: 'watermark', label: '워터마크' },
  { value: 'intro', label: '인트로' },
  { value: 'outro', label: '아웃트로' },
  { value: 'overlay', label: '오버레이' },
  { value: 'thumbnail_template', label: '썸네일 템플릿' },
]

const filteredAssets = computed(() => {
  if (filterType.value === 'all') {
    return props.assets
  }
  return props.assets.filter(asset => asset.type === filterType.value)
})

function handleUpload() {
  fileInputRef.value?.click()
}

async function handleFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  uploading.value = true
  try {
    const response = await assetsApi.upload(file, 'brand-kit')
    emit('add', {
      name: response.filename || file.name,
      type: 'logo',
      url: response.fileUrl,
      format: file.name.split('.').pop()?.toUpperCase() || 'PNG',
      size: formatFileSize(file.size),
      uploadedAt: new Date().toISOString(),
    })
  } catch (e) {
    alert('에셋 업로드에 실패했습니다.')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

function formatFileSize(bytes: number): string {
  if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(1)} MB`
  return `${(bytes / 1024).toFixed(0)} KB`
}

function handleRemove(id: number) {
  if (confirm('이 에셋을 삭제하시겠습니까?')) {
    emit('remove', id)
  }
}

function handlePreview(asset: BrandAsset) {
  selectedAsset.value = asset
}

function closePreview() {
  selectedAsset.value = null
}

function handleDownload(asset: BrandAsset) {
  // Mock download
  alert(`${asset.name} 다운로드를 시작합니다.`)
}

function getAssetIcon(format: string) {
  const imageFormats = ['PNG', 'JPG', 'JPEG', 'GIF', 'SVG', 'WEBP']
  const videoFormats = ['MP4', 'MOV', 'AVI', 'WEBM']

  if (imageFormats.includes(format.toUpperCase())) {
    return PhotoIcon
  } else if (videoFormats.includes(format.toUpperCase())) {
    return VideoCameraIcon
  } else {
    return DocumentIcon
  }
}

function formatDate(dateString: string) {
  const date = new Date(dateString)
  return date.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })
}
</script>

<template>
  <div class="space-y-4">
    <!-- Filter -->
    <div class="flex items-center gap-2">
      <label class="text-sm font-medium text-gray-700 dark:text-gray-300">유형:</label>
      <select
        v-model="filterType"
        class="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-sm focus:ring-2 focus:ring-blue-500 focus:border-transparent"
      >
        <option v-for="type in assetTypes" :key="type.value" :value="type.value">
          {{ type.label }}
        </option>
      </select>
    </div>

    <!-- Asset Grid -->
    <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
      <div
        v-for="asset in filteredAssets"
        :key="asset.id"
        class="group bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-4 hover:shadow-md transition-shadow"
      >
        <div class="space-y-3">
          <!-- Thumbnail -->
          <div class="relative aspect-square bg-gray-100 dark:bg-gray-900 rounded-lg flex items-center justify-center overflow-hidden">
            <component
              :is="getAssetIcon(asset.format)"
              class="w-12 h-12 text-gray-400 dark:text-gray-500"
            />
            <div class="absolute inset-0 bg-black/0 group-hover:bg-black/10 transition-colors flex items-center justify-center">
              <button
                @click="handlePreview(asset)"
                class="opacity-0 group-hover:opacity-100 p-2 bg-white dark:bg-gray-800 rounded-full shadow-lg transition-opacity"
                title="미리보기"
              >
                <EyeIcon class="w-5 h-5 text-gray-700 dark:text-gray-300" />
              </button>
            </div>
          </div>

          <!-- Asset Info -->
          <div class="space-y-1">
            <h4 class="font-medium text-gray-900 dark:text-gray-100 text-sm truncate" :title="asset.name">
              {{ asset.name }}
            </h4>
            <div class="flex items-center gap-1">
              <span class="px-2 py-0.5 bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 text-xs rounded-full">
                {{ assetTypeLabels[asset.type] }}
              </span>
            </div>
            <p class="text-xs text-gray-600 dark:text-gray-400">
              {{ asset.format }} · {{ asset.size }}
            </p>
          </div>

          <!-- Actions -->
          <div class="flex gap-1 pt-2 border-t border-gray-200 dark:border-gray-700">
            <button
              @click="handleDownload(asset)"
              class="flex-1 flex items-center justify-center gap-1 px-2 py-1.5 bg-gray-100 dark:bg-gray-700 hover:bg-gray-200 dark:hover:bg-gray-600 rounded text-xs font-medium text-gray-700 dark:text-gray-300 transition-colors"
              title="다운로드"
            >
              <ArrowDownTrayIcon class="w-4 h-4" />
              <span>다운로드</span>
            </button>
            <button
              @click="handleRemove(asset.id)"
              class="p-1.5 bg-gray-100 dark:bg-gray-700 hover:bg-red-50 dark:hover:bg-red-900/30 rounded transition-colors"
              title="삭제"
            >
              <TrashIcon class="w-4 h-4 text-gray-600 dark:text-gray-300" />
            </button>
          </div>
        </div>
      </div>

      <!-- Upload Button -->
      <button
        @click="handleUpload"
        class="bg-white dark:bg-gray-800 rounded-lg border-2 border-dashed border-gray-300 dark:border-gray-600 p-4 hover:border-blue-400 dark:hover:border-blue-600 hover:bg-blue-50 dark:hover:bg-blue-900/10 transition-colors flex flex-col items-center justify-center gap-2 aspect-square"
      >
        <PlusIcon class="w-8 h-8 text-gray-400 dark:text-gray-500" />
        <span class="text-sm font-medium text-gray-600 dark:text-gray-400">에셋 업로드</span>
      </button>
    </div>

    <!-- Preview Modal -->
    <div
      v-if="selectedAsset"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-label="에셋 미리보기"
      @click="closePreview"
    >
      <div
        class="bg-white dark:bg-gray-800 rounded-lg shadow-xl max-w-3xl w-full mx-4 overflow-hidden"
        @click.stop
        @keydown.escape="closePreview"
      >
        <div class="p-6 border-b border-gray-200 dark:border-gray-700">
          <div class="flex items-start justify-between">
            <div>
              <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
                {{ selectedAsset.name }}
              </h3>
              <div class="flex items-center gap-2 mt-2">
                <span class="px-2 py-0.5 bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 text-xs rounded-full">
                  {{ assetTypeLabels[selectedAsset.type] }}
                </span>
                <span class="text-sm text-gray-600 dark:text-gray-400">
                  {{ selectedAsset.format }} · {{ selectedAsset.size }}
                </span>
              </div>
            </div>
            <button
              @click="closePreview"
              class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
              aria-label="모달 닫기"
            >
              <svg class="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>
        <div class="p-6">
          <div class="bg-gray-100 dark:bg-gray-900 rounded-lg aspect-video flex items-center justify-center">
            <component
              :is="getAssetIcon(selectedAsset.format)"
              class="w-20 h-20 text-gray-400 dark:text-gray-500"
            />
          </div>
          <div class="mt-4 space-y-2">
            <p class="text-sm text-gray-600 dark:text-gray-400">
              <span class="font-medium">업로드 일자:</span> {{ formatDate(selectedAsset.uploadedAt) }}
            </p>
            <p class="text-sm text-gray-600 dark:text-gray-400">
              <span class="font-medium">파일 경로:</span> {{ selectedAsset.url }}
            </p>
          </div>
        </div>
        <div class="p-6 border-t border-gray-200 dark:border-gray-700 flex gap-2">
          <button
            @click="handleDownload(selectedAsset)"
            class="flex-1 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-md transition-colors flex items-center justify-center gap-2"
          >
            <ArrowDownTrayIcon class="w-5 h-5" />
            <span>다운로드</span>
          </button>
          <button
            @click="closePreview"
            class="px-4 py-2 bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-900 dark:text-gray-100 font-medium rounded-md transition-colors"
          >
            닫기
          </button>
        </div>
      </div>
    </div>

    <input
      ref="fileInputRef"
      type="file"
      class="hidden"
      accept="image/*,video/*"
      @change="handleFileSelected"
    />
  </div>
</template>

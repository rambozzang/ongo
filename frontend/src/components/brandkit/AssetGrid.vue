<script setup lang="ts">
import { ref, computed } from 'vue'
import { PlusIcon, TrashIcon, ArrowDownTrayIcon, EyeIcon, PhotoIcon, VideoCameraIcon, DocumentIcon } from '@heroicons/vue/24/outline'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import type { BrandAsset } from '@/types/brandkit'
import { assetsApi } from '@/api/assets'
import { useNotification } from '@/composables/useNotification'
import { useLocale } from '@/composables/useLocale'

const props = defineProps<{
  assets: BrandAsset[]
}>()

const notify = useNotification()
const { t } = useLocale()

const emit = defineEmits<{
  add: [asset: Omit<BrandAsset, 'id'>]
  remove: [id: number]
}>()

const selectedAsset = ref<BrandAsset | null>(null)
const filterType = ref<string>('all')
const fileInputRef = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const showDeleteModal = ref(false)
const deleteTargetId = ref<number | null>(null)

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
      /*
       * **원본 에셋 id 를 함께 넘긴다.**
       *
       * URL 문자열만 복사해 두면 7 일 뒤 서명이 만료돼 로고가 깨진다. id 가 있으면 서버가
       * 조회할 때마다 소유권을 확인하고 저장 키로 URL 을 새로 발급한다.
       */
      assetId: response.id,
      url: response.fileUrl,
      format: file.name.split('.').pop()?.toUpperCase() || 'PNG',
      size: formatFileSize(file.size),
      uploadedAt: new Date().toISOString(),
    })
  } catch {
    notify.error(t('brandKit.assets.uploadFailed'))
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
  deleteTargetId.value = id
  showDeleteModal.value = true
}

function confirmRemove() {
  if (deleteTargetId.value === null) return
  emit('remove', deleteTargetId.value)
  deleteTargetId.value = null
}

function handlePreview(asset: BrandAsset) {
  selectedAsset.value = asset
}

function closePreview() {
  selectedAsset.value = null
}

function handleDownload(asset: BrandAsset) {
  if (!asset.url) {
    notify.error(t('brandKit.assets.downloadFailed'))
    return
  }

  const link = document.createElement('a')
  link.href = asset.url
  link.download = asset.name
  link.target = '_blank'
  link.rel = 'noopener noreferrer'
  document.body.appendChild(link)
  link.click()
  link.remove()
  notify.info(t('brandKit.assets.downloadStarted', { name: asset.name }))
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
      <label class="text-body font-medium text-gray-700 dark:text-gray-300">유형:</label>
      <select
        v-model="filterType"
        class="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 text-body focus:ring-2 focus:ring-primary-500 focus:border-transparent"
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
                class="opacity-0 group-hover:opacity-100 p-2 bg-white dark:bg-gray-800 rounded-full shadow-lg transition-opacity"
                title="미리보기"
                @click="handlePreview(asset)"
              >
                <EyeIcon class="w-5 h-5 text-gray-700 dark:text-gray-300" />
              </button>
            </div>
          </div>

          <!-- Asset Info -->
          <div class="space-y-1">
            <h4 class="font-medium text-gray-900 dark:text-gray-100 text-body truncate" :title="asset.name">
              {{ asset.name }}
            </h4>
            <div class="flex items-center gap-1">
              <span class="px-2 py-0.5 bg-info-subtle text-info-strong text-body-xs rounded-full">
                {{ assetTypeLabels[asset.type] }}
              </span>
            </div>
            <p class="text-body-xs text-gray-600 dark:text-gray-400">
              {{ asset.format }} · {{ asset.size }}
            </p>
          </div>

          <!-- Actions -->
          <div class="flex gap-1 pt-2 border-t border-gray-200 dark:border-gray-700">
            <button
              class="flex-1 flex items-center justify-center gap-1 px-2 py-1.5 bg-gray-100 dark:bg-gray-700 hover:bg-gray-200 dark:hover:bg-gray-600 rounded text-body-xs font-medium text-gray-700 dark:text-gray-300 transition-colors"
              title="다운로드"
              @click="handleDownload(asset)"
            >
              <ArrowDownTrayIcon class="w-4 h-4" />
              <span>다운로드</span>
            </button>
            <button
              class="p-1.5 bg-gray-100 dark:bg-gray-700 hover:bg-error-subtle rounded transition-colors"
              title="삭제"
              @click="handleRemove(asset.id)"
            >
              <TrashIcon class="w-4 h-4 text-gray-600 dark:text-gray-300" />
            </button>
          </div>
        </div>
      </div>

      <!-- Upload Button -->
      <button
        class="bg-white dark:bg-gray-800 rounded-lg border-2 border-dashed border-gray-300 dark:border-gray-600 p-4 hover:border-primary-400 dark:hover:border-primary-600 hover:bg-primary-50 dark:hover:bg-primary-900/10 transition-colors flex flex-col items-center justify-center gap-2 aspect-square"
        @click="handleUpload"
      >
        <PlusIcon class="w-8 h-8 text-gray-400 dark:text-gray-500" />
        <span class="text-body font-medium text-gray-600 dark:text-gray-400">에셋 업로드</span>
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
              <h3 class="text-title font-semibold text-gray-900 dark:text-gray-100">
                {{ selectedAsset.name }}
              </h3>
              <div class="flex items-center gap-2 mt-2">
                <span class="px-2 py-0.5 bg-info-subtle text-info-strong text-body-xs rounded-full">
                  {{ assetTypeLabels[selectedAsset.type] }}
                </span>
                <span class="text-body text-gray-600 dark:text-gray-400">
                  {{ selectedAsset.format }} · {{ selectedAsset.size }}
                </span>
              </div>
            </div>
            <button
              class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
              aria-label="모달 닫기"
              @click="closePreview"
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
            <p class="text-body text-gray-600 dark:text-gray-400">
              <span class="font-medium">업로드 일자:</span> {{ formatDate(selectedAsset.uploadedAt) }}
            </p>
            <p class="text-body text-gray-600 dark:text-gray-400">
              <span class="font-medium">파일 경로:</span> {{ selectedAsset.url }}
            </p>
          </div>
        </div>
        <div class="p-6 border-t border-gray-200 dark:border-gray-700 flex gap-2">
          <button
            class="flex-1 px-4 py-2 bg-primary-600 hover:bg-primary-700 text-white font-medium rounded-md transition-colors flex items-center justify-center gap-2"
            @click="handleDownload(selectedAsset)"
          >
            <ArrowDownTrayIcon class="w-5 h-5" />
            <span>다운로드</span>
          </button>
          <button
            class="px-4 py-2 bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 text-gray-900 dark:text-gray-100 font-medium rounded-md transition-colors"
            @click="closePreview"
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

    <!-- 에셋 삭제 확인 -->
    <ConfirmModal
      v-model="showDeleteModal"
      :title="$t('brandKit.assets.deleteTitle')"
      :message="$t('brandKit.assets.deleteMessage')"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="confirmRemove"
      @cancel="deleteTargetId = null"
    />
  </div>
</template>

<template>
  <div class="card">
    <!-- Header -->
    <div class="mb-4 flex items-center justify-between">
      <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
        {{ t('video.thumbnail.title') }}
      </h3>
      <button
        class="btn-secondary inline-flex items-center gap-1.5 text-sm"
        :disabled="uploading"
        @click="triggerFileInput"
      >
        <ArrowUpTrayIcon class="h-4 w-4" />
        {{ t('video.thumbnail.uploadCustom') }}
      </button>
    </div>

    <!-- Hidden file input for custom thumbnail upload -->
    <input
      ref="fileInputRef"
      type="file"
      accept="image/jpeg,image/png,image/webp"
      class="hidden"
      @change="handleFileSelect"
    />

    <!-- Empty State -->
    <div
      v-if="thumbnails.length === 0 && !uploading"
      class="flex flex-col items-center justify-center rounded-lg border-2 border-dashed border-gray-300 py-12 dark:border-gray-600"
    >
      <PhotoIcon class="mb-3 h-10 w-10 text-gray-400 dark:text-gray-500" />
      <p class="text-sm text-gray-500 dark:text-gray-400">
        {{ t('video.thumbnail.empty') }}
      </p>
    </div>

    <!-- Thumbnail Grid -->
    <div v-else class="grid grid-cols-2 gap-3 tablet:grid-cols-3 desktop:grid-cols-4">
      <button
        v-for="(thumb, index) in thumbnails"
        :key="index"
        class="group relative aspect-video overflow-hidden rounded-lg border-2 transition-all focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900"
        :class="
          index === selectedIndex
            ? 'border-primary-500 ring-2 ring-primary-500/30 shadow-lg'
            : 'border-gray-200 dark:border-gray-700 hover:border-primary-300 dark:hover:border-primary-600'
        "
        :aria-label="t('video.thumbnail.selectAria', { index: index + 1 })"
        :aria-pressed="index === selectedIndex"
        @click="handleSelect(index)"
      >
        <!-- Thumbnail Image -->
        <img
          :src="thumb"
          :alt="t('video.thumbnail.imageAlt', { index: index + 1 })"
          class="h-full w-full object-cover transition-transform group-hover:scale-105"
          loading="lazy"
        />

        <!-- Selected Overlay -->
        <div
          v-if="index === selectedIndex"
          class="absolute inset-0 flex items-center justify-center bg-primary-500/20"
        >
          <div class="rounded-full bg-primary-500 p-1.5 shadow-lg">
            <CheckIcon class="h-4 w-4 text-white" />
          </div>
        </div>

        <!-- Hover Overlay -->
        <div
          v-else
          class="absolute inset-0 flex items-center justify-center bg-black/0 transition-colors group-hover:bg-black/20"
        >
          <span class="rounded-full bg-white/80 px-2 py-1 text-xs font-medium text-gray-700 opacity-0 transition-opacity group-hover:opacity-100 dark:bg-gray-800/80 dark:text-gray-300">
            {{ t('video.thumbnail.select') }}
          </span>
        </div>

        <!-- Index Badge -->
        <span class="absolute left-1.5 top-1.5 rounded bg-black/50 px-1.5 py-0.5 text-[10px] font-medium text-white backdrop-blur-sm">
          {{ index + 1 }}
        </span>
      </button>
    </div>

    <!-- Upload Progress -->
    <div v-if="uploading" class="mt-4 flex items-center gap-3">
      <div class="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-primary-600 dark:border-gray-600" />
      <span class="text-sm text-gray-600 dark:text-gray-400">{{ t('video.thumbnail.uploading') }}</span>
    </div>

    <!-- Error Message -->
    <div
      v-if="errorMessage"
      class="mt-4 flex items-center gap-2 rounded-lg bg-red-50 px-4 py-3 dark:bg-red-900/20"
    >
      <ExclamationTriangleIcon class="h-4 w-4 flex-shrink-0 text-red-500" />
      <p class="text-sm text-red-700 dark:text-red-400">{{ errorMessage }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ArrowUpTrayIcon,
  PhotoIcon,
  CheckIcon,
  ExclamationTriangleIcon,
} from '@heroicons/vue/24/outline'
import { videoApi } from '@/api/video'

// ---- i18n ----

const { t } = useI18n({ useScope: 'global' })

// ---- Props ----

const props = defineProps<{
  thumbnails: string[]
  selectedIndex: number
  videoId: number
}>()

// ---- Emits ----

const emit = defineEmits<{
  select: [index: number]
  upload: [file: File]
}>()

// ---- Refs ----

const fileInputRef = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const errorMessage = ref<string | null>(null)

// ---- Methods ----

/** Open the file picker dialog */
function triggerFileInput() {
  fileInputRef.value?.click()
}

/** Handle thumbnail selection */
async function handleSelect(index: number) {
  if (index === props.selectedIndex) return

  errorMessage.value = null
  try {
    await videoApi.selectThumbnail(props.videoId, index)
    emit('select', index)
  } catch (err) {
    errorMessage.value = t('video.thumbnail.selectError')
  }
}

/** Handle custom thumbnail file selection */
async function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  // Validate file type
  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp']
  if (!allowedTypes.includes(file.type)) {
    errorMessage.value = t('video.thumbnail.invalidType')
    input.value = ''
    return
  }

  // Validate file size (max 5MB)
  const maxSize = 5 * 1024 * 1024
  if (file.size > maxSize) {
    errorMessage.value = t('video.thumbnail.tooLarge')
    input.value = ''
    return
  }

  errorMessage.value = null
  uploading.value = true

  try {
    await videoApi.uploadCustomThumbnail(props.videoId, file)
    emit('upload', file)
  } catch (err) {
    errorMessage.value = t('video.thumbnail.uploadError')
  } finally {
    uploading.value = false
    input.value = ''
  }
}
</script>

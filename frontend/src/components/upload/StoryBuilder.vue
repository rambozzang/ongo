<template>
  <div class="rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ t('video.image.story') }}
      </h3>
      <span class="text-xs text-gray-500 dark:text-gray-400">
        {{ t('video.image.countLabel', { count: images.length, max: MAX_IMAGES }) }}
      </span>
    </div>

    <!-- Image Grid -->
    <div class="grid grid-cols-2 gap-3 tablet:grid-cols-3 desktop:grid-cols-5">
      <div
        v-for="(file, index) in images"
        :key="index"
        class="group relative aspect-square rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden bg-gray-100 dark:bg-gray-700"
        :class="dragIndex === index && 'ring-2 ring-primary-500 opacity-50'"
        draggable="true"
        @dragstart="onItemDragStart(index, $event)"
        @dragover.prevent="onItemDragOver(index)"
        @dragend="onItemDragEnd"
      >
        <!-- Image Preview -->
        <img
          :src="previews[index]"
          :alt="file.name"
          class="w-full h-full object-cover"
        />

        <!-- Order badge -->
        <div
          class="absolute top-1.5 left-1.5 flex h-5 w-5 items-center justify-center rounded-full bg-black/60 text-[10px] font-semibold text-white"
        >
          {{ index + 1 }}
        </div>

        <!-- Hover overlay with actions -->
        <div
          class="absolute inset-0 flex flex-col items-center justify-center gap-1.5 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity"
        >
          <!-- Move up -->
          <button
            v-if="index > 0"
            type="button"
            class="rounded-full bg-white/90 p-1 text-gray-700 hover:bg-white transition-colors"
            @click.stop="moveImage(index, index - 1)"
          >
            <ChevronUpIcon class="h-4 w-4" />
          </button>

          <!-- Remove -->
          <button
            type="button"
            class="rounded-full bg-red-500/90 p-1 text-white hover:bg-red-600 transition-colors"
            @click.stop="removeImage(index)"
          >
            <XMarkIcon class="h-4 w-4" />
          </button>

          <!-- Move down -->
          <button
            v-if="index < images.length - 1"
            type="button"
            class="rounded-full bg-white/90 p-1 text-gray-700 hover:bg-white transition-colors"
            @click.stop="moveImage(index, index + 1)"
          >
            <ChevronDownIcon class="h-4 w-4" />
          </button>
        </div>

        <!-- File info on hover -->
        <div
          class="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/60 to-transparent px-2 py-1.5 opacity-0 group-hover:opacity-100 transition-opacity"
        >
          <p class="truncate text-[10px] text-white">{{ file.name }}</p>
          <p class="text-[10px] text-white/70">{{ formatFileSize(file.size) }}</p>
        </div>
      </div>

      <!-- Add more button -->
      <button
        v-if="images.length < MAX_IMAGES"
        type="button"
        class="flex aspect-square flex-col items-center justify-center rounded-lg border-2 border-dashed border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800/50 hover:border-primary-400 dark:hover:border-primary-500 hover:bg-primary-50 dark:hover:bg-primary-900/10 transition-colors"
        @click="openFilePicker"
      >
        <PlusIcon class="h-6 w-6 text-gray-400 dark:text-gray-500" />
        <span class="mt-1 text-xs text-gray-500 dark:text-gray-400">{{ t('video.image.add') }}</span>
      </button>
    </div>

    <!-- Hidden file input -->
    <input
      ref="fileInput"
      type="file"
      class="hidden"
      multiple
      accept=".jpg,.jpeg,.png,.webp,.gif,.heic"
      @change="onFileSelect"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue'
import { PlusIcon, XMarkIcon, ChevronUpIcon, ChevronDownIcon } from '@heroicons/vue/24/outline'
import { useLocale } from '@/composables/useLocale'

const { t } = useLocale()

const MAX_IMAGES = 10
const IMAGE_MAX_SIZE = 50 * 1024 * 1024 // 50MB
const IMAGE_MIMES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/heic']
const IMAGE_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.webp', '.gif', '.heic']

const props = defineProps<{
  images: File[]
}>()

const emit = defineEmits<{
  'update:images': [files: File[]]
  remove: [index: number]
  add: [files: File[]]
}>()

const fileInput = ref<HTMLInputElement>()
const previews = ref<string[]>([])
const dragIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)

// Generate preview URLs when images change
watch(
  () => props.images,
  (newImages) => {
    // Revoke old URLs
    previews.value.forEach((url) => URL.revokeObjectURL(url))
    // Create new URLs
    previews.value = newImages.map((file) => URL.createObjectURL(file))
  },
  { immediate: true },
)

onUnmounted(() => {
  previews.value.forEach((url) => URL.revokeObjectURL(url))
})

function openFilePicker() {
  fileInput.value?.click()
}

function onFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (!files || files.length === 0) return

  const newFiles: File[] = []
  const remaining = MAX_IMAGES - props.images.length

  for (let i = 0; i < Math.min(files.length, remaining); i++) {
    const file = files[i]
    const ext = '.' + file.name.split('.').pop()?.toLowerCase()
    if (!IMAGE_EXTENSIONS.includes(ext) && !IMAGE_MIMES.includes(file.type)) continue
    if (file.size > IMAGE_MAX_SIZE) continue
    newFiles.push(file)
  }

  if (newFiles.length > 0) {
    emit('add', newFiles)
    emit('update:images', [...props.images, ...newFiles])
  }

  input.value = ''
}

function removeImage(index: number) {
  emit('remove', index)
  const updated = [...props.images]
  updated.splice(index, 1)
  emit('update:images', updated)
}

function moveImage(fromIndex: number, toIndex: number) {
  const updated = [...props.images]
  const [moved] = updated.splice(fromIndex, 1)
  updated.splice(toIndex, 0, moved)
  emit('update:images', updated)
}

// Drag-to-reorder
function onItemDragStart(index: number, event: DragEvent) {
  dragIndex.value = index
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

function onItemDragOver(index: number) {
  if (dragIndex.value === null || dragIndex.value === index) return
  dragOverIndex.value = index
  moveImage(dragIndex.value, index)
  dragIndex.value = index
}

function onItemDragEnd() {
  dragIndex.value = null
  dragOverIndex.value = null
}

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}
</script>

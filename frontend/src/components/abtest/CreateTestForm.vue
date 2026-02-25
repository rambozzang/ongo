<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { SparklesIcon } from '@heroicons/vue/24/outline'
import VariantEditor from './VariantEditor.vue'
import type { AbTestType, VariantLabel, VideoForAbTest } from '@/types/abtest'

const props = defineProps<{
  videos: VideoForAbTest[]
  processing: boolean
}>()

const emit = defineEmits<{
  create: [data: {
    videoId: number
    type: AbTestType
    variants: { label: VariantLabel; value: string }[]
    durationHours: number
  }]
}>()

const { t } = useI18n({ useScope: 'global' })

const selectedVideoId = ref<number | null>(null)
const testType = ref<AbTestType>('THUMBNAIL')
const durationHours = ref(24)
const variants = ref<{ label: VariantLabel; value: string }[]>([
  { label: 'A', value: '' },
  { label: 'B', value: '' },
])

const testTypes: { value: AbTestType; label: string }[] = [
  { value: 'THUMBNAIL', label: t('abTest.typeThumbnail') },
  { value: 'TITLE', label: t('abTest.typeTitle') },
  { value: 'DESCRIPTION', label: t('abTest.typeDescription') },
]

const selectedVideo = computed(() =>
  props.videos.find(v => v.id === selectedVideoId.value) ?? null
)

const canSubmit = computed(() => {
  if (!selectedVideoId.value) return false
  if (variants.value.some(v => !v.value.trim())) return false
  if (durationHours.value < 1) return false
  return true
})

function handleVariantsUpdate(updated: { label: VariantLabel; value: string }[]) {
  variants.value = updated
}

function handleSubmit() {
  if (!canSubmit.value || !selectedVideoId.value) return
  emit('create', {
    videoId: selectedVideoId.value,
    type: testType.value,
    variants: variants.value,
    durationHours: durationHours.value,
  })
}

function resetForm() {
  selectedVideoId.value = null
  testType.value = 'THUMBNAIL'
  durationHours.value = 24
  variants.value = [
    { label: 'A', value: '' },
    { label: 'B', value: '' },
  ]
}
</script>

<template>
  <div class="mx-auto max-w-3xl space-y-6">
    <h2 class="text-xl font-bold text-gray-900 dark:text-white">
      {{ $t('abTest.createTitle') }}
    </h2>

    <!-- Video Selection -->
    <div>
      <label class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">
        {{ $t('abTest.selectVideo') }}
      </label>
      <div v-if="videos.length > 0" class="max-h-48 space-y-2 overflow-y-auto">
        <div
          v-for="video in videos"
          :key="video.id"
          :class="[
            'flex cursor-pointer items-center gap-3 rounded-lg border p-3 transition-all',
            selectedVideoId === video.id
              ? 'border-blue-500 bg-blue-50 dark:border-blue-500 dark:bg-blue-900/20'
              : 'border-gray-200 hover:border-blue-300 dark:border-gray-700 dark:hover:border-blue-600',
            video.hasActiveTest ? 'pointer-events-none opacity-50' : ''
          ]"
          @click="!video.hasActiveTest && (selectedVideoId = video.id)"
        >
          <img
            v-if="video.thumbnailUrl"
            :src="video.thumbnailUrl"
            :alt="video.title"
            class="h-12 w-20 flex-shrink-0 rounded object-cover"
          />
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium text-gray-900 dark:text-white">{{ video.title }}</p>
            <p class="text-xs text-gray-500 dark:text-gray-400">
              CTR: {{ video.currentCtr.toFixed(1) }}% | {{ video.views.toLocaleString() }} views
            </p>
          </div>
        </div>
      </div>
      <div v-else class="rounded-lg border border-dashed border-gray-300 p-6 text-center dark:border-gray-600">
        <p class="text-sm text-gray-500 dark:text-gray-400">{{ $t('abTest.noVideos') }}</p>
      </div>
    </div>

    <!-- Test Type -->
    <div>
      <label class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">
        {{ $t('abTest.testType') }}
      </label>
      <div class="grid grid-cols-3 gap-3">
        <button
          v-for="tt in testTypes"
          :key="tt.value"
          :class="[
            'rounded-lg border p-3 text-center text-sm font-medium transition-all',
            testType === tt.value
              ? 'border-blue-500 bg-blue-50 text-blue-700 dark:border-blue-500 dark:bg-blue-900/20 dark:text-blue-400'
              : 'border-gray-200 text-gray-700 hover:border-blue-300 dark:border-gray-700 dark:text-gray-300 dark:hover:border-blue-600'
          ]"
          @click="testType = tt.value"
        >
          {{ tt.label }}
        </button>
      </div>
    </div>

    <!-- Variants Editor -->
    <VariantEditor
      :variants="variants"
      :test-type="testType"
      @update="handleVariantsUpdate"
    />

    <!-- Duration -->
    <div>
      <label class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">
        {{ $t('abTest.duration') }}
      </label>
      <div class="flex items-center gap-3">
        <input
          v-model.number="durationHours"
          type="number"
          min="1"
          max="168"
          :placeholder="$t('abTest.durationPlaceholder')"
          class="w-32 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 focus:border-transparent focus:ring-2 focus:ring-blue-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white dark:focus:ring-blue-400"
        />
        <span class="text-sm text-gray-500 dark:text-gray-400">{{ $t('abTest.durationHours') }}</span>
        <span v-if="durationHours >= 24" class="text-xs text-gray-400 dark:text-gray-500">
          ({{ Math.floor(durationHours / 24) }}{{ $t('abTest.durationDays') }}
          {{ durationHours % 24 > 0 ? `${durationHours % 24}${$t('abTest.durationHours')}` : '' }})
        </span>
      </div>
    </div>

    <!-- Summary & Selected Video -->
    <div v-if="selectedVideo" class="rounded-lg border border-blue-200 bg-blue-50 p-4 dark:border-blue-800 dark:bg-blue-900/20">
      <h4 class="mb-2 text-sm font-semibold text-blue-900 dark:text-blue-300">{{ selectedVideo.title }}</h4>
      <div class="space-y-1 text-xs text-blue-800 dark:text-blue-400">
        <p>{{ $t('abTest.testType') }}: {{ testTypes.find(tt => tt.value === testType)?.label }}</p>
        <p>{{ $t('abTest.variants') }}: {{ variants.length }}</p>
        <p>{{ $t('abTest.duration') }}: {{ durationHours }}{{ $t('abTest.durationHours') }}</p>
      </div>
    </div>

    <!-- Actions -->
    <div class="flex items-center justify-between border-t border-gray-200 pt-4 dark:border-gray-700">
      <div class="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
        <SparklesIcon class="h-4 w-4 text-primary-500" />
        {{ $t('abTest.creditCost') }}
      </div>
      <div class="flex items-center gap-3">
        <button
          class="rounded-lg px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-700"
          @click="resetForm"
        >
          {{ $t('abTest.saveDraft') }}
        </button>
        <button
          :disabled="!canSubmit || processing"
          :class="[
            'rounded-lg px-4 py-2 text-sm font-medium transition-colors',
            canSubmit && !processing
              ? 'bg-blue-600 text-white hover:bg-blue-700 dark:bg-blue-600 dark:hover:bg-blue-700'
              : 'cursor-not-allowed bg-gray-300 text-gray-500 dark:bg-gray-600 dark:text-gray-400'
          ]"
          @click="handleSubmit"
        >
          {{ processing ? '...' : $t('abTest.createAndStart') }}
        </button>
      </div>
    </div>
  </div>
</template>

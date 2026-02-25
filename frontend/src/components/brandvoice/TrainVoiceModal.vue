<template>
  <Teleport to="body">
    <div
      v-if="show"
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      :aria-label="$t('brandVoice.train.title')"
    >
      <div class="fixed inset-0 bg-black/50" @click="$emit('close')" />
      <div class="relative max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl bg-white dark:bg-gray-800 shadow-xl">
        <!-- Header -->
        <div class="sticky top-0 z-10 flex items-center justify-between border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-6 py-4">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('brandVoice.train.title') }}
          </h2>
          <button
            class="rounded-lg p-2 text-gray-400 dark:text-gray-500 transition-colors hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-gray-600 dark:hover:text-gray-300"
            @click="$emit('close')"
          >
            <XMarkIcon class="h-5 w-5" />
          </button>
        </div>

        <!-- Body -->
        <div class="space-y-4 p-6">
          <!-- Name -->
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
              {{ $t('brandVoice.train.nameLabel') }}
            </label>
            <input
              v-model="form.name"
              type="text"
              class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
              :placeholder="$t('brandVoice.train.namePlaceholder')"
              :disabled="submitting"
            />
          </div>

          <!-- Tone -->
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
              {{ $t('brandVoice.train.toneLabel') }}
            </label>
            <select
              v-model="form.tone"
              class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
              :disabled="submitting"
            >
              <option value="" disabled>{{ $t('brandVoice.train.toneSelect') }}</option>
              <option v-for="tone in toneOptions" :key="tone" :value="tone">
                {{ $t(`brandVoice.tones.${tone}`) }}
              </option>
            </select>
          </div>

          <!-- Sample Texts -->
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
              {{ $t('brandVoice.train.sampleLabel') }}
            </label>
            <textarea
              v-model="form.sampleTextsRaw"
              rows="6"
              class="w-full resize-y rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
              :placeholder="$t('brandVoice.train.samplePlaceholder')"
              :disabled="submitting"
            />
            <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
              {{ $t('brandVoice.train.sampleHelp') }}
            </p>
          </div>

          <!-- Avoid Words -->
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
              {{ $t('brandVoice.train.avoidLabel') }}
            </label>
            <input
              v-model="form.avoidWordsRaw"
              type="text"
              class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
              :placeholder="$t('brandVoice.train.avoidPlaceholder')"
              :disabled="submitting"
            />
          </div>

          <!-- Actions -->
          <div class="flex justify-end gap-3 border-t border-gray-200 dark:border-gray-700 pt-4">
            <button
              class="rounded-lg border border-gray-200 dark:border-gray-700 px-4 py-2 text-sm text-gray-600 dark:text-gray-300 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
              :disabled="submitting"
              @click="$emit('close')"
            >
              {{ $t('brandVoice.train.cancelButton') }}
            </button>
            <button
              class="btn-primary inline-flex items-center gap-2"
              :disabled="!isValid || submitting"
              @click="handleSubmit"
            >
              <SparklesIcon class="h-4 w-4" />
              {{ submitting ? $t('brandVoice.train.submitting') : $t('brandVoice.train.submitButton') }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { XMarkIcon, SparklesIcon } from '@heroicons/vue/24/outline'
import type { VoiceTone, TrainVoiceRequest } from '@/types/brandVoice'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  close: []
  submit: [request: TrainVoiceRequest]
}>()

const toneOptions: VoiceTone[] = ['CASUAL', 'PROFESSIONAL', 'HUMOROUS', 'EDUCATIONAL', 'INSPIRATIONAL']

const submitting = ref(false)

const form = ref({
  name: '',
  tone: '' as VoiceTone | '',
  sampleTextsRaw: '',
  avoidWordsRaw: '',
})

const parsedSampleTexts = computed(() =>
  form.value.sampleTextsRaw
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0),
)

const parsedAvoidWords = computed(() => {
  if (!form.value.avoidWordsRaw.trim()) return []
  return form.value.avoidWordsRaw
    .split(',')
    .map((w) => w.trim())
    .filter((w) => w.length > 0)
})

const isValid = computed(() =>
  form.value.name.trim().length > 0 &&
  form.value.tone !== '' &&
  parsedSampleTexts.value.length >= 1,
)

// Reset form when modal opens
watch(
  () => props.show,
  (newVal) => {
    if (newVal) {
      form.value = {
        name: '',
        tone: '',
        sampleTextsRaw: '',
        avoidWordsRaw: '',
      }
      submitting.value = false
    }
  },
)

async function handleSubmit() {
  if (!isValid.value || submitting.value) return

  submitting.value = true
  try {
    const request: TrainVoiceRequest = {
      name: form.value.name.trim(),
      sampleTexts: parsedSampleTexts.value,
      tone: form.value.tone as VoiceTone,
      avoidWords: parsedAvoidWords.value.length > 0 ? parsedAvoidWords.value : undefined,
    }
    emit('submit', request)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
    <h3 class="mb-4 text-sm font-semibold text-gray-900 dark:text-gray-100">
      {{ $t('subtitleEditor.generate.title') }}
    </h3>

    <!-- Language Selection -->
    <div class="mb-4">
      <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
        {{ $t('subtitleEditor.generate.language') }}
      </label>
      <select
        v-model="language"
        class="input-field"
        :disabled="processing"
      >
        <option
          v-for="lang in languageOptions"
          :key="lang.value"
          :value="lang.value"
        >
          {{ lang.label }}
        </option>
      </select>
    </div>

    <!-- Translate To -->
    <div class="mb-4">
      <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
        {{ $t('subtitleEditor.generate.translateTo') }}
      </label>
      <select
        v-model="translateTo"
        class="input-field"
        :disabled="processing"
      >
        <option value="">{{ $t('subtitleEditor.generate.translateToNone') }}</option>
        <option
          v-for="lang in translateOptions"
          :key="lang.value"
          :value="lang.value"
        >
          {{ lang.label }}
        </option>
      </select>
    </div>

    <!-- Speaker Diarization -->
    <div class="mb-4">
      <label class="flex items-center gap-2 text-sm font-medium text-gray-700 dark:text-gray-300">
        <input
          v-model="speakerDiarization"
          type="checkbox"
          class="rounded border-gray-300 text-primary-600 focus:ring-primary-500 dark:border-gray-600"
          :disabled="processing"
        />
        {{ $t('subtitleEditor.generate.speakerDiarization') }}
      </label>
      <p class="mt-1 pl-6 text-xs text-gray-500 dark:text-gray-400">
        {{ $t('subtitleEditor.generate.speakerDiarizationDesc') }}
      </p>
    </div>

    <!-- Credit Info -->
    <p class="mb-4 text-xs text-gray-500 dark:text-gray-400">
      {{ $t('subtitleEditor.generate.credits') }}
    </p>

    <!-- Generate Button -->
    <button
      class="btn-primary inline-flex w-full items-center justify-center gap-2"
      :disabled="!selectedVideo || processing"
      @click="handleGenerate"
    >
      <template v-if="processing">
        <ArrowPathIcon class="h-4 w-4 animate-spin" />
        {{ $t('subtitleEditor.generate.processing') }}
      </template>
      <template v-else>
        <MicrophoneIcon class="h-4 w-4" />
        {{ $t('subtitleEditor.generate.button') }}
      </template>
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { MicrophoneIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import type { SubtitleLanguage } from '@/types/subtitleEditor'

defineProps<{
  processing: boolean
  selectedVideo: { id: number; title: string } | null
}>()

const emit = defineEmits<{
  generate: [params: { language: SubtitleLanguage; translateTo?: SubtitleLanguage; speakerDiarization: boolean }]
}>()

const { t } = useI18n({ useScope: 'global' })

const language = ref<SubtitleLanguage>('auto')
const translateTo = ref<SubtitleLanguage | ''>('')
const speakerDiarization = ref(false)

const languageOptions = computed(() => [
  { value: 'auto' as SubtitleLanguage, label: t('subtitleEditor.generate.languageOptions.auto') },
  { value: 'ko' as SubtitleLanguage, label: t('subtitleEditor.generate.languageOptions.ko') },
  { value: 'en' as SubtitleLanguage, label: t('subtitleEditor.generate.languageOptions.en') },
  { value: 'ja' as SubtitleLanguage, label: t('subtitleEditor.generate.languageOptions.ja') },
  { value: 'zh' as SubtitleLanguage, label: t('subtitleEditor.generate.languageOptions.zh') },
])

const translateOptions = computed(() => [
  { value: 'ko' as SubtitleLanguage, label: t('subtitleEditor.generate.languageOptions.ko') },
  { value: 'en' as SubtitleLanguage, label: t('subtitleEditor.generate.languageOptions.en') },
  { value: 'ja' as SubtitleLanguage, label: t('subtitleEditor.generate.languageOptions.ja') },
  { value: 'zh' as SubtitleLanguage, label: t('subtitleEditor.generate.languageOptions.zh') },
])

function handleGenerate() {
  emit('generate', {
    language: language.value,
    translateTo: translateTo.value || undefined,
    speakerDiarization: speakerDiarization.value,
  })
}
</script>

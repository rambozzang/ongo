<template>
  <div class="space-y-5">
    <!-- Period -->
    <div>
      <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
        {{ $t('aiCalendar.settings.period') }}
      </label>
      <div class="flex gap-2">
        <button
          v-for="opt in periodOptions"
          :key="opt.value"
          class="flex-1 rounded-lg border px-3 py-2 text-center text-sm font-medium transition-colors"
          :class="[
            modelValue.period === opt.value
              ? 'border-primary-300 bg-primary-50 text-primary-700 dark:border-primary-600 dark:bg-primary-900/20 dark:text-primary-300'
              : 'border-gray-200 text-gray-600 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-700',
            disabled ? 'opacity-50 pointer-events-none' : '',
          ]"
          :disabled="disabled"
          @click="emit('update:modelValue', { ...modelValue, period: opt.value })"
        >
          {{ opt.label }}
        </button>
      </div>
    </div>

    <!-- Frequency -->
    <div>
      <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
        {{ $t('aiCalendar.settings.frequency') }}
      </label>
      <select
        :value="modelValue.frequency"
        class="input-field"
        :disabled="disabled"
        @change="emit('update:modelValue', { ...modelValue, frequency: ($event.target as HTMLSelectElement).value as PostingFrequency })"
      >
        <option v-for="opt in frequencyOptions" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </option>
      </select>
    </div>

    <!-- Platforms -->
    <div>
      <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
        {{ $t('aiCalendar.settings.platforms') }}
      </label>
      <div class="flex flex-wrap gap-2">
        <label
          v-for="p in platforms"
          :key="p.value"
          class="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm transition-colors"
          :class="[
            modelValue.platforms.includes(p.value)
              ? 'border-primary-300 bg-primary-50 text-primary-700 dark:border-primary-600 dark:bg-primary-900/20 dark:text-primary-300'
              : 'border-gray-200 text-gray-600 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-700',
            disabled ? 'opacity-50 pointer-events-none' : '',
          ]"
        >
          <input
            type="checkbox"
            :checked="modelValue.platforms.includes(p.value)"
            class="sr-only"
            :disabled="disabled"
            @change="togglePlatform(p.value)"
          />
          {{ p.label }}
        </label>
      </div>
    </div>

    <!-- Categories -->
    <div>
      <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
        {{ $t('aiCalendar.settings.categories') }}
      </label>
      <div class="flex flex-wrap gap-2">
        <label
          v-for="cat in categories"
          :key="cat"
          class="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm transition-colors"
          :class="[
            modelValue.categories.includes(cat)
              ? 'border-primary-300 bg-primary-50 text-primary-700 dark:border-primary-600 dark:bg-primary-900/20 dark:text-primary-300'
              : 'border-gray-200 text-gray-600 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-700',
            disabled ? 'opacity-50 pointer-events-none' : '',
          ]"
        >
          <input
            type="checkbox"
            :checked="modelValue.categories.includes(cat)"
            class="sr-only"
            :disabled="disabled"
            @change="toggleCategory(cat)"
          />
          {{ cat }}
        </label>
      </div>
    </div>

    <!-- Season Events Toggle -->
    <div>
      <label class="flex items-center gap-2 text-sm font-medium text-gray-700 dark:text-gray-300">
        <input
          type="checkbox"
          :checked="modelValue.includeSeasonEvents"
          class="rounded border-gray-300 text-primary-600 focus:ring-primary-500 dark:border-gray-600"
          :disabled="disabled"
          @change="emit('update:modelValue', { ...modelValue, includeSeasonEvents: !modelValue.includeSeasonEvents })"
        />
        {{ $t('aiCalendar.settings.includeSeasonEvents') }}
      </label>
      <p class="ml-6 mt-1 text-xs text-gray-500 dark:text-gray-400">
        {{ $t('aiCalendar.settings.seasonEventsHint') }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { Platform } from '@/types/channel'
import type { CalendarPeriod, PostingFrequency } from '@/types/aiCalendar'

interface SettingsForm {
  period: CalendarPeriod
  frequency: PostingFrequency
  platforms: Platform[]
  categories: string[]
  includeSeasonEvents: boolean
}

const props = defineProps<{
  modelValue: SettingsForm
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: SettingsForm]
}>()

const { t } = useI18n({ useScope: 'global' })

const periodOptions: { value: CalendarPeriod; label: string }[] = [
  { value: '2weeks', label: t('aiCalendar.period.2weeks') },
  { value: '3weeks', label: t('aiCalendar.period.3weeks') },
  { value: '4weeks', label: t('aiCalendar.period.4weeks') },
]

const frequencyOptions: { value: PostingFrequency; label: string }[] = [
  { value: 'daily', label: t('aiCalendar.frequency.daily') },
  { value: '3perWeek', label: t('aiCalendar.frequency.3perWeek') },
  { value: '2perWeek', label: t('aiCalendar.frequency.2perWeek') },
  { value: '1perWeek', label: t('aiCalendar.frequency.1perWeek') },
]

const platforms: { value: Platform; label: string }[] = [
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'TIKTOK', label: 'TikTok' },
  { value: 'INSTAGRAM', label: 'Instagram' },
  { value: 'NAVER_CLIP', label: 'Naver Clip' },
]

const categories = [
  '엔터테인먼트',
  '게임',
  '음악',
  '교육',
  '뉴스/정치',
  '과학기술',
  '스포츠',
  '여행/이벤트',
  '하우투/스타일',
  '반려동물/동물',
  '코미디',
  '푸드',
  '뷰티/패션',
  '일상/브이로그',
]

function togglePlatform(platform: Platform) {
  const current = props.modelValue.platforms
  const updated = current.includes(platform)
    ? current.filter((p) => p !== platform)
    : [...current, platform]
  emit('update:modelValue', { ...props.modelValue, platforms: updated })
}

function toggleCategory(cat: string) {
  const current = props.modelValue.categories
  const updated = current.includes(cat)
    ? current.filter((c) => c !== cat)
    : [...current, cat]
  emit('update:modelValue', { ...props.modelValue, categories: updated })
}
</script>

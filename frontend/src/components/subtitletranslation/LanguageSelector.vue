<script setup lang="ts">
import type { SupportedLanguage } from '@/types/subtitleTranslation'

defineProps<{
  label: string
  modelValue: string
  languages: SupportedLanguage[]
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const handleChange = (event: Event) => {
  const target = event.target as HTMLSelectElement
  emit('update:modelValue', target.value)
}
</script>

<template>
  <div>
    <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
      {{ label }}
    </label>
    <select
      :value="modelValue"
      class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
      @change="handleChange"
    >
      <option value="" disabled>
        {{ placeholder || '언어를 선택하세요' }}
      </option>
      <option
        v-for="lang in languages"
        :key="lang.code"
        :value="lang.code"
      >
        {{ lang.nativeName }} ({{ lang.name }})
      </option>
    </select>
  </div>
</template>

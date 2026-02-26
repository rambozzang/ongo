<template>
  <div>
    <label class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">
      {{ $t('contentRewriter.outputFormatsLabel') }}
    </label>
    <div class="grid grid-cols-2 gap-2 tablet:grid-cols-3">
      <button
        v-for="format in formats"
        :key="format.value"
        type="button"
        class="flex flex-col items-center gap-1.5 rounded-lg border p-3 text-center transition-all"
        :class="isSelected(format.value)
          ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20 ring-1 ring-primary-500'
          : 'border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 hover:border-gray-300 dark:hover:border-gray-600'"
        @click="toggle(format.value)"
      >
        <component
          :is="format.icon"
          class="h-5 w-5"
          :class="isSelected(format.value)
            ? 'text-primary-600 dark:text-primary-400'
            : 'text-gray-400 dark:text-gray-500'"
        />
        <span
          class="text-xs font-medium"
          :class="isSelected(format.value)
            ? 'text-primary-700 dark:text-primary-300'
            : 'text-gray-700 dark:text-gray-300'"
        >
          {{ $t(`contentRewriter.format.${format.value}`) }}
        </span>
        <span class="text-[10px] text-gray-400 dark:text-gray-500">
          {{ format.hint }}
        </span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  VideoCameraIcon,
  ChatBubbleLeftIcon,
  DocumentTextIcon,
  PhotoIcon,
  EnvelopeIcon,
  QueueListIcon,
} from '@heroicons/vue/24/outline'
import type { OutputFormat } from '@/types/contentRewriter'

const props = defineProps<{
  modelValue: OutputFormat[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: OutputFormat[]]
}>()

const formats = [
  { value: 'SHORT_VIDEO' as OutputFormat, icon: VideoCameraIcon, hint: '~150 chars' },
  { value: 'TWEET' as OutputFormat, icon: ChatBubbleLeftIcon, hint: '~280 chars' },
  { value: 'BLOG_POST' as OutputFormat, icon: DocumentTextIcon, hint: '~2000 chars' },
  { value: 'CAPTION' as OutputFormat, icon: PhotoIcon, hint: '~300 chars' },
  { value: 'NEWSLETTER' as OutputFormat, icon: EnvelopeIcon, hint: '~1500 chars' },
  { value: 'THREAD' as OutputFormat, icon: QueueListIcon, hint: '~1400 chars' },
]

function isSelected(format: OutputFormat): boolean {
  return props.modelValue.includes(format)
}

function toggle(format: OutputFormat) {
  const current = [...props.modelValue]
  const index = current.indexOf(format)
  if (index >= 0) {
    current.splice(index, 1)
  } else {
    current.push(format)
  }
  emit('update:modelValue', current)
}
</script>

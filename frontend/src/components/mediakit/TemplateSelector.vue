<template>
  <div>
    <h3 class="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-100">
      {{ $t('mediaKit.selectTemplate') }}
    </h3>
    <div class="grid grid-cols-2 gap-3 tablet:grid-cols-4">
      <button
        v-for="tmpl in templateStyles"
        :key="tmpl.style"
        class="group relative flex flex-col items-center rounded-xl border-2 p-4 transition-all hover:shadow-md"
        :class="
          selectedStyle === tmpl.style
            ? 'border-primary-500 bg-primary-50 dark:border-primary-400 dark:bg-primary-900/20'
            : 'border-gray-200 bg-white hover:border-gray-300 dark:border-gray-700 dark:bg-gray-900 dark:hover:border-gray-600'
        "
        @click="$emit('select', tmpl.style)"
      >
        <!-- Visual preview icon area -->
        <div
          class="mb-3 flex h-20 w-full items-center justify-center rounded-lg"
          :class="tmpl.previewClass"
        >
          <component :is="tmpl.icon" class="h-8 w-8" :class="tmpl.iconClass" />
        </div>

        <!-- Style name -->
        <span
          class="text-sm font-medium"
          :class="
            selectedStyle === tmpl.style
              ? 'text-primary-700 dark:text-primary-300'
              : 'text-gray-700 dark:text-gray-300'
          "
        >
          {{ $t(`mediaKit.templateStyles.${tmpl.style}`) }}
        </span>

        <!-- Description -->
        <span class="mt-1 text-center text-xs text-gray-500 dark:text-gray-400">
          {{ $t(`mediaKit.templateDesc.${tmpl.style}`) }}
        </span>

        <!-- Selected indicator -->
        <div
          v-if="selectedStyle === tmpl.style"
          class="absolute -right-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full bg-primary-500 text-white"
        >
          <CheckIcon class="h-3 w-3" />
        </div>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  SparklesIcon,
  AcademicCapIcon,
  MinusIcon,
  PaintBrushIcon,
  CheckIcon,
} from '@heroicons/vue/24/outline'

defineProps<{
  selectedStyle?: string
}>()

defineEmits<{
  select: [style: string]
}>()

const templateStyles = [
  {
    style: 'MODERN',
    icon: SparklesIcon,
    previewClass: 'bg-gradient-to-br from-blue-100 to-indigo-100 dark:from-blue-900/30 dark:to-indigo-900/30',
    iconClass: 'text-blue-600 dark:text-blue-400',
  },
  {
    style: 'CLASSIC',
    icon: AcademicCapIcon,
    previewClass: 'bg-gradient-to-br from-amber-100 to-orange-100 dark:from-amber-900/30 dark:to-orange-900/30',
    iconClass: 'text-amber-600 dark:text-amber-400',
  },
  {
    style: 'MINIMAL',
    icon: MinusIcon,
    previewClass: 'bg-gradient-to-br from-gray-100 to-slate-100 dark:from-gray-800 dark:to-slate-800',
    iconClass: 'text-gray-600 dark:text-gray-400',
  },
  {
    style: 'CREATIVE',
    icon: PaintBrushIcon,
    previewClass: 'bg-gradient-to-br from-pink-100 to-purple-100 dark:from-pink-900/30 dark:to-purple-900/30',
    iconClass: 'text-pink-600 dark:text-pink-400',
  },
]
</script>

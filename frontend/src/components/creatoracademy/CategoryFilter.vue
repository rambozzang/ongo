<script setup lang="ts">
import {
  Squares2X2Icon,
  VideoCameraIcon,
  ScissorsIcon,
  MegaphoneIcon,
  CpuChipIcon,
  CurrencyDollarIcon,
  ArrowTrendingUpIcon,
  SwatchIcon,
  ChartBarIcon,
} from '@heroicons/vue/24/outline'
import { type Component } from 'vue'

interface Props {
  selected: string
}

interface Emits {
  (e: 'select', category: string): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

interface CategoryItem {
  key: string
  label: string
  icon: Component
}

const categories: CategoryItem[] = [
  { key: 'ALL', label: '전체', icon: Squares2X2Icon },
  { key: 'FILMING', label: '촬영', icon: VideoCameraIcon },
  { key: 'EDITING', label: '편집', icon: ScissorsIcon },
  { key: 'MARKETING', label: '마케팅', icon: MegaphoneIcon },
  { key: 'AI_TOOLS', label: 'AI 도구', icon: CpuChipIcon },
  { key: 'MONETIZATION', label: '수익화', icon: CurrencyDollarIcon },
  { key: 'GROWTH', label: '성장', icon: ArrowTrendingUpIcon },
  { key: 'BRANDING', label: '브랜딩', icon: SwatchIcon },
  { key: 'ANALYTICS', label: '분석', icon: ChartBarIcon },
]
</script>

<template>
  <div class="flex gap-2 overflow-x-auto pb-2 scrollbar-hide">
    <button
      v-for="category in categories"
      :key="category.key"
      @click="emit('select', category.key)"
      :class="[
        'inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium whitespace-nowrap transition-all duration-200',
        selected === category.key
          ? 'bg-primary-600 text-white dark:bg-primary-500 shadow-md'
          : 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700',
      ]"
    >
      <component :is="category.icon" class="w-4 h-4" />
      {{ category.label }}
    </button>
  </div>
</template>

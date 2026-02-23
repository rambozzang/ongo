<template>
  <div
    role="article"
    :aria-label="`${title}: ${formattedValue}${change !== undefined ? `, 전주 대비 ${changeIcon}${Math.abs(change)}${changeType === 'percent' ? '%' : ''}` : ''}`"
    class="card cursor-pointer transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md"
    @click="$emit('click')"
  >
    <div class="flex items-center justify-between">
      <p class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ title }}</p>
      <div
        class="flex h-10 w-10 items-center justify-center rounded-lg"
        :class="iconBgClass"
      >
        <component :is="icon" class="h-5 w-5" :class="iconColorClass" />
      </div>
    </div>
    <p class="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">{{ formattedValue }}</p>
    <div v-if="change !== undefined" class="mt-1 flex items-center gap-1 text-sm">
      <span :class="changeColor">
        {{ changeIcon }}{{ Math.abs(change) }}{{ changeType === 'percent' ? '%' : '' }}
      </span>
      <span class="text-gray-400 dark:text-gray-500">{{ $t('dashboard.vsLastWeek') }}</span>
    </div>
    <div v-if="progressBar" class="mt-2 h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
      <div
        class="h-full rounded-full transition-all"
        :class="(progressPercent ?? 0) <= 20 ? 'bg-red-500' : 'bg-primary-500'"
        :style="{ width: `${progressPercent ?? 0}%` }"
      />
    </div>
    <slot />
  </div>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'

type CardColor = 'blue' | 'green' | 'rose' | 'purple' | 'gray'

const colorMap: Record<CardColor, { bg: string; text: string }> = {
  blue: { bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-600 dark:text-blue-400' },
  green: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-600 dark:text-green-400' },
  rose: { bg: 'bg-rose-100 dark:bg-rose-900/30', text: 'text-rose-600 dark:text-rose-400' },
  purple: { bg: 'bg-purple-100 dark:bg-purple-900/30', text: 'text-purple-600 dark:text-purple-400' },
  gray: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-500 dark:text-gray-400' },
}

const props = withDefaults(
  defineProps<{
    title: string
    value: number
    change?: number
    changeType?: 'percent' | 'number'
    icon: Component
    color?: CardColor
    format?: 'number' | 'compact'
    progressBar?: boolean
    progressPercent?: number
  }>(),
  {
    change: undefined,
    changeType: 'percent',
    color: 'gray',
    format: 'compact',
    progressBar: false,
    progressPercent: undefined,
  },
)

defineEmits<{
  click: []
}>()

const iconBgClass = computed(() => colorMap[props.color].bg)
const iconColorClass = computed(() => colorMap[props.color].text)

const formattedValue = computed(() => {
  if (props.format === 'compact') {
    if (props.value >= 1_000_000) return `${(props.value / 1_000_000).toFixed(1)}M`
    if (props.value >= 1_000) return `${(props.value / 1_000).toFixed(1)}K`
  }
  return props.value.toLocaleString()
})

const changeIcon = computed(() => {
  if (props.change === undefined) return ''
  if (props.changeType === 'number') return props.change >= 0 ? '+' : ''
  return props.change >= 0 ? '\u2191' : '\u2193'
})

const changeColor = computed(() => {
  if (props.change === undefined) return ''
  return props.change >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'
})
</script>

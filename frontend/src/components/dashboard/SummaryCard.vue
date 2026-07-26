<template>
  <div
    role="article"
    :aria-label="`${title}: ${formattedValue}${change !== undefined ? `, 전주 대비 ${changeIcon}${Math.abs(change)}${changeType === 'percent' ? '%' : ''}` : ''}`"
    class="card cursor-pointer transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md"
    @click="$emit('click')"
  >
    <div class="flex items-center justify-between">
      <p class="text-body font-medium text-gray-500 dark:text-gray-400">{{ title }}</p>
      <div
        class="flex h-9 w-9 items-center justify-center rounded-lg"
        :class="iconBgClass"
      >
        <component :is="icon" class="h-5 w-5" :class="iconColorClass" />
      </div>
    </div>
    <p class="mt-3 text-[1.75rem] font-bold tracking-[-0.04em] text-gray-900 dark:text-gray-100">{{ formattedValue }}</p>
    <div v-if="change !== undefined" class="mt-1 flex items-center gap-1 text-body">
      <span :class="changeColor">
        {{ changeIcon }}{{ Math.abs(change) }}{{ changeType === 'percent' ? '%' : '' }}
      </span>
      <span class="text-gray-400 dark:text-gray-500">{{ $t('dashboard.vsLastWeek') }}</span>
    </div>
    <div v-if="progressBar" class="mt-2 h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
      <div
        class="h-full rounded-full transition-all"
        :class="(progressPercent ?? 0) <= 20 ? 'bg-error' : 'bg-primary-500'"
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
  blue: { bg: 'bg-info-subtle', text: 'text-info-strong' },
  green: { bg: 'bg-success-subtle', text: 'text-success-strong' },
  rose: { bg: 'bg-error-subtle', text: 'text-error-strong' },
  purple: { bg: 'bg-info-subtle', text: 'text-info-strong' },
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
  return props.change >= 0 ? 'text-success-strong' : 'text-error-strong'
})
</script>

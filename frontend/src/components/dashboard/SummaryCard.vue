<template>
  <div
    role="article"
    :aria-label="`${title}: ${formattedValue}${change !== undefined ? `, 전주 대비 ${changeIcon}${Math.abs(change)}${changeType === 'percent' ? '%' : ''}` : ''}`"
    class="card cursor-pointer transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md"
    @click="$emit('click')"
  >
    <div class="flex items-center justify-between">
      <p class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ title }}</p>
      <component :is="icon" class="h-5 w-5 text-gray-400 dark:text-gray-500" />
    </div>
    <p class="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">{{ formattedValue }}</p>
    <div v-if="change !== undefined" class="mt-1 flex items-center gap-1 text-sm">
      <span :class="changeColor">
        {{ changeIcon }}{{ Math.abs(change) }}{{ changeType === 'percent' ? '%' : '' }}
      </span>
      <span class="text-gray-400 dark:text-gray-500">전주 대비</span>
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

const props = withDefaults(
  defineProps<{
    title: string
    value: number
    change?: number
    changeType?: 'percent' | 'number'
    icon: Component
    format?: 'number' | 'compact'
    progressBar?: boolean
    progressPercent?: number
  }>(),
  {
    change: undefined,
    changeType: 'percent',
    format: 'compact',
    progressBar: false,
    progressPercent: undefined,
  },
)

defineEmits<{
  click: []
}>()

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
  return props.change >= 0 ? 'text-green-600' : 'text-red-600'
})
</script>

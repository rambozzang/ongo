<template>
  <div>
    <div
      ref="tablistRef"
      role="tablist"
      :aria-label="ariaLabel"
      class="flex gap-1"
      :class="variant === 'line' ? 'border-b border-gray-200 dark:border-gray-700' : ''"
      @keydown="handleKeydown"
    >
      <button
        v-for="tab in tabs"
        :id="`${tabsId}-tab-${tab.key}`"
        :key="tab.key"
        role="tab"
        type="button"
        :aria-selected="modelValue === tab.key"
        :aria-controls="`${tabsId}-panel-${tab.key}`"
        :tabindex="modelValue === tab.key ? 0 : -1"
        class="relative inline-flex items-center gap-2 px-4 py-2.5 text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-inset"
        :class="getTabClass(tab.key)"
        @click="selectTab(tab.key)"
      >
        <component
          :is="tab.icon"
          v-if="tab.icon"
          class="h-5 w-5"
          aria-hidden="true"
        />
        <span>{{ tab.label }}</span>
        <span
          v-if="tab.count !== undefined"
          class="inline-flex items-center justify-center rounded-full px-2 py-0.5 text-xs font-semibold"
          :class="getCountClass(tab.key)"
          :aria-label="`${tab.count}개`"
        >
          {{ tab.count }}
        </span>
        <div
          v-if="variant === 'line' && modelValue === tab.key"
          class="absolute bottom-0 left-0 right-0 h-0.5 bg-primary-600 dark:bg-primary-400"
          style="animation: slideIn 0.2s ease-out"
          aria-hidden="true"
        />
      </button>
    </div>
    <!-- Tab panels are rendered by parent; this provides panel IDs for association -->
    <slot
      :active-tab="modelValue"
      :panel-id="`${tabsId}-panel-${modelValue}`"
      :tab-id="`${tabsId}-tab-${modelValue}`"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, type Component, useId } from 'vue'

interface Tab {
  key: string
  label: string
  icon?: Component
  count?: number
}

const props = withDefaults(defineProps<{
  modelValue: string
  tabs: Tab[]
  variant?: 'line' | 'pill'
  ariaLabel?: string
}>(), {
  variant: 'line',
  ariaLabel: '탭',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const tabsId = `tabs-${useId()}`
const tablistRef = ref<HTMLElement | null>(null)

function selectTab(key: string) {
  emit('update:modelValue', key)
}

function handleKeydown(event: KeyboardEvent) {
  const currentIndex = props.tabs.findIndex(t => t.key === props.modelValue)
  let nextIndex = -1

  if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
    event.preventDefault()
    nextIndex = (currentIndex + 1) % props.tabs.length
  } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
    event.preventDefault()
    nextIndex = (currentIndex - 1 + props.tabs.length) % props.tabs.length
  } else if (event.key === 'Home') {
    event.preventDefault()
    nextIndex = 0
  } else if (event.key === 'End') {
    event.preventDefault()
    nextIndex = props.tabs.length - 1
  }

  if (nextIndex >= 0) {
    const nextTab = props.tabs[nextIndex]
    selectTab(nextTab.key)
    // Focus the newly selected tab button
    const tabButton = tablistRef.value?.querySelector<HTMLElement>(
      `#${tabsId}-tab-${nextTab.key}`
    )
    tabButton?.focus()
  }
}

function getTabClass(key: string) {
  const isActive = props.modelValue === key

  if (props.variant === 'pill') {
    return isActive
      ? 'rounded-lg bg-primary-600 text-white dark:bg-primary-500'
      : 'rounded-lg text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'
  }

  // line variant
  return isActive
    ? 'text-primary-600 dark:text-primary-400'
    : 'text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-200'
}

function getCountClass(key: string) {
  const isActive = props.modelValue === key

  if (props.variant === 'pill') {
    return isActive
      ? 'bg-primary-700 text-white dark:bg-primary-600'
      : 'bg-gray-200 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
  }

  // line variant
  return isActive
    ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
    : 'bg-gray-200 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
}
</script>

<style scoped>
@keyframes slideIn {
  from {
    transform: scaleX(0);
  }
  to {
    transform: scaleX(1);
  }
}
</style>

<template>
  <div>
    <div
      ref="tablistRef"
      role="tablist"
      :aria-label="ariaLabel"
      class="flex gap-1 overflow-x-auto"
      :class="variant === 'line' ? 'border-b border-line-row' : ''"
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
        class="relative inline-flex min-h-11 shrink-0 items-center gap-2 px-1.5 text-body-sm font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-accent focus:ring-inset tablet:px-2"
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
          class="absolute bottom-0 left-0 right-0 h-0.5 rounded-full bg-accent"
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
      ? 'rounded-lg bg-accent text-accent-on'
      : 'rounded-lg text-content-secondary hover:bg-surface-raised hover:text-content'
  }

  // line variant
    return isActive
    ? 'text-accent'
    : 'text-content-secondary hover:text-content'
}

function getCountClass(key: string) {
  const isActive = props.modelValue === key

  if (props.variant === 'pill') {
    return isActive
      ? 'bg-accent-hover text-accent-on'
      : 'bg-surface-raised text-content-secondary'
  }

  // line variant
    return isActive
    ? 'bg-accent-dim text-accent'
    : 'bg-surface-raised text-content-secondary'
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

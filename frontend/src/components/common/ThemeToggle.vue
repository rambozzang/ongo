<template>
  <div class="flex items-center rounded-lg border border-gray-200 bg-gray-100 p-0.5 dark:border-gray-700 dark:bg-gray-800">
    <button
      v-for="opt in options"
      :key="opt.value"
      :aria-label="`${opt.label} 테마`"
      class="rounded-md p-1.5 transition-colors"
      :class="
        themeStore.mode === opt.value
          ? 'bg-white text-primary-600 shadow-sm dark:bg-gray-700 dark:text-primary-400'
          : 'text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300'
      "
      @click="themeStore.setMode(opt.value)"
    >
      <component :is="opt.icon" class="h-4 w-4" aria-hidden="true" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { type Component } from 'vue'
import { SunIcon, MoonIcon, ComputerDesktopIcon } from '@heroicons/vue/24/outline'
import { useThemeStore, type ThemeMode } from '@/stores/theme'

const themeStore = useThemeStore()

const options: { value: ThemeMode; label: string; icon: Component }[] = [
  { value: 'light', label: '라이트', icon: SunIcon },
  { value: 'system', label: '시스템', icon: ComputerDesktopIcon },
  { value: 'dark', label: '다크', icon: MoonIcon },
]
</script>

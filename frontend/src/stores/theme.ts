import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>((localStorage.getItem('theme') as ThemeMode) || 'system')

  const systemDark = ref(window.matchMedia('(prefers-color-scheme: dark)').matches)

  const isDark = computed(() =>
    mode.value === 'dark' || (mode.value === 'system' && systemDark.value),
  )

  function setMode(newMode: ThemeMode) {
    mode.value = newMode
    localStorage.setItem('theme', newMode)
  }

  function applyTheme() {
    document.documentElement.classList.toggle('dark', isDark.value)
  }

  watch(isDark, applyTheme, { immediate: true })

  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  mediaQuery.addEventListener('change', (e) => {
    systemDark.value = e.matches
  })

  return {
    mode,
    isDark,
    setMode,
  }
})

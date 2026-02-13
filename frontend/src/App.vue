<template>
  <router-view />
  <ToastNotification />
  <OCommandPalette />
  <OShortcutHelp />
</template>

<script setup lang="ts">
import '@/assets/transitions.css'
import ToastNotification from '@/components/common/ToastNotification.vue'
import OCommandPalette from '@/components/ui/OCommandPalette.vue'
import OShortcutHelp from '@/components/ui/OShortcutHelp.vue'
import { useThemeStore } from '@/stores/theme'
import { useKeyboardShortcuts } from '@/composables/useKeyboardShortcuts'

useThemeStore()
useKeyboardShortcuts()
</script>


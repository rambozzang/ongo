<template>
  <router-view />
  <ToastNotification />
  <SearchOverlay v-model="searchOpen" />
  <OShortcutHelp />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import '@/assets/transitions.css'
import ToastNotification from '@/components/common/ToastNotification.vue'
import SearchOverlay from '@/components/common/SearchOverlay.vue'
import OShortcutHelp from '@/components/ui/OShortcutHelp.vue'
import { useThemeStore } from '@/stores/theme'
import { useKeyboardShortcuts } from '@/composables/useKeyboardShortcuts'

const searchOpen = ref(false)

function openSearch() {
  searchOpen.value = true
}

useThemeStore()
useKeyboardShortcuts()

onMounted(() => window.addEventListener('ongo:open-search', openSearch))
onBeforeUnmount(() => window.removeEventListener('ongo:open-search', openSearch))
</script>

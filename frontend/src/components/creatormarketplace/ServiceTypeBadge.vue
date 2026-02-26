<script setup lang="ts">
defineProps<{
  serviceType: 'EDITING' | 'THUMBNAIL' | 'SCRIPT' | 'VOICEOVER' | 'CONSULTING' | 'COLLAB'
}>()

const typeConfig: Record<string, { bg: string; text: string; label: string }> = {
  EDITING: { bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-300', label: '편집' },
  THUMBNAIL: { bg: 'bg-purple-100 dark:bg-purple-900/30', text: 'text-purple-700 dark:text-purple-300', label: '썸네일' },
  SCRIPT: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300', label: '대본' },
  VOICEOVER: { bg: 'bg-orange-100 dark:bg-orange-900/30', text: 'text-orange-700 dark:text-orange-300', label: '성우' },
  CONSULTING: { bg: 'bg-pink-100 dark:bg-pink-900/30', text: 'text-pink-700 dark:text-pink-300', label: '컨설팅' },
  COLLAB: { bg: 'bg-teal-100 dark:bg-teal-900/30', text: 'text-teal-700 dark:text-teal-300', label: '콜라보' },
}

const getConfig = (type: string) => typeConfig[type] ?? typeConfig.EDITING
</script>

<template>
  <span
    :class="[getConfig(serviceType).bg, getConfig(serviceType).text]"
    class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
  >
    {{ getConfig(serviceType).label }}
  </span>
</template>

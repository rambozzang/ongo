<template>
  <span
    :aria-label="`플랫폼: ${config.label}`"
    class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium text-white"
    :style="{ backgroundColor: config.color }"
  >
    {{ config.label }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'

const props = defineProps<{
  platform: Platform
}>()

const config = computed(() => PLATFORM_CONFIG[props.platform])
</script>

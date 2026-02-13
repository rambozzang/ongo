<script setup lang="ts">
import { computed } from 'vue'
import { usePermission } from '@/composables/usePermission'

const props = defineProps<{
  permission: string
  fallback?: boolean
}>()

const { hasPermission, isLoaded } = usePermission()

const allowed = computed(() => {
  if (!isLoaded.value) return true // show by default until loaded
  return hasPermission(props.permission)
})
</script>

<template>
  <template v-if="allowed">
    <slot />
  </template>
  <template v-else-if="fallback">
    <slot name="fallback">
      <div
        class="rounded-lg border border-gray-200 bg-gray-50 p-4 text-center text-sm text-gray-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-400"
      >
        이 기능에 대한 권한이 없습니다.
      </div>
    </slot>
  </template>
</template>

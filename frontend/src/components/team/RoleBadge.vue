<script setup lang="ts">
import type { TeamRole } from '@/types/team'

interface Props {
  role: TeamRole
}

defineProps<Props>()

const roleConfig: Record<TeamRole, { label: string; class: string }> = {
  owner: {
    label: '소유자',
    class: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300',
  },
  admin: {
    label: '관리자',
    class: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
  },
  editor: {
    label: '에디터',
    class: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
  },
  viewer: {
    label: '뷰어',
    class: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
  },
}
</script>

<template>
  <span
    :class="roleConfig[role].class"
    class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
  >
    {{ roleConfig[role].label }}
  </span>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ChevronUpDownIcon, PlusIcon, CheckIcon } from '@heroicons/vue/24/outline'
import { useWorkspaceStore } from '@/stores/workspace'

const props = defineProps<{ collapsed: boolean }>()

const workspaceStore = useWorkspaceStore()
const isOpen = ref(false)

onMounted(() => {
  if (workspaceStore.workspaces.length === 0) {
    workspaceStore.fetchWorkspaces()
  }
})

function selectWorkspace(id: number) {
  workspaceStore.switchWorkspace(id)
  isOpen.value = false
}

function getInitial(name: string): string {
  return name.charAt(0).toUpperCase()
}
</script>

<template>
  <div class="relative px-2 py-2">
    <button
      @click="isOpen = !isOpen"
      class="flex w-full items-center gap-2 rounded-lg px-2 py-2 text-left transition-colors hover:bg-gray-100 dark:hover:bg-gray-800"
    >
      <div class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg bg-primary-100 dark:bg-primary-900/30 text-sm font-bold text-primary-700 dark:text-primary-400">
        {{ workspaceStore.activeWorkspace ? getInitial(workspaceStore.activeWorkspace.name) : '?' }}
      </div>
      <template v-if="!props.collapsed">
        <div class="min-w-0 flex-1">
          <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
            {{ workspaceStore.activeWorkspace?.name ?? '워크스페이스' }}
          </p>
          <p class="truncate text-xs text-gray-500 dark:text-gray-400">
            {{ workspaceStore.activeWorkspace?.memberCount ?? 0 }}명
          </p>
        </div>
        <ChevronUpDownIcon class="h-4 w-4 flex-shrink-0 text-gray-400" />
      </template>
    </button>

    <!-- Dropdown -->
    <div
      v-if="isOpen"
      class="absolute left-2 right-2 top-full z-50 mt-1 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 py-1 shadow-lg"
    >
      <button
        v-for="ws in workspaceStore.workspaces"
        :key="ws.id"
        @click="selectWorkspace(ws.id)"
        class="flex w-full items-center gap-2 px-3 py-2 text-sm transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
      >
        <div class="flex h-6 w-6 flex-shrink-0 items-center justify-center rounded bg-gray-100 dark:bg-gray-700 text-xs font-bold text-gray-600 dark:text-gray-300">
          {{ getInitial(ws.name) }}
        </div>
        <span class="flex-1 truncate text-gray-700 dark:text-gray-300">{{ ws.name }}</span>
        <CheckIcon v-if="ws.id === workspaceStore.activeWorkspaceId" class="h-4 w-4 text-primary-600" />
      </button>
      <div class="border-t border-gray-100 dark:border-gray-700 mt-1 pt-1">
        <router-link
          to="/settings?tab=workspaces"
          @click="isOpen = false"
          class="flex w-full items-center gap-2 px-3 py-2 text-sm text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700"
        >
          <PlusIcon class="h-4 w-4" />
          워크스페이스 관리
        </router-link>
      </div>
    </div>
  </div>
</template>

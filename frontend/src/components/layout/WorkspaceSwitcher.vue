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
  <div class="relative px-3 py-3">
    <button
      class="flex w-full items-center gap-3 rounded-lg border border-white/10 bg-white/[0.04] px-2.5 py-2.5 text-left transition-colors hover:bg-white/[0.08]"
      @click="isOpen = !isOpen"
    >
      <div class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg bg-primary-400/20 text-sm font-bold text-primary-200">
        {{ workspaceStore.activeWorkspace ? getInitial(workspaceStore.activeWorkspace.name) : '?' }}
      </div>
      <template v-if="!props.collapsed">
        <div class="min-w-0 flex-1">
          <p class="truncate text-sm font-semibold text-gray-100">
            {{ workspaceStore.activeWorkspace?.name ?? '워크스페이스' }}
          </p>
          <p class="truncate text-xs text-gray-500">
            {{ workspaceStore.activeWorkspace?.memberCount ?? 0 }}명
          </p>
        </div>
        <ChevronUpDownIcon class="h-4 w-4 flex-shrink-0 text-gray-500" />
      </template>
    </button>

    <!-- Dropdown -->
    <div
      v-if="isOpen"
      class="absolute left-3 right-3 top-full z-50 mt-1 rounded-lg border border-gray-700 bg-gray-900 py-1 shadow-xl"
    >
      <button
        v-for="ws in workspaceStore.workspaces"
        :key="ws.id"
        class="flex w-full items-center gap-2 px-3 py-2 text-sm transition-colors hover:bg-white/10"
        @click="selectWorkspace(ws.id)"
      >
        <div class="flex h-6 w-6 flex-shrink-0 items-center justify-center rounded bg-white/10 text-xs font-bold text-gray-300">
          {{ getInitial(ws.name) }}
        </div>
        <span class="flex-1 truncate text-gray-200">{{ ws.name }}</span>
        <CheckIcon v-if="ws.id === workspaceStore.activeWorkspaceId" class="h-4 w-4 text-primary-300" />
      </button>
      <div class="mt-1 border-t border-white/10 pt-1">
        <router-link
          to="/settings?tab=workspaces"
          class="flex w-full items-center gap-2 px-3 py-2 text-sm text-gray-400 hover:bg-white/10 hover:text-gray-100"
          @click="isOpen = false"
        >
          <PlusIcon class="h-4 w-4" />
          워크스페이스 관리
        </router-link>
      </div>
    </div>
  </div>
</template>

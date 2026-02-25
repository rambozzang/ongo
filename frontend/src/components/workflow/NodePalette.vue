<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { NODE_DEFINITIONS } from '@/stores/workflowBuilder'
import type { WfNodeType, WfNodeKind, WfNodeDefinition } from '@/types/workflowBuilder'
import {
  BoltIcon,
  AdjustmentsHorizontalIcon,
  CpuChipIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  MagnifyingGlassIcon,
} from '@heroicons/vue/24/outline'

const { t } = useI18n()

const emit = defineEmits<{
  addNode: [kind: WfNodeKind]
}>()

const searchQuery = ref('')
const expandedCategories = ref<Set<WfNodeType>>(new Set(['trigger', 'condition', 'action']))

const categories = computed<{ type: WfNodeType; label: string; icon: typeof BoltIcon; nodes: WfNodeDefinition[] }[]>(() => {
  const query = searchQuery.value.toLowerCase()
  const filter = (defs: WfNodeDefinition[]) =>
    query ? defs.filter(d => d.label.toLowerCase().includes(query) || d.description.toLowerCase().includes(query)) : defs

  return [
    { type: 'trigger' as WfNodeType, label: t('workflowBuilder.triggers'), icon: BoltIcon, nodes: filter(NODE_DEFINITIONS.filter(d => d.type === 'trigger')) },
    { type: 'condition' as WfNodeType, label: t('workflowBuilder.conditions'), icon: AdjustmentsHorizontalIcon, nodes: filter(NODE_DEFINITIONS.filter(d => d.type === 'condition')) },
    { type: 'action' as WfNodeType, label: t('workflowBuilder.actions'), icon: CpuChipIcon, nodes: filter(NODE_DEFINITIONS.filter(d => d.type === 'action')) },
  ]
})

function toggleCategory(type: WfNodeType) {
  if (expandedCategories.value.has(type)) {
    expandedCategories.value.delete(type)
  } else {
    expandedCategories.value.add(type)
  }
}

const categoryColorClasses: Record<WfNodeType, { badge: string; item: string }> = {
  trigger: {
    badge: 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300',
    item: 'hover:bg-blue-50 dark:hover:bg-blue-900/10 border-blue-100 dark:border-blue-800',
  },
  condition: {
    badge: 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300',
    item: 'hover:bg-amber-50 dark:hover:bg-amber-900/10 border-amber-100 dark:border-amber-800',
  },
  action: {
    badge: 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300',
    item: 'hover:bg-green-50 dark:hover:bg-green-900/10 border-green-100 dark:border-green-800',
  },
}
</script>

<template>
  <div class="w-64 bg-white dark:bg-gray-800 border-r border-gray-200 dark:border-gray-700 flex flex-col h-full overflow-hidden">
    <!-- Header -->
    <div class="p-4 border-b border-gray-200 dark:border-gray-700">
      <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-3">
        {{ $t('workflowBuilder.nodePalette') }}
      </h3>
      <div class="relative">
        <MagnifyingGlassIcon class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
        <input
          v-model="searchQuery"
          type="text"
          :placeholder="$t('workflowBuilder.searchNodes')"
          class="w-full pl-9 pr-3 py-2 text-sm bg-gray-100 dark:bg-gray-700 border-0 rounded-lg text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:ring-2 focus:ring-blue-500"
        />
      </div>
    </div>

    <!-- Categories -->
    <div class="flex-1 overflow-y-auto p-3 space-y-2">
      <div v-for="cat in categories" :key="cat.type">
        <!-- Category header -->
        <button
          class="w-full flex items-center justify-between px-3 py-2 rounded-lg text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
          @click="toggleCategory(cat.type)"
        >
          <div class="flex items-center gap-2">
            <component :is="cat.icon" class="w-4 h-4" />
            <span>{{ cat.label }}</span>
            <span :class="['text-xs px-1.5 py-0.5 rounded-full font-semibold', categoryColorClasses[cat.type].badge]">
              {{ cat.nodes.length }}
            </span>
          </div>
          <component
            :is="expandedCategories.has(cat.type) ? ChevronUpIcon : ChevronDownIcon"
            class="w-4 h-4 text-gray-400"
          />
        </button>

        <!-- Nodes list -->
        <div v-if="expandedCategories.has(cat.type)" class="mt-1 space-y-1 pl-2">
          <button
            v-for="nodeDef in cat.nodes"
            :key="nodeDef.kind"
            class="w-full flex items-start gap-2.5 px-3 py-2.5 rounded-lg border border-transparent text-left transition-colors"
            :class="categoryColorClasses[cat.type].item"
            @click="emit('addNode', nodeDef.kind)"
            draggable="true"
            @dragstart="$event.dataTransfer?.setData('nodeKind', nodeDef.kind)"
          >
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
                {{ nodeDef.label }}
              </p>
              <p class="text-xs text-gray-500 dark:text-gray-400 line-clamp-1">
                {{ nodeDef.description }}
              </p>
            </div>
          </button>
          <p v-if="cat.nodes.length === 0" class="text-xs text-gray-400 dark:text-gray-500 px-3 py-2">
            {{ $t('workflowBuilder.noResults') }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

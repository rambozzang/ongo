<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { WfNode, WfConnection } from '@/types/workflowBuilder'
import { NODE_DEFINITIONS } from '@/stores/workflowBuilder'
import {
  ArrowDownIcon,
  TrashIcon,
  PlusIcon,
} from '@heroicons/vue/24/outline'

const { t } = useI18n()

const props = defineProps<{
  nodes: WfNode[]
  connections: WfConnection[]
  selectedNodeId: string | null
}>()

const emit = defineEmits<{
  selectNode: [nodeId: string]
  deleteNode: [nodeId: string]
  addNodeAfter: [nodeId: string | null]
}>()

// Build ordered list following connections
const orderedNodes = computed(() => {
  if (props.nodes.length === 0) return []

  // Find trigger (start) nodes
  const triggers = props.nodes.filter(n => n.type === 'trigger')
  if (triggers.length === 0) return props.nodes

  const ordered: WfNode[] = []
  const visited = new Set<string>()

  function walk(nodeId: string) {
    if (visited.has(nodeId)) return
    visited.add(nodeId)
    const node = props.nodes.find(n => n.id === nodeId)
    if (node) ordered.push(node)
    // Find outgoing connections
    const outgoing = props.connections.filter(c => c.sourceNodeId === nodeId)
    for (const conn of outgoing) {
      walk(conn.targetNodeId)
    }
  }

  for (const trigger of triggers) {
    walk(trigger.id)
  }

  // Add remaining nodes not in connection chain
  for (const node of props.nodes) {
    if (!visited.has(node.id)) {
      ordered.push(node)
    }
  }

  return ordered
})

const typeColors: Record<string, { bg: string; text: string; border: string }> = {
  trigger: {
    bg: 'bg-blue-50 dark:bg-blue-900/20',
    text: 'text-blue-700 dark:text-blue-300',
    border: 'border-blue-200 dark:border-blue-700',
  },
  condition: {
    bg: 'bg-amber-50 dark:bg-amber-900/20',
    text: 'text-amber-700 dark:text-amber-300',
    border: 'border-amber-200 dark:border-amber-700',
  },
  action: {
    bg: 'bg-green-50 dark:bg-green-900/20',
    text: 'text-green-700 dark:text-green-300',
    border: 'border-green-200 dark:border-green-700',
  },
}

function getTypeBadge(type: string) {
  const map: Record<string, string> = {
    trigger: t('workflowBuilder.trigger'),
    condition: t('workflowBuilder.condition'),
    action: t('workflowBuilder.action'),
  }
  return map[type] ?? type
}

function getNodeDef(kind: string) {
  return NODE_DEFINITIONS.find(d => d.kind === kind)
}
</script>

<template>
  <div class="space-y-2 p-4">
    <p class="text-xs text-gray-500 dark:text-gray-400 mb-3">
      {{ $t('workflowBuilder.mobileHint') }}
    </p>

    <template v-if="orderedNodes.length > 0">
      <div v-for="(node, idx) in orderedNodes" :key="node.id">
        <!-- Node card -->
        <div
          :class="[
            'rounded-xl border-2 p-4 transition-all cursor-pointer',
            typeColors[node.type]?.bg,
            selectedNodeId === node.id
              ? 'ring-2 ring-blue-500 ' + typeColors[node.type]?.border
              : typeColors[node.type]?.border,
          ]"
          @click="emit('selectNode', node.id)"
        >
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3 min-w-0">
              <span :class="['text-xs px-2 py-0.5 rounded-full font-medium', typeColors[node.type]?.text, typeColors[node.type]?.bg]">
                {{ getTypeBadge(node.type) }}
              </span>
              <div class="min-w-0">
                <p class="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">
                  {{ node.label }}
                </p>
                <p class="text-xs text-gray-500 dark:text-gray-400 truncate">
                  {{ getNodeDef(node.kind)?.description }}
                </p>
              </div>
            </div>
            <button
              class="p-1.5 text-red-400 hover:text-red-600 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors flex-shrink-0"
              @click.stop="emit('deleteNode', node.id)"
            >
              <TrashIcon class="w-4 h-4" />
            </button>
          </div>
        </div>

        <!-- Arrow connector -->
        <div v-if="idx < orderedNodes.length - 1" class="flex justify-center py-1">
          <ArrowDownIcon class="w-4 h-4 text-gray-400 dark:text-gray-600" />
        </div>
      </div>
    </template>

    <!-- Empty state -->
    <div v-else class="text-center py-12">
      <p class="text-gray-500 dark:text-gray-400 text-sm mb-3">
        {{ $t('workflowBuilder.emptyCanvas') }}
      </p>
    </div>

    <!-- Add node button -->
    <button
      class="w-full mt-4 flex items-center justify-center gap-2 px-4 py-3 rounded-xl border-2 border-dashed border-gray-300 dark:border-gray-600 text-gray-500 dark:text-gray-400 hover:border-blue-400 dark:hover:border-blue-500 hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
      @click="emit('addNodeAfter', orderedNodes.length > 0 ? orderedNodes[orderedNodes.length - 1].id : null)"
    >
      <PlusIcon class="w-5 h-5" />
      {{ $t('workflowBuilder.addNode') }}
    </button>
  </div>
</template>

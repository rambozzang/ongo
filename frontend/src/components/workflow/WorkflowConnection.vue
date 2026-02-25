<script setup lang="ts">
import { computed } from 'vue'
import type { WfConnection, WfNode } from '@/types/workflowBuilder'

const props = defineProps<{
  connection: WfConnection
  nodes: WfNode[]
  isSelected: boolean
}>()

const emit = defineEmits<{
  select: [connectionId: string]
  delete: [connectionId: string]
}>()

const sourceNode = computed(() => props.nodes.find(n => n.id === props.connection.sourceNodeId))
const targetNode = computed(() => props.nodes.find(n => n.id === props.connection.targetNodeId))

const pathData = computed(() => {
  if (!sourceNode.value || !targetNode.value) return ''

  const sx = sourceNode.value.position.x + 180 // output port is at right
  const sy = sourceNode.value.position.y + 36
  const tx = targetNode.value.position.x // input port is at left
  const ty = targetNode.value.position.y + 36

  const dx = Math.abs(tx - sx)
  const cpOffset = Math.max(60, dx * 0.4)

  return `M ${sx} ${sy} C ${sx + cpOffset} ${sy}, ${tx - cpOffset} ${ty}, ${tx} ${ty}`
})

function onClick(e: MouseEvent) {
  e.stopPropagation()
  emit('select', props.connection.id)
}
</script>

<template>
  <g @click="onClick" class="cursor-pointer">
    <!-- Invisible hit area -->
    <path
      :d="pathData"
      fill="none"
      stroke="transparent"
      stroke-width="16"
    />
    <!-- Visible path -->
    <path
      :d="pathData"
      fill="none"
      :class="[
        'transition-colors',
        isSelected
          ? 'stroke-blue-500 dark:stroke-blue-400'
          : 'stroke-gray-300 dark:stroke-gray-600'
      ]"
      stroke-width="2"
    />
    <!-- Arrow marker -->
    <circle
      v-if="targetNode"
      :cx="targetNode.position.x"
      :cy="targetNode.position.y + 36"
      r="3"
      :class="[
        isSelected
          ? 'fill-blue-500 dark:fill-blue-400'
          : 'fill-gray-300 dark:fill-gray-600'
      ]"
    />
  </g>
</template>

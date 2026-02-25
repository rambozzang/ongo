<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import type { WfNode, WfConnection, WfNodeKind } from '@/types/workflowBuilder'
import WorkflowNode from './WorkflowNode.vue'
import WorkflowConnection from './WorkflowConnection.vue'

const props = defineProps<{
  nodes: WfNode[]
  connections: WfConnection[]
  selectedNodeId: string | null
  zoom: number
  panOffset: { x: number; y: number }
}>()

const emit = defineEmits<{
  selectNode: [nodeId: string | null]
  moveNode: [nodeId: string, position: { x: number; y: number }]
  addConnection: [sourceNodeId: string, targetNodeId: string]
  removeConnection: [connectionId: string]
  setZoom: [zoom: number]
  setPanOffset: [offset: { x: number; y: number }]
  dropNode: [kind: WfNodeKind, position: { x: number; y: number }]
}>()

const svgRef = ref<SVGSVGElement | null>(null)
const isPanning = ref(false)
const panStart = ref({ x: 0, y: 0 })
const dragNodeId = ref<string | null>(null)
const dragNodeStart = ref({ x: 0, y: 0 })
const dragMouseStart = ref({ x: 0, y: 0 })

// Connection dragging
const isDrawingConnection = ref(false)
const connectionSourceNodeId = ref<string | null>(null)
const connectionSourcePortType = ref<'input' | 'output' | null>(null)
const connectionTempEnd = ref({ x: 0, y: 0 })
const connectionSourcePos = ref({ x: 0, y: 0 })

const selectedConnectionId = ref<string | null>(null)

const viewBox = computed(() => {
  return `${-props.panOffset.x / props.zoom} ${-props.panOffset.y / props.zoom} ${(svgRef.value?.clientWidth ?? 1200) / props.zoom} ${(svgRef.value?.clientHeight ?? 800) / props.zoom}`
})

function toSvgCoords(clientX: number, clientY: number): { x: number; y: number } {
  if (!svgRef.value) return { x: clientX, y: clientY }
  const rect = svgRef.value.getBoundingClientRect()
  return {
    x: (clientX - rect.left - props.panOffset.x) / props.zoom,
    y: (clientY - rect.top - props.panOffset.y) / props.zoom,
  }
}

// ─── Panning ──────────────────────────────────────────
function onCanvasMouseDown(e: MouseEvent) {
  if (e.button !== 0) return
  emit('selectNode', null)
  selectedConnectionId.value = null
  isPanning.value = true
  panStart.value = { x: e.clientX - props.panOffset.x, y: e.clientY - props.panOffset.y }
}

function onMouseMove(e: MouseEvent) {
  if (isPanning.value) {
    emit('setPanOffset', {
      x: e.clientX - panStart.value.x,
      y: e.clientY - panStart.value.y,
    })
  }
  if (dragNodeId.value) {
    const dx = (e.clientX - dragMouseStart.value.x) / props.zoom
    const dy = (e.clientY - dragMouseStart.value.y) / props.zoom
    emit('moveNode', dragNodeId.value, {
      x: dragNodeStart.value.x + dx,
      y: dragNodeStart.value.y + dy,
    })
  }
  if (isDrawingConnection.value) {
    const pos = toSvgCoords(e.clientX, e.clientY)
    connectionTempEnd.value = pos
  }
}

function onMouseUp(e: MouseEvent) {
  if (isDrawingConnection.value && connectionSourceNodeId.value) {
    // Check if we're over a node port
    const target = document.elementFromPoint(e.clientX, e.clientY)
    if (target instanceof SVGCircleElement) {
      const nodeGroup = target.closest('g[data-node-id]')
      if (nodeGroup) {
        const targetNodeId = nodeGroup.getAttribute('data-node-id')
        if (targetNodeId && targetNodeId !== connectionSourceNodeId.value) {
          if (connectionSourcePortType.value === 'output') {
            emit('addConnection', connectionSourceNodeId.value, targetNodeId)
          } else {
            emit('addConnection', targetNodeId, connectionSourceNodeId.value)
          }
        }
      }
    }
  }
  isPanning.value = false
  dragNodeId.value = null
  isDrawingConnection.value = false
  connectionSourceNodeId.value = null
  connectionSourcePortType.value = null
}

// ─── Node dragging ────────────────────────────────────
function onNodeDragStart(nodeId: string, e: MouseEvent) {
  const node = props.nodes.find(n => n.id === nodeId)
  if (!node) return
  dragNodeId.value = nodeId
  dragNodeStart.value = { ...node.position }
  dragMouseStart.value = { x: e.clientX, y: e.clientY }
}

// ─── Port dragging (connections) ──────────────────────
function onPortDragStart(nodeId: string, _portId: string, portType: 'input' | 'output', e: MouseEvent) {
  isDrawingConnection.value = true
  connectionSourceNodeId.value = nodeId
  connectionSourcePortType.value = portType
  const node = props.nodes.find(n => n.id === nodeId)
  if (!node) return

  if (portType === 'output') {
    connectionSourcePos.value = { x: node.position.x + 180, y: node.position.y + 36 }
  } else {
    connectionSourcePos.value = { x: node.position.x, y: node.position.y + 36 }
  }
  connectionTempEnd.value = toSvgCoords(e.clientX, e.clientY)
}

// ─── Wheel zoom ───────────────────────────────────────
function onWheel(e: WheelEvent) {
  e.preventDefault()
  const delta = e.deltaY > 0 ? -0.1 : 0.1
  emit('setZoom', props.zoom + delta)
}

// ─── Connection select ────────────────────────────────
function onConnectionSelect(connectionId: string) {
  selectedConnectionId.value = connectionId
  emit('selectNode', null)
}

// ─── Drag-and-drop from palette ───────────────────────
function onDragOver(e: DragEvent) {
  e.preventDefault()
  if (e.dataTransfer) {
    e.dataTransfer.dropEffect = 'copy'
  }
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  const kind = e.dataTransfer?.getData('nodeKind') as WfNodeKind | undefined
  if (!kind) return
  const pos = toSvgCoords(e.clientX, e.clientY)
  emit('dropNode', kind, pos)
}

// ─── Keyboard ─────────────────────────────────────────
function onKeyDown(e: KeyboardEvent) {
  if (e.key === 'Delete' || e.key === 'Backspace') {
    if (selectedConnectionId.value) {
      emit('removeConnection', selectedConnectionId.value)
      selectedConnectionId.value = null
    }
  }
  if (e.key === 'Escape') {
    emit('selectNode', null)
    selectedConnectionId.value = null
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeyDown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeyDown)
})

// Connection drawing temp path
const tempConnectionPath = computed(() => {
  if (!isDrawingConnection.value) return ''
  const sx = connectionSourcePos.value.x
  const sy = connectionSourcePos.value.y
  const tx = connectionTempEnd.value.x
  const ty = connectionTempEnd.value.y
  const dx = Math.abs(tx - sx)
  const cpOffset = Math.max(60, dx * 0.4)
  return `M ${sx} ${sy} C ${sx + cpOffset} ${sy}, ${tx - cpOffset} ${ty}, ${tx} ${ty}`
})
</script>

<template>
  <svg
    ref="svgRef"
    class="w-full h-full bg-gray-50 dark:bg-gray-900"
    :viewBox="viewBox"
    @mousedown.self="onCanvasMouseDown"
    @mousemove="onMouseMove"
    @mouseup="onMouseUp"
    @wheel="onWheel"
    @dragover="onDragOver"
    @drop="onDrop"
    tabindex="0"
  >
    <!-- Grid pattern -->
    <defs>
      <pattern id="grid" width="20" height="20" patternUnits="userSpaceOnUse">
        <circle cx="10" cy="10" r="0.5" class="fill-gray-300 dark:fill-gray-700" />
      </pattern>
      <pattern id="gridLarge" width="100" height="100" patternUnits="userSpaceOnUse">
        <rect width="100" height="100" fill="url(#grid)" />
        <circle cx="50" cy="50" r="1" class="fill-gray-300 dark:fill-gray-600" />
      </pattern>
    </defs>
    <rect x="-10000" y="-10000" width="20000" height="20000" fill="url(#gridLarge)" />

    <!-- Connections -->
    <WorkflowConnection
      v-for="conn in connections"
      :key="conn.id"
      :connection="conn"
      :nodes="nodes"
      :is-selected="selectedConnectionId === conn.id"
      @select="onConnectionSelect"
      @delete="emit('removeConnection', $event)"
    />

    <!-- Temp connection being drawn -->
    <path
      v-if="isDrawingConnection"
      :d="tempConnectionPath"
      fill="none"
      stroke="#3b82f6"
      stroke-width="2"
      stroke-dasharray="6 3"
      class="pointer-events-none"
    />

    <!-- Nodes -->
    <g v-for="node in nodes" :key="node.id" :data-node-id="node.id">
      <WorkflowNode
        :node="node"
        :is-selected="selectedNodeId === node.id"
        :zoom="zoom"
        @select="emit('selectNode', $event)"
        @drag-start="onNodeDragStart"
        @port-drag-start="onPortDragStart"
        @delete="$emit('selectNode', null)"
      />
    </g>
  </svg>
</template>

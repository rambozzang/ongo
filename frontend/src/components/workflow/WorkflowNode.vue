<script setup lang="ts">
import { computed } from 'vue'
import type { WfNode, WfNodeType } from '@/types/workflowBuilder'
import { NODE_DEFINITIONS } from '@/stores/workflowBuilder'
import {
  CloudArrowUpIcon,
  PlayCircleIcon,
  ChatBubbleLeftIcon,
  ClockIcon,
  ChartBarIcon,
  UserGroupIcon,
  ExclamationTriangleIcon,
  FunnelIcon,
  TagIcon,
  CalendarDaysIcon,
  EyeIcon,
  FilmIcon,
  LanguageIcon,
  BellAlertIcon,
  RocketLaunchIcon,
  ArrowsRightLeftIcon,
  SparklesIcon,
  ArrowPathIcon,
  PhotoIcon,
  CubeTransparentIcon,
} from '@heroicons/vue/24/outline'

const props = defineProps<{
  node: WfNode
  isSelected: boolean
  zoom: number
}>()

const emit = defineEmits<{
  select: [nodeId: string]
  dragStart: [nodeId: string, event: MouseEvent]
  portDragStart: [nodeId: string, portId: string, portType: 'input' | 'output', event: MouseEvent]
  delete: [nodeId: string]
}>()

const iconMap: Record<string, typeof CubeTransparentIcon> = {
  CloudArrowUpIcon,
  PlayCircleIcon,
  ChatBubbleLeftIcon,
  ClockIcon,
  ChartBarIcon,
  UserGroupIcon,
  ExclamationTriangleIcon,
  FunnelIcon,
  TagIcon,
  CalendarDaysIcon,
  EyeIcon,
  FilmIcon,
  LanguageIcon,
  BellAlertIcon,
  RocketLaunchIcon,
  ArrowsRightLeftIcon,
  SparklesIcon,
  ArrowPathIcon,
  PhotoIcon,
}

const def = computed(() => NODE_DEFINITIONS.find(d => d.kind === props.node.kind))
const iconComponent = computed(() => {
  const iconName = def.value?.icon ?? 'CubeTransparentIcon'
  return iconMap[iconName] ?? CubeTransparentIcon
})

const nodeColorClasses = computed(() => {
  const typeColors: Record<WfNodeType, { bg: string; border: string; icon: string; selectedBorder: string }> = {
    trigger: {
      bg: 'bg-blue-50 dark:bg-blue-900/20',
      border: 'border-blue-200 dark:border-blue-700',
      icon: 'text-blue-600 dark:text-blue-400 bg-blue-100 dark:bg-blue-900/40',
      selectedBorder: 'border-blue-500 dark:border-blue-400 ring-2 ring-blue-200 dark:ring-blue-800',
    },
    condition: {
      bg: 'bg-amber-50 dark:bg-amber-900/20',
      border: 'border-amber-200 dark:border-amber-700',
      icon: 'text-amber-600 dark:text-amber-400 bg-amber-100 dark:bg-amber-900/40',
      selectedBorder: 'border-amber-500 dark:border-amber-400 ring-2 ring-amber-200 dark:ring-amber-800',
    },
    action: {
      bg: 'bg-green-50 dark:bg-green-900/20',
      border: 'border-green-200 dark:border-green-700',
      icon: 'text-green-600 dark:text-green-400 bg-green-100 dark:bg-green-900/40',
      selectedBorder: 'border-green-500 dark:border-green-400 ring-2 ring-green-200 dark:ring-green-800',
    },
  }
  return typeColors[props.node.type]
})

const inputPort = computed(() => props.node.ports.find(p => p.type === 'input'))
const outputPort = computed(() => props.node.ports.find(p => p.type === 'output'))

function onMouseDown(e: MouseEvent) {
  e.stopPropagation()
  emit('select', props.node.id)
  emit('dragStart', props.node.id, e)
}

function onPortMouseDown(portId: string, portType: 'input' | 'output', e: MouseEvent) {
  e.stopPropagation()
  emit('portDragStart', props.node.id, portId, portType, e)
}
</script>

<template>
  <g
    :transform="`translate(${node.position.x}, ${node.position.y})`"
    class="cursor-grab active:cursor-grabbing"
    @mousedown="onMouseDown"
  >
    <!-- Node body -->
    <foreignObject width="180" height="72" x="0" y="0">
      <div
        :class="[
          'w-[180px] h-[72px] rounded-xl border-2 shadow-sm transition-all flex items-center gap-3 px-3',
          nodeColorClasses.bg,
          isSelected ? nodeColorClasses.selectedBorder : nodeColorClasses.border,
        ]"
      >
        <div :class="['w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0', nodeColorClasses.icon]">
          <component :is="iconComponent" class="w-5 h-5" />
        </div>
        <div class="min-w-0 flex-1">
          <p class="text-xs font-semibold text-gray-900 dark:text-gray-100 truncate">
            {{ node.label }}
          </p>
          <p class="text-[10px] text-gray-500 dark:text-gray-400 truncate">
            {{ def?.description }}
          </p>
        </div>
      </div>
    </foreignObject>

    <!-- Input port (left) -->
    <circle
      v-if="inputPort"
      :cx="0"
      :cy="36"
      r="6"
      class="fill-gray-300 dark:fill-gray-600 stroke-white dark:stroke-gray-800 stroke-2 cursor-crosshair hover:fill-blue-500 dark:hover:fill-blue-400 transition-colors"
      @mousedown.stop="onPortMouseDown(inputPort.id, 'input', $event)"
    />

    <!-- Output port (right) -->
    <circle
      v-if="outputPort"
      :cx="180"
      :cy="36"
      r="6"
      class="fill-gray-300 dark:fill-gray-600 stroke-white dark:stroke-gray-800 stroke-2 cursor-crosshair hover:fill-blue-500 dark:hover:fill-blue-400 transition-colors"
      @mousedown.stop="onPortMouseDown(outputPort.id, 'output', $event)"
    />
  </g>
</template>

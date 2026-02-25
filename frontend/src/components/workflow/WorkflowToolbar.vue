<script setup lang="ts">
import { computed } from 'vue'
import {
  ArrowUturnLeftIcon,
  PlayIcon,
  StopIcon,
  ArrowPathIcon,
  MagnifyingGlassMinusIcon,
  MagnifyingGlassPlusIcon,
  ArrowsPointingOutIcon,
  CheckCircleIcon,
} from '@heroicons/vue/24/outline'

const props = defineProps<{
  workflowName: string
  isEnabled: boolean
  isDirty: boolean
  zoom: number
  isSaving: boolean
}>()

const emit = defineEmits<{
  save: []
  testRun: []
  toggleEnabled: []
  zoomIn: []
  zoomOut: []
  resetView: []
  back: []
  updateName: [name: string]
}>()

const zoomPercent = computed(() => Math.round(props.zoom * 100))
</script>

<template>
  <div class="flex items-center justify-between px-4 py-2.5 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
    <!-- Left: Back + Name -->
    <div class="flex items-center gap-3">
      <button
        class="p-1.5 text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
        @click="emit('back')"
        :title="$t('action.back')"
      >
        <ArrowUturnLeftIcon class="w-5 h-5" />
      </button>
      <div class="h-6 w-px bg-gray-200 dark:bg-gray-700" />
      <input
        :value="workflowName"
        @input="emit('updateName', ($event.target as HTMLInputElement).value)"
        class="text-lg font-semibold text-gray-900 dark:text-gray-100 bg-transparent border-none focus:outline-none focus:ring-0 px-1 max-w-[240px] truncate"
        :placeholder="$t('workflowBuilder.untitledWorkflow')"
      />
      <span v-if="isDirty" class="text-xs text-amber-500 dark:text-amber-400 font-medium">
        {{ $t('workflowBuilder.unsaved') }}
      </span>
    </div>

    <!-- Center: Zoom controls -->
    <div class="hidden tablet:flex items-center gap-1 bg-gray-100 dark:bg-gray-700 rounded-lg p-1">
      <button
        class="p-1.5 text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 rounded transition-colors"
        @click="emit('zoomOut')"
        :title="$t('workflowBuilder.zoomOut')"
      >
        <MagnifyingGlassMinusIcon class="w-4 h-4" />
      </button>
      <span class="text-xs text-gray-600 dark:text-gray-400 font-medium min-w-[40px] text-center">
        {{ zoomPercent }}%
      </span>
      <button
        class="p-1.5 text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 rounded transition-colors"
        @click="emit('zoomIn')"
        :title="$t('workflowBuilder.zoomIn')"
      >
        <MagnifyingGlassPlusIcon class="w-4 h-4" />
      </button>
      <div class="h-4 w-px bg-gray-300 dark:bg-gray-600 mx-1" />
      <button
        class="p-1.5 text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 rounded transition-colors"
        @click="emit('resetView')"
        :title="$t('workflowBuilder.resetView')"
      >
        <ArrowsPointingOutIcon class="w-4 h-4" />
      </button>
    </div>

    <!-- Right: Actions -->
    <div class="flex items-center gap-2">
      <!-- Toggle enable/disable -->
      <button
        @click="emit('toggleEnabled')"
        :class="[
          'inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors',
          isEnabled
            ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300 hover:bg-green-200 dark:hover:bg-green-900/50'
            : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-600'
        ]"
      >
        <CheckCircleIcon v-if="isEnabled" class="w-4 h-4" />
        <StopIcon v-else class="w-4 h-4" />
        {{ isEnabled ? $t('workflowBuilder.active') : $t('workflowBuilder.inactive') }}
      </button>

      <!-- Test run -->
      <button
        @click="emit('testRun')"
        class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300 hover:bg-amber-200 dark:hover:bg-amber-900/50 transition-colors"
      >
        <PlayIcon class="w-4 h-4" />
        {{ $t('workflowBuilder.testRun') }}
      </button>

      <!-- Save -->
      <button
        @click="emit('save')"
        :disabled="isSaving"
        class="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-lg text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
      >
        <ArrowPathIcon v-if="isSaving" class="w-4 h-4 animate-spin" />
        {{ $t('action.save') }}
      </button>
    </div>
  </div>
</template>

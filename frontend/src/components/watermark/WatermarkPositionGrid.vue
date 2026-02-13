<script setup lang="ts">
import type { WatermarkPosition } from '@/types/watermark'

const props = defineProps<{
  modelValue: WatermarkPosition
}>()

const emit = defineEmits<{
  'update:modelValue': [value: WatermarkPosition]
}>()

interface PositionCell {
  position: WatermarkPosition
  label: string
}

const positions: PositionCell[][] = [
  [
    { position: 'top-left', label: '좌상' },
    { position: 'top-center', label: '중상' },
    { position: 'top-right', label: '우상' },
  ],
  [
    { position: 'center-left', label: '좌중' },
    { position: 'center', label: '중앙' },
    { position: 'center-right', label: '우중' },
  ],
  [
    { position: 'bottom-left', label: '좌하' },
    { position: 'bottom-center', label: '중하' },
    { position: 'bottom-right', label: '우하' },
  ],
]

function selectPosition(position: WatermarkPosition) {
  emit('update:modelValue', position)
}
</script>

<template>
  <div class="space-y-2">
    <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
      위치
    </label>
    <div class="inline-grid grid-cols-3 gap-1.5 p-2 bg-gray-100 dark:bg-gray-700 rounded-lg">
      <template v-for="(row, rowIndex) in positions" :key="rowIndex">
        <button
          v-for="cell in row"
          :key="cell.position"
          type="button"
          class="relative flex flex-col items-center justify-center w-16 h-14 rounded-md text-xs font-medium transition-all duration-150"
          :class="
            props.modelValue === cell.position
              ? 'bg-primary-600 text-white shadow-sm dark:bg-primary-500'
              : 'bg-white dark:bg-gray-600 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-500 border border-gray-200 dark:border-gray-500'
          "
          :title="cell.label"
          @click="selectPosition(cell.position)"
        >
          <span
            class="w-2 h-2 rounded-full mb-1"
            :class="
              props.modelValue === cell.position
                ? 'bg-white'
                : 'bg-gray-400 dark:bg-gray-400'
            "
          ></span>
          <span class="leading-none">{{ cell.label }}</span>
        </button>
      </template>
    </div>
  </div>
</template>

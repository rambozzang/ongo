<template>
  <div ref="dropdownRef" class="relative">
    <!-- Trigger Button -->
    <button
      :id="triggerId"
      class="btn-secondary inline-flex items-center gap-2"
      :aria-expanded="isOpen"
      aria-haspopup="true"
      :aria-controls="menuId"
      @click="toggleDropdown"
      @keydown.escape="closeDropdown"
    >
      <ArrowDownTrayIcon class="h-5 w-5" aria-hidden="true" />
      내보내기
    </button>

    <!-- Dropdown Menu -->
    <Transition
      enter-active-class="transition duration-100 ease-out"
      enter-from-class="transform scale-95 opacity-0"
      enter-to-class="transform scale-100 opacity-100"
      leave-active-class="transition duration-75 ease-in"
      leave-from-class="transform scale-100 opacity-100"
      leave-to-class="transform scale-95 opacity-0"
    >
      <div
        v-if="isOpen"
        :id="menuId"
        role="menu"
        :aria-labelledby="triggerId"
        class="absolute right-0 z-50 mt-2 w-48 origin-top-right rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 py-1 shadow-lg"
        @keydown.escape="closeDropdown"
      >
        <button
          role="menuitem"
          class="flex w-full items-center gap-3 px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700"
          @click="handleExportCSV"
        >
          <DocumentTextIcon class="h-5 w-5 text-gray-400" aria-hidden="true" />
          <span>CSV로 내보내기</span>
        </button>
        <button
          role="menuitem"
          class="flex w-full items-center gap-3 px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700"
          @click="handleExportJSON"
        >
          <CodeBracketIcon class="h-5 w-5 text-gray-400" aria-hidden="true" />
          <span>JSON으로 내보내기</span>
        </button>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, useId } from 'vue'
import { ArrowDownTrayIcon, DocumentTextIcon, CodeBracketIcon } from '@heroicons/vue/24/outline'
import { exportToCSV, exportToJSON, type ColumnDefinition } from '@/utils/export'

interface Props {
  data: Record<string, unknown>[]
  columns: ColumnDefinition<Record<string, unknown>>[]
  filenamePrefix: string
}

const props = defineProps<Props>()

const id = useId()
const triggerId = `export-trigger-${id}`
const menuId = `export-menu-${id}`

const isOpen = ref(false)
const dropdownRef = ref<HTMLElement | null>(null)

function toggleDropdown() {
  isOpen.value = !isOpen.value
}

function closeDropdown() {
  isOpen.value = false
}

function handleExportCSV() {
  const filename = `${props.filenamePrefix}.csv`
  exportToCSV(props.data, props.columns, filename)
  closeDropdown()
}

function handleExportJSON() {
  const filename = `${props.filenamePrefix}.json`
  exportToJSON(props.data, filename)
  closeDropdown()
}

// Close dropdown when clicking outside
function handleClickOutside(event: MouseEvent) {
  if (dropdownRef.value && !dropdownRef.value.contains(event.target as Node)) {
    closeDropdown()
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

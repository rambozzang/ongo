<script setup lang="ts">
import { ref } from 'vue'
import { ArrowDownTrayIcon } from '@heroicons/vue/24/outline'

const emit = defineEmits<{
  export: [format: string]
}>()

const showMenu = ref(false)
const formats = ['SRT', 'VTT', 'ASS', 'TXT']

function handleExport(format: string) {
  showMenu.value = false
  emit('export', format)
}
</script>

<template>
  <div class="relative">
    <button
      class="btn-secondary inline-flex items-center gap-1.5 text-xs"
      @click="showMenu = !showMenu"
    >
      <ArrowDownTrayIcon class="h-4 w-4" />
      {{ $t('subtitleEditor.export.title') }}
    </button>
    <div
      v-if="showMenu"
      class="absolute right-0 top-full z-10 mt-1 w-48 rounded-lg border border-gray-200 bg-white py-1 shadow-lg dark:border-gray-700 dark:bg-gray-800"
    >
      <button
        v-for="fmt in formats"
        :key="fmt"
        class="block w-full px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-50 dark:text-gray-300 dark:hover:bg-gray-700"
        @click="handleExport(fmt)"
      >
        {{ $t('subtitleEditor.export.formats.' + fmt) }}
      </button>
    </div>
  </div>
</template>

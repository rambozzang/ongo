<script setup lang="ts">
import { FolderIcon } from '@heroicons/vue/24/outline'
import type { LibraryFolder } from '@/types/contentLibrary'

defineProps<{
  folder: LibraryFolder
}>()

defineEmits<{
  select: [id: number]
}>()
</script>

<template>
  <div
    class="group cursor-pointer overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-900"
    @click="$emit('select', folder.id)"
  >
    <!-- Color stripe -->
    <div
      class="h-2"
      :style="{ backgroundColor: folder.color }"
    />

    <div class="p-4">
      <div class="mb-2 flex items-center gap-2">
        <FolderIcon
          class="h-5 w-5 flex-shrink-0"
          :style="{ color: folder.color }"
        />
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">
          {{ folder.name }}
        </h3>
      </div>

      <p class="text-xs text-gray-500 dark:text-gray-400">
        {{ folder.itemCount.toLocaleString('ko-KR') }}개 항목
      </p>
    </div>
  </div>
</template>

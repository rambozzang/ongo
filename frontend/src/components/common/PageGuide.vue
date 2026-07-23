<template>
  <div class="mb-4">
    <button
      class="flex w-full items-center gap-3 rounded-lg border bg-white px-4 py-3 text-sm text-gray-600 transition-colors hover:border-primary-200 hover:bg-primary-50/30 dark:bg-gray-900 dark:text-gray-400 dark:hover:bg-gray-800"
      style="border-color: var(--border-default)"
      @click="toggle"
    >
      <InformationCircleIcon class="h-5 w-5 flex-shrink-0 text-primary-500" />
      <span class="font-semibold">{{ title }}</span>
      <span class="text-xs text-gray-400">{{ isOpen ? '접기' : '자세히 보기' }}</span>
      <ChevronUpIcon v-if="isOpen" class="ml-auto h-4 w-4" />
      <ChevronDownIcon v-else class="ml-auto h-4 w-4" />
    </button>
    <div
      class="grid transition-all duration-300 ease-in-out"
      :class="isOpen ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'"
    >
      <div class="overflow-hidden">
        <ul class="mt-2 space-y-1.5 rounded-lg border bg-white px-4 py-3 dark:bg-gray-900" style="border-color: var(--border-default)">
          <li
            v-for="(item, index) in items"
            :key="index"
            class="flex items-start gap-2 text-sm text-gray-600 dark:text-gray-400"
          >
            <span class="mt-1.5 h-1.5 w-1.5 flex-shrink-0 rounded-full bg-primary-400" />
            <span>{{ item }}</span>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { InformationCircleIcon, ChevronDownIcon, ChevronUpIcon } from '@heroicons/vue/24/outline'

const props = defineProps<{
  title: string
  items: string[]
}>()

const storageKey = `pageGuide_${props.title}`
const isOpen = ref(false)

onMounted(() => {
  const saved = localStorage.getItem(storageKey)
  if (saved !== null) {
    isOpen.value = saved === 'true'
  }
})

function toggle() {
  isOpen.value = !isOpen.value
  localStorage.setItem(storageKey, String(isOpen.value))
}
</script>

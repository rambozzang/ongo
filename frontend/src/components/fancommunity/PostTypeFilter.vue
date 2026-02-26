<script setup lang="ts">
defineProps<{
  selected: string
}>()

const emit = defineEmits<{
  'update:selected': [value: string]
}>()

interface FilterOption {
  value: string
  label: string
  activeClass: string
}

const filterOptions: FilterOption[] = [
  { value: 'ALL', label: '전체', activeClass: 'bg-blue-600 text-white' },
  { value: 'ANNOUNCEMENT', label: '공지', activeClass: 'bg-red-600 text-white' },
  { value: 'DISCUSSION', label: '토론', activeClass: 'bg-blue-600 text-white' },
  { value: 'POLL', label: '투표', activeClass: 'bg-purple-600 text-white' },
  { value: 'QNA', label: 'Q&A', activeClass: 'bg-green-600 text-white' },
  { value: 'BEHIND_SCENES', label: '비하인드', activeClass: 'bg-yellow-600 text-white' },
  { value: 'FAN_ART', label: '팬아트', activeClass: 'bg-pink-600 text-white' },
]
</script>

<template>
  <div class="flex flex-wrap gap-2">
    <button
      v-for="option in filterOptions"
      :key="option.value"
      class="rounded-full px-4 py-1.5 text-sm font-medium transition-colors"
      :class="selected === option.value
        ? option.activeClass
        : 'bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-gray-700'"
      @click="emit('update:selected', option.value)"
    >
      {{ option.label }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { MagnifyingGlassIcon } from '@heroicons/vue/24/outline'

withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
  }>(),
  {
    placeholder: '검색...',
  },
)

defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <div class="relative">
    <MagnifyingGlassIcon class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
    <input
      type="text"
      :value="modelValue"
      :placeholder="placeholder"
      class="input-field pl-10"
      @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
  </div>
</template>

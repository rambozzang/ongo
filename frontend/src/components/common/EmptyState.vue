<template>
  <div
    class="flex animate-fade-in flex-col items-center justify-center py-12 text-center"
    :class="variant === 'compact' ? 'py-8' : 'py-12'"
  >
    <div
      class="mb-4 flex items-center justify-center rounded-full bg-gray-100 dark:bg-gray-800"
      :class="variant === 'compact' ? 'h-12 w-12' : 'h-16 w-16'"
    >
      <component
        v-if="icon"
        :is="icon"
        class="text-gray-400 dark:text-gray-500"
        :class="variant === 'compact' ? 'h-6 w-6' : 'h-8 w-8'"
      />
    </div>
    <h3
      class="mb-2 font-semibold text-gray-900 dark:text-gray-100"
      :class="variant === 'compact' ? 'text-base' : 'text-lg'"
    >
      {{ title }}
    </h3>
    <p class="mb-6 max-w-md text-sm leading-relaxed text-gray-600 dark:text-gray-400">
      {{ description }}
    </p>
    <div v-if="actionLabel || actionTo" class="flex flex-col gap-3">
      <router-link v-if="actionTo" :to="actionTo" class="btn-primary">
        {{ actionLabel }}
      </router-link>
      <button v-else-if="actionLabel" class="btn-primary" @click="$emit('action')">
        {{ actionLabel }}
      </button>
      <slot name="action" />
      <router-link
        v-if="secondaryActionLabel && secondaryActionTo"
        :to="secondaryActionTo"
        class="text-sm text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
      >
        {{ secondaryActionLabel }}
      </router-link>
    </div>
    <slot v-else name="action" />
  </div>
</template>

<script setup lang="ts">
import { type Component } from 'vue'

withDefaults(
  defineProps<{
    title: string
    description: string
    icon?: Component
    actionLabel?: string
    actionTo?: string
    secondaryActionLabel?: string
    secondaryActionTo?: string
    variant?: 'default' | 'compact'
  }>(),
  {
    icon: undefined,
    actionLabel: undefined,
    actionTo: undefined,
    secondaryActionLabel: undefined,
    secondaryActionTo: undefined,
    variant: 'default',
  },
)

defineEmits<{
  action: []
}>()
</script>

<style scoped>
@keyframes fade-in {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-fade-in {
  animation: fade-in 200ms ease-out;
}
</style>

<template>
  <div
    class="flex animate-fade-in flex-col items-center justify-center rounded-[11px] border border-dashed border-line-control bg-surface-card px-6 py-14 text-center"
    :class="variant === 'compact' ? 'py-8' : 'py-12'"
  >
    <div
      class="mb-4 flex items-center justify-center rounded-lg border border-line-control bg-surface-raised"
      :class="variant === 'compact' ? 'h-12 w-12' : 'h-16 w-16'"
    >
      <component
        :is="icon"
        v-if="icon"
        class="text-content-quaternary"
        :class="variant === 'compact' ? 'h-6 w-6' : 'h-8 w-8'"
      />
    </div>
    <h3
      class="mb-2 text-h3 text-content"
      :class="variant === 'compact' ? 'text-base' : 'text-lg'"
    >
      {{ title }}
    </h3>
    <p class="mb-6 max-w-md text-body-sm leading-relaxed text-content-secondary">
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
        class="text-body-sm text-accent transition-colors hover:text-accent-hover"
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

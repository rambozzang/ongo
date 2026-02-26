<template>
  <Teleport to="body">
    <div
      v-if="modelValue"
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      :aria-label="$t('influencerMatch.modal.title')"
    >
      <div class="fixed inset-0 bg-black/50" aria-hidden="true" @click="close" />
      <div class="relative w-full max-w-lg rounded-xl bg-white dark:bg-gray-800 p-6 shadow-xl space-y-4">
        <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">
          {{ $t('influencerMatch.modal.title') }}
        </h2>

        <!-- Influencer Name -->
        <div class="rounded-lg bg-gray-50 dark:bg-gray-700/50 px-3 py-2">
          <span class="text-xs text-gray-500 dark:text-gray-400">{{ $t('influencerMatch.modal.to') }}</span>
          <p class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ influencerName }}</p>
        </div>

        <!-- Message -->
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ $t('influencerMatch.modal.message') }}
          </label>
          <textarea
            v-model="message"
            rows="4"
            class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            :placeholder="$t('influencerMatch.modal.messagePlaceholder')"
          />
        </div>

        <!-- Budget -->
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ $t('influencerMatch.modal.budget') }}
          </label>
          <div class="relative">
            <input
              v-model.number="budget"
              type="number"
              min="0"
              step="10000"
              class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 pr-12 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
              :placeholder="$t('influencerMatch.modal.budgetPlaceholder')"
            />
            <span class="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-gray-400 dark:text-gray-500">KRW</span>
          </div>
          <p v-if="budget > 0" class="mt-1 text-xs text-gray-500 dark:text-gray-400">
            {{ formattedBudget }}
          </p>
        </div>

        <!-- Actions -->
        <div class="flex justify-end gap-3 pt-2">
          <button class="btn-secondary" @click="close">
            {{ $t('influencerMatch.modal.cancel') }}
          </button>
          <button
            class="btn-primary"
            :disabled="!canSend"
            @click="handleSend"
          >
            {{ $t('influencerMatch.modal.send') }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'

const props = defineProps<{
  modelValue: boolean
  influencerName: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  send: [payload: { message: string; budget: number }]
}>()

const message = ref('')
const budget = ref<number>(0)

const canSend = computed(() => message.value.trim().length > 0 && budget.value > 0)

const formattedBudget = computed(() => {
  return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(budget.value)
})

function close() {
  emit('update:modelValue', false)
}

function handleSend() {
  if (!canSend.value) return
  emit('send', { message: message.value.trim(), budget: budget.value })
}

watch(() => props.modelValue, (isOpen) => {
  if (isOpen) {
    message.value = ''
    budget.value = 0
  }
})
</script>

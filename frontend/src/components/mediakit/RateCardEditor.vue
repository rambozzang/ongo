<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('mediaKit.rateCards') }}
      </h3>
      <button
        class="inline-flex items-center gap-1 rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-xs font-medium text-gray-700 transition-colors hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
        @click="addCard"
      >
        <PlusIcon class="h-4 w-4" />
        {{ $t('mediaKit.addRateCard') }}
      </button>
    </div>

    <!-- Empty state -->
    <div
      v-if="localCards.length === 0"
      class="flex flex-col items-center justify-center rounded-lg border border-dashed border-gray-300 py-8 dark:border-gray-600"
    >
      <CurrencyDollarIcon class="mb-2 h-8 w-8 text-gray-400 dark:text-gray-500" />
      <p class="text-sm text-gray-500 dark:text-gray-400">{{ $t('mediaKit.noRateCards') }}</p>
      <button
        class="mt-2 text-sm font-medium text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
        @click="addCard"
      >
        {{ $t('mediaKit.addFirstRateCard') }}
      </button>
    </div>

    <!-- Rate card list -->
    <div v-else class="space-y-3">
      <div
        v-for="(card, index) in localCards"
        :key="index"
        class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900"
      >
        <div class="flex items-start justify-between gap-3">
          <div class="flex-1 space-y-3">
            <!-- Type selector -->
            <div>
              <label class="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-400">
                {{ $t('mediaKit.rateCardType') }}
              </label>
              <select
                :value="card.type"
                class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                @change="updateCard(index, 'type', ($event.target as HTMLSelectElement).value)"
              >
                <option
                  v-for="typeOpt in rateCardTypes"
                  :key="typeOpt.value"
                  :value="typeOpt.value"
                >
                  {{ $t(`mediaKit.rateCardTypes.${typeOpt.value}`) }}
                </option>
              </select>
            </div>

            <!-- Price input -->
            <div>
              <label class="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-400">
                {{ $t('mediaKit.price') }}
              </label>
              <div class="relative">
                <input
                  :value="card.price"
                  type="number"
                  min="0"
                  step="10000"
                  class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 pr-12 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                  :placeholder="$t('mediaKit.pricePlaceholder')"
                  @input="updateCard(index, 'price', Number(($event.target as HTMLInputElement).value))"
                />
                <span class="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-gray-400 dark:text-gray-500">
                  KRW
                </span>
              </div>
              <p v-if="card.price > 0" class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                {{ formatKRW(card.price) }}
              </p>
            </div>

            <!-- Description input -->
            <div>
              <label class="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-400">
                {{ $t('mediaKit.rateCardDescription') }}
              </label>
              <input
                :value="card.description"
                type="text"
                class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                :placeholder="$t('mediaKit.rateCardDescPlaceholder')"
                @input="updateCard(index, 'description', ($event.target as HTMLInputElement).value)"
              />
            </div>
          </div>

          <!-- Delete button -->
          <button
            class="mt-5 flex-shrink-0 rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-red-50 hover:text-red-500 dark:hover:bg-red-900/20 dark:hover:text-red-400"
            :title="$t('mediaKit.removeRateCard')"
            @click="removeCard(index)"
          >
            <TrashIcon class="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { PlusIcon, TrashIcon, CurrencyDollarIcon } from '@heroicons/vue/24/outline'
import type { RateCard } from '@/types/mediaKit'

const props = defineProps<{
  rateCards: RateCard[]
}>()

const emit = defineEmits<{
  update: [rateCards: RateCard[]]
}>()

const localCards = ref<RateCard[]>([...props.rateCards])

watch(
  () => props.rateCards,
  (newCards) => {
    localCards.value = [...newCards]
  },
)

const rateCardTypes = [
  { value: 'SPONSORED_VIDEO' },
  { value: 'PRODUCT_PLACEMENT' },
  { value: 'STORY_POST' },
  { value: 'SHORT_FORM' },
  { value: 'LIVE_STREAM' },
  { value: 'BUNDLE' },
]

function formatKRW(value: number): string {
  return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(value)
}

function addCard() {
  localCards.value.push({
    type: 'SPONSORED_VIDEO',
    price: 0,
    currency: 'KRW',
    description: '',
  })
  emit('update', [...localCards.value])
}

function removeCard(index: number) {
  localCards.value.splice(index, 1)
  emit('update', [...localCards.value])
}

function updateCard(index: number, field: keyof RateCard, value: string | number) {
  const updated = { ...localCards.value[index], [field]: value } as RateCard
  localCards.value[index] = updated
  emit('update', [...localCards.value])
}
</script>

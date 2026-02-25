<script setup lang="ts">
import { computed } from 'vue'
import {
  PlusIcon,
  TrashIcon,
  PhotoIcon,
} from '@heroicons/vue/24/outline'
import type { AbTestType, VariantLabel } from '@/types/abtest'

interface VariantItem {
  label: VariantLabel
  value: string
}

const props = defineProps<{
  variants: VariantItem[]
  testType: AbTestType
}>()

const emit = defineEmits<{
  update: [variants: VariantItem[]]
}>()

const allLabels: VariantLabel[] = ['A', 'B', 'C', 'D']

const variantColors = computed(() => ({
  A: {
    bg: 'bg-blue-50 dark:bg-blue-900/20',
    border: 'border-blue-300 dark:border-blue-700',
    badge: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
    bar: 'bg-blue-500',
  },
  B: {
    bg: 'bg-purple-50 dark:bg-purple-900/20',
    border: 'border-purple-300 dark:border-purple-700',
    badge: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
    bar: 'bg-purple-500',
  },
  C: {
    bg: 'bg-orange-50 dark:bg-orange-900/20',
    border: 'border-orange-300 dark:border-orange-700',
    badge: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
    bar: 'bg-orange-500',
  },
  D: {
    bg: 'bg-teal-50 dark:bg-teal-900/20',
    border: 'border-teal-300 dark:border-teal-700',
    badge: 'bg-teal-100 text-teal-700 dark:bg-teal-900/30 dark:text-teal-400',
    bar: 'bg-teal-500',
  },
}))

const canAdd = computed(() => props.variants.length < 4)
const canRemove = computed(() => props.variants.length > 2)

function addVariant() {
  if (!canAdd.value) return
  const usedLabels = props.variants.map(v => v.label)
  const nextLabel = allLabels.find(l => !usedLabels.includes(l))
  if (!nextLabel) return
  const updated = [...props.variants, { label: nextLabel, value: '' }]
  emit('update', updated)
}

function removeVariant(index: number) {
  if (!canRemove.value) return
  const updated = props.variants.filter((_, i) => i !== index)
  emit('update', updated)
}

function updateValue(index: number, value: string) {
  const updated = props.variants.map((v, i) => i === index ? { ...v, value } : v)
  emit('update', updated)
}

function getColor(label: VariantLabel) {
  return variantColors.value[label]
}

const isThumbnail = computed(() => props.testType === 'THUMBNAIL')
const isTitle = computed(() => props.testType === 'TITLE')
</script>

<template>
  <div class="space-y-4">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <h3 class="text-sm font-semibold text-gray-900 dark:text-white">
        {{ $t('abTest.variants') }}
        <span class="ml-1 text-xs font-normal text-gray-500 dark:text-gray-400">
          ({{ variants.length }}/4)
        </span>
      </h3>
      <button
        v-if="canAdd"
        class="inline-flex items-center gap-1 rounded-lg px-3 py-1.5 text-xs font-medium text-blue-600 transition-colors hover:bg-blue-50 dark:text-blue-400 dark:hover:bg-blue-900/20"
        @click="addVariant"
      >
        <PlusIcon class="h-4 w-4" />
        {{ $t('abTest.addVariant') }}
      </button>
      <span v-else class="text-xs text-gray-400 dark:text-gray-500">
        {{ $t('abTest.maxVariants') }}
      </span>
    </div>

    <!-- Variant Cards -->
    <div class="space-y-3">
      <div
        v-for="(variant, index) in variants"
        :key="variant.label"
        :class="['rounded-lg border p-4', getColor(variant.label).bg, getColor(variant.label).border]"
      >
        <!-- Variant Header -->
        <div class="mb-3 flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span :class="['rounded-full px-2.5 py-0.5 text-xs font-bold', getColor(variant.label).badge]">
              {{ $t('abTest.variantLabel') }} {{ variant.label }}
            </span>
          </div>
          <button
            v-if="canRemove"
            class="rounded-lg p-1.5 text-red-500 transition-colors hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-900/20"
            :title="$t('abTest.removeVariant')"
            @click="removeVariant(index)"
          >
            <TrashIcon class="h-4 w-4" />
          </button>
          <span v-else class="text-xs text-gray-400 dark:text-gray-500">
            {{ $t('abTest.minVariants') }}
          </span>
        </div>

        <!-- THUMBNAIL type: Image upload slot -->
        <div v-if="isThumbnail" class="space-y-2">
          <div
            v-if="!variant.value"
            class="flex aspect-video cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed border-gray-300 bg-white transition-colors hover:border-blue-400 dark:border-gray-600 dark:bg-gray-800 dark:hover:border-blue-500"
            @click="() => {}"
          >
            <PhotoIcon class="mb-2 h-8 w-8 text-gray-400 dark:text-gray-500" />
            <span class="text-xs text-gray-500 dark:text-gray-400">{{ $t('abTest.thumbnailDragDrop') }}</span>
          </div>
          <div v-else class="relative aspect-video overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-700">
            <img :src="variant.value" :alt="`${$t('abTest.variantLabel')} ${variant.label}`" class="h-full w-full object-cover" />
          </div>
          <input
            :value="variant.value"
            type="text"
            :placeholder="$t('abTest.thumbnailUpload')"
            class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-transparent focus:ring-2 focus:ring-blue-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white dark:placeholder-gray-500 dark:focus:ring-blue-400"
            @input="updateValue(index, ($event.target as HTMLInputElement).value)"
          />
        </div>

        <!-- TITLE / DESCRIPTION type: Text input -->
        <div v-else>
          <textarea
            v-if="!isTitle"
            :value="variant.value"
            :placeholder="$t('abTest.descriptionPlaceholder')"
            rows="3"
            class="w-full resize-none rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-transparent focus:ring-2 focus:ring-blue-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white dark:placeholder-gray-500 dark:focus:ring-blue-400"
            @input="updateValue(index, ($event.target as HTMLTextAreaElement).value)"
          ></textarea>
          <input
            v-else
            :value="variant.value"
            type="text"
            :placeholder="$t('abTest.titlePlaceholder')"
            class="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-transparent focus:ring-2 focus:ring-blue-500 dark:border-gray-600 dark:bg-gray-700 dark:text-white dark:placeholder-gray-500 dark:focus:ring-blue-400"
            @input="updateValue(index, ($event.target as HTMLInputElement).value)"
          />
          <div class="mt-1 text-right text-xs text-gray-400 dark:text-gray-500">
            {{ $t('abTest.charCount', { count: variant.value.length }) }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<template>
  <div
    class="group rounded-lg border p-3 transition-all"
    :class="[
      slot.status === 'confirmed'
        ? 'border-green-300 bg-green-50 dark:border-green-700 dark:bg-green-900/20'
        : slot.status === 'edited'
          ? 'border-amber-300 bg-amber-50 dark:border-amber-700 dark:bg-amber-900/20'
          : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800',
      'hover:shadow-sm',
    ]"
    :style="{ animationDelay: `${(index ?? 0) * 80}ms` }"
    style="animation: slot-fade-in 400ms ease-out backwards"
  >
    <!-- Header: Time + Platform -->
    <div class="mb-2 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <span class="text-xs font-medium text-gray-500 dark:text-gray-400">
          {{ slot.time }}
        </span>
        <span
          class="rounded px-1.5 py-0.5 text-xs font-medium"
          :style="{ backgroundColor: platformColor + '15', color: platformColor }"
        >
          {{ platformLabel }}
        </span>
      </div>
      <div class="flex items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
        <button
          class="rounded p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
          :title="$t('aiCalendar.slot.confirm')"
          @click="$emit('toggle-confirm', slot.id)"
        >
          <CheckIcon class="h-3.5 w-3.5" />
        </button>
        <button
          class="rounded p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
          :title="$t('aiCalendar.slot.regenerate')"
          @click="$emit('regenerate', slot.id)"
        >
          <ArrowPathIcon class="h-3.5 w-3.5" />
        </button>
      </div>
    </div>

    <!-- Title -->
    <h4 class="mb-1.5 text-sm font-medium text-gray-900 dark:text-gray-100">
      {{ slot.title }}
    </h4>

    <!-- Category + Trend Score -->
    <div class="mb-2 flex items-center gap-2">
      <span class="badge-blue text-xs">{{ slot.category }}</span>
      <div class="flex items-center gap-1">
        <div class="flex">
          <span
            v-for="i in 5"
            :key="i"
            class="text-xs"
            :class="i <= trendStars ? 'text-amber-400' : 'text-gray-300 dark:text-gray-600'"
          >
            \u2605
          </span>
        </div>
        <span class="text-xs text-gray-400 dark:text-gray-500">{{ slot.trendScore }}</span>
      </div>
    </div>

    <!-- Trend Keywords -->
    <div v-if="slot.trendKeywords.length > 0" class="mb-2 flex flex-wrap gap-1">
      <span
        v-for="keyword in slot.trendKeywords.slice(0, 3)"
        :key="keyword"
        class="rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-400"
      >
        #{{ keyword }}
      </span>
    </div>

    <!-- Season Event Badge -->
    <SeasonEventBadge v-if="slot.seasonEvent" :event="slot.seasonEvent" />

    <!-- Status Indicator -->
    <div v-if="slot.status !== 'suggested'" class="mt-2 flex items-center gap-1">
      <span
        class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium"
        :class="
          slot.status === 'confirmed'
            ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
            : 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400'
        "
      >
        <CheckIcon v-if="slot.status === 'confirmed'" class="h-3 w-3" />
        <PencilIcon v-else class="h-3 w-3" />
        {{ slot.status === 'confirmed' ? $t('aiCalendar.slot.confirmed') : $t('aiCalendar.slot.edited') }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CheckIcon, ArrowPathIcon, PencilIcon } from '@heroicons/vue/24/outline'
import SeasonEventBadge from './SeasonEventBadge.vue'
import { PLATFORM_CONFIG } from '@/types/channel'
import type { ContentSlot } from '@/types/aiCalendar'
import type { Platform } from '@/types/channel'

const props = defineProps<{
  slot: ContentSlot
  index?: number
}>()

defineEmits<{
  'toggle-confirm': [slotId: string]
  'regenerate': [slotId: string]
}>()

const platformLabel = computed(() => PLATFORM_CONFIG[props.slot.platform as Platform]?.label ?? props.slot.platform)
const platformColor = computed(() => PLATFORM_CONFIG[props.slot.platform as Platform]?.color ?? '#6B7280')
const trendStars = computed(() => Math.round(props.slot.trendScore / 20))
</script>

<style scoped>
@keyframes slot-fade-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>

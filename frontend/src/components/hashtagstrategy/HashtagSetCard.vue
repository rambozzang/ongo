<template>
  <div class="card">
    <!-- Header -->
    <div class="mb-4 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <component :is="strategyIcon" class="h-5 w-5" :class="strategyIconClass" />
        <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">
          {{ strategyLabel }}
        </h3>
      </div>
      <div class="flex items-center gap-1">
        <button
          class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
          :title="$t('hashtagStrategy.copyAll')"
          @click="emit('copy', set)"
        >
          <ClipboardDocumentIcon class="h-4 w-4" />
        </button>
        <button
          v-if="!isSaved"
          class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-primary-600 dark:hover:bg-gray-700 dark:hover:text-primary-400"
          :title="$t('action.save')"
          @click="emit('save', set)"
        >
          <BookmarkIcon class="h-4 w-4" />
        </button>
        <button
          v-if="isSaved"
          class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-red-600 dark:hover:bg-gray-700 dark:hover:text-red-400"
          :title="$t('action.delete')"
          @click="emit('delete', set)"
        >
          <TrashIcon class="h-4 w-4" />
        </button>
      </div>
    </div>

    <!-- Hashtag Pills -->
    <div class="mb-4 flex flex-wrap gap-1.5">
      <HashtagPill
        v-for="hashtag in set.hashtags"
        :key="hashtag.tag"
        :hashtag="hashtag"
      />
    </div>

    <!-- Reach Estimator -->
    <ReachEstimator
      :total-reach="set.totalReachEstimate"
      :difficulty="set.overallDifficulty"
      :score="set.score"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ClipboardDocumentIcon,
  BookmarkIcon,
  TrashIcon,
  ShieldCheckIcon,
  ScaleIcon,
  FireIcon,
} from '@heroicons/vue/24/outline'
import HashtagPill from './HashtagPill.vue'
import ReachEstimator from './ReachEstimator.vue'
import type { HashtagSet } from '@/types/hashtagStrategy'

const props = defineProps<{
  set: HashtagSet
  isSaved?: boolean
}>()

const emit = defineEmits<{
  copy: [set: HashtagSet]
  save: [set: HashtagSet]
  delete: [set: HashtagSet]
}>()

const { t } = useI18n({ useScope: 'global' })

const strategyLabel = computed(() => {
  switch (props.set.name) {
    case 'conservative': return t('hashtagStrategy.strategyConservative')
    case 'balanced': return t('hashtagStrategy.strategyBalanced')
    case 'aggressive': return t('hashtagStrategy.strategyAggressive')
    default: return props.set.name
  }
})

const strategyIcon = computed(() => {
  switch (props.set.name) {
    case 'conservative': return ShieldCheckIcon
    case 'balanced': return ScaleIcon
    case 'aggressive': return FireIcon
    default: return ScaleIcon
  }
})

const strategyIconClass = computed(() => {
  switch (props.set.name) {
    case 'conservative': return 'text-green-500'
    case 'balanced': return 'text-yellow-500'
    case 'aggressive': return 'text-red-500'
    default: return 'text-gray-500'
  }
})
</script>

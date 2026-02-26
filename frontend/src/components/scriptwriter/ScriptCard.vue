<script setup lang="ts">
import { computed } from 'vue'
import {
  TrashIcon,
  ClockIcon,
  DocumentTextIcon,
  CreditCardIcon,
} from '@heroicons/vue/24/outline'
import type { Script, ScriptFormat, ScriptTone, ScriptStatus } from '@/types/scriptWriter'

interface Props {
  script: Script
}

interface Emits {
  (e: 'click', id: number): void
  (e: 'delete', id: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const statusConfig = computed(() => {
  const configs: Record<ScriptStatus, { label: string; color: string }> = {
    DRAFT: {
      label: '초안',
      color: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
    },
    REVIEWING: {
      label: '검토중',
      color: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
    },
    FINAL: {
      label: '완성',
      color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
    },
    ARCHIVED: {
      label: '보관',
      color: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300',
    },
  }
  return configs[props.script.status] ?? configs.DRAFT
})

const formatConfig = computed(() => {
  const configs: Record<ScriptFormat, { label: string; color: string }> = {
    LONG_FORM: {
      label: '롱폼',
      color: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300',
    },
    SHORT_FORM: {
      label: '숏폼',
      color: 'bg-pink-100 text-pink-800 dark:bg-pink-900/30 dark:text-pink-300',
    },
    TUTORIAL: {
      label: '튜토리얼',
      color: 'bg-indigo-100 text-indigo-800 dark:bg-indigo-900/30 dark:text-indigo-300',
    },
    REVIEW: {
      label: '리뷰',
      color: 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300',
    },
    VLOG: {
      label: '브이로그',
      color: 'bg-teal-100 text-teal-800 dark:bg-teal-900/30 dark:text-teal-300',
    },
    INTERVIEW: {
      label: '인터뷰',
      color: 'bg-cyan-100 text-cyan-800 dark:bg-cyan-900/30 dark:text-cyan-300',
    },
  }
  return configs[props.script.format] ?? configs.LONG_FORM
})

const toneConfig = computed(() => {
  const configs: Record<ScriptTone, { label: string; color: string }> = {
    CASUAL: {
      label: '캐주얼',
      color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
    },
    PROFESSIONAL: {
      label: '전문적',
      color: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
    },
    HUMOROUS: {
      label: '유머',
      color: 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-300',
    },
    EDUCATIONAL: {
      label: '교육적',
      color: 'bg-indigo-100 text-indigo-800 dark:bg-indigo-900/30 dark:text-indigo-300',
    },
    DRAMATIC: {
      label: '드라마틱',
      color: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
    },
    STORYTELLING: {
      label: '스토리텔링',
      color: 'bg-violet-100 text-violet-800 dark:bg-violet-900/30 dark:text-violet-300',
    },
  }
  return configs[props.script.tone] ?? configs.CASUAL
})

function formatDuration(seconds: number): string {
  const min = Math.floor(seconds / 60)
  const sec = seconds % 60
  return sec > 0 ? `${min}분 ${sec}초` : `${min}분`
}
</script>

<template>
  <div
    class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-6 hover:shadow-xl dark:hover:shadow-gray-900/50 transition-all duration-200 cursor-pointer group"
    @click="emit('click', script.id)"
  >
    <!-- Header: Title & Status -->
    <div class="flex items-start justify-between mb-3">
      <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100 truncate flex-1 mr-3">
        {{ script.title }}
      </h3>
      <span
        :class="['inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium whitespace-nowrap', statusConfig.color]"
      >
        {{ statusConfig.label }}
      </span>
    </div>

    <!-- Topic -->
    <p class="text-sm text-gray-500 dark:text-gray-400 mb-3 line-clamp-1">
      {{ script.topic }}
    </p>

    <!-- Format & Tone Badges -->
    <div class="flex flex-wrap gap-1.5 mb-4">
      <span
        :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', formatConfig.color]"
      >
        {{ formatConfig.label }}
      </span>
      <span
        :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', toneConfig.color]"
      >
        {{ toneConfig.label }}
      </span>
    </div>

    <!-- Stats -->
    <div class="grid grid-cols-3 gap-2 mb-4">
      <div class="flex items-center gap-1.5 text-sm text-gray-600 dark:text-gray-400">
        <ClockIcon class="w-4 h-4 shrink-0" />
        <span class="truncate">{{ formatDuration(script.targetDuration) }}</span>
      </div>
      <div class="flex items-center gap-1.5 text-sm text-gray-600 dark:text-gray-400">
        <DocumentTextIcon class="w-4 h-4 shrink-0" />
        <span>{{ script.estimatedWordCount.toLocaleString() }}자</span>
      </div>
      <div class="flex items-center gap-1.5 text-sm text-gray-600 dark:text-gray-400">
        <CreditCardIcon class="w-4 h-4 shrink-0" />
        <span>{{ script.creditCost }} 크레딧</span>
      </div>
    </div>

    <!-- Keywords -->
    <div v-if="script.keywords.length > 0" class="flex flex-wrap gap-1 mb-4">
      <span
        v-for="keyword in script.keywords.slice(0, 4)"
        :key="keyword"
        class="inline-flex items-center px-2 py-0.5 rounded-full text-xs bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400"
      >
        #{{ keyword }}
      </span>
      <span
        v-if="script.keywords.length > 4"
        class="inline-flex items-center px-2 py-0.5 rounded-full text-xs bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400"
      >
        +{{ script.keywords.length - 4 }}
      </span>
    </div>

    <!-- Actions -->
    <div class="flex items-center justify-end border-t border-gray-200 dark:border-gray-700 pt-3">
      <button
        @click.stop="emit('delete', script.id)"
        class="inline-flex items-center gap-1 px-3 py-1.5 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors"
      >
        <TrashIcon class="w-4 h-4" />
        삭제
      </button>
    </div>
  </div>
</template>

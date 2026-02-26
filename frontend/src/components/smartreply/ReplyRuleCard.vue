<script setup lang="ts">
import { TrashIcon, SparklesIcon, PaperAirplaneIcon } from '@heroicons/vue/24/outline'
import type { SmartReplyRule } from '@/types/smartReply'

defineProps<{
  rule: SmartReplyRule
}>()

const emit = defineEmits<{
  toggle: [id: number]
  delete: [id: number]
}>()

const contextColors: Record<string, { bg: string; text: string }> = {
  COMMENT: { bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-300' },
  DM: { bg: 'bg-purple-100 dark:bg-purple-900/30', text: 'text-purple-700 dark:text-purple-300' },
  MENTION: { bg: 'bg-orange-100 dark:bg-orange-900/30', text: 'text-orange-700 dark:text-orange-300' },
  REVIEW: { bg: 'bg-teal-100 dark:bg-teal-900/30', text: 'text-teal-700 dark:text-teal-300' },
}

const toneColors: Record<string, { bg: string; text: string }> = {
  FRIENDLY: { bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300' },
  PROFESSIONAL: { bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-300' },
  CASUAL: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
  GRATEFUL: { bg: 'bg-pink-100 dark:bg-pink-900/30', text: 'text-pink-700 dark:text-pink-300' },
  HUMOROUS: { bg: 'bg-orange-100 dark:bg-orange-900/30', text: 'text-orange-700 dark:text-orange-300' },
}

const contextLabels: Record<string, string> = {
  COMMENT: 'comment',
  DM: 'dm',
  MENTION: 'mention',
  REVIEW: 'review',
}

const toneLabels: Record<string, string> = {
  FRIENDLY: 'friendly',
  PROFESSIONAL: 'professional',
  CASUAL: 'casual',
  GRATEFUL: 'grateful',
  HUMOROUS: 'humorous',
}

const getContextStyle = (context: string) => contextColors[context] ?? contextColors.COMMENT
const getToneStyle = (tone: string) => toneColors[tone] ?? toneColors.FRIENDLY

const formatLastUsed = (iso?: string) => {
  if (!iso) return null
  try {
    const date = new Date(iso)
    const now = new Date()
    const diffMs = now.getTime() - date.getTime()
    const diffDays = Math.floor(diffMs / 86400000)

    if (diffDays < 1) return '오늘'
    if (diffDays < 7) return `${diffDays}일 전`
    return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })
  } catch {
    return iso
  }
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
    <!-- Header: Name + Toggle -->
    <div class="mb-3 flex items-center justify-between">
      <div class="flex items-center gap-3">
        <h3
          class="text-sm font-semibold"
          :class="rule.isActive ? 'text-gray-900 dark:text-gray-100' : 'text-gray-400 dark:text-gray-500'"
        >
          {{ rule.name }}
        </h3>
      </div>

      <label class="relative inline-flex cursor-pointer items-center">
        <input
          type="checkbox"
          :checked="rule.isActive"
          class="peer sr-only"
          @change="emit('toggle', rule.id)"
        />
        <div
          class="h-5 w-9 rounded-full bg-gray-200 after:absolute after:left-[2px] after:top-[2px] after:h-4 after:w-4 after:rounded-full after:border after:border-gray-300 after:bg-white after:transition-all after:content-[''] peer-checked:bg-primary-600 peer-checked:after:translate-x-full peer-checked:after:border-white peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-primary-300 dark:bg-gray-700 dark:peer-focus:ring-primary-800"
        />
      </label>
    </div>

    <!-- Badges row: Context, Tone, AI/Template, AutoSend -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <span
        :class="[getContextStyle(rule.context).bg, getContextStyle(rule.context).text]"
        class="rounded-full px-2 py-0.5 text-xs font-medium"
      >
        {{ $t(`smartReply.context.${contextLabels[rule.context]}`) }}
      </span>

      <span
        :class="[getToneStyle(rule.tone).bg, getToneStyle(rule.tone).text]"
        class="rounded-full px-2 py-0.5 text-xs font-medium"
      >
        {{ $t(`smartReply.tone.${toneLabels[rule.tone]}`) }}
      </span>

      <span
        v-if="rule.useAi"
        class="inline-flex items-center gap-1 rounded-full bg-purple-100 px-2 py-0.5 text-xs font-medium text-purple-700 dark:bg-purple-900/30 dark:text-purple-300"
      >
        <SparklesIcon class="h-3 w-3" />
        AI
      </span>
      <span
        v-else
        class="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600 dark:bg-gray-800 dark:text-gray-400"
      >
        {{ $t('smartReply.template') }}
      </span>

      <span
        v-if="rule.autoSend"
        class="inline-flex items-center gap-1 rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700 dark:bg-green-900/30 dark:text-green-300"
      >
        <PaperAirplaneIcon class="h-3 w-3" />
        {{ $t('smartReply.autoSend') }}
      </span>

      <span
        v-if="rule.platform"
        class="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600 dark:bg-gray-800 dark:text-gray-400"
      >
        {{ rule.platform }}
      </span>
    </div>

    <!-- Trigger keywords -->
    <div class="mb-3">
      <p class="mb-1.5 text-xs text-gray-500 dark:text-gray-400">{{ $t('smartReply.triggerKeywords') }}</p>
      <div class="flex flex-wrap gap-1.5">
        <span
          v-for="keyword in rule.triggerKeywords"
          :key="keyword"
          class="rounded-md bg-gray-100 px-2 py-0.5 text-xs text-gray-700 dark:bg-gray-800 dark:text-gray-300"
        >
          {{ keyword }}
        </span>
      </div>
    </div>

    <!-- Template text preview (if not AI) -->
    <div
      v-if="!rule.useAi && rule.templateText"
      class="mb-3 rounded-lg bg-gray-50 p-2.5 dark:bg-gray-800/50"
    >
      <p class="line-clamp-2 text-xs text-gray-600 dark:text-gray-400">{{ rule.templateText }}</p>
    </div>

    <!-- Footer: Stats + Delete -->
    <div class="flex items-center justify-between border-t border-gray-100 pt-3 dark:border-gray-800">
      <div class="flex items-center gap-4 text-xs text-gray-500 dark:text-gray-400">
        <span>
          {{ $t('smartReply.replyCount') }}:
          <span class="font-semibold text-gray-700 dark:text-gray-300">{{ rule.replyCount }}</span>
        </span>
        <span v-if="formatLastUsed(rule.lastUsed)">
          {{ $t('smartReply.lastUsed') }}: {{ formatLastUsed(rule.lastUsed) }}
        </span>
      </div>

      <button
        class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-900/20 dark:hover:text-red-400"
        :title="$t('smartReply.deleteRule')"
        @click="emit('delete', rule.id)"
      >
        <TrashIcon class="h-4 w-4" />
      </button>
    </div>
  </div>
</template>

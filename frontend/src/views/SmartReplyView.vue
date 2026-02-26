<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import {
  ChatBubbleLeftRightIcon,
  PlusIcon,
  XMarkIcon,
} from '@heroicons/vue/24/outline'
import { useSmartReplyStore } from '@/stores/smartReply'
import SuggestionCard from '@/components/smartreply/SuggestionCard.vue'
import ReplyRuleCard from '@/components/smartreply/ReplyRuleCard.vue'
import ReplyStatsPanel from '@/components/smartreply/ReplyStatsPanel.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import type { SmartReplyRule, ReplyTone, ReplyContext } from '@/types/smartReply'

const { t } = useI18n({ useScope: 'global' })
const store = useSmartReplyStore()

const { suggestions, rules, stats, loading, pendingCount } = storeToRefs(store)

const activeTab = ref<'suggestions' | 'rules' | 'stats'>('suggestions')

// ─── New Rule Modal ──────────────────────────────────────
const showRuleModal = ref(false)
const newRuleName = ref('')
const newRuleContext = ref<ReplyContext>('COMMENT')
const newRuleTone = ref<ReplyTone>('FRIENDLY')
const newRuleKeywords = ref('')
const newRuleTemplate = ref('')
const newRuleUseAi = ref(true)
const newRuleAutoSend = ref(false)

const contextOptions: { value: ReplyContext; labelKey: string }[] = [
  { value: 'COMMENT', labelKey: 'smartReply.context.comment' },
  { value: 'DM', labelKey: 'smartReply.context.dm' },
  { value: 'MENTION', labelKey: 'smartReply.context.mention' },
  { value: 'REVIEW', labelKey: 'smartReply.context.review' },
]

const toneOptions: { value: ReplyTone; labelKey: string }[] = [
  { value: 'FRIENDLY', labelKey: 'smartReply.tone.friendly' },
  { value: 'PROFESSIONAL', labelKey: 'smartReply.tone.professional' },
  { value: 'CASUAL', labelKey: 'smartReply.tone.casual' },
  { value: 'GRATEFUL', labelKey: 'smartReply.tone.grateful' },
  { value: 'HUMOROUS', labelKey: 'smartReply.tone.humorous' },
]

const openRuleModal = () => {
  newRuleName.value = ''
  newRuleContext.value = 'COMMENT'
  newRuleTone.value = 'FRIENDLY'
  newRuleKeywords.value = ''
  newRuleTemplate.value = ''
  newRuleUseAi.value = true
  newRuleAutoSend.value = false
  showRuleModal.value = true
}

const closeRuleModal = () => {
  showRuleModal.value = false
}

const handleCreateRule = async () => {
  if (!newRuleName.value.trim()) return

  const keywords = newRuleKeywords.value
    .split(',')
    .map((k) => k.trim())
    .filter(Boolean)

  const rule: Omit<SmartReplyRule, 'id' | 'replyCount' | 'lastUsed'> = {
    name: newRuleName.value.trim(),
    isActive: true,
    context: newRuleContext.value,
    triggerKeywords: keywords,
    tone: newRuleTone.value,
    templateText: newRuleTemplate.value.trim(),
    useAi: newRuleUseAi.value,
    autoSend: newRuleAutoSend.value,
  }

  await store.createRule(rule)
  closeRuleModal()
}

const handleSendReply = async (id: string, text: string) => {
  await store.sendReply(id, text)
}

const handleDismiss = async (id: string) => {
  await store.dismissSuggestion(id)
}

const handleToggleRule = (id: number) => {
  store.toggleRule(id)
}

const handleDeleteRule = (id: number) => {
  if (confirm(t('smartReply.confirmDeleteRule'))) {
    store.deleteRule(id)
  }
}

onMounted(() => {
  store.fetchSuggestions()
  store.fetchRules()
  store.fetchStats()
  store.fetchConfig()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ $t('smartReply.title') }}
          </h1>
          <span
            v-if="pendingCount > 0"
            class="rounded-full bg-primary-600 px-2.5 py-0.5 text-xs font-semibold text-white"
          >
            {{ pendingCount }}
          </span>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('smartReply.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button
          v-if="activeTab === 'rules'"
          class="btn-primary inline-flex items-center gap-2"
          @click="openRuleModal"
        >
          <PlusIcon class="h-5 w-5" />
          {{ $t('smartReply.newRule') }}
        </button>
      </div>
    </div>

    <PageGuide :title="$t('smartReply.pageGuideTitle')" :items="($tm('smartReply.pageGuide') as string[])" />

    <!-- Tabs -->
    <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
      <nav class="-mb-px flex gap-8">
        <button
          @click="activeTab = 'suggestions'"
          :class="[
            'py-4 px-1 border-b-2 font-medium text-sm transition-colors',
            activeTab === 'suggestions'
              ? 'border-blue-600 text-blue-600 dark:text-blue-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
          ]"
        >
          {{ $t('smartReply.tabSuggestions') }}
          <span
            v-if="pendingCount > 0"
            :class="[
              'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold',
              activeTab === 'suggestions'
                ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
            ]"
          >
            {{ pendingCount }}
          </span>
        </button>

        <button
          @click="activeTab = 'rules'"
          :class="[
            'py-4 px-1 border-b-2 font-medium text-sm transition-colors',
            activeTab === 'rules'
              ? 'border-blue-600 text-blue-600 dark:text-blue-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
          ]"
        >
          {{ $t('smartReply.tabRules') }}
          <span
            :class="[
              'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold',
              activeTab === 'rules'
                ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
            ]"
          >
            {{ rules.length }}
          </span>
        </button>

        <button
          @click="activeTab = 'stats'"
          :class="[
            'py-4 px-1 border-b-2 font-medium text-sm transition-colors',
            activeTab === 'stats'
              ? 'border-blue-600 text-blue-600 dark:text-blue-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
          ]"
        >
          {{ $t('smartReply.tabStats') }}
        </button>
      </nav>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="loading" :full-page="true" size="lg" />

    <!-- Suggestions Tab -->
    <div v-else-if="activeTab === 'suggestions'">
      <div v-if="suggestions.length > 0" class="space-y-4">
        <SuggestionCard
          v-for="suggestion in suggestions"
          :key="suggestion.id"
          :suggestion="suggestion"
          @send="handleSendReply"
          @dismiss="handleDismiss"
        />
      </div>

      <!-- Empty state -->
      <div
        v-else
        class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 py-16 text-center shadow-sm"
      >
        <ChatBubbleLeftRightIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
        <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('smartReply.emptySuggestions') }}
        </h3>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('smartReply.emptySuggestionsHint') }}
        </p>
      </div>
    </div>

    <!-- Rules Tab -->
    <div v-else-if="activeTab === 'rules'">
      <div v-if="rules.length > 0" class="space-y-4">
        <ReplyRuleCard
          v-for="rule in rules"
          :key="rule.id"
          :rule="rule"
          @toggle="handleToggleRule"
          @delete="handleDeleteRule"
        />
      </div>

      <!-- Empty state -->
      <div
        v-else
        class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 py-16 text-center shadow-sm"
      >
        <ChatBubbleLeftRightIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
        <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('smartReply.emptyRules') }}
        </h3>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('smartReply.emptyRulesHint') }}
        </p>
        <button
          class="btn-primary mt-4 inline-flex items-center gap-2"
          @click="openRuleModal"
        >
          <PlusIcon class="h-5 w-5" />
          {{ $t('smartReply.createFirstRule') }}
        </button>
      </div>
    </div>

    <!-- Stats Tab -->
    <div v-else-if="activeTab === 'stats'">
      <ReplyStatsPanel v-if="stats" :stats="stats" />

      <div
        v-else
        class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 py-16 text-center shadow-sm"
      >
        <p class="text-sm text-gray-500 dark:text-gray-400">
          {{ $t('smartReply.noStats') }}
        </p>
      </div>
    </div>

    <!-- New Rule Modal -->
    <Teleport to="body">
      <div
        v-if="showRuleModal"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
        @click.self="closeRuleModal"
      >
        <div class="w-full max-w-lg rounded-xl bg-white p-6 shadow-xl dark:bg-gray-900">
          <!-- Modal header -->
          <div class="mb-5 flex items-center justify-between">
            <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">
              {{ $t('smartReply.newRuleTitle') }}
            </h2>
            <button
              class="rounded-lg p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-800 dark:hover:text-gray-300"
              @click="closeRuleModal"
            >
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <!-- Form -->
          <div class="space-y-4">
            <!-- Rule name -->
            <div>
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('smartReply.ruleName') }}
              </label>
              <input
                v-model="newRuleName"
                type="text"
                :placeholder="$t('smartReply.ruleNamePlaceholder')"
                class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
              />
            </div>

            <!-- Context -->
            <div>
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('smartReply.ruleContext') }}
              </label>
              <select
                v-model="newRuleContext"
                class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
              >
                <option
                  v-for="opt in contextOptions"
                  :key="opt.value"
                  :value="opt.value"
                >
                  {{ $t(opt.labelKey) }}
                </option>
              </select>
            </div>

            <!-- Tone -->
            <div>
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('smartReply.ruleTone') }}
              </label>
              <select
                v-model="newRuleTone"
                class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
              >
                <option
                  v-for="opt in toneOptions"
                  :key="opt.value"
                  :value="opt.value"
                >
                  {{ $t(opt.labelKey) }}
                </option>
              </select>
            </div>

            <!-- Trigger keywords -->
            <div>
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('smartReply.ruleKeywords') }}
              </label>
              <input
                v-model="newRuleKeywords"
                type="text"
                :placeholder="$t('smartReply.ruleKeywordsPlaceholder')"
                class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
              />
            </div>

            <!-- AI toggle -->
            <div class="flex items-center justify-between">
              <label class="text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('smartReply.ruleUseAi') }}
              </label>
              <label class="relative inline-flex cursor-pointer items-center">
                <input v-model="newRuleUseAi" type="checkbox" class="peer sr-only" />
                <div
                  class="h-5 w-9 rounded-full bg-gray-200 after:absolute after:left-[2px] after:top-[2px] after:h-4 after:w-4 after:rounded-full after:border after:border-gray-300 after:bg-white after:transition-all after:content-[''] peer-checked:bg-primary-600 peer-checked:after:translate-x-full peer-checked:after:border-white peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-primary-300 dark:bg-gray-700 dark:peer-focus:ring-primary-800"
                />
              </label>
            </div>

            <!-- Template text (if not AI) -->
            <div v-if="!newRuleUseAi">
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('smartReply.ruleTemplate') }}
              </label>
              <textarea
                v-model="newRuleTemplate"
                rows="3"
                :placeholder="$t('smartReply.ruleTemplatePlaceholder')"
                class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
              />
            </div>

            <!-- Auto-send toggle -->
            <div class="flex items-center justify-between">
              <label class="text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('smartReply.ruleAutoSend') }}
              </label>
              <label class="relative inline-flex cursor-pointer items-center">
                <input v-model="newRuleAutoSend" type="checkbox" class="peer sr-only" />
                <div
                  class="h-5 w-9 rounded-full bg-gray-200 after:absolute after:left-[2px] after:top-[2px] after:h-4 after:w-4 after:rounded-full after:border after:border-gray-300 after:bg-white after:transition-all after:content-[''] peer-checked:bg-primary-600 peer-checked:after:translate-x-full peer-checked:after:border-white peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-primary-300 dark:bg-gray-700 dark:peer-focus:ring-primary-800"
                />
              </label>
            </div>
          </div>

          <!-- Modal actions -->
          <div class="mt-6 flex items-center justify-end gap-3">
            <button
              class="rounded-lg px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
              @click="closeRuleModal"
            >
              {{ $t('smartReply.cancel') }}
            </button>
            <button
              class="btn-primary text-sm"
              :disabled="!newRuleName.trim()"
              @click="handleCreateRule"
            >
              {{ $t('smartReply.createRule') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

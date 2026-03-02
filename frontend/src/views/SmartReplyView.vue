<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import {
  ChatBubbleLeftRightIcon,
  PlusIcon,
} from '@heroicons/vue/24/outline'
import { useSmartReplyStore } from '@/stores/smartReply'
import OTabs from '@/components/ui/OTabs.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import SuggestionCard from '@/components/smartreply/SuggestionCard.vue'
import ReplyRuleCard from '@/components/smartreply/ReplyRuleCard.vue'
import ReplyStatsPanel from '@/components/smartreply/ReplyStatsPanel.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import type { SmartReplyRule, ReplyTone, ReplyContext } from '@/types/smartReply'

const { t } = useI18n({ useScope: 'global' })
const store = useSmartReplyStore()

const { suggestions, rules, stats, loading, pendingCount } = storeToRefs(store)

const activeTab = ref<'suggestions' | 'rules' | 'stats'>('suggestions')

const tabList = computed(() => [
  { key: 'suggestions', label: t('smartReply.tabSuggestions'), count: pendingCount.value > 0 ? pendingCount.value : undefined },
  { key: 'rules', label: t('smartReply.tabRules'), count: rules.value.length },
  { key: 'stats', label: t('smartReply.tabStats') },
])

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
    <PageHeader :title="$t('smartReply.title')" :description="$t('smartReply.description')">
      <template #title-suffix>
        <span
          v-if="pendingCount > 0"
          class="rounded-full bg-primary-600 px-2.5 py-0.5 text-xs font-semibold text-white"
        >
          {{ pendingCount }}
        </span>
      </template>
      <template #actions>
        <button
          v-if="activeTab === 'rules'"
          class="btn-primary inline-flex items-center gap-2"
          @click="openRuleModal"
        >
          <PlusIcon class="h-5 w-5" />
          {{ $t('smartReply.newRule') }}
        </button>
      </template>
    </PageHeader>

    <PageGuide :title="$t('smartReply.pageGuideTitle')" :items="($tm('smartReply.pageGuide') as string[])" />

    <!-- Tabs -->
    <div class="mb-6">
      <OTabs v-model="activeTab" :tabs="tabList" />
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
        class="card py-16 text-center"
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
        class="card py-16 text-center"
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
        class="card py-16 text-center"
      >
        <p class="text-sm text-gray-500 dark:text-gray-400">
          {{ $t('smartReply.noStats') }}
        </p>
      </div>
    </div>

    <!-- New Rule Modal -->
    <BaseModal v-model="showRuleModal" :title="$t('smartReply.newRuleTitle')">
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
            class="input-field w-full"
          />
        </div>

        <!-- Context -->
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ $t('smartReply.ruleContext') }}
          </label>
          <select
            v-model="newRuleContext"
            class="input-field w-full"
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
            class="input-field w-full"
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
            class="input-field w-full"
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
            class="input-field w-full"
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

      <template #footer>
        <button
          class="btn-secondary"
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
      </template>
    </BaseModal>
  </div>
</template>

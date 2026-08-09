<script setup lang="ts">
import { ref, shallowRef, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  PlusIcon,
  BoltIcon,
  MagnifyingGlassIcon,
  TrashIcon,
} from '@heroicons/vue/24/outline'
import { useAutomationStore } from '@/stores/automation'
import { useNotification } from '@/composables/useNotification'
import { useListControls, type ListSortOption } from '@/composables/useListControls'
import AutomationRuleCard from '@/components/automation/AutomationRuleCard.vue'
import AutomationLogTable from '@/components/automation/AutomationLogTable.vue'
import AutomationFormModal from '@/components/automation/AutomationFormModal.vue'
import SmartTriggerTemplateSelector from '@/components/automation/SmartTriggerTemplateSelector.vue'
import OTabs from '@/components/ui/OTabs.vue'
import AsyncState from '@/components/common/AsyncState.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ListToolbar from '@/components/common/ListToolbar.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SectionCard from '@/components/redesign/SectionCard.vue'
import type { AutomationRule } from '@/types/automation'
import type { SmartTriggerTemplate } from '@/components/automation/SmartTriggerTemplateSelector.vue'
import type { AutomationInitialTrigger } from '@/components/automation/AutomationFormModal.vue'
import apiClient, { unwrapResponse } from '@/api/client'
import type { ResData } from '@/types/api'

const { t } = useI18n()
const automationStore = useAutomationStore()
const notification = useNotification()

/** 체크박스 공통 스타일 — 목록 확산 시 그대로 복사해 쓴다. */
const CHECKBOX_CLASS =
  'h-4 w-4 shrink-0 cursor-pointer rounded border-gray-300 accent-primary-600 focus:ring-2 focus:ring-primary-500 dark:border-gray-600'

const activeTab = ref<'rules' | 'logs'>('rules')
const automationTabs = computed(() => [
  { key: 'rules', label: t('automation.tabRules'), count: automationStore.rules.length },
  { key: 'logs', label: t('automation.tabLogs'), count: automationStore.logs.length },
])
const isModalOpen = ref(false)
const editingRule = ref<AutomationRule | undefined>(undefined)
const initialTrigger = ref<AutomationInitialTrigger | undefined>(undefined)

// 삭제 확인 모달 — 규칙 / 워크플로우 삭제가 하나의 모달을 공유
const showConfirmModal = ref(false)
const confirmTitle = ref('')
const confirmMessage = ref('')
const pendingAction = shallowRef<(() => void | Promise<void>) | null>(null)
const bulkDeleting = ref(false)

function askConfirm(title: string, message: string, action: () => void | Promise<void>) {
  confirmTitle.value = title
  confirmMessage.value = message
  pendingAction.value = action
  showConfirmModal.value = true
}

function runPendingAction() {
  const action = pendingAction.value
  pendingAction.value = null
  void action?.()
}

const activeRuleCount = computed(() => {
  return automationStore.rules.filter(r => r.isEnabled && r.status === 'active').length
})

/**
 * 기존 기본 정렬(활성 우선 → 최근 수정순)을 하나의 비교 축으로 합친 값.
 * 활성 가산점이 어떤 타임스탬프보다도 크므로 "활성 우선, 그 안에서 최신순"이 그대로 유지된다.
 */
const ENABLED_BOOST = 1e15

const timeOf = (value: string | null | undefined): number => {
  if (!value) return 0
  const parsed = new Date(value).getTime()
  return Number.isNaN(parsed) ? 0 : parsed
}

/** 규칙 목록의 활성 상태 필터 옵션. */
const enabledFilters = computed(() => [
  { label: t('automation.filterAll'), value: 'all' as const },
  { label: t('automation.filterEnabled'), value: 'enabled' as const },
  { label: t('automation.filterDisabled'), value: 'disabled' as const },
])

// ─── 규칙 목록 제어 ───────────────────────────────────────
const rulePriority = (rule: AutomationRule): number =>
  (rule.isEnabled ? ENABLED_BOOST : 0) + timeOf(rule.updatedAt)

const ruleSortOptions = computed<ListSortOption<AutomationRule>[]>(() => [
  {
    key: 'status',
    label: t('automation.sortEnabledFirst'),
    accessor: rulePriority,
    kind: 'number',
    defaultDir: 'desc',
  },
  {
    key: 'recent',
    label: t('automation.sortRecent'),
    accessor: 'updatedAt',
    kind: 'date',
    defaultDir: 'desc',
  },
  {
    key: 'executions',
    label: t('automation.sortExecutions'),
    accessor: 'executionCount',
    kind: 'number',
    defaultDir: 'desc',
  },
  { key: 'name', label: t('automation.sortName'), accessor: 'name', kind: 'string', defaultDir: 'asc' },
])

const ruleFilter = ref<'all' | 'enabled' | 'disabled'>('all')

const {
  query: ruleQuery,
  sortKey: ruleSortKey,
  sortDir: ruleSortDir,
  filtered: filteredRules,
  visibleCount: visibleRuleCount,
  isSourceEmpty: isRuleSourceEmpty,
  isResultEmpty: isRuleResultEmpty,
  resetFilters: resetRuleFilters,
  selectedIds: selectedRuleIds,
  selectedCount: selectedRuleCount,
  allSelected: allRulesSelected,
  someSelected: someRulesSelected,
  isSelected: isRuleSelected,
  toggle: toggleRuleSelection,
  toggleAll: toggleAllRules,
  clearSelection: clearRuleSelection,
} = useListControls<AutomationRule>(() => automationStore.rules, {
  searchFields: ['name', 'description', (rule) => rule.trigger.type, (rule) => rule.actions.map((a) => a.type)],
  sortOptions: ruleSortOptions,
  defaultSortKey: 'status',
  filters: computed(() =>
    ruleFilter.value === 'all'
      ? []
      : [(rule: AutomationRule) => (ruleFilter.value === 'enabled' ? rule.isEnabled : !rule.isEnabled)],
  ),
})

const resetRuleSearchAndFilters = () => {
  resetRuleFilters()
  ruleFilter.value = 'all'
}

const openCreateModal = () => {
  editingRule.value = undefined
  initialTrigger.value = undefined
  isModalOpen.value = true
}

const openEditModal = (id: number) => {
  editingRule.value = automationStore.rules.find(r => r.id === id)
  initialTrigger.value = undefined
  isModalOpen.value = true
}

const closeModal = () => {
  isModalOpen.value = false
  editingRule.value = undefined
  initialTrigger.value = undefined
}

const handleSave = (rule: Omit<AutomationRule, 'id' | 'createdAt' | 'updatedAt' | 'executionCount' | 'lastExecutedAt'>) => {
  if (editingRule.value) {
    automationStore.updateRule(editingRule.value.id, rule)
  } else {
    automationStore.createRule(rule)
  }
}

const handleDelete = (id: number) => {
  askConfirm(t('automation.deleteRuleTitle'), t('automation.confirmDeleteRule'), () => {
    automationStore.deleteRule(id)
  })
}

const handleToggle = (id: number) => {
  automationStore.toggleRule(id)
}

/** 선택된 규칙 일괄 삭제 — 이 화면의 기존 확인 모달(askConfirm)을 그대로 쓴다. */
const handleBulkDeleteRules = () => {
  const ids = [...selectedRuleIds.value]
  if (ids.length === 0) return
  askConfirm(
    t('automation.bulkDeleteRulesTitle'),
    t('automation.bulkDeleteMessage', { count: ids.length }),
    async () => {
      bulkDeleting.value = true
      try {
        await Promise.all(ids.map((id) => automationStore.deleteRule(id)))
      } finally {
        bulkDeleting.value = false
        clearRuleSelection()
      }
    },
  )
}

// ─── Smart Trigger Templates ─────────────────────────────
const smartTemplates = ref<SmartTriggerTemplate[]>([])

async function fetchSmartTemplates() {
  try {
    const result = await apiClient
      .get<ResData<SmartTriggerTemplate[]>>('/automation/rules/smart-templates')
      .then(unwrapResponse)
    smartTemplates.value = result
  } catch {
    notification.error('스마트 템플릿을 불러오지 못했습니다')
    smartTemplates.value = []
  }
}

function handleSmartTriggerSelect(payload: { triggerType: string; config: Record<string, unknown> }) {
  const template = smartTemplates.value.find(t => t.triggerType === payload.triggerType)
  if (!template) return

  editingRule.value = undefined
  initialTrigger.value = {
    triggerType: payload.triggerType,
    config: payload.config,
    name: template.name,
    description: template.description,
  }
  isModalOpen.value = true
}

onMounted(() => {
  automationStore.fetchRules()
  fetchSmartTemplates()
})

watch(activeTab, (tab) => {
  if (tab === 'logs') void automationStore.fetchLogs()
})

</script>

<template>
  <div class="relative min-h-full space-y-5 py-5 text-content">
      <!-- Header -->
      <PageHeader :title="$t('automation.title')" :description="$t('automation.description')">
        <template #title-suffix>
          <span class="inline-flex items-center gap-2 rounded-full bg-info-subtle px-3 py-1 text-body font-medium text-info-strong">
            <span class="h-2 w-2 animate-pulse rounded-full bg-info"></span>
            {{ $t('automation.activeRules', { count: activeRuleCount }) }}
          </span>
        </template>
        <template #actions>
          <button
            class="btn-primary inline-flex items-center gap-2"
            @click="openCreateModal"
          >
            <PlusIcon class="w-5 h-5" />
            {{ $t('automation.newRule') }}
          </button>
        </template>
      </PageHeader>

      <PageGuide :title="$t('automation.pageGuideTitle')" :items="($tm('automation.pageGuide') as string[])" />

      <!-- Tabs -->
      <OTabs v-model="activeTab" :tabs="automationTabs" class="mb-6" />

      <!-- Smart Trigger Templates -->
      <SectionCard v-if="activeTab === 'rules' && smartTemplates.length > 0" :title="$t('automation.tabRules')" class="mb-4">
        <SmartTriggerTemplateSelector
          :templates="smartTemplates"
          @select="handleSmartTriggerSelect"
        />
      </SectionCard>

      <!-- Rules Tab -->
      <div v-if="activeTab === 'rules'">
        <div v-if="automationStore.error" class="mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-error-subtle bg-error-subtle px-3 py-2.5 text-body text-error-strong" role="alert">
          <span class="min-w-0 flex-1">{{ $t('automation.loadFailed') }}</span>
          <button type="button" class="shrink-0 rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold" :disabled="automationStore.loading" @click="automationStore.fetchRules()">
            {{ $t('action.retry') }}
          </button>
        </div>
        <!-- 검색 · 정렬 · 일괄 작업 -->
        <ListToolbar
          v-if="!isRuleSourceEmpty"
          v-model="ruleQuery"
          v-model:sort-key="ruleSortKey"
          v-model:sort-dir="ruleSortDir"
          :sort-options="ruleSortOptions"
          :selected-count="selectedRuleCount"
          :total-count="visibleRuleCount"
          :search-placeholder="$t('automation.searchRulesPlaceholder')"
          :search-label="$t('automation.searchRulesLabel')"
          @clear-selection="clearRuleSelection"
        >
          <template #filters>
            <button
              v-for="option in enabledFilters"
              :key="option.value"
              type="button"
              :class="[
                'min-h-9 rounded-lg border px-3 py-2 text-body font-medium transition-colors',
                ruleFilter === option.value
                  ? 'border-accent bg-accent-dim text-accent'
                  : 'border-line-control bg-surface-input text-content-secondary hover:bg-surface-raised hover:text-content'
              ]"
              @click="ruleFilter = option.value"
            >
              {{ option.label }}
            </button>
          </template>

          <template #bulk-actions>
            <button
              type="button"
              class="btn-danger inline-flex items-center gap-1.5"
              :disabled="bulkDeleting"
              @click="handleBulkDeleteRules"
            >
              <TrashIcon class="h-4 w-4" aria-hidden="true" />
              {{ $t('list.bulkDelete') }}
            </button>
          </template>
        </ListToolbar>

        <!-- 전체 선택 -->
        <div v-if="!isRuleSourceEmpty && visibleRuleCount > 0" class="mb-3 flex items-center gap-2">
          <input
            id="automation-rules-select-all"
            type="checkbox"
            :class="CHECKBOX_CLASS"
            :checked="allRulesSelected"
            :indeterminate="someRulesSelected"
            @change="toggleAllRules"
          />
          <label for="automation-rules-select-all" class="cursor-pointer text-body text-gray-500 dark:text-gray-400">
            {{ $t('list.selectAll', { count: visibleRuleCount }) }}
          </label>
        </div>

        <AsyncState
          :loading="automationStore.loading && isRuleSourceEmpty"
          :empty="isRuleSourceEmpty"
          skeleton="card"
          :skeleton-count="3"
          :empty-icon="BoltIcon"
          :empty-title="$t('automation.emptyRulesTitle')"
          :empty-description="$t('automation.emptyRulesDesc')"
          :empty-action-label="$t('automation.createFirstRule')"
          :retryable="false"
          @empty-action="openCreateModal"
        >
          <!-- 검색·필터 결과만 비었을 때 -->
          <EmptyState
            v-if="isRuleResultEmpty"
            :icon="MagnifyingGlassIcon"
            :title="$t('list.noResultsTitle')"
            :description="$t('list.noResultsDescription')"
            :action-label="$t('list.resetFilters')"
            @action="resetRuleSearchAndFilters"
          />

          <div v-else class="space-y-4">
            <div
              v-for="rule in filteredRules"
              :key="rule.id"
              class="flex items-start gap-3"
            >
              <input
                type="checkbox"
                :class="[CHECKBOX_CLASS, 'mt-7']"
                :checked="isRuleSelected(rule.id)"
                :aria-label="$t('list.selectItem', { name: rule.name })"
                @change="toggleRuleSelection(rule.id)"
              />
              <AutomationRuleCard
                :rule="rule"
                class="min-w-0 flex-1"
                @edit="openEditModal"
                @delete="handleDelete"
                @toggle="handleToggle"
              />
            </div>
          </div>
        </AsyncState>
      </div>

      <div v-if="activeTab === 'logs'">
        <div v-if="automationStore.logsError" class="mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-error-subtle bg-error-subtle px-3 py-2.5 text-body text-error-strong" role="alert">
          <span class="min-w-0 flex-1">{{ $t('automation.loadFailed') }}</span>
          <button type="button" class="shrink-0 rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold" :disabled="automationStore.logsLoading" @click="automationStore.fetchLogs()">
            {{ $t('action.retry') }}
          </button>
        </div>
        <AutomationLogTable :logs="automationStore.recentLogs" />
      </div>

    <!-- Modal -->
    <AutomationFormModal
      :is-open="isModalOpen"
      :rule="editingRule"
      :initial-trigger="initialTrigger"
      @close="closeModal"
      @save="handleSave"
    />

    <!-- 삭제 확인 -->
    <ConfirmModal
      v-model="showConfirmModal"
      :title="confirmTitle"
      :message="confirmMessage"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="runPendingAction"
      @cancel="pendingAction = null"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, shallowRef, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  PlusIcon,
  BoltIcon,
  MagnifyingGlassIcon,
  Squares2X2Icon,
  TrashIcon,
} from '@heroicons/vue/24/outline'
import { useAutomationStore } from '@/stores/automation'
import { useNotification } from '@/composables/useNotification'
import { useListControls, type ListSortOption } from '@/composables/useListControls'
import AutomationRuleCard from '@/components/automation/AutomationRuleCard.vue'
import AutomationLogTable from '@/components/automation/AutomationLogTable.vue'
import AutomationFormModal from '@/components/automation/AutomationFormModal.vue'
import SmartTriggerTemplateSelector from '@/components/automation/SmartTriggerTemplateSelector.vue'
import WorkflowNodeEditor from '@/components/automation/WorkflowNodeEditor.vue'
import WorkflowExecutionHistory from '@/components/automation/WorkflowExecutionHistory.vue'
import OTabs from '@/components/ui/OTabs.vue'
import AsyncState from '@/components/common/AsyncState.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ListToolbar from '@/components/common/ListToolbar.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import type { AutomationRule, ConditionOperator, Workflow, WorkflowTriggerType, WorkflowActionType } from '@/types/automation'
import type { SmartTriggerTemplate } from '@/components/automation/SmartTriggerTemplateSelector.vue'
import { automationApi } from '@/api/automation'
import apiClient, { unwrapResponse } from '@/api/client'
import type { ResData } from '@/types/api'

const { t } = useI18n()
const automationStore = useAutomationStore()
const notification = useNotification()

/** 체크박스 공통 스타일 — 목록 확산 시 그대로 복사해 쓴다. */
const CHECKBOX_CLASS =
  'h-4 w-4 shrink-0 cursor-pointer rounded border-gray-300 accent-primary-600 focus:ring-2 focus:ring-primary-500 dark:border-gray-600'

const activeTab = ref<'rules' | 'workflows' | 'logs'>('rules')
const automationTabs = computed(() => [
  { key: 'rules', label: t('automation.tabRules'), count: automationStore.rules.length },
  { key: 'workflows', label: t('automation.tabWorkflows'), count: workflows.value.length },
  { key: 'logs', label: t('automation.tabLogs'), count: automationStore.logs.length },
])
const isModalOpen = ref(false)
const editingRule = ref<AutomationRule | undefined>(undefined)

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

/** 규칙/워크플로우 두 목록이 공유하는 활성 상태 필터 옵션. */
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
  isModalOpen.value = true
}

const openEditModal = (id: number) => {
  editingRule.value = automationStore.rules.find(r => r.id === id)
  isModalOpen.value = true
}

const closeModal = () => {
  isModalOpen.value = false
  editingRule.value = undefined
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

let smartTriggerTimeout: ReturnType<typeof setTimeout> | null = null

function handleSmartTriggerSelect(payload: { triggerType: string; config: Record<string, unknown> }) {
  const template = smartTemplates.value.find(t => t.triggerType === payload.triggerType)
  if (!template) return

  editingRule.value = undefined
  isModalOpen.value = true
  // Pre-fill with smart trigger data via a nextTick
  smartTriggerTimeout = setTimeout(() => {
    // The modal will be opened with pre-filled trigger type and config
  }, 0)
}

// ─── Workflows ───────────────────────────────────────────
const workflows = ref<Workflow[]>([])
const showWorkflowEditor = ref(false)
const editingWorkflow = ref<Workflow | null>(null)
/** 실행 이력을 펼쳐 둔 워크플로우 — 목록의 다중 선택(selectedWorkflowIds)과는 별개다. */
const selectedWorkflowId = ref<number | null>(null)
const workflowsLoading = ref(false)

const workflowPriority = (workflow: Workflow): number =>
  (workflow.enabled ? ENABLED_BOOST : 0) + timeOf(workflow.updatedAt ?? workflow.createdAt)

const workflowSortOptions = computed<ListSortOption<Workflow>[]>(() => [
  {
    key: 'status',
    label: t('automation.sortEnabledFirst'),
    accessor: workflowPriority,
    kind: 'number',
    defaultDir: 'desc',
  },
  {
    key: 'recent',
    label: t('automation.sortRecent'),
    accessor: (workflow) => workflow.updatedAt ?? workflow.createdAt,
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

const workflowFilter = ref<'all' | 'enabled' | 'disabled'>('all')

/**
 * 규칙과 완전히 독립된 인스턴스다.
 * 규칙 id와 워크플로우 id는 서로 다른 시퀀스라 값이 겹칠 수 있으므로,
 * 선택 상태를 공유하면 다른 탭의 항목이 함께 지워지는 사고가 난다.
 */
const {
  query: workflowQuery,
  sortKey: workflowSortKey,
  sortDir: workflowSortDir,
  filtered: filteredWorkflows,
  visibleCount: visibleWorkflowCount,
  isSourceEmpty: isWorkflowSourceEmpty,
  isResultEmpty: isWorkflowResultEmpty,
  resetFilters: resetWorkflowFilters,
  selectedIds: selectedWorkflowIds,
  selectedCount: selectedWorkflowCount,
  allSelected: allWorkflowsSelected,
  someSelected: someWorkflowsSelected,
  isSelected: isWorkflowSelected,
  toggle: toggleWorkflowSelection,
  toggleAll: toggleAllWorkflows,
  clearSelection: clearWorkflowSelection,
} = useListControls<Workflow>(() => workflows.value, {
  searchFields: ['name', 'description', 'triggerType'],
  sortOptions: workflowSortOptions,
  defaultSortKey: 'status',
  filters: computed(() =>
    workflowFilter.value === 'all'
      ? []
      : [
          (workflow: Workflow) =>
            workflowFilter.value === 'enabled' ? workflow.enabled : !workflow.enabled,
        ],
  ),
})

const resetWorkflowSearchAndFilters = () => {
  resetWorkflowFilters()
  workflowFilter.value = 'all'
}

async function fetchWorkflows() {
  workflowsLoading.value = true
  try {
    workflows.value = await automationApi.listWorkflows()
  } catch {
    notification.error('워크플로우 목록을 불러오지 못했습니다')
    workflows.value = []
  } finally {
    workflowsLoading.value = false
  }
}

function openWorkflowEditor(workflow?: Workflow) {
  editingWorkflow.value = workflow ?? null
  showWorkflowEditor.value = true
}

async function handleWorkflowSave(data: {
  triggerType: WorkflowTriggerType
  triggerConfig: Record<string, unknown>
  conditions: Array<{ groupType: string; field?: string; operator?: string; value?: string; expression?: string }>
  actions: Array<{ actionType: WorkflowActionType; config: Record<string, unknown>; delayMinutes: number }>
}) {
  try {
    if (editingWorkflow.value) {
      await automationApi.updateWorkflow(editingWorkflow.value.id, {
        triggerType: data.triggerType,
        triggerConfig: data.triggerConfig,
        conditions: data.conditions.map((c) => ({
          groupType: (c.groupType as 'AND' | 'OR') ?? 'AND',
          field: c.field,
          operator: c.operator as ConditionOperator,
          value: c.value,
          expression: c.expression,
        })),
        actions: data.actions.map((a) => ({
          actionType: a.actionType,
          config: a.config,
          delayMinutes: a.delayMinutes,
        })),
      })
    } else {
      await automationApi.createWorkflow({
        name: '새 워크플로우',
        triggerType: data.triggerType,
        triggerConfig: data.triggerConfig,
        conditions: data.conditions.map((c) => ({
          groupType: (c.groupType as 'AND' | 'OR') ?? 'AND',
          field: c.field,
          operator: c.operator as ConditionOperator,
          value: c.value,
          expression: c.expression,
        })),
        actions: data.actions.map((a) => ({
          actionType: a.actionType,
          config: a.config,
          delayMinutes: a.delayMinutes,
        })),
        enabled: false,
      })
    }
    showWorkflowEditor.value = false
    editingWorkflow.value = null
    await fetchWorkflows()
  } catch {
    notification.error('워크플로우 저장에 실패했습니다')
  }
}

async function handleWorkflowToggle(id: number) {
  try {
    await automationApi.toggleWorkflow(id)
    await fetchWorkflows()
  } catch {
    notification.error('워크플로우 상태 변경에 실패했습니다')
  }
}

function handleWorkflowDelete(id: number) {
  askConfirm(t('automation.deleteWorkflowTitle'), t('automation.confirmDeleteWorkflow'), async () => {
    try {
      await automationApi.deleteWorkflow(id)
      await fetchWorkflows()
    } catch {
      notification.error('워크플로우 삭제에 실패했습니다')
    }
  })
}

/** 선택된 워크플로우 일괄 삭제 — 규칙과 동일하게 기존 확인 모달을 재사용한다. */
function handleBulkDeleteWorkflows() {
  const ids = [...selectedWorkflowIds.value]
  if (ids.length === 0) return
  askConfirm(
    t('automation.bulkDeleteWorkflowsTitle'),
    t('automation.bulkDeleteMessage', { count: ids.length }),
    async () => {
      bulkDeleting.value = true
      try {
        await Promise.all(ids.map((id) => automationApi.deleteWorkflow(id)))
      } catch {
        notification.error('워크플로우 삭제에 실패했습니다')
      } finally {
        bulkDeleting.value = false
        clearWorkflowSelection()
        await fetchWorkflows()
      }
    },
  )
}

onMounted(() => {
  automationStore.fetchRules()
  fetchSmartTemplates()
  fetchWorkflows()
})

onUnmounted(() => {
  if (smartTriggerTimeout) clearTimeout(smartTriggerTimeout)
})
</script>

<template>
  <div class="relative">
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
      <div v-if="activeTab === 'rules' && smartTemplates.length > 0" class="card mb-6">
        <SmartTriggerTemplateSelector
          :templates="smartTemplates"
          @select="handleSmartTriggerSelect"
        />
      </div>

      <!-- Rules Tab -->
      <div v-if="activeTab === 'rules'">
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
                'min-h-10 rounded-lg px-3 py-2 text-body font-medium transition-colors',
                ruleFilter === option.value
                  ? 'bg-primary-600 text-white dark:bg-primary-500'
                  : 'border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
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

      <!-- Workflows Tab -->
      <div v-if="activeTab === 'workflows'">
        <!-- Workflow Editor -->
        <div v-if="showWorkflowEditor" class="card mb-6">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-title font-semibold text-gray-900 dark:text-gray-100">
              {{ editingWorkflow ? $t('automation.editWorkflow') : $t('automation.newWorkflow') }}
            </h2>
            <button
              class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
              @click="showWorkflowEditor = false; editingWorkflow = null"
            >
              <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
          <WorkflowNodeEditor
            :initial-trigger-type="editingWorkflow?.triggerType"
            :initial-trigger-config="editingWorkflow?.triggerConfig"
            :initial-conditions="editingWorkflow?.conditions"
            :initial-actions="editingWorkflow?.actions"
            @save="handleWorkflowSave"
          />
        </div>

        <!-- Workflow list -->
        <div v-else>
          <div class="flex items-center justify-between mb-4">
            <p class="text-body text-gray-600 dark:text-gray-400">
              {{ $t('automation.workflowSubtitle') }}
            </p>
            <button
              class="btn-primary inline-flex items-center gap-2"
              @click="openWorkflowEditor()"
            >
              <PlusIcon class="w-4 h-4" />
              {{ $t('automation.newWorkflow') }}
            </button>
          </div>

          <!-- 검색 · 정렬 · 일괄 작업 -->
          <ListToolbar
            v-if="!isWorkflowSourceEmpty"
            v-model="workflowQuery"
            v-model:sort-key="workflowSortKey"
            v-model:sort-dir="workflowSortDir"
            :sort-options="workflowSortOptions"
            :selected-count="selectedWorkflowCount"
            :total-count="visibleWorkflowCount"
            :search-placeholder="$t('automation.searchWorkflowsPlaceholder')"
            :search-label="$t('automation.searchWorkflowsLabel')"
            @clear-selection="clearWorkflowSelection"
          >
            <template #filters>
              <button
                v-for="option in enabledFilters"
                :key="option.value"
                type="button"
                :class="[
                  'min-h-10 rounded-lg px-3 py-2 text-body font-medium transition-colors',
                  workflowFilter === option.value
                    ? 'bg-primary-600 text-white dark:bg-primary-500'
                    : 'border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
                ]"
                @click="workflowFilter = option.value"
              >
                {{ option.label }}
              </button>
            </template>

            <template #bulk-actions>
              <button
                type="button"
                class="btn-danger inline-flex items-center gap-1.5"
                :disabled="bulkDeleting"
                @click="handleBulkDeleteWorkflows"
              >
                <TrashIcon class="h-4 w-4" aria-hidden="true" />
                {{ $t('list.bulkDelete') }}
              </button>
            </template>
          </ListToolbar>

          <!-- 전체 선택 -->
          <div v-if="!isWorkflowSourceEmpty && visibleWorkflowCount > 0" class="mb-3 flex items-center gap-2">
            <input
              id="automation-workflows-select-all"
              type="checkbox"
              :class="CHECKBOX_CLASS"
              :checked="allWorkflowsSelected"
              :indeterminate="someWorkflowsSelected"
              @change="toggleAllWorkflows"
            />
            <label for="automation-workflows-select-all" class="cursor-pointer text-body text-gray-500 dark:text-gray-400">
              {{ $t('list.selectAll', { count: visibleWorkflowCount }) }}
            </label>
          </div>

          <AsyncState
            :loading="workflowsLoading && isWorkflowSourceEmpty"
            :empty="isWorkflowSourceEmpty"
            skeleton="list"
            :skeleton-count="3"
            :empty-icon="Squares2X2Icon"
            :empty-title="$t('automation.emptyWorkflowsTitle')"
            :empty-description="$t('automation.emptyWorkflowsDesc')"
            :empty-action-label="$t('automation.createFirstWorkflow')"
            :retryable="false"
            @empty-action="openWorkflowEditor()"
          >
            <!-- 검색·필터 결과만 비었을 때 -->
            <EmptyState
              v-if="isWorkflowResultEmpty"
              :icon="MagnifyingGlassIcon"
              :title="$t('list.noResultsTitle')"
              :description="$t('list.noResultsDescription')"
              :action-label="$t('list.resetFilters')"
              @action="resetWorkflowSearchAndFilters"
            />

            <div v-else class="space-y-3">
              <div
                v-for="wf in filteredWorkflows"
                :key="wf.id"
                class="card"
              >
                <div class="flex items-center justify-between gap-3">
                  <div class="flex min-w-0 items-center gap-3">
                    <input
                      type="checkbox"
                      :class="CHECKBOX_CLASS"
                      :checked="isWorkflowSelected(wf.id)"
                      :aria-label="$t('list.selectItem', { name: wf.name })"
                      @change="toggleWorkflowSelection(wf.id)"
                    />
                    <div class="w-10 h-10 rounded-lg flex items-center justify-center" :class="wf.enabled ? 'bg-success-subtle' : 'bg-gray-100 dark:bg-gray-800'">
                      <svg class="w-5 h-5" :class="wf.enabled ? 'text-success-strong' : 'text-gray-400'" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75z" />
                      </svg>
                    </div>
                    <div>
                      <h3 class="text-body font-semibold text-gray-900 dark:text-gray-100">{{ wf.name }}</h3>
                      <p class="text-body-xs text-gray-500 dark:text-gray-400">
                        {{ $t('automation.workflowStats', { conditions: wf.conditions.length, actions: wf.actions.length }) }}
                        <span v-if="wf.executionCount > 0" class="ml-2">{{ $t('automation.executionCount', { count: wf.executionCount }) }}</span>
                      </p>
                    </div>
                  </div>
                  <div class="flex items-center gap-2">
                    <button
                      class="text-body-xs text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
                      @click="selectedWorkflowId = selectedWorkflowId === wf.id ? null : wf.id"
                    >
                      {{ $t('automation.history') }}
                    </button>
                    <button
                      class="text-body-xs text-primary-600 dark:text-primary-400 hover:underline"
                      @click="openWorkflowEditor(wf)"
                    >
                      {{ $t('automation.edit') }}
                    </button>
                    <button
                      class="px-3 py-1 text-body-xs rounded-full font-medium transition-colors"
                      :class="wf.enabled ? 'bg-success-subtle text-success-strong' : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400'"
                      @click="handleWorkflowToggle(wf.id)"
                    >
                      {{ wf.enabled ? $t('automation.enabled') : $t('automation.disabled') }}
                    </button>
                    <button
                      class="text-body-xs text-error-strong transition hover:opacity-80"
                      @click="handleWorkflowDelete(wf.id)"
                    >
                      {{ $t('automation.delete') }}
                    </button>
                  </div>
                </div>

                <!-- Execution history (expandable) -->
                <div v-if="selectedWorkflowId === wf.id" class="mt-4 pt-4 border-t border-gray-200 dark:border-gray-700">
                  <WorkflowExecutionHistory :workflow-id="wf.id" />
                </div>
              </div>
            </div>
          </AsyncState>
        </div>
      </div>

      <!-- Logs Tab -->
      <div v-if="activeTab === 'logs'">
        <AutomationLogTable :logs="automationStore.recentLogs" />
      </div>

    <!-- Modal -->
    <AutomationFormModal
      :is-open="isModalOpen"
      :rule="editingRule"
      @close="closeModal"
      @save="handleSave"
    />

    <!-- 삭제 확인 (규칙 / 워크플로우 공용) -->
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

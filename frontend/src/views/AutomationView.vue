<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { PlusIcon, BoltIcon } from '@heroicons/vue/24/outline'
import { useAutomationStore } from '@/stores/automation'
import AutomationRuleCard from '@/components/automation/AutomationRuleCard.vue'
import AutomationLogTable from '@/components/automation/AutomationLogTable.vue'
import AutomationFormModal from '@/components/automation/AutomationFormModal.vue'
import SmartTriggerTemplateSelector from '@/components/automation/SmartTriggerTemplateSelector.vue'
import WorkflowNodeEditor from '@/components/automation/WorkflowNodeEditor.vue'
import WorkflowExecutionHistory from '@/components/automation/WorkflowExecutionHistory.vue'
import type { AutomationRule, Workflow, WorkflowTriggerType, WorkflowActionType } from '@/types/automation'
import type { SmartTriggerTemplate } from '@/components/automation/SmartTriggerTemplateSelector.vue'
import { automationApi } from '@/api/automation'
import apiClient, { unwrapResponse } from '@/api/client'
import type { ResData } from '@/types/api'

const automationStore = useAutomationStore()

const activeTab = ref<'rules' | 'workflows' | 'logs'>('rules')
const isModalOpen = ref(false)
const editingRule = ref<AutomationRule | undefined>(undefined)

const activeRuleCount = computed(() => {
  return automationStore.rules.filter(r => r.isEnabled && r.status === 'active').length
})

const sortedRules = computed(() => {
  return [...automationStore.rules].sort((a, b) => {
    if (a.isEnabled !== b.isEnabled) {
      return a.isEnabled ? -1 : 1
    }
    return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
  })
})

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
  if (confirm('이 규칙을 삭제하시겠습니까?')) {
    automationStore.deleteRule(id)
  }
}

const handleToggle = (id: number) => {
  automationStore.toggleRule(id)
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
    smartTemplates.value = []
  }
}

function handleSmartTriggerSelect(payload: { triggerType: string; config: Record<string, unknown> }) {
  const template = smartTemplates.value.find(t => t.triggerType === payload.triggerType)
  if (!template) return

  editingRule.value = undefined
  isModalOpen.value = true
  // Pre-fill with smart trigger data via a nextTick
  setTimeout(() => {
    // The modal will be opened with pre-filled trigger type and config
  }, 0)
}

// ─── Workflows ───────────────────────────────────────────
const workflows = ref<Workflow[]>([])
const showWorkflowEditor = ref(false)
const editingWorkflow = ref<Workflow | null>(null)
const selectedWorkflowId = ref<number | null>(null)

async function fetchWorkflows() {
  try {
    workflows.value = await automationApi.listWorkflows()
  } catch {
    workflows.value = []
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
          operator: c.operator as any,
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
          operator: c.operator as any,
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
    // error
  }
}

async function handleWorkflowToggle(id: number) {
  try {
    await automationApi.toggleWorkflow(id)
    await fetchWorkflows()
  } catch {
    // error
  }
}

async function handleWorkflowDelete(id: number) {
  if (!confirm('이 워크플로우를 삭제하시겠습니까?')) return
  try {
    await automationApi.deleteWorkflow(id)
    await fetchWorkflows()
  } catch {
    // error
  }
}

onMounted(() => {
  automationStore.fetchRules()
  fetchSmartTemplates()
  fetchWorkflows()
})
</script>

<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <!-- Header -->
      <div class="mb-8">
        <div class="flex items-center justify-between">
          <div>
            <div class="flex items-center gap-3 mb-2">
              <BoltIcon class="w-8 h-8 text-blue-600 dark:text-blue-400" />
              <h1 class="text-3xl font-bold text-gray-900 dark:text-white">
                자동화 규칙
              </h1>
            </div>
            <p class="text-gray-600 dark:text-gray-400">
              반복 작업을 자동화하여 효율적으로 관리하세요
            </p>
            <div class="mt-3 inline-flex items-center gap-2 px-3 py-1 bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 rounded-full text-sm font-medium">
              <span class="w-2 h-2 bg-blue-600 rounded-full animate-pulse"></span>
              활성 규칙 {{ activeRuleCount }}개
            </div>
          </div>

          <button
            @click="openCreateModal"
            class="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium"
          >
            <PlusIcon class="w-5 h-5" />
            새 규칙
          </button>
        </div>
      </div>

      <!-- Tabs -->
      <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
        <nav class="-mb-px flex gap-8">
          <button
            @click="activeTab = 'rules'"
            :class="[
              'py-4 px-1 border-b-2 font-medium text-sm transition-colors',
              activeTab === 'rules'
                ? 'border-blue-600 text-blue-600 dark:text-blue-400'
                : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600'
            ]"
          >
            규칙 목록
            <span
              :class="[
                'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold',
                activeTab === 'rules'
                  ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                  : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400'
              ]"
            >
              {{ automationStore.rules.length }}
            </span>
          </button>

          <button
            @click="activeTab = 'workflows'"
            :class="[
              'py-4 px-1 border-b-2 font-medium text-sm transition-colors',
              activeTab === 'workflows'
                ? 'border-blue-600 text-blue-600 dark:text-blue-400'
                : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600'
            ]"
          >
            워크플로우
            <span
              :class="[
                'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold',
                activeTab === 'workflows'
                  ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                  : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400'
              ]"
            >
              {{ workflows.length }}
            </span>
          </button>

          <button
            @click="activeTab = 'logs'"
            :class="[
              'py-4 px-1 border-b-2 font-medium text-sm transition-colors',
              activeTab === 'logs'
                ? 'border-blue-600 text-blue-600 dark:text-blue-400'
                : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600'
            ]"
          >
            실행 로그
            <span
              :class="[
                'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold',
                activeTab === 'logs'
                  ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                  : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400'
              ]"
            >
              {{ automationStore.logs.length }}
            </span>
          </button>
        </nav>
      </div>

      <!-- Smart Trigger Templates -->
      <div v-if="activeTab === 'rules' && smartTemplates.length > 0" class="mb-6 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-5">
        <SmartTriggerTemplateSelector
          :templates="smartTemplates"
          @select="handleSmartTriggerSelect"
        />
      </div>

      <!-- Rules Tab -->
      <div v-if="activeTab === 'rules'">
        <div v-if="sortedRules.length > 0" class="space-y-4">
          <AutomationRuleCard
            v-for="rule in sortedRules"
            :key="rule.id"
            :rule="rule"
            @edit="openEditModal"
            @delete="handleDelete"
            @toggle="handleToggle"
          />
        </div>

        <!-- Empty State -->
        <div
          v-else
          class="text-center py-16 bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700"
        >
          <BoltIcon class="w-16 h-16 mx-auto text-gray-400 dark:text-gray-600 mb-4" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-2">
            자동화 규칙이 없습니다
          </h3>
          <p class="text-gray-600 dark:text-gray-400 mb-6">
            첫 번째 자동화 규칙을 만들어 작업을 효율화하세요
          </p>
          <button
            @click="openCreateModal"
            class="inline-flex items-center gap-2 px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium"
          >
            <PlusIcon class="w-5 h-5" />
            첫 규칙 만들기
          </button>
        </div>
      </div>

      <!-- Workflows Tab -->
      <div v-if="activeTab === 'workflows'">
        <!-- Workflow Editor -->
        <div v-if="showWorkflowEditor" class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6 mb-6">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
              {{ editingWorkflow ? '워크플로우 수정' : '새 워크플로우' }}
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
            <p class="text-sm text-gray-600 dark:text-gray-400">
              조건 분기와 다중 액션을 포함한 고급 자동화 워크플로우
            </p>
            <button
              class="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium"
              @click="openWorkflowEditor()"
            >
              <PlusIcon class="w-4 h-4" />
              새 워크플로우
            </button>
          </div>

          <div v-if="workflows.length > 0" class="space-y-3">
            <div
              v-for="wf in workflows"
              :key="wf.id"
              class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4"
            >
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-3">
                  <div class="w-10 h-10 rounded-lg flex items-center justify-center" :class="wf.enabled ? 'bg-green-100 dark:bg-green-900/30' : 'bg-gray-100 dark:bg-gray-800'">
                    <svg class="w-5 h-5" :class="wf.enabled ? 'text-green-600 dark:text-green-400' : 'text-gray-400'" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75z" />
                    </svg>
                  </div>
                  <div>
                    <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ wf.name }}</h3>
                    <p class="text-xs text-gray-500 dark:text-gray-400">
                      {{ wf.conditions.length }}개 조건 / {{ wf.actions.length }}개 액션
                      <span v-if="wf.executionCount > 0" class="ml-2">{{ wf.executionCount }}회 실행</span>
                    </p>
                  </div>
                </div>
                <div class="flex items-center gap-2">
                  <button
                    class="text-xs text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
                    @click="selectedWorkflowId = selectedWorkflowId === wf.id ? null : wf.id"
                  >
                    이력
                  </button>
                  <button
                    class="text-xs text-primary-600 dark:text-primary-400 hover:underline"
                    @click="openWorkflowEditor(wf)"
                  >
                    수정
                  </button>
                  <button
                    class="px-3 py-1 text-xs rounded-full font-medium transition-colors"
                    :class="wf.enabled ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300' : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400'"
                    @click="handleWorkflowToggle(wf.id)"
                  >
                    {{ wf.enabled ? '활성' : '비활성' }}
                  </button>
                  <button
                    class="text-xs text-red-500 hover:text-red-700"
                    @click="handleWorkflowDelete(wf.id)"
                  >
                    삭제
                  </button>
                </div>
              </div>

              <!-- Execution history (expandable) -->
              <div v-if="selectedWorkflowId === wf.id" class="mt-4 pt-4 border-t border-gray-200 dark:border-gray-700">
                <WorkflowExecutionHistory :workflow-id="wf.id" />
              </div>
            </div>
          </div>

          <!-- Empty state -->
          <div v-else class="text-center py-16 bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700">
            <svg class="w-16 h-16 mx-auto text-gray-400 dark:text-gray-600 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1">
              <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75z" />
            </svg>
            <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-2">워크플로우가 없습니다</h3>
            <p class="text-gray-600 dark:text-gray-400 mb-6">조건 분기와 다중 액션을 포함한 고급 자동화를 만들어보세요</p>
            <button
              class="inline-flex items-center gap-2 px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium"
              @click="openWorkflowEditor()"
            >
              <PlusIcon class="w-5 h-5" />
              첫 워크플로우 만들기
            </button>
          </div>
        </div>
      </div>

      <!-- Logs Tab -->
      <div v-if="activeTab === 'logs'">
        <AutomationLogTable :logs="automationStore.recentLogs" />
      </div>
    </div>

    <!-- Modal -->
    <AutomationFormModal
      :is-open="isModalOpen"
      :rule="editingRule"
      @close="closeModal"
      @save="handleSave"
    />
  </div>
</template>

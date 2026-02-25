<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import {
  PlusIcon,
  BoltIcon,
  PlayIcon,
  PencilSquareIcon,
  TrashIcon,
  ClockIcon,
  DocumentDuplicateIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import WorkflowToolbar from '@/components/workflow/WorkflowToolbar.vue'
import WorkflowCanvas from '@/components/workflow/WorkflowCanvas.vue'
import NodePalette from '@/components/workflow/NodePalette.vue'
import NodeConfigPanel from '@/components/workflow/NodeConfigPanel.vue'
import WorkflowMobileList from '@/components/workflow/WorkflowMobileList.vue'
import { useWorkflowBuilderStore, WORKFLOW_TEMPLATES } from '@/stores/workflowBuilder'
import type { WfWorkflow, WfNodeKind, WfTemplate } from '@/types/workflowBuilder'

const { t } = useI18n()
const store = useWorkflowBuilderStore()
const {
  workflows,
  currentWorkflow,
  nodes,
  connections,
  selectedNodeId,
  loading,
  isDirty,
  zoom,
  panOffset,
  selectedNode,
} = storeToRefs(store)

const mode = ref<'list' | 'editor'>('list')
const isSaving = ref(false)
const showTemplateModal = ref(false)
const showMobilePalette = ref(false)
const isMobile = ref(false)

function checkMobile() {
  isMobile.value = window.innerWidth < 768
}

onMounted(() => {
  store.fetchWorkflows()
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})

// ─── List mode actions ────────────────────────────────
function openEditor(workflow?: WfWorkflow) {
  if (workflow) {
    store.loadWorkflow(workflow)
  } else {
    store.createNewWorkflow()
  }
  mode.value = 'editor'
}

function openTemplates() {
  showTemplateModal.value = true
}

function applyTemplate(template: WfTemplate) {
  store.loadTemplate(template)
  showTemplateModal.value = false
  mode.value = 'editor'
}

async function deleteWorkflow(id: number) {
  if (!confirm(t('workflowBuilder.confirmDelete'))) return
  await store.deleteWorkflow(id)
}

async function toggleWorkflow(id: number) {
  await store.toggleWorkflow(id)
}

// ─── Editor mode actions ──────────────────────────────
function goBack() {
  if (isDirty.value && !confirm(t('workflowBuilder.unsavedWarning'))) return
  mode.value = 'list'
  store.selectNode(null)
}

async function handleSave() {
  if (!currentWorkflow.value) return
  isSaving.value = true
  await store.saveWorkflow()
  isSaving.value = false
}

async function handleTestRun() {
  await store.testRunWorkflow()
}

function handleToggleEnabled() {
  if (!currentWorkflow.value) return
  currentWorkflow.value.isEnabled = !currentWorkflow.value.isEnabled
  store.$patch({ isDirty: true })
}

function handleUpdateName(name: string) {
  if (!currentWorkflow.value) return
  currentWorkflow.value.name = name
  store.$patch({ isDirty: true })
}

function handleAddNode(kind: WfNodeKind) {
  const pos = { x: 200 + Math.random() * 300, y: 150 + Math.random() * 200 }
  store.addNode(kind, pos)
  showMobilePalette.value = false
}

function handleDropNode(kind: WfNodeKind, position: { x: number; y: number }) {
  store.addNode(kind, position)
}

function handleMobileAddNode(_afterNodeId: string | null) {
  showMobilePalette.value = true
}

function handleMobileDeleteNode(nodeId: string) {
  store.removeNode(nodeId)
}

function formatDate(date: string | null): string {
  if (!date) return '-'
  return new Date(date).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}
</script>

<template>
  <div class="relative h-full">
    <!-- ═══ LIST MODE ═══ -->
    <template v-if="mode === 'list'">
      <div>
        <!-- Header -->
        <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
          <div>
            <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
              {{ $t('workflowBuilder.title') }}
            </h1>
            <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
              {{ $t('workflowBuilder.description') }}
            </p>
            <div class="mt-3 inline-flex items-center gap-2 px-3 py-1 bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 rounded-full text-sm font-medium">
              <span class="w-2 h-2 bg-blue-600 rounded-full animate-pulse"></span>
              {{ $t('workflowBuilder.activeCount', { count: store.activeWorkflows.length }) }}
            </div>
          </div>
          <div class="flex items-center gap-3">
            <button
              @click="openTemplates"
              class="inline-flex items-center gap-2 px-4 py-2 bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors text-sm font-medium"
            >
              <DocumentDuplicateIcon class="w-5 h-5" />
              {{ $t('workflowBuilder.templates') }}
            </button>
            <button
              @click="openEditor()"
              class="btn-primary inline-flex items-center gap-2"
            >
              <PlusIcon class="w-5 h-5" />
              {{ $t('workflowBuilder.newWorkflow') }}
            </button>
          </div>
        </div>

        <PageGuide :title="$t('workflowBuilder.pageGuideTitle')" :items="($tm('workflowBuilder.pageGuide') as string[])" />

        <!-- Workflow list -->
        <div v-if="workflows.length > 0" class="space-y-3">
          <div
            v-for="wf in workflows"
            :key="wf.id"
            class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5 hover:shadow-md transition-shadow"
          >
            <div class="flex flex-col gap-3 tablet:flex-row tablet:items-center tablet:justify-between">
              <div class="flex items-center gap-4 min-w-0">
                <div
                  :class="[
                    'w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0',
                    wf.isEnabled
                      ? 'bg-green-100 dark:bg-green-900/30'
                      : 'bg-gray-100 dark:bg-gray-800'
                  ]"
                >
                  <BoltIcon
                    class="w-6 h-6"
                    :class="wf.isEnabled ? 'text-green-600 dark:text-green-400' : 'text-gray-400'"
                  />
                </div>
                <div class="min-w-0">
                  <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100 truncate">
                    {{ wf.name }}
                  </h3>
                  <p v-if="wf.description" class="text-sm text-gray-500 dark:text-gray-400 truncate mt-0.5">
                    {{ wf.description }}
                  </p>
                  <div class="flex items-center gap-4 mt-1 text-xs text-gray-400 dark:text-gray-500">
                    <span>{{ $t('workflowBuilder.nodesCount', { count: wf.nodes.length }) }}</span>
                    <span class="flex items-center gap-1">
                      <PlayIcon class="w-3.5 h-3.5" />
                      {{ $t('workflowBuilder.executionCount', { count: wf.executionCount }) }}
                    </span>
                    <span v-if="wf.lastExecutedAt" class="flex items-center gap-1">
                      <ClockIcon class="w-3.5 h-3.5" />
                      {{ formatDate(wf.lastExecutedAt) }}
                    </span>
                  </div>
                </div>
              </div>

              <div class="flex items-center gap-2 flex-shrink-0">
                <button
                  class="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium text-primary-600 dark:text-primary-400 hover:bg-primary-50 dark:hover:bg-primary-900/20 rounded-lg transition-colors"
                  @click="openEditor(wf)"
                >
                  <PencilSquareIcon class="w-4 h-4" />
                  {{ $t('action.edit') }}
                </button>
                <button
                  @click="toggleWorkflow(wf.id)"
                  :class="[
                    'px-3 py-1.5 text-xs rounded-full font-medium transition-colors',
                    wf.isEnabled
                      ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300'
                      : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400'
                  ]"
                >
                  {{ wf.isEnabled ? $t('workflowBuilder.active') : $t('workflowBuilder.inactive') }}
                </button>
                <button
                  class="p-1.5 text-red-400 hover:text-red-600 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
                  @click="deleteWorkflow(wf.id)"
                >
                  <TrashIcon class="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty state -->
        <div
          v-else-if="!loading"
          class="text-center py-16 bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700"
        >
          <BoltIcon class="w-16 h-16 mx-auto text-gray-400 dark:text-gray-600 mb-4" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-2">
            {{ $t('workflowBuilder.emptyTitle') }}
          </h3>
          <p class="text-gray-600 dark:text-gray-400 mb-6 max-w-md mx-auto">
            {{ $t('workflowBuilder.emptyDesc') }}
          </p>
          <div class="flex items-center justify-center gap-3">
            <button
              @click="openTemplates"
              class="inline-flex items-center gap-2 px-4 py-2 bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors text-sm font-medium"
            >
              <DocumentDuplicateIcon class="w-5 h-5" />
              {{ $t('workflowBuilder.startFromTemplate') }}
            </button>
            <button
              @click="openEditor()"
              class="btn-primary inline-flex items-center gap-2"
            >
              <PlusIcon class="w-5 h-5" />
              {{ $t('workflowBuilder.createFirst') }}
            </button>
          </div>
        </div>

        <!-- Loading -->
        <div v-if="loading" class="flex items-center justify-center py-16">
          <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
        </div>
      </div>

      <!-- Template selection modal -->
      <teleport to="body">
        <div
          v-if="showTemplateModal"
          class="fixed inset-0 z-50 flex items-center justify-center p-4"
        >
          <div class="fixed inset-0 bg-black/50" @click="showTemplateModal = false" />
          <div class="relative bg-white dark:bg-gray-800 rounded-2xl shadow-xl max-w-2xl w-full max-h-[80vh] overflow-hidden">
            <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
              <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
                {{ $t('workflowBuilder.selectTemplate') }}
              </h2>
              <button
                class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
                @click="showTemplateModal = false"
              >
                <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <div class="p-6 overflow-y-auto max-h-[60vh] grid grid-cols-1 tablet:grid-cols-2 gap-4">
              <button
                v-for="tmpl in WORKFLOW_TEMPLATES"
                :key="tmpl.id"
                class="text-left p-4 rounded-xl border-2 border-gray-200 dark:border-gray-700 hover:border-blue-400 dark:hover:border-blue-500 bg-white dark:bg-gray-800 hover:bg-blue-50 dark:hover:bg-blue-900/10 transition-all"
                @click="applyTemplate(tmpl)"
              >
                <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-1">
                  {{ tmpl.name }}
                </h3>
                <p class="text-xs text-gray-500 dark:text-gray-400 mb-3">
                  {{ tmpl.description }}
                </p>
                <div class="flex items-center gap-2">
                  <span class="text-xs px-2 py-0.5 rounded-full bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 font-medium">
                    {{ $t('workflowBuilder.nodesCount', { count: tmpl.nodes.length }) }}
                  </span>
                </div>
              </button>
            </div>
          </div>
        </div>
      </teleport>
    </template>

    <!-- ═══ EDITOR MODE ═══ -->
    <template v-if="mode === 'editor' && currentWorkflow">
      <div class="flex flex-col h-[calc(100vh-64px)]">
        <!-- Toolbar -->
        <WorkflowToolbar
          :workflow-name="currentWorkflow.name"
          :is-enabled="currentWorkflow.isEnabled"
          :is-dirty="isDirty"
          :zoom="zoom"
          :is-saving="isSaving"
          @save="handleSave"
          @test-run="handleTestRun"
          @toggle-enabled="handleToggleEnabled"
          @zoom-in="store.setZoom(zoom + 0.1)"
          @zoom-out="store.setZoom(zoom - 0.1)"
          @reset-view="store.resetView()"
          @back="goBack"
          @update-name="handleUpdateName"
        />

        <!-- Main editor area -->
        <div class="flex flex-1 overflow-hidden">
          <!-- Left sidebar: Node Palette (hidden on mobile) -->
          <div class="hidden tablet:block">
            <NodePalette @add-node="handleAddNode" />
          </div>

          <!-- Canvas (desktop) / List (mobile) -->
          <div class="flex-1 relative">
            <!-- Desktop: SVG Canvas -->
            <div class="hidden tablet:block h-full">
              <WorkflowCanvas
                :nodes="nodes"
                :connections="connections"
                :selected-node-id="selectedNodeId"
                :zoom="zoom"
                :pan-offset="panOffset"
                @select-node="store.selectNode"
                @move-node="store.updateNodePosition"
                @add-connection="store.addConnection"
                @remove-connection="store.removeConnection"
                @set-zoom="store.setZoom"
                @set-pan-offset="store.setPanOffset"
                @drop-node="handleDropNode"
              />
            </div>

            <!-- Mobile: List view -->
            <div class="tablet:hidden h-full overflow-y-auto">
              <WorkflowMobileList
                :nodes="nodes"
                :connections="connections"
                :selected-node-id="selectedNodeId"
                @select-node="store.selectNode"
                @delete-node="handleMobileDeleteNode"
                @add-node-after="handleMobileAddNode"
              />
            </div>
          </div>

          <!-- Right sidebar: Config panel -->
          <NodeConfigPanel
            v-if="selectedNode"
            :node="selectedNode"
            @close="store.selectNode(null)"
            @update-config="store.updateNodeConfig"
            @update-label="store.updateNodeLabel"
            @delete="store.removeNode"
          />
        </div>
      </div>

      <!-- Mobile: Palette modal -->
      <teleport to="body">
        <div
          v-if="showMobilePalette"
          class="tablet:hidden fixed inset-0 z-50 flex flex-col"
        >
          <div class="fixed inset-0 bg-black/50" @click="showMobilePalette = false" />
          <div class="relative mt-auto bg-white dark:bg-gray-800 rounded-t-2xl shadow-xl max-h-[70vh] overflow-hidden flex flex-col">
            <div class="px-4 py-3 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
              <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
                {{ $t('workflowBuilder.addNode') }}
              </h3>
              <button
                class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
                @click="showMobilePalette = false"
              >
                <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <div class="flex-1 overflow-y-auto">
              <NodePalette @add-node="handleAddNode" />
            </div>
          </div>
        </div>
      </teleport>
    </template>
  </div>
</template>

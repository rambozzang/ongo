<script setup lang="ts">
import { ref, computed } from 'vue'
import TriggerNodeConfig from '@/components/automation/TriggerNodeConfig.vue'
import ConditionNodeConfig from '@/components/automation/ConditionNodeConfig.vue'
import ActionNodeConfig from '@/components/automation/ActionNodeConfig.vue'
import {
  type WorkflowTriggerType,
  type WorkflowCondition,
  type WorkflowActionType,
  type ConditionGroupType,
  type ConditionOperator,
  WORKFLOW_TRIGGER_OPTIONS,
  WORKFLOW_ACTION_OPTIONS,
} from '@/types/automation'



interface ConditionData {
  groupType: ConditionGroupType
  field?: string
  operator?: ConditionOperator
  value?: string
  expression?: string
}

interface ActionData {
  actionType: WorkflowActionType
  config: Record<string, unknown>
  delayMinutes: number
}

const props = defineProps<{
  initialTriggerType?: WorkflowTriggerType
  initialTriggerConfig?: Record<string, unknown>
  initialConditions?: WorkflowCondition[]
  initialActions?: { actionType: WorkflowActionType; config: Record<string, unknown>; delayMinutes: number }[]
}>()

const emit = defineEmits<{
  save: [data: {
    triggerType: WorkflowTriggerType
    triggerConfig: Record<string, unknown>
    conditions: WorkflowCondition[]
    actions: { actionType: WorkflowActionType; config: Record<string, unknown>; delayMinutes: number }[]
  }]
}>()

const triggerType = ref<WorkflowTriggerType>(props.initialTriggerType ?? 'VIDEO_UPLOADED')
const triggerConfig = ref<Record<string, unknown>>(props.initialTriggerConfig ?? {})

const conditions = ref<ConditionData[]>(
  props.initialConditions?.map((c) => ({
    groupType: c.groupType,
    field: c.field,
    operator: c.operator,
    value: c.value,
    expression: c.expression,
  })) ?? [],
)

const actions = ref<ActionData[]>(
  props.initialActions?.map((a) => ({
    actionType: a.actionType,
    config: a.config,
    delayMinutes: a.delayMinutes,
  })) ?? [],
)

const selectedNode = ref<{ type: string; index: number } | null>(null)

function addCondition() {
  conditions.value.push({
    groupType: 'AND',
    field: '',
    operator: 'EQUALS',
    value: '',
  })
}

function removeCondition(index: number) {
  conditions.value.splice(index, 1)
  if (selectedNode.value?.type === 'condition' && selectedNode.value.index === index) {
    selectedNode.value = null
  }
}

function addAction() {
  actions.value.push({
    actionType: 'SEND_NOTIFICATION',
    config: {},
    delayMinutes: 0,
  })
}

function removeAction(index: number) {
  actions.value.splice(index, 1)
  if (selectedNode.value?.type === 'action' && selectedNode.value.index === index) {
    selectedNode.value = null
  }
}

function selectNode(type: string, index: number) {
  selectedNode.value = { type, index }
}

function handleSave() {
  emit('save', {
    triggerType: triggerType.value,
    triggerConfig: triggerConfig.value,
    conditions: conditions.value.map((c) => ({
      groupType: c.groupType,
      field: c.field ?? undefined,
      operator: c.operator ?? undefined,
      value: c.value ?? undefined,
      expression: c.expression ?? undefined,
    })),
    actions: actions.value.map((a) => ({
      actionType: a.actionType,
      config: a.config,
      delayMinutes: a.delayMinutes,
    })),
  })
}

const triggerLabel = computed(() => {
  return WORKFLOW_TRIGGER_OPTIONS.find((o) => o.value === triggerType.value)?.label ?? triggerType.value
})
</script>

<template>
  <div class="flex gap-6">
    <!-- Node Canvas -->
    <div class="flex-1 space-y-3">
      <!-- Trigger Node -->
      <div
        class="p-4 rounded-xl border-2 cursor-pointer transition-all"
        :class="
          selectedNode?.type === 'trigger'
            ? 'border-green-500 bg-green-50 dark:bg-green-900/20 shadow-md'
            : 'border-green-300 dark:border-green-700 bg-green-50/50 dark:bg-green-900/10 hover:border-green-400'
        "
        @click="selectNode('trigger', 0)"
      >
        <div class="flex items-center gap-2">
          <div class="w-8 h-8 rounded-lg bg-green-500 flex items-center justify-center">
            <svg class="w-4 h-4 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
          </div>
          <div>
            <p class="text-xs font-medium text-green-600 dark:text-green-400 uppercase">트리거</p>
            <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ triggerLabel }}</p>
          </div>
        </div>
      </div>

      <!-- Connection line -->
      <div class="flex justify-center">
        <div class="w-px h-6 bg-gray-300 dark:bg-gray-600"></div>
      </div>

      <!-- Condition Nodes -->
      <div v-for="(cond, i) in conditions" :key="'cond-' + i">
        <div
          class="p-4 rounded-xl border-2 cursor-pointer transition-all relative"
          :class="
            selectedNode?.type === 'condition' && selectedNode.index === i
              ? 'border-amber-500 bg-amber-50 dark:bg-amber-900/20 shadow-md'
              : 'border-amber-300 dark:border-amber-700 bg-amber-50/50 dark:bg-amber-900/10 hover:border-amber-400'
          "
          @click="selectNode('condition', i)"
        >
          <button
            class="absolute -top-2 -right-2 w-5 h-5 rounded-full bg-red-500 text-white flex items-center justify-center text-xs hover:bg-red-600"
            @click.stop="removeCondition(i)"
          >
            &times;
          </button>
          <div class="flex items-center gap-2">
            <div class="w-8 h-8 rounded-lg bg-amber-500 flex items-center justify-center transform rotate-45">
              <svg class="w-4 h-4 text-white -rotate-45" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <div>
              <p class="text-xs font-medium text-amber-600 dark:text-amber-400 uppercase">조건 {{ i + 1 }}</p>
              <p class="text-sm text-gray-700 dark:text-gray-300">
                <span v-if="cond.expression">{{ cond.expression }}</span>
                <span v-else-if="cond.field">{{ cond.field }} {{ cond.operator }} {{ cond.value }}</span>
                <span v-else class="text-gray-400">클릭하여 설정</span>
              </p>
            </div>
            <span class="ml-auto px-2 py-0.5 rounded text-xs font-medium" :class="cond.groupType === 'AND' ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300' : 'bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300'">
              {{ cond.groupType }}
            </span>
          </div>
        </div>
        <div class="flex justify-center">
          <div class="w-px h-6 bg-gray-300 dark:bg-gray-600"></div>
        </div>
      </div>

      <!-- Add Condition Button -->
      <button
        class="w-full py-2 border-2 border-dashed border-amber-300 dark:border-amber-700 rounded-xl text-sm text-amber-600 dark:text-amber-400 hover:bg-amber-50 dark:hover:bg-amber-900/10 transition-colors"
        @click="addCondition"
      >
        + 조건 추가
      </button>

      <div class="flex justify-center">
        <div class="w-px h-6 bg-gray-300 dark:bg-gray-600"></div>
      </div>

      <!-- Action Nodes -->
      <div v-for="(action, i) in actions" :key="'act-' + i">
        <div
          class="p-4 rounded-xl border-2 cursor-pointer transition-all relative"
          :class="
            selectedNode?.type === 'action' && selectedNode.index === i
              ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 shadow-md'
              : 'border-blue-300 dark:border-blue-700 bg-blue-50/50 dark:bg-blue-900/10 hover:border-blue-400'
          "
          @click="selectNode('action', i)"
        >
          <button
            class="absolute -top-2 -right-2 w-5 h-5 rounded-full bg-red-500 text-white flex items-center justify-center text-xs hover:bg-red-600"
            @click.stop="removeAction(i)"
          >
            &times;
          </button>
          <div class="flex items-center gap-2">
            <div class="w-8 h-8 rounded-lg bg-blue-500 flex items-center justify-center">
              <svg class="w-4 h-4 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z" />
              </svg>
            </div>
            <div>
              <p class="text-xs font-medium text-blue-600 dark:text-blue-400 uppercase">
                액션 {{ i + 1 }}
                <span v-if="action.delayMinutes > 0" class="text-gray-500 normal-case">({{ action.delayMinutes }}분 지연)</span>
              </p>
              <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
                {{ WORKFLOW_ACTION_OPTIONS.find((o) => o.value === action.actionType)?.label ?? action.actionType }}
              </p>
            </div>
          </div>
        </div>
        <div v-if="i < actions.length - 1" class="flex justify-center">
          <div class="w-px h-6 bg-gray-300 dark:bg-gray-600"></div>
        </div>
      </div>

      <!-- Add Action Button -->
      <button
        class="w-full py-2 border-2 border-dashed border-blue-300 dark:border-blue-700 rounded-xl text-sm text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/10 transition-colors mt-3"
        @click="addAction"
      >
        + 액션 추가
      </button>

      <!-- Save Button -->
      <button
        class="w-full mt-4 py-2.5 bg-primary-600 text-white rounded-lg text-sm font-semibold hover:bg-primary-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        :disabled="actions.length === 0"
        @click="handleSave"
      >
        워크플로우 저장
      </button>
    </div>

    <!-- Side Panel for node config -->
    <div class="w-80 flex-shrink-0">
      <div class="sticky top-4 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4">
        <!-- Trigger config -->
        <TriggerNodeConfig
          v-if="selectedNode?.type === 'trigger'"
          v-model:trigger-type="triggerType"
          v-model:config="triggerConfig"
        />

        <!-- Condition config -->
        <ConditionNodeConfig
          v-else-if="selectedNode?.type === 'condition' && conditions[selectedNode.index]"
          v-model:group-type="conditions[selectedNode.index].groupType"
          v-model:field="conditions[selectedNode.index].field"
          v-model:operator="conditions[selectedNode.index].operator"
          v-model:value="conditions[selectedNode.index].value"
          v-model:expression="conditions[selectedNode.index].expression"
        />

        <!-- Action config -->
        <ActionNodeConfig
          v-else-if="selectedNode?.type === 'action' && actions[selectedNode.index]"
          v-model:action-type="actions[selectedNode.index].actionType"
          v-model:config="actions[selectedNode.index].config"
          v-model:delay-minutes="actions[selectedNode.index].delayMinutes"
        />

        <!-- Default state -->
        <div v-else class="text-center py-8 text-gray-400 dark:text-gray-500">
          <svg class="w-12 h-12 mx-auto mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1">
            <path stroke-linecap="round" stroke-linejoin="round" d="M15.042 21.672L13.684 16.6m0 0l-2.51 2.225.569-9.47 5.227 7.917-3.286-.672zM12 2.25V4.5m5.834.166l-1.591 1.591M20.25 10.5H18M7.757 14.743l-1.59 1.59M6 10.5H3.75m4.007-4.243l-1.59-1.59" />
          </svg>
          <p class="text-sm">노드를 클릭하여 설정하세요</p>
        </div>
      </div>
    </div>
  </div>
</template>

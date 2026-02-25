<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { WfNode } from '@/types/workflowBuilder'
import { NODE_DEFINITIONS } from '@/stores/workflowBuilder'
import { XMarkIcon, TrashIcon } from '@heroicons/vue/24/outline'

const { t } = useI18n()

const props = defineProps<{
  node: WfNode
}>()

const emit = defineEmits<{
  close: []
  updateConfig: [nodeId: string, config: Record<string, unknown>]
  updateLabel: [nodeId: string, label: string]
  delete: [nodeId: string]
}>()

const localLabel = ref(props.node.label)
const localConfig = ref<Record<string, unknown>>({ ...props.node.config })

watch(() => props.node, (n) => {
  localLabel.value = n.label
  localConfig.value = { ...n.config }
}, { deep: true })

const def = computed(() => NODE_DEFINITIONS.find(d => d.kind === props.node.kind))

function onLabelChange() {
  emit('updateLabel', props.node.id, localLabel.value)
}

function onConfigChange(key: string, value: unknown) {
  localConfig.value[key] = value
  emit('updateConfig', props.node.id, { ...localConfig.value })
}

function onArrayConfigChange(key: string, value: string) {
  const arr = value.split(',').map(s => s.trim()).filter(Boolean)
  localConfig.value[key] = arr
  emit('updateConfig', props.node.id, { ...localConfig.value })
}

const typeLabel = computed(() => {
  const map: Record<string, string> = {
    trigger: t('workflowBuilder.triggers'),
    condition: t('workflowBuilder.conditions'),
    action: t('workflowBuilder.actions'),
  }
  return map[props.node.type] ?? props.node.type
})

const typeColor = computed(() => {
  const map: Record<string, string> = {
    trigger: 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300',
    condition: 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300',
    action: 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300',
  }
  return map[props.node.type] ?? ''
})
</script>

<template>
  <div class="w-80 bg-white dark:bg-gray-800 border-l border-gray-200 dark:border-gray-700 flex flex-col h-full overflow-hidden">
    <!-- Header -->
    <div class="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700">
      <div class="flex items-center gap-2">
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('workflowBuilder.nodeConfig') }}
        </h3>
        <span :class="['text-xs px-2 py-0.5 rounded-full font-medium', typeColor]">
          {{ typeLabel }}
        </span>
      </div>
      <button
        class="p-1 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded transition-colors"
        @click="emit('close')"
      >
        <XMarkIcon class="w-5 h-5" />
      </button>
    </div>

    <!-- Config form -->
    <div class="flex-1 overflow-y-auto p-4 space-y-4">
      <!-- Label -->
      <div>
        <label class="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
          {{ $t('workflowBuilder.nodeLabel') }}
        </label>
        <input
          v-model="localLabel"
          @change="onLabelChange"
          class="w-full px-3 py-2 text-sm bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        />
      </div>

      <!-- Description -->
      <div v-if="def" class="text-xs text-gray-500 dark:text-gray-400 bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3">
        {{ def.description }}
      </div>

      <!-- Dynamic config fields based on node config keys -->
      <div v-for="(value, key) in localConfig" :key="key">
        <label class="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1 capitalize">
          {{ String(key) }}
        </label>

        <!-- Array field -->
        <template v-if="Array.isArray(value)">
          <input
            :value="(value as string[]).join(', ')"
            @change="onArrayConfigChange(String(key), ($event.target as HTMLInputElement).value)"
            class="w-full px-3 py-2 text-sm bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            :placeholder="$t('workflowBuilder.commaSeparated')"
          />
          <p class="text-xs text-gray-400 dark:text-gray-500 mt-0.5">
            {{ $t('workflowBuilder.commaSeparatedHint') }}
          </p>
        </template>

        <!-- Boolean field -->
        <template v-else-if="typeof value === 'boolean'">
          <label class="relative inline-flex items-center cursor-pointer">
            <input
              type="checkbox"
              :checked="value"
              @change="onConfigChange(String(key), ($event.target as HTMLInputElement).checked)"
              class="sr-only peer"
            />
            <div class="w-9 h-5 bg-gray-200 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-blue-300 dark:peer-focus:ring-blue-800 rounded-full peer dark:bg-gray-600 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all dark:after:border-gray-600 peer-checked:bg-blue-600"></div>
          </label>
        </template>

        <!-- Number field -->
        <template v-else-if="typeof value === 'number'">
          <input
            type="number"
            :value="value"
            @change="onConfigChange(String(key), Number(($event.target as HTMLInputElement).value))"
            class="w-full px-3 py-2 text-sm bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </template>

        <!-- Select for known enum-like fields -->
        <template v-else-if="String(key) === 'operator'">
          <select
            :value="String(value)"
            @change="onConfigChange(String(key), ($event.target as HTMLSelectElement).value)"
            class="w-full px-3 py-2 text-sm bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          >
            <option value="GTE">&gt;=</option>
            <option value="GT">&gt;</option>
            <option value="LTE">&lt;=</option>
            <option value="LT">&lt;</option>
            <option value="EQUALS">=</option>
          </select>
        </template>

        <template v-else-if="String(key) === 'channel'">
          <select
            :value="String(value)"
            @change="onConfigChange(String(key), ($event.target as HTMLSelectElement).value)"
            class="w-full px-3 py-2 text-sm bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          >
            <option value="push">{{ $t('workflowBuilder.pushNotification') }}</option>
            <option value="email">{{ $t('workflowBuilder.email') }}</option>
            <option value="both">{{ $t('workflowBuilder.both') }}</option>
          </select>
        </template>

        <template v-else-if="String(key) === 'matchMode'">
          <select
            :value="String(value)"
            @change="onConfigChange(String(key), ($event.target as HTMLSelectElement).value)"
            class="w-full px-3 py-2 text-sm bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          >
            <option value="any">{{ $t('workflowBuilder.matchAny') }}</option>
            <option value="all">{{ $t('workflowBuilder.matchAll') }}</option>
          </select>
        </template>

        <!-- Default text field -->
        <template v-else>
          <input
            :value="String(value ?? '')"
            @change="onConfigChange(String(key), ($event.target as HTMLInputElement).value)"
            class="w-full px-3 py-2 text-sm bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          />
        </template>
      </div>
    </div>

    <!-- Footer: delete -->
    <div class="p-4 border-t border-gray-200 dark:border-gray-700">
      <button
        class="w-full flex items-center justify-center gap-2 px-4 py-2 text-sm font-medium text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/20 rounded-lg hover:bg-red-100 dark:hover:bg-red-900/30 transition-colors"
        @click="emit('delete', node.id)"
      >
        <TrashIcon class="w-4 h-4" />
        {{ $t('workflowBuilder.deleteNode') }}
      </button>
    </div>
  </div>
</template>

<template>
  <div class="space-y-4">
    <div class="flex items-center gap-2 mb-3">
      <svg class="w-5 h-5 text-amber-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
      </svg>
      <h3 class="text-sm font-semibold text-gray-900 dark:text-white">스마트 트리거 템플릿</h3>
    </div>

    <div class="grid gap-3 sm:grid-cols-3">
      <div
        v-for="template in templates"
        :key="template.triggerType"
        class="relative cursor-pointer rounded-xl border-2 p-4 transition-all hover:shadow-md"
        :class="
          selectedType === template.triggerType
            ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 dark:border-blue-400'
            : 'border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 hover:border-gray-300 dark:hover:border-gray-600'
        "
        @click="selectTemplate(template)"
      >
        <!-- Icon -->
        <div
          class="flex h-10 w-10 items-center justify-center rounded-lg mb-3"
          :class="getIconBgClass(template.triggerType)"
        >
          <component :is="getIcon(template.triggerType)" class="w-5 h-5" :class="getIconClass(template.triggerType)" />
        </div>

        <h4 class="text-sm font-semibold text-gray-900 dark:text-white mb-1">
          {{ template.name }}
        </h4>
        <p class="text-xs text-gray-500 dark:text-gray-400 leading-relaxed">
          {{ template.description }}
        </p>

        <!-- Config slider -->
        <div v-if="selectedType === template.triggerType" class="mt-4 space-y-3">
          <!-- Views Milestone -->
          <template v-if="template.triggerType === 'VIEWS_MILESTONE'">
            <label class="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">마일스톤 목표</label>
            <div class="flex flex-wrap gap-1.5">
              <button
                v-for="milestone in availableMilestones"
                :key="milestone"
                class="rounded-full px-2.5 py-1 text-xs font-medium transition-colors"
                :class="
                  selectedMilestones.includes(milestone)
                    ? 'bg-blue-600 text-white'
                    : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600'
                "
                @click.stop="toggleMilestone(milestone)"
              >
                {{ formatMilestone(milestone) }}
              </button>
            </div>
          </template>

          <!-- Viral Detection -->
          <template v-if="template.triggerType === 'VIRAL_DETECTED'">
            <label class="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
              감지 배수: <strong>{{ viralMultiplier }}x</strong>
            </label>
            <input
              v-model.number="viralMultiplier"
              type="range"
              min="2"
              max="10"
              step="0.5"
              class="w-full accent-blue-600"
              @click.stop
            />
            <div class="flex justify-between text-[10px] text-gray-400">
              <span>2x</span>
              <span>10x</span>
            </div>
          </template>

          <!-- Engagement Drop -->
          <template v-if="template.triggerType === 'ENGAGEMENT_DROP'">
            <label class="block text-xs font-medium text-gray-700 dark:text-gray-300 mb-1">
              하락 기준: <strong>{{ engagementDropPercent }}%</strong>
            </label>
            <input
              v-model.number="engagementDropPercent"
              type="range"
              min="20"
              max="80"
              step="5"
              class="w-full accent-blue-600"
              @click.stop
            />
            <div class="flex justify-between text-[10px] text-gray-400">
              <span>20%</span>
              <span>80%</span>
            </div>
          </template>
        </div>

        <!-- Selected indicator -->
        <div
          v-if="selectedType === template.triggerType"
          class="absolute top-2 right-2"
        >
          <div class="flex h-5 w-5 items-center justify-center rounded-full bg-blue-600">
            <svg class="h-3 w-3 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7" />
            </svg>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import {
  ChartBarIcon,
  FireIcon,
  ArrowTrendingDownIcon,
} from '@heroicons/vue/24/outline'

export interface SmartTriggerTemplate {
  triggerType: string
  name: string
  description: string
  defaultConfig: Record<string, unknown>
  configSchema: Record<string, unknown>
}

export interface Props {
  templates: SmartTriggerTemplate[]
}

defineProps<Props>()

const emit = defineEmits<{
  select: [payload: { triggerType: string; config: Record<string, unknown> }]
}>()

const selectedType = ref<string | null>(null)

// Config state
const availableMilestones = [1000, 5000, 10000, 50000, 100000]
const selectedMilestones = ref<number[]>([1000, 5000, 10000, 100000])
const viralMultiplier = ref(3)
const engagementDropPercent = ref(50)

function selectTemplate(template: SmartTriggerTemplate) {
  if (selectedType.value === template.triggerType) {
    selectedType.value = null
    return
  }
  selectedType.value = template.triggerType
  emitConfig()
}

function toggleMilestone(value: number) {
  const idx = selectedMilestones.value.indexOf(value)
  if (idx >= 0) {
    selectedMilestones.value.splice(idx, 1)
  } else {
    selectedMilestones.value.push(value)
  }
  emitConfig()
}

function emitConfig() {
  if (!selectedType.value) return

  let config: Record<string, unknown> = {}
  switch (selectedType.value) {
    case 'VIEWS_MILESTONE':
      config = { milestones: [...selectedMilestones.value] }
      break
    case 'VIRAL_DETECTED':
      config = { multiplier: viralMultiplier.value }
      break
    case 'ENGAGEMENT_DROP':
      config = { dropPercent: engagementDropPercent.value }
      break
  }

  emit('select', { triggerType: selectedType.value, config })
}

watch([viralMultiplier, engagementDropPercent], () => {
  emitConfig()
})

function formatMilestone(value: number): string {
  if (value >= 100000) return `${value / 10000}만`
  if (value >= 10000) return `${value / 10000}만`
  if (value >= 1000) return `${value / 1000}천`
  return String(value)
}

function getIcon(type: string) {
  switch (type) {
    case 'VIEWS_MILESTONE': return ChartBarIcon
    case 'VIRAL_DETECTED': return FireIcon
    case 'ENGAGEMENT_DROP': return ArrowTrendingDownIcon
    default: return ChartBarIcon
  }
}

function getIconBgClass(type: string): string {
  switch (type) {
    case 'VIEWS_MILESTONE': return 'bg-blue-100 dark:bg-blue-900/30'
    case 'VIRAL_DETECTED': return 'bg-red-100 dark:bg-red-900/30'
    case 'ENGAGEMENT_DROP': return 'bg-amber-100 dark:bg-amber-900/30'
    default: return 'bg-gray-100 dark:bg-gray-800'
  }
}

function getIconClass(type: string): string {
  switch (type) {
    case 'VIEWS_MILESTONE': return 'text-blue-600 dark:text-blue-400'
    case 'VIRAL_DETECTED': return 'text-red-600 dark:text-red-400'
    case 'ENGAGEMENT_DROP': return 'text-amber-600 dark:text-amber-400'
    default: return 'text-gray-600 dark:text-gray-400'
  }
}
</script>

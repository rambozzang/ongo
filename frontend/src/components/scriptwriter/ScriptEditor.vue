<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  ChevronUpIcon,
  ChevronDownIcon,
  ClockIcon,
} from '@heroicons/vue/24/outline'
import type { Script, ScriptSection } from '@/types/scriptWriter'

interface Props {
  script: Script
}

const props = defineProps<Props>()

const editableSections = ref<ScriptSection[]>(
  [...props.script.content].sort((a, b) => a.orderNumber - b.orderNumber)
)

const totalDuration = computed(() =>
  editableSections.value.reduce((sum, s) => sum + s.duration, 0)
)

const sectionTypeConfig: Record<ScriptSection['type'], { label: string; color: string }> = {
  HOOK: {
    label: '훅',
    color: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
  },
  INTRO: {
    label: '인트로',
    color: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
  },
  BODY: {
    label: '본문',
    color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
  },
  CTA: {
    label: 'CTA',
    color: 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300',
  },
  OUTRO: {
    label: '아웃트로',
    color: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300',
  },
  TRANSITION: {
    label: '전환',
    color: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300',
  },
}

function moveSection(index: number, direction: 'up' | 'down') {
  const target = direction === 'up' ? index - 1 : index + 1
  if (target < 0 || target >= editableSections.value.length) return

  const sections = [...editableSections.value]
  const temp = sections[index]
  sections[index] = sections[target]
  sections[target] = temp

  // 순서 번호 재정렬
  sections.forEach((s, i) => {
    s.orderNumber = i + 1
  })
  editableSections.value = sections
}

function formatDuration(seconds: number): string {
  const min = Math.floor(seconds / 60)
  const sec = seconds % 60
  if (min === 0) return `${sec}초`
  return sec > 0 ? `${min}분 ${sec}초` : `${min}분`
}
</script>

<template>
  <div class="space-y-4">
    <!-- Script Header Info -->
    <div class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-6">
      <div class="flex flex-col tablet:flex-row tablet:items-center tablet:justify-between gap-3">
        <div>
          <h2 class="text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ script.title }}
          </h2>
          <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
            {{ script.topic }}
          </p>
        </div>
        <div class="flex items-center gap-3 text-sm text-gray-600 dark:text-gray-400">
          <div class="flex items-center gap-1.5">
            <ClockIcon class="w-4 h-4" />
            <span>총 {{ formatDuration(totalDuration) }}</span>
          </div>
          <span class="text-gray-300 dark:text-gray-600">|</span>
          <span>{{ script.estimatedWordCount.toLocaleString() }}자</span>
          <span class="text-gray-300 dark:text-gray-600">|</span>
          <span>{{ editableSections.length }}개 섹션</span>
        </div>
      </div>
    </div>

    <!-- Sections -->
    <div
      v-for="(section, index) in editableSections"
      :key="section.id"
      class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-6 group"
    >
      <!-- Section Header -->
      <div class="flex items-center justify-between mb-3">
        <div class="flex items-center gap-3">
          <!-- Reorder Buttons -->
          <div class="flex flex-col gap-0.5">
            <button
              :disabled="index === 0"
              @click="moveSection(index, 'up')"
              class="p-0.5 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <ChevronUpIcon class="w-4 h-4" />
            </button>
            <button
              :disabled="index === editableSections.length - 1"
              @click="moveSection(index, 'down')"
              class="p-0.5 rounded text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <ChevronDownIcon class="w-4 h-4" />
            </button>
          </div>

          <!-- Section Type Badge -->
          <span
            :class="['inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium', sectionTypeConfig[section.type]?.color ?? 'bg-gray-100 text-gray-800']"
          >
            {{ sectionTypeConfig[section.type]?.label ?? section.type }}
          </span>

          <!-- Section Title -->
          <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ section.title }}
          </h3>
        </div>

        <!-- Duration -->
        <div class="flex items-center gap-1.5 text-sm text-gray-500 dark:text-gray-400">
          <ClockIcon class="w-4 h-4" />
          <span>{{ formatDuration(section.duration) }}</span>
        </div>
      </div>

      <!-- Editable Content -->
      <textarea
        v-model="section.content"
        rows="4"
        class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-4 py-3 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400 resize-y leading-relaxed"
        :placeholder="`${sectionTypeConfig[section.type]?.label ?? section.type} 내용을 입력하세요...`"
      />

      <!-- Section Notes -->
      <div v-if="section.notes" class="mt-2 text-xs text-gray-400 dark:text-gray-500 italic">
        {{ section.notes }}
      </div>
    </div>

    <!-- Empty State -->
    <div
      v-if="editableSections.length === 0"
      class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-12 text-center"
    >
      <p class="text-gray-500 dark:text-gray-400">
        아직 섹션이 없습니다. AI로 스크립트를 생성해 보세요.
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  DocumentTextIcon,
  CheckCircleIcon,
  DocumentDuplicateIcon,
  LightBulbIcon,
} from '@heroicons/vue/24/outline'
import { useVideoScriptAssistantStore } from '@/stores/videoScriptAssistant'
import ScriptCard from '@/components/videoscriptassistant/ScriptCard.vue'
import SuggestionItem from '@/components/videoscriptassistant/SuggestionItem.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const store = useVideoScriptAssistantStore()

const selectedScriptId = ref<number | null>(null)

const selectedScript = computed(() =>
  store.scripts.find((s) => s.id === selectedScriptId.value) ?? null,
)

function handleSelectScript(id: number) {
  selectedScriptId.value = id
  store.fetchSuggestions(id)
}

async function handleApplySuggestion(suggestionId: number) {
  if (!selectedScriptId.value) return
  const found = store.suggestions.find((s) => s.id === suggestionId)
  if (found) {
    found.isApplied = true
  }
}

onMounted(() => {
  store.fetchScripts()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          AI 비디오 스크립트 어시스턴트
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          AI가 비디오 스크립트를 분석하고 개선 제안을 제공합니다
        </p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="store.loading" :full-page="true" size="lg" />

    <template v-else>
      <!-- Summary Cards -->
      <div class="mb-6 grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-4">
        <!-- Total Scripts -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100 dark:bg-blue-900/30">
              <DocumentTextIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">총 스크립트</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ store.summary?.totalScripts ?? 0 }}
              </p>
            </div>
          </div>
        </div>

        <!-- Completed -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-green-100 dark:bg-green-900/30">
              <CheckCircleIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">완료</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ store.summary?.completedScripts ?? 0 }}
              </p>
            </div>
          </div>
        </div>

        <!-- Average Word Count -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-indigo-100 dark:bg-indigo-900/30">
              <DocumentDuplicateIcon class="h-5 w-5 text-indigo-600 dark:text-indigo-400" />
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">평균 단어수</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ store.summary?.avgWordCount?.toLocaleString() ?? 0 }}
              </p>
            </div>
          </div>
        </div>

        <!-- Suggestions Applied -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-yellow-100 dark:bg-yellow-900/30">
              <LightBulbIcon class="h-5 w-5 text-yellow-600 dark:text-yellow-400" />
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">적용된 제안</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ store.summary?.suggestionsApplied ?? 0 }}
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Script Grid -->
      <section class="mb-8">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          스크립트 목록
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">
            ({{ store.scripts.length }})
          </span>
        </h2>

        <div v-if="store.scripts.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
          <ScriptCard
            v-for="s in store.scripts"
            :key="s.id"
            :script="s"
            :selected="s.id === selectedScriptId"
            @select="handleSelectScript"
          />
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <DocumentTextIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            스크립트가 없습니다
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            새로운 비디오 스크립트를 생성해보세요
          </p>
        </div>
      </section>

      <!-- Suggestions for selected script -->
      <section v-if="selectedScript">
        <div class="mb-4 flex items-center justify-between">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            "{{ selectedScript.title }}" 개선 제안
          </h2>
          <span class="text-sm text-gray-500 dark:text-gray-400">
            {{ store.suggestions.length }}개의 제안
          </span>
        </div>

        <div v-if="store.suggestions.length > 0" class="space-y-4">
          <SuggestionItem
            v-for="suggestion in store.suggestions"
            :key="suggestion.id"
            :suggestion="suggestion"
            @apply="handleApplySuggestion"
          />
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-12 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <LightBulbIcon class="mx-auto mb-3 h-10 w-10 text-gray-400 dark:text-gray-600" />
          <p class="text-sm text-gray-500 dark:text-gray-400">
            이 스크립트에 대한 제안이 아직 없습니다
          </p>
        </div>
      </section>
    </template>
  </div>
</template>

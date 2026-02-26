<script setup lang="ts">
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  LanguageIcon,
  CheckCircleIcon,
  SparklesIcon,
  GlobeAltIcon,
} from '@heroicons/vue/24/outline'
import { useContentTranslatorStore } from '@/stores/contentTranslator'
import TranslationJobCard from '@/components/contenttranslator/TranslationJobCard.vue'
import GlossaryRow from '@/components/contenttranslator/GlossaryRow.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const store = useContentTranslatorStore()
const { jobs, glossary, summary, loading } = storeToRefs(store)

onMounted(() => {
  store.fetchJobs()
  store.fetchGlossary()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          콘텐츠 번역기
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          AI 기반 다국어 콘텐츠 번역 및 용어집 관리
        </p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="loading" :full-page="true" size="lg" />

    <div v-else class="space-y-8">
      <!-- Summary Cards -->
      <div v-if="summary" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <!-- Total Translations -->
        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100 dark:bg-blue-900/30">
              <LanguageIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">총 번역</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.totalTranslations.toLocaleString('ko-KR') }}
              </p>
            </div>
          </div>
        </div>

        <!-- Completed -->
        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-green-100 dark:bg-green-900/30">
              <CheckCircleIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">완료</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.completedTranslations.toLocaleString('ko-KR') }}
              </p>
            </div>
          </div>
        </div>

        <!-- Average Quality -->
        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-100 dark:bg-purple-900/30">
              <SparklesIcon class="h-5 w-5 text-purple-600 dark:text-purple-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">평균 품질</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.avgQuality }}점
              </p>
            </div>
          </div>
        </div>

        <!-- Top Language Pair -->
        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-orange-100 dark:bg-orange-900/30">
              <GlobeAltIcon class="h-5 w-5 text-orange-600 dark:text-orange-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">주요 언어 쌍</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.topLanguagePair }}
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Translation Jobs Grid -->
      <section>
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          번역 작업
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">({{ jobs.length }})</span>
        </h2>

        <div v-if="jobs.length > 0" class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          <TranslationJobCard
            v-for="job in jobs"
            :key="job.id"
            :job="job"
          />
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <LanguageIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            번역 작업이 없습니다
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            새로운 번역 작업을 시작해보세요
          </p>
        </div>
      </section>

      <!-- Glossary Table -->
      <section>
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          용어집
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">({{ glossary.length }})</span>
        </h2>

        <div v-if="glossary.length > 0" class="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="overflow-x-auto">
            <table class="w-full">
              <thead>
                <tr class="border-b border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-800">
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    소스 단어
                  </th>
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    타겟 단어
                  </th>
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    언어 쌍
                  </th>
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    컨텍스트
                  </th>
                </tr>
              </thead>
              <tbody>
                <GlossaryRow
                  v-for="item in glossary"
                  :key="item.id"
                  :glossary="item"
                />
              </tbody>
            </table>
          </div>
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-12 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <p class="text-sm text-gray-500 dark:text-gray-400">
            등록된 용어가 없습니다
          </p>
        </div>
      </section>
    </div>
  </div>
</template>

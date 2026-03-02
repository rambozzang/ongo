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
import PageHeader from '@/components/common/PageHeader.vue'
import { useLocale } from '@/composables/useLocale'

useLocale()

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
    <PageHeader :title="$t('contentTranslator.title')" :description="$t('contentTranslator.description')" />

    <!-- Loading -->
    <LoadingSpinner v-if="loading" :full-page="true" size="lg" />

    <div v-else class="space-y-8">
      <!-- Summary Cards -->
      <div v-if="summary" class="grid grid-cols-1 gap-4 mobile:grid-cols-2 desktop:grid-cols-4">
        <!-- Total Translations -->
        <div class="card">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100 dark:bg-blue-900/30">
              <LanguageIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('contentTranslator.totalTranslations') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.totalTranslations.toLocaleString('ko-KR') }}
              </p>
            </div>
          </div>
        </div>

        <!-- Completed -->
        <div class="card">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-green-100 dark:bg-green-900/30">
              <CheckCircleIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('contentTranslator.completed') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.completedTranslations.toLocaleString('ko-KR') }}
              </p>
            </div>
          </div>
        </div>

        <!-- Average Quality -->
        <div class="card">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-100 dark:bg-purple-900/30">
              <SparklesIcon class="h-5 w-5 text-purple-600 dark:text-purple-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('contentTranslator.avgQuality') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.avgQuality }}{{ $t('contentTranslator.points') }}
              </p>
            </div>
          </div>
        </div>

        <!-- Top Language Pair -->
        <div class="card">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-orange-100 dark:bg-orange-900/30">
              <GlobeAltIcon class="h-5 w-5 text-orange-600 dark:text-orange-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('contentTranslator.topLanguagePair') }}</p>
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
          {{ $t('contentTranslator.translationJobs') }}
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">({{ jobs.length }})</span>
        </h2>

        <div v-if="jobs.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
          <TranslationJobCard
            v-for="job in jobs"
            :key="job.id"
            :job="job"
          />
        </div>

        <div
          v-else
          class="card py-16 text-center"
        >
          <LanguageIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('contentTranslator.noJobs') }}
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {{ $t('contentTranslator.noJobsHint') }}
          </p>
        </div>
      </section>

      <!-- Glossary Table -->
      <section>
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('contentTranslator.glossary') }}
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">({{ glossary.length }})</span>
        </h2>

        <div v-if="glossary.length > 0" class="card overflow-hidden">
          <div class="overflow-x-auto">
            <table class="w-full">
              <thead>
                <tr class="border-b border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-800">
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    {{ $t('contentTranslator.sourceWord') }}
                  </th>
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    {{ $t('contentTranslator.targetWord') }}
                  </th>
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    {{ $t('contentTranslator.languagePair') }}
                  </th>
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    {{ $t('contentTranslator.context') }}
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
          class="card py-12 text-center"
        >
          <p class="text-sm text-gray-500 dark:text-gray-400">
            {{ $t('contentTranslator.noGlossary') }}
          </p>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { DocumentArrowDownIcon, LinkIcon, ClipboardDocumentIcon } from '@heroicons/vue/24/outline'
import type { Portfolio, PortfolioTheme } from '@/types/portfolio'

const props = defineProps<{
  portfolio: Portfolio
}>()

const emit = defineEmits<{
  (e: 'updateTheme', theme: PortfolioTheme): void
  (e: 'updateSlug', slug: string): void
  (e: 'togglePublic'): void
  (e: 'exportPdf'): void
  (e: 'copyLink'): void
}>()

const { t } = useI18n()
const exporting = ref(false)
const slugInput = ref(props.portfolio.slug)

const themes: { value: PortfolioTheme; label: string; description: string }[] = [
  { value: 'minimal', label: 'Minimal', description: 'portfolio.themeMinimalDesc' },
  { value: 'creative', label: 'Creative', description: 'portfolio.themeCreativeDesc' },
  { value: 'professional', label: 'Professional', description: 'portfolio.themeProfessionalDesc' },
  { value: 'bold', label: 'Bold', description: 'portfolio.themeBoldDesc' },
]

async function handleExport() {
  exporting.value = true
  try {
    emit('exportPdf')
  } finally {
    setTimeout(() => { exporting.value = false }, 1500)
  }
}

function handleSlugChange() {
  if (slugInput.value !== props.portfolio.slug) {
    emit('updateSlug', slugInput.value)
  }
}
</script>

<template>
  <div class="space-y-6">
    <!-- Theme Selection -->
    <div class="card p-6">
      <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
        {{ t('portfolio.themeSelection') }}
      </h2>
      <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <button
          v-for="theme in themes"
          :key="theme.value"
          :class="[
            'rounded-lg border-2 p-3 text-center transition-all',
            portfolio.theme === theme.value
              ? 'border-primary-500 bg-primary-50 dark:border-primary-400 dark:bg-primary-900/20'
              : 'border-gray-200 hover:border-gray-300 dark:border-gray-700 dark:hover:border-gray-600',
          ]"
          @click="emit('updateTheme', theme.value)"
        >
          <div class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ theme.label }}</div>
          <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">{{ t(theme.description) }}</div>
        </button>
      </div>
    </div>

    <!-- Public URL & Sharing -->
    <div class="card p-6">
      <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
        {{ t('portfolio.sharing') }}
      </h2>

      <div class="space-y-4">
        <!-- Public toggle -->
        <div class="flex items-center justify-between">
          <div>
            <div class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('portfolio.publicAccess') }}</div>
            <div class="text-xs text-gray-500 dark:text-gray-400">{{ t('portfolio.publicAccessDesc') }}</div>
          </div>
          <button
            :class="[
              'relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors',
              portfolio.isPublic ? 'bg-primary-600' : 'bg-gray-200 dark:bg-gray-600',
            ]"
            @click="emit('togglePublic')"
          >
            <span
              :class="[
                'pointer-events-none inline-block h-5 w-5 rounded-full bg-white shadow ring-0 transition-transform',
                portfolio.isPublic ? 'translate-x-5' : 'translate-x-0',
              ]"
            />
          </button>
        </div>

        <!-- Slug -->
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('portfolio.customUrl') }}</label>
          <div class="flex items-center gap-2">
            <span class="text-sm text-gray-500 dark:text-gray-400">ongo.kr/p/</span>
            <input
              v-model="slugInput"
              type="text"
              class="flex-1 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
              @blur="handleSlugChange"
            />
          </div>
        </div>

        <!-- Copy link -->
        <div v-if="portfolio.isPublic" class="flex items-center gap-2 rounded-lg border border-gray-300 bg-gray-50 px-3 py-2 dark:border-gray-600 dark:bg-gray-700">
          <LinkIcon class="h-4 w-4 text-gray-400" />
          <span class="flex-1 truncate text-sm text-gray-600 dark:text-gray-400">{{ portfolio.publicUrl }}</span>
          <button
            class="text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
            @click="emit('copyLink')"
          >
            <ClipboardDocumentIcon class="h-5 w-5" />
          </button>
        </div>
      </div>
    </div>

    <!-- PDF Export -->
    <div class="card p-6">
      <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
        {{ t('portfolio.mediaKitExport') }}
      </h2>
      <p class="mb-4 text-sm text-gray-500 dark:text-gray-400">
        {{ t('portfolio.mediaKitExportDesc') }}
      </p>
      <button
        class="btn-primary inline-flex items-center gap-2"
        :disabled="exporting"
        @click="handleExport"
      >
        <DocumentArrowDownIcon class="h-5 w-5" />
        {{ exporting ? t('portfolio.exporting') : t('portfolio.exportPdf') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { TrashIcon } from '@heroicons/vue/24/outline'
import type { BrandCollaboration, PlatformType } from '@/types/portfolio'

defineProps<{
  collaborations: BrandCollaboration[]
}>()

const emit = defineEmits<{
  (e: 'remove', collabId: number): void
}>()

const { t } = useI18n()

const platformLabel: Record<PlatformType, string> = {
  youtube: 'YouTube',
  tiktok: 'TikTok',
  instagram: 'Instagram',
  naver: 'Naver',
}
</script>

<template>
  <div class="card p-6">
    <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
      {{ t('portfolio.brandCollaborations') }}
    </h2>

    <div v-if="collaborations.length === 0" class="py-8 text-center text-gray-400 dark:text-gray-500">
      {{ t('portfolio.noCollaborations') }}
    </div>

    <div v-else class="relative">
      <!-- Timeline line -->
      <div class="absolute left-4 top-0 bottom-0 w-0.5 bg-gray-200 dark:bg-gray-700" />

      <div class="space-y-6">
        <div
          v-for="collab in collaborations"
          :key="collab.id"
          class="relative pl-10"
        >
          <!-- Timeline dot -->
          <div class="absolute left-2.5 top-1.5 h-3 w-3 rounded-full border-2 border-primary-500 bg-white dark:bg-gray-900" />

          <div class="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
            <div class="flex items-start justify-between">
              <div>
                <div class="flex items-center gap-2">
                  <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100">
                    {{ collab.brandName }}
                  </h3>
                  <span class="rounded bg-gray-100 px-1.5 py-0.5 text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-400">
                    {{ platformLabel[collab.platform] }}
                  </span>
                </div>
                <p class="mt-1 text-sm text-gray-600 dark:text-gray-300">
                  {{ collab.campaignTitle }}
                </p>
              </div>
              <button
                class="shrink-0 text-gray-400 hover:text-red-500 dark:text-gray-500 dark:hover:text-red-400"
                @click="emit('remove', collab.id)"
              >
                <TrashIcon class="h-4 w-4" />
              </button>
            </div>

            <div class="mt-3 grid grid-cols-1 gap-2 text-xs text-gray-500 dark:text-gray-400 sm:grid-cols-3">
              <div>
                <span class="font-medium text-gray-600 dark:text-gray-300">{{ t('portfolio.deliverables') }}:</span>
                {{ collab.deliverables }}
              </div>
              <div>
                <span class="font-medium text-gray-600 dark:text-gray-300">{{ t('portfolio.completedAt') }}:</span>
                {{ collab.completedAt }}
              </div>
              <div>
                <span class="font-medium text-gray-600 dark:text-gray-300">{{ t('portfolio.result') }}:</span>
                {{ collab.resultSummary }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

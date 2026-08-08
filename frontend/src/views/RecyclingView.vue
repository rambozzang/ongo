<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowPathIcon, LightBulbIcon } from '@heroicons/vue/24/outline'
import { useRecyclingStore } from '@/stores/recycling'
import { useNotificationStore } from '@/stores/notification'
import { useRouter } from 'vue-router'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import KpiCard from '@/components/redesign/KpiCard.vue'

const { t } = useI18n()
const router = useRouter()
const recyclingStore = useRecyclingStore()
const notificationStore = useNotificationStore()

const pendingSuggestions = computed(() =>
  recyclingStore.suggestions.filter((suggestion) => suggestion.status === 'PENDING'),
)

const suggestionTypeLabels = computed<Record<string, string>>(() => ({
  REPOST: t('recycling.typeRepost'),
  CLIP: t('recycling.typeClip'),
  REMIX: t('recycling.typeRemix'),
  UPDATE_METADATA: t('recycling.typeUpdateMetadata'),
}))

async function generateSuggestions() {
  await recyclingStore.generateSuggestions()
  if (recyclingStore.error) notificationStore.error(recyclingStore.error)
}

async function dismissSuggestion(id: number) {
  const result = await recyclingStore.dismissSuggestion(id)
  if (!result && recyclingStore.error) notificationStore.error(recyclingStore.error)
}

function openVideo(videoId: number) {
  void router.push(`/videos/${videoId}`)
}

onMounted(() => {
  void recyclingStore.fetchSuggestions()
})
</script>

<template>
  <div class="relative min-h-full space-y-5 py-5 text-content">
    <PageHeader :title="$t('recycling.title')" :description="$t('recycling.description')">
      <template #actions>
        <button
          type="button"
          class="btn-primary inline-flex items-center gap-2"
          :disabled="recyclingStore.suggestionsLoading"
          @click="generateSuggestions"
        >
          <LightBulbIcon class="h-5 w-5" aria-hidden="true" />
          {{ recyclingStore.suggestionsLoading ? $t('recycling.analyzing') : $t('recycling.generateSuggestions') }}
        </button>
      </template>
    </PageHeader>

    <PageGuide :title="$t('recycling.pageGuideTitle')" :items="($tm('recycling.pageGuide') as string[])" />

    <div class="grid gap-2.5 tablet:grid-cols-3">
      <KpiCard :label="$t('recycling.pendingSuggestions')" :value="`${pendingSuggestions.length}`" />
      <KpiCard :label="$t('recycling.serverBacked')" :value="$t('recycling.serverReady')" :delta-variant="'success'" />
      <KpiCard :label="$t('recycling.workflowHint')" :value="$t('recycling.openVideoToPublish')" />
    </div>

    <div
      v-if="recyclingStore.error"
      class="rounded-lg border border-error-subtle bg-error-subtle px-4 py-3 text-body text-error-strong"
      role="alert"
    >
      {{ recyclingStore.error }}
    </div>

    <div v-if="recyclingStore.suggestionsLoading" class="rounded-lg border border-line bg-surface-card p-8 text-center text-body-sm text-content-tertiary">
      {{ $t('recycling.analyzing') }}
    </div>

    <div v-else-if="pendingSuggestions.length === 0" class="rounded-lg border border-line bg-surface-card p-12 text-center">
      <ArrowPathIcon class="mx-auto mb-4 h-12 w-12 text-content-quaternary" aria-hidden="true" />
      <h2 class="text-title font-semibold text-content">{{ $t('recycling.emptySuggestionsTitle') }}</h2>
      <p class="mx-auto mt-2 max-w-lg text-body-sm text-content-secondary">{{ $t('recycling.emptySuggestionsDescription') }}</p>
      <button type="button" class="btn-primary mt-5" @click="generateSuggestions">
        {{ $t('recycling.generateSuggestionsBtn') }}
      </button>
    </div>

    <div v-else class="space-y-3">
      <article
        v-for="suggestion in pendingSuggestions"
        :key="suggestion.id"
        class="rounded-lg border border-line bg-surface-card p-4"
      >
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div class="min-w-0 flex-1">
            <div class="mb-2 flex flex-wrap items-center gap-2">
              <span class="rounded-full bg-accent-dim px-2 py-1 text-body-xs font-semibold text-accent">
                {{ suggestionTypeLabels[suggestion.suggestionType] || suggestion.suggestionType }}
              </span>
              <span class="text-body-xs text-content-tertiary">
                {{ $t('recycling.priority') }}: {{ suggestion.priorityScore }}
              </span>
            </div>
            <p class="text-body text-content">{{ suggestion.reason }}</p>
            <div class="mt-2 flex flex-wrap gap-1.5">
              <span
                v-for="platform in suggestion.suggestedPlatforms"
                :key="platform"
                class="rounded bg-surface-input px-2 py-0.5 text-body-xs text-content-secondary"
              >
                {{ platform }}
              </span>
            </div>
          </div>
          <div class="flex shrink-0 gap-2">
            <button type="button" class="btn-secondary !text-body-xs" @click="openVideo(suggestion.videoId)">
              {{ $t('recycling.openVideoToPublish') }}
            </button>
            <button type="button" class="btn-secondary !text-body-xs" @click="dismissSuggestion(suggestion.id)">
              {{ $t('recycling.dismiss') }}
            </button>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

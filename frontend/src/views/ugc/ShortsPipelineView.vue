<template>
  <div>
    <PageHeader :title="$t('ugc.shorts.runs.title')" :description="$t('ugc.shorts.runs.description')">
      <template #actions>
        <router-link to="/ugc/shorts/prompts" class="btn-secondary inline-flex items-center gap-2">
          <ChatBubbleLeftRightIcon class="h-5 w-5" />
          {{ $t('ugc.shorts.runs.toPrompts') }}
        </router-link>
        <router-link to="/ugc/shorts/templates" class="btn-secondary inline-flex items-center gap-2">
          <Square2StackIcon class="h-5 w-5" />
          {{ $t('ugc.shorts.runs.toTemplates') }}
        </router-link>
        <button class="btn-primary inline-flex items-center gap-2" @click="openCreate">
          <PlusIcon class="h-5 w-5" />
          {{ $t('ugc.shorts.runs.newRun') }}
        </button>
      </template>
    </PageHeader>

    <LoadingSpinner v-if="store.runsLoading" full-page />

    <EmptyState
      v-else-if="store.runs.length === 0"
      :title="$t('ugc.shorts.runs.empty')"
      :description="$t('ugc.shorts.runs.emptyDescription')"
      :action-label="$t('ugc.shorts.runs.newRun')"
      :icon="FilmIcon"
      @action="openCreate"
    />

    <!-- 실행 카드 목록 -->
    <div v-else class="space-y-3">
      <button
        v-for="run in store.runs"
        :key="run.id"
        class="card flex w-full items-center justify-between gap-4 text-left transition-colors hover:border-primary-300 dark:hover:border-primary-700"
        @click="goDetail(run.id)"
      >
        <div class="min-w-0 flex-1">
          <div class="flex flex-wrap items-center gap-2">
            <span class="font-semibold text-gray-900 dark:text-gray-100">
              {{ run.sourceVideoTitle || `#${run.sourceVideoId}` }}
            </span>
            <span :class="['rounded-full px-2 py-0.5 text-caption', statusBadgeClass(run.status)]">
              {{ $t(`ugc.shorts.runs.status.${run.status}`) }}
            </span>
          </div>
          <div class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-body-xs text-gray-400">
            <span>{{ $t('ugc.shorts.runs.clipCount', { count: run.clipCount }) }}</span>
            <span v-if="run.currentStage">
              {{ $t('ugc.shorts.runs.currentStage') }}: {{ $t(`ugc.shorts.runs.stageNames.${run.currentStage}`) }}
            </span>
            <span v-if="run.createdAt">{{ formatDate(run.createdAt) }}</span>
          </div>
          <p v-if="run.status === 'FAILED' && run.errorMessage" class="mt-1 truncate text-body-xs text-error-strong">
            {{ run.errorMessage }}
          </p>
        </div>
        <ChevronRightIcon class="h-5 w-5 shrink-0 text-gray-300 dark:text-gray-600" />
      </button>

      <!-- 페이지네이션 -->
      <div v-if="store.runsTotalPages > 1" class="flex items-center justify-between pt-2">
        <button class="btn-secondary" :disabled="!store.runsHasPrevious" @click="goPage(store.runsPage - 1)">
          {{ $t('ugc.shorts.runs.prev') }}
        </button>
        <span class="text-body-xs text-gray-400">
          {{ $t('ugc.shorts.runs.pageInfo', { page: store.runsPage + 1, total: store.runsTotalPages }) }}
        </span>
        <button class="btn-secondary" :disabled="!store.runsHasNext" @click="goPage(store.runsPage + 1)">
          {{ $t('ugc.shorts.runs.next') }}
        </button>
      </div>
    </div>

    <!-- 실행 생성 모달 -->
    <BaseModal v-model="createOpen" :title="$t('ugc.shorts.runs.create.title')" max-width="lg">
      <div class="space-y-4">
        <div>
          <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300" for="shorts-run-video">
            {{ $t('ugc.shorts.runs.create.sourceVideo') }}
          </label>
          <select id="shorts-run-video" v-model="form.sourceVideoId" class="input-field w-full">
            <option :value="0" disabled>{{ $t('ugc.shorts.runs.create.selectVideo') }}</option>
            <option v-for="v in videos" :key="v.id" :value="v.id">{{ v.title }}</option>
          </select>
          <p v-if="!videosLoading && videos.length === 0" class="mt-1 text-body-xs text-gray-400">
            {{ $t('ugc.shorts.runs.create.noVideos') }}
          </p>
        </div>
        <div>
          <label class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300" for="shorts-run-template">
            {{ $t('ugc.shorts.runs.create.template') }}
          </label>
          <select id="shorts-run-template" v-model="form.templateId" class="input-field w-full">
            <option :value="null">{{ $t('ugc.shorts.runs.create.defaultTemplate') }}</option>
            <option v-for="tpl in shortsStore.templates" :key="tpl.id" :value="tpl.id">
              {{ templateLabel(tpl) }}
            </option>
          </select>
        </div>
      </div>

      <template #footer>
        <button class="btn-primary" :disabled="creating" @click="create">
          {{ creating ? $t('ugc.shorts.runs.create.submitting') : $t('ugc.shorts.runs.create.submit') }}
        </button>
      </template>
    </BaseModal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUgcShortsPipelineStore } from '@/stores/ugcShortsPipeline'
import { useUgcShortsStore } from '@/stores/ugcShorts'
import { useNotificationStore } from '@/stores/notification'
import { videoApi } from '@/api/video'
import type { Video } from '@/types/video'
import type { ShortsTemplateResponse } from '@/api/ugcShortsTemplate'
import type { PipelineRunStatus } from '@/api/ugcShortsPipeline'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import {
  ChatBubbleLeftRightIcon,
  ChevronRightIcon,
  FilmIcon,
  PlusIcon,
  Square2StackIcon,
} from '@heroicons/vue/24/outline'

const { t } = useI18n({ useScope: 'global' })
const router = useRouter()
const store = useUgcShortsPipelineStore()
const shortsStore = useUgcShortsStore()
const notify = useNotificationStore()

const createOpen = ref(false)
const creating = ref(false)
const videos = ref<Video[]>([])
const videosLoading = ref(false)

// 생성 폼 — 원본 영상은 필수, 템플릿은 null 이면 워크스페이스 기본 템플릿
const form = ref({
  sourceVideoId: 0,
  templateId: null as number | null,
})

function statusBadgeClass(status: PipelineRunStatus): string {
  switch (status) {
    case 'RUNNING':
      return 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
    case 'AWAITING_HOOK_SELECTION':
    case 'AWAITING_SCHEDULE':
      return 'bg-warning-subtle text-warning-strong'
    case 'COMPLETED':
      return 'bg-success-subtle text-success-strong'
    case 'FAILED':
      return 'bg-error-subtle text-error-strong'
    default:
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
  }
}

function formatDate(iso: string): string {
  return iso.slice(0, 16).replace('T', ' ')
}

/** 기본 템플릿은 이름 뒤에 배지를 붙여 구분한다 */
function templateLabel(tpl: ShortsTemplateResponse): string {
  return tpl.isDefault ? `${tpl.name} (${t('ugc.shorts.templates.defaultBadge')})` : tpl.name
}

function goDetail(runId: number) {
  router.push(`/ugc/shorts/runs/${runId}`)
}

async function goPage(page: number) {
  try {
    await store.fetchRuns(page)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.runs.loadFailed'))
  }
}

function openCreate() {
  form.value = { sourceVideoId: 0, templateId: null }
  createOpen.value = true
  loadVideos()
  if (shortsStore.templates.length === 0) {
    shortsStore.fetchTemplates().catch(() => undefined)
  }
}

/** 원본 후보는 동영상만 보여 준다 (이미지는 파이프라인 대상이 아님) */
async function loadVideos() {
  videosLoading.value = true
  try {
    const res = await videoApi.list({ page: 0, size: 50 })
    videos.value = res.content.filter((v) => v.mediaType === 'VIDEO')
  } catch {
    videos.value = []
  } finally {
    videosLoading.value = false
  }
}

async function create() {
  if (!form.value.sourceVideoId) {
    notify.error(t('ugc.shorts.runs.create.videoRequired'))
    return
  }
  creating.value = true
  try {
    const run = await store.createRun({
      sourceVideoId: form.value.sourceVideoId,
      templateId: form.value.templateId,
    })
    createOpen.value = false
    notify.success(t('ugc.shorts.runs.create.created'))
    router.push(`/ugc/shorts/runs/${run.id}`)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.runs.create.createFailed'))
  } finally {
    creating.value = false
  }
}

onMounted(async () => {
  try {
    await store.fetchRuns(0)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.runs.loadFailed'))
  }
})
</script>

<template>
  <div class="min-h-full space-y-5 py-5 text-content">
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

    <div
      v-else-if="store.runsLoadError"
      class="card flex flex-col items-center justify-center gap-3 py-16 text-center"
      role="alert"
    >
      <ExclamationTriangleIcon class="h-10 w-10 text-error-strong" />
      <p class="text-body text-error-strong">{{ store.runsLoadError }}</p>
      <button type="button" class="btn-secondary mt-2" @click="retryRuns">
        {{ $t('action.retry') }}
      </button>
    </div>

    <EmptyState
      v-else-if="!store.runsLoadError && store.runs.length === 0"
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
          <p v-if="run.errorMessage" class="mt-1 truncate text-body-xs" :class="run.status === 'PARTIALLY_COMPLETED' ? 'text-warning-strong' : 'text-error-strong'">
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
        <!--
          완주 크레딧이 모자란 경우에만 나온다. 모달을 닫지 않으므로 선택한 영상이 그대로
          남고, 사용자는 여기서 요금제로 가거나 그냥 닫을 수 있다.
        -->
        <div
          v-if="creditBlockMessage"
          class="rounded-xl border border-warning-strong/40 bg-warning-subtle p-3"
          role="alert"
        >
          <p class="text-body-xs text-warning-strong">{{ creditBlockMessage }}</p>

          <!--
            체험 자격이 있는 무료 사용자에게는 무료로 풀 수 있는 길을 먼저 보여준다.
            요금제 화면으로 보내면 그 화면에서 무료 출구가 유료 버튼 옆 보조 버튼이라
            결제가 유일한 해법처럼 보인다. 자격 판정은 서버가 내려준 구독 상태로만 한다.
          -->
          <button
            v-if="trialEligible"
            type="button"
            class="btn-primary mt-2 inline-flex min-h-[44px] items-center"
            :disabled="startingTrial"
            @click="startTrialForRun"
          >
            {{ startingTrial
              ? $t('ugc.shorts.runs.create.startingTrial')
              : $t('ugc.shorts.runs.create.startTrial') }}
          </button>

          <button
            type="button"
            class="mt-2 inline-flex min-h-[44px] items-center rounded-lg px-2 text-body-xs font-semibold text-primary-600 dark:text-primary-400"
            :class="trialEligible ? 'ml-2' : ''"
            @click="goToPlans"
          >
            {{ $t('ugc.shorts.runs.create.viewPlans') }}
          </button>
        </div>

        <!--
          체험이 시작돼 이제 한 번은 돌릴 수 있다는 사실을 명시한다. 잔액이 늘었다는
          말만으로는 지금 무엇을 할 수 있는지 알 수 없다.
        -->
        <div
          v-if="trialStartedMessage"
          class="rounded-xl border border-primary-400/40 bg-primary-50 p-3 dark:bg-primary-900/20"
          role="status"
        >
          <p class="text-body-xs text-primary-700 dark:text-primary-300">{{ trialStartedMessage }}</p>
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
import { ref, computed, onMounted } from 'vue'
import { useSubscriptionStore } from '@/stores/subscription'
import { useCreditStore } from '@/stores/credit'
import {
  matchesCode,
  PLAN_UPGRADE_PATH,
  SHORTS_INSUFFICIENT_CREDIT_FOR_RUN,
} from '@/composables/usePlanLimit'
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
  ExclamationTriangleIcon,
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
/** 완주 크레딧 부족 안내. 이 코드일 때만 채워지며 다른 오류는 기존 토스트로 간다. */
const creditBlockMessage = ref<string | null>(null)

const subscriptionStore = useSubscriptionStore()
const creditStore = useCreditStore()
const startingTrial = ref(false)
/** 체험 시작 성공 안내. 실패했을 때는 채우지 않는다 — 성공한 척하면 안 된다. */
const trialStartedMessage = ref<string | null>(null)

/**
 * 무료 체험을 지금 시작할 수 있는 사용자인지.
 *
 * 서버가 내려준 구독 상태로만 판단한다. 자격의 최종 판정은 서버가 하고(이미 체험한
 * 사용자는 거절한다), 여기서는 **명백히 자격이 없는 사용자에게 버튼을 보이지 않기 위한**
 * 보수적 필터다.
 *
 * 구독 정보를 아직 못 읽었으면 보이지 않는다. 눌러도 되는지 모르는 버튼을 띄우느니
 * 기존 요금제 링크만 두는 편이 낫다.
 *
 * `trialEnd` 가 있으면 이미 체험을 쓴 것이다. 유료·체험중 사용자는 planType/status 로 걸린다.
 */
const trialEligible = computed(() => {
  const subscription = subscriptionStore.subscription
  if (!subscription) return false
  if (subscription.planType !== 'FREE' || subscription.status !== 'FREE') return false
  return !subscription.trialEnd
})
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
    case 'PARTIALLY_COMPLETED':
      return 'bg-warning-subtle text-warning-strong'
    case 'FAILED':
      return 'bg-error-subtle text-error-strong'
    default:
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
  }
}

function formatDate(iso: string): string {
  return iso.slice(0, 16).replace('T', ' ')
}

async function retryRuns() {
  try {
    await store.fetchRuns(store.runsPage)
  } catch {
    // The store exposes the error inline; keep the retry event handled.
  }
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
  // 다시 시도할 때 이전 안내가 남아 있으면 방금 결과인지 알 수 없다.
  creditBlockMessage.value = null
  trialStartedMessage.value = null
  try {
    const run = await store.createRun({
      sourceVideoId: form.value.sourceVideoId,
      templateId: form.value.templateId,
    })
    createOpen.value = false
    notify.success(t('ugc.shorts.runs.create.created'))
    router.push(`/ugc/shorts/runs/${run.id}`)
  } catch (e) {
    /*
     * 서버가 "지금 잔액으로는 완주 불가"라고 생성 전에 판정한 경우에만 결제를 권한다.
     * 판정은 안정 코드로만 한다 — 문구는 번역되거나 다듬어지므로 문자열로 분기하면 조용히 깨진다.
     *
     * 모달을 닫지 않고 선택한 영상도 그대로 둔다. 업그레이드하고 돌아온 사용자가 처음부터
     * 다시 고르게 만들면, 결제를 마친 직후에 다시 일을 시키는 셈이다.
     */
    if (matchesCode(e, SHORTS_INSUFFICIENT_CREDIT_FOR_RUN)) {
      creditBlockMessage.value = e instanceof Error ? e.message : t('ugc.shorts.runs.create.createFailed')
    } else {
      // 그 밖의 실패(일반 크레딧 부족·검증·네트워크·5xx)는 기존 동작 그대로 둔다.
      notify.error(e instanceof Error ? e.message : t('ugc.shorts.runs.create.createFailed'))
    }
  } finally {
    creating.value = false
  }
}

function goToPlans() {
  router.push(PLAN_UPGRADE_PATH)
}

/**
 * 막힌 자리에서 무료 체험을 시작한다.
 *
 * 화면을 떠나지 않는다. 사용자는 이미 영상을 고르고 '실행 시작'을 눌렀다 — 요금제 화면으로
 * 보냈다가 돌아오게 하면 그 선택을 다시 하게 만드는 것이다.
 *
 * 체험은 결제가 아니다. 서버의 구독 API 를 그대로 쓰며 PortOne 체크아웃을 지나지 않는다.
 * 자격의 최종 판정도 서버가 한다 — 이미 체험한 사용자는 여기서 거절되고, 그때는 기존
 * 차단 안내를 그대로 둔 채 실패 사유만 보여준다.
 */
async function startTrialForRun() {
  startingTrial.value = true
  try {
    await subscriptionStore.startTrial('STARTER')
    // 재시도에 필요한 것만 갱신한다. 실행 목록은 아직 만든 것이 없어 다시 읽을 이유가 없다.
    await creditStore.fetchBalance()
    creditBlockMessage.value = null
    trialStartedMessage.value = t('ugc.shorts.runs.create.trialStarted')
  } catch (e) {
    // 성공한 척하지 않는다. 차단 안내는 그대로 두고 실패 사유만 알린다.
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.runs.create.trialFailed'))
  } finally {
    startingTrial.value = false
  }
}

onMounted(async () => {
  try {
    await store.fetchRuns(0)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.shorts.runs.loadFailed'))
  }
  /*
   * 체험 자격 판정에 쓴다. 실패해도 화면은 그대로 동작한다 — 구독 상태를 모르면
   * trialEligible 이 false 라 체험 버튼이 숨겨지고 기존 요금제 링크만 남는다.
   */
  await subscriptionStore.fetchSubscription().catch(() => undefined)
})
</script>

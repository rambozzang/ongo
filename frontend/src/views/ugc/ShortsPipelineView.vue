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

          <!--
            과금 규칙을 미리 말한다. 영상 목록에는 길이가 없어 정확한 금액을 항상 알 수는
            없으므로, 길이를 읽었을 때만 예상치를 붙이고 못 읽으면 규칙만 안내한다.
            어느 쪽이든 최종 판정은 서버가 한다는 것을 함께 적는다.
          -->
          <div
            v-if="form.sourceVideoId"
            class="mt-2 rounded-lg border border-gray-200 bg-gray-50 p-3 dark:border-gray-700 dark:bg-gray-800/50"
            data-testid="shorts-credit-notice"
          >
            <!--
              `.value` 가 있어야 한다. `creditEstimate` 는 반응형 객체가 아니라 ref 를 담은
              평범한 객체라, `creditEstimate.measuring` 은 **ref 그 자체**이고 언제나 truthy 다.
              그래서 이 줄이 항상 이기고 아래 예상치·규칙 줄은 렌더된 적이 없었다 —
              안내가 영원히 "확인하는 중" 에 멈춰 있었다. 바로 아래 `estimate.value` 처럼
              여기서도 값을 꺼내 읽는다.
            -->
            <p v-if="creditEstimate.measuring.value" class="text-body-xs text-gray-500 dark:text-gray-400">
              {{ $t('ugc.shorts.runs.create.creditMeasuring') }}
            </p>
            <p
              v-else-if="creditEstimate.estimate.value.credits !== null"
              class="text-body-xs font-medium text-gray-700 dark:text-gray-200"
              data-testid="shorts-credit-estimate"
            >
              {{ $t('ugc.shorts.runs.create.creditEstimate', {
                credits: creditEstimate.estimate.value.credits,
                minutes: estimatedMinutes,
              }) }}
            </p>
            <p v-else class="text-body-xs text-gray-700 dark:text-gray-200" data-testid="shorts-credit-rule">
              {{ $t('ugc.shorts.runs.create.creditRule', {
                min: SHORTS_MIN_CREDITS,
                window: SHORTS_TRANSCRIBE_WINDOW_MINUTES,
                perWindow: SHORTS_TRANSCRIBE_CREDITS_PER_WINDOW,
              }) }}
            </p>
            <!--
              잔액은 **알 때만** 적는다. 조회 실패·미로딩에서 0 을 그리면 측정하지 못한
              값을 실측처럼 단언하게 된다(knownBalance 주석 참고).
            -->
            <p
              v-if="knownBalance !== null"
              class="mt-1 text-body-xs text-gray-600 dark:text-gray-300"
              data-testid="shorts-credit-balance"
            >
              {{ $t('ugc.shorts.runs.create.creditBalance', { balance: knownBalance }) }}
            </p>
            <!--
              부족은 잔액과 예상치를 **둘 다** 알 때만 말한다. 버튼을 막지는 않는다 —
              최종 판정은 서버가 하고, 여기서 막으면 그 사이 잔액이 늘어난 경우까지 막는다.
            -->
            <p
              v-if="creditShortfall !== null"
              class="mt-1 text-body-xs font-medium text-warning-strong"
              data-testid="shorts-credit-shortfall"
            >
              {{ $t('ugc.shorts.runs.create.creditShortfall', { shortfall: creditShortfall }) }}
            </p>
            <p class="mt-1 text-body-xs text-gray-400">
              {{ $t('ugc.shorts.runs.create.creditServerDecides') }}
            </p>

            <!--
              부족이 확실하고 체험 자격이 있으면, 막히기 **전에** 무료 출구를 보여준다.
              막힌 뒤에 나오는 CTA 와 같은 핸들러·같은 자격 기준을 쓴다.
            -->
            <button
              v-if="showPreemptiveTrial"
              type="button"
              class="btn-primary mt-2 inline-flex min-h-[44px] items-center"
              :disabled="startingTrial"
              data-testid="shorts-credit-preemptive-trial"
              @click="startTrialForRun"
            >
              {{ startingTrial
                ? $t('ugc.shorts.runs.create.startingTrial')
                : $t('ugc.shorts.runs.create.startTrial') }}
            </button>

            <!--
              체험을 써도 이 영상은 완주하지 못한다는 것을 **확실히 아는** 경우다.
              체험은 1회성이라 여기서 쓰게 두면 결과물 없이 사라진다. 그렇다고 무료 경로를
              조용히 지우지 않는다 — 이유와 다음 행동을 함께 준다.
            -->
            <div
              v-if="trialWastedOnThisVideo"
              class="mt-2 rounded-lg border border-warning-strong/40 bg-warning-subtle p-2.5"
              data-testid="shorts-trial-not-enough"
            >
              <p class="text-body-xs text-warning-strong">
                {{ $t('ugc.shorts.runs.create.trialNotEnough', {
                  credits: creditEstimate.estimate.value.credits,
                  trialCredits: starterTrialCredits,
                }) }}
              </p>
              <!-- 커버되는 길이가 있을 때만 분을 말한다. 없으면 숫자를 지어내지 않는다. -->
              <p v-if="trialMaxMinutes !== null" class="mt-1 text-body-xs text-warning-strong">
                {{ $t('ugc.shorts.runs.create.trialCoversUpTo', { minutes: trialMaxMinutes }) }}
              </p>
              <div class="mt-2 flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  class="btn-primary inline-flex min-h-[44px] items-center"
                  data-testid="shorts-pick-shorter-video"
                  @click="clearSelectedVideo"
                >
                  {{ $t('ugc.shorts.runs.create.pickShorterVideo') }}
                </button>
                <button
                  type="button"
                  class="inline-flex min-h-[44px] items-center rounded-lg px-2 text-body-xs font-semibold text-primary-600 dark:text-primary-400"
                  @click="goToPlans"
                >
                  {{ $t('ugc.shorts.runs.create.viewPlans') }}
                </button>
              </div>
            </div>
          </div>
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
          <!--
            서버가 거절한 뒤에도 같은 판단을 쓴다. 여기만 열어 두면 사전 안내로 막은 소진이
            한 단계 뒤에서 그대로 일어난다.
          -->
          <button
            v-if="trialEligible && trialCoversEstimate !== false"
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
import { ref, computed, onMounted, watch } from 'vue'
import {
  SHORTS_FIXED_CREDITS,
  SHORTS_MIN_CREDITS,
  SHORTS_TRANSCRIBE_CREDITS_PER_WINDOW,
  SHORTS_TRANSCRIBE_WINDOW_MINUTES,
  useShortsCreditEstimate,
} from '@/composables/useShortsCreditEstimate'
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

/*
 * 예상 크레딧. 영상 목록에는 길이가 없어 fileUrl 헤더만 읽어 재본다. 실패는 오류가
 * 아니라 "모름" 이며, 그때는 규칙 안내만 보여준다.
 */
const creditEstimate = useShortsCreditEstimate()
const estimatedMinutes = computed(() => {
  const seconds = creditEstimate.estimate.value.durationSeconds
  return seconds == null ? '' : String(Math.max(1, Math.round(seconds / 60)))
})

/**
 * **알려진** 잔액만. 모르면 null 이다.
 *
 * `creditStore.totalBalance` 를 쓰면 안 된다 — 그 computed 는 아직 불러오지 못한 상태를
 * **0 으로** 돌려주므로, 조회 실패한 사용자에게 "잔여 0개" 라고 단언하게 된다. 그건
 * 측정하지 못한 값을 실측처럼 보여주는 것이다. 원시 `balance` 로 미로딩과 실제 0 을
 * 구분한다.
 */
const knownBalance = computed<number | null>(() =>
  creditStore.balance ? creditStore.balance.totalBalance : null,
)

/**
 * 이번 실행에 부족한 크레딧. **잔액과 예상치를 둘 다 알 때만** 계산한다.
 *
 * 하나라도 모르면 null 이고 화면은 부족을 주장하지 않는다. 길이를 못 잰 영상(CORS·스트리밍)
 * 이나 잔액 조회 실패에서 숫자를 지어내면, 충분한 사용자에게 부족하다고 말하거나 그 반대가
 * 된다. 판정의 최종 권한은 어차피 서버에 있으므로 화면은 확실할 때만 말한다.
 */
const creditShortfall = computed<number | null>(() => {
  const balance = knownBalance.value
  const credits = creditEstimate.estimate.value.credits
  if (balance === null || credits === null) return null
  const shortfall = credits - balance
  return shortfall > 0 ? shortfall : null
})

/**
 * 실행 버튼을 누르기 **전에** 체험을 권할 수 있는 경우.
 *
 * 부족이 확실하고 체험 자격도 있을 때만이다. 막힌 뒤에 나오는 기존 CTA 와 같은 출구를
 * 한 단계 앞에서 보여줄 뿐, 자격 판정 기준([trialEligible])은 그대로 쓴다.
 *
 * **실행 버튼은 비활성화하지 않는다.** 이건 브라우저가 읽은 길이에 기반한 *예상*이고,
 * 그 사이 다른 요청이 잔액을 바꿀 수도 있다. 화면의 추정이 서버 판정을 앞질러 막으면
 * 멀쩡한 실행까지 막게 된다.
 */
/**
 * 체험이 주는 크레딧. **서버 값이며 하드코딩하지 않는다.**
 *
 * `/subscriptions/plans` 가 전 플랜의 `features.freeCredits` 를 내려주고
 * (`SubscriptionUseCase.getPlans`), 스토어가 `Plan.freeAiCredits` 로 옮겨 담는다.
 * 여기에 숫자를 박으면 요금제가 바뀔 때 화면만 옛 값으로 남아 조용히 거짓말을 한다.
 *
 * 목록을 못 읽었으면 `[]` 이므로 null 이다 — **모른다는 뜻이지 0 이 아니다.**
 */
const starterTrialCredits = computed<number | null>(
  () => subscriptionStore.plans.find((p) => p.type === 'STARTER')?.freeAiCredits ?? null,
)

/**
 * 체험이 이 영상을 완주시킬 수 있는가.
 *
 * **null 은 "모른다" 이지 "커버한다" 가 아니다.** 체험 크레딧이나 예상 비용 중 하나라도
 * 모르면 판정하지 않는다. 확실히 모자랄 때만 false 다.
 */
const trialCoversEstimate = computed<boolean | null>(() => {
  const credits = starterTrialCredits.value
  const cost = creditEstimate.estimate.value.credits
  if (credits === null || cost === null) return null
  return cost <= credits
})

/**
 * 체험으로 완주할 수 있는 최대 원본 길이(분). 커버되는 길이가 없으면 null.
 *
 * 서버 값에서 유도한다 — 고정 단계 합계와 전사 구간 단가는
 * `useShortsCreditEstimate` 가 서버 규칙의 사본으로 들고 있다.
 */
const trialMaxMinutes = computed<number | null>(() => {
  const credits = starterTrialCredits.value
  if (credits === null) return null
  const windows = Math.floor((credits - SHORTS_FIXED_CREDITS) / SHORTS_TRANSCRIBE_CREDITS_PER_WINDOW)
  return windows > 0 ? windows * SHORTS_TRANSCRIBE_WINDOW_MINUTES : null
})

/**
 * 체험을 써도 이 영상은 완주하지 못한다고 **확실히** 아는 상태.
 *
 * 체험은 1회성이라 여기서 쓰게 두면 결과물 없이 사라진다. 그렇다고 무료 경로를 조용히
 * 지우지는 않는다 — 아래 안내가 이유와 다음 행동을 함께 준다.
 */
const trialWastedOnThisVideo = computed(
  () => trialCoversEstimate.value === false && trialEligible.value,
)

const showPreemptiveTrial = computed(
  () => creditShortfall.value !== null && trialEligible.value && trialCoversEstimate.value !== false,
)

/** 더 짧은 영상을 고르게 되돌린다. **모달은 닫지 않는다** — 다시 열게 만들 이유가 없다. */
function clearSelectedVideo() {
  form.value.sourceVideoId = 0
}

// 생성 폼 — 원본 영상은 필수, 템플릿은 null 이면 워크스페이스 기본 템플릿
const form = ref({
  sourceVideoId: 0,
  templateId: null as number | null,
})

/*
 * 선택이 바뀔 때마다 다시 잰다. **form 선언 뒤에 있어야 한다** — watch 는 설정 시점에
 * 소스 게터를 한 번 실행하므로, 앞에 두면 form 이 TDZ 라 ReferenceError 가 난다.
 *
 * `immediate` 를 쓰지 않는다. 초기값 0 은 "미선택" 이라 잴 대상이 없다.
 */
watch(
  () => form.value.sourceVideoId,
  (id) => {
    if (!id) {
      creditEstimate.reset()
      return
    }
    const selected = videos.value.find((v) => v.id === id)
    void creditEstimate.measure(selected?.fileUrl)
  },
)

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
    /*
     * **체험을 시작했다고 이 영상을 돌릴 수 있는 것은 아니다.**
     *
     * 체험은 잔액을 Starter 기준 100 으로 올린다. 그런데 완주 비용은 원본 길이에 비례해
     * `27 + ceil(분/10) × 10` 이고 허용 상한이 180 분이라 최대 207 까지 간다. 즉 100 은
     * 70 분까지만 커버한다. 그보다 긴 영상을 고른 사용자에게 "한 번 만들 수 있다"고 하면
     * 다시 눌렀을 때 서버가 거절한다 — 우리가 거짓말을 한 것이 된다.
     *
     * 그래서 잔액을 다시 읽은 **뒤에** 부족을 재평가한다.
     */
    const shortfall = creditShortfall.value
    if (shortfall !== null) {
      /*
       * 기존 차단 안내를 그냥 지우지 않는다. 그 문구에는 체험 전 잔여(30)가 적혀 있어
       * 지금은 틀린 숫자다. 방금 잰 부족액으로 갈아 끼운다 — 지우면 사용자는 무엇이
       * 얼마나 모자란지 알 방법이 없어진다.
       */
      creditBlockMessage.value = t('ugc.shorts.runs.create.trialStartedStillShort', { shortfall })
      trialStartedMessage.value = null
    } else {
      /*
       * 부족을 확인하지 못한 경우다. **충분하다는 뜻이 아니다** — 길이를 못 재 예상치가
       * 없거나(null) 잔액을 못 읽었을 수도 있다. 그래서 "한 번 만들 수 있다"고 단정하지
       * 않고, 체험이 시작됐다는 사실과 다시 눌러보라는 안내만 한다. 판정은 서버가 한다.
       */
      creditBlockMessage.value = null
      trialStartedMessage.value = t('ugc.shorts.runs.create.trialStarted')
    }
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
  /*
   * 잔액은 **한 번만** 읽는다. 실패해도 화면은 그대로 동작한다 — 잔액을 모르면
   * knownBalance 가 null 이라 잔여·부족 표시가 함께 사라지고, 종전과 같은 안내만 남는다.
   * 지어낸 0 으로 부족을 단언하는 것보다 아무 말도 하지 않는 편이 낫다.
   */
  await creditStore.fetchBalance().catch(() => undefined)
  /*
   * 체험이 주는 크레딧을 서버에서 읽는다. 화면에 숫자를 박지 않기 위한 것이다.
   *
   * 실패해도 무료 경로를 막지 않는다 — 목록이 비면 starterTrialCredits 가 null 이고
   * trialCoversEstimate 도 null 이라, CTA 는 종전대로 나온다. 모른다는 이유로 유용한
   * 경로를 없애면 흔한 조회 실패에서 사용자가 무료로 풀 길을 잃는다.
   */
  await subscriptionStore.fetchPlans().catch(() => undefined)
})
</script>

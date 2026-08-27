<script setup lang="ts">
/**
 * 쇼츠 유료 파일럿 운영자 화면.
 *
 * ## 이 화면의 유일한 규칙
 *
 * **미입력을 0으로 그리지 않는다.** 백엔드가 `null` 과 `0` 을 구분하려고 일부러 nullable
 * 로 내려보내는데, 화면이 `?? 0` 한 줄로 뭉개면 그 설계가 통째로 무의미해진다.
 * "원가 미입력"이 "원가 0원"으로 보이는 순간 이익률 100% 짜리 거짓 보고서가 된다.
 *
 * ## 왜 입력에 확인 단계가 있나
 *
 * 원장이 append-only 라 잘못 넣은 값을 지울 수 없다. 자릿수 하나 틀리면 영구히 남는다.
 * 그래서 입력 → 확인 두 단계로 나누고, 확인 화면에 서식이 적용된 값을 다시 보여준다.
 */
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/common/PageHeader.vue'
import SectionCard from '@/components/common/SectionCard.vue'
import StatCard from '@/components/common/StatCard.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import OTabs from '@/components/ui/OTabs.vue'
import { useNotificationStore } from '@/stores/notification'
import { adminShortsPilotApi } from '@/api/adminShortsPilot'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import type {
  ShortsPilotCandidate,
  ShortsPilotEntry,
  ShortsPilotReport,
  ShortsPilotRunRow,
} from '@/types/adminShortsPilot'

const { t, locale } = useI18n()
const notify = useNotificationStore()

const report = ref<ShortsPilotReport | null>(null)
const loading = ref(false)
const activeTab = ref<'summary' | 'runs'>('summary')

const tabs = computed(() => [
  { key: 'summary', label: t('admin.shortsPilot.tabs.summary') },
  { key: 'runs', label: t('admin.shortsPilot.tabs.runs') },
])

// ---- 서식 ----
// 단위 문구를 i18n 키로 새로 만들지 않고 Intl 에 맡긴다. 로케일이 바뀌면 단위 표기도
// 같이 바뀌므로 ko/en 두 벌을 손으로 맞출 필요가 없다.

const krw = computed(
  () => new Intl.NumberFormat(locale.value, { style: 'currency', currency: 'KRW' }),
)
const minuteFmt = computed(
  () => new Intl.NumberFormat(locale.value, { style: 'unit', unit: 'minute', unitDisplay: 'short' }),
)
const hourFmt = computed(
  () =>
    new Intl.NumberFormat(locale.value, {
      style: 'unit',
      unit: 'hour',
      unitDisplay: 'short',
      maximumFractionDigits: 1,
    }),
)

/** 미입력 표시. 이 함수를 거치지 않고 값을 그리는 곳이 있으면 안 된다. */
const notRecorded = computed(() => t('admin.shortsPilot.notRecorded'))

function showKrw(value: number | null | undefined): string {
  return value == null ? notRecorded.value : krw.value.format(value)
}

function showMinutes(value: number | null | undefined): string {
  return value == null ? notRecorded.value : minuteFmt.value.format(value)
}

function showCount(value: number | null | undefined): string {
  return value == null ? notRecorded.value : String(value)
}

function showLeadTime(ms: number | null | undefined): string {
  return ms == null ? notRecorded.value : hourFmt.value.format(ms / 3_600_000)
}

function showDate(value: string | null | undefined): string {
  if (!value) return notRecorded.value
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? notRecorded.value : parsed.toLocaleString(locale.value)
}

// ---- 보고서 조회 ----

async function loadReport() {
  loading.value = true
  try {
    report.value = await adminShortsPilotApi.getReport()
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('status.error'))
  } finally {
    loading.value = false
  }
}

onMounted(loadReport)

const summary = computed(() => report.value?.summary ?? null)
const runs = computed<ShortsPilotRunRow[]>(() => report.value?.runs ?? [])
const hasData = computed(() => report.value?.state === 'OK' && summary.value !== null)

// ---- 코호트 등록 ----

const enrollRunId = ref<string>('')
const enrolling = ref(false)

const enrollDisabled = computed(() => {
  const parsed = Number(enrollRunId.value)
  return enrolling.value || !Number.isInteger(parsed) || parsed <= 0
})

async function submitEnroll() {
  if (enrollDisabled.value) return
  await enroll(Number(enrollRunId.value), () => {
    enrollRunId.value = ''
  })
}

/**
 * 등록 공통 경로. 직접 입력과 후보 목록이 같은 함수를 쓴다 — 두 곳에 나눠 두면
 * 한쪽만 고쳐져 "목록에서 등록하면 사라지는데 직접 입력하면 안 사라진다" 같은 차이가 생긴다.
 */
async function enroll(runId: number, onSuccess?: () => void) {
  enrolling.value = true
  try {
    const result = await adminShortsPilotApi.enroll(runId)
    // 이미 등록된 실행도 성공이지만 같은 문구로 알리면 운영자가 두 번 등록했는지 모른다.
    notify.success(
      result.alreadyEnrolled
        ? t('admin.shortsPilot.enroll.alreadyEnrolled')
        : t('admin.shortsPilot.enroll.success'),
    )
    onSuccess?.()
    /*
     * 서버가 이미 등록된 실행을 제외해 보내지만, 목록을 다시 받기 전까지는 방금 등록한
     * 행이 화면에 남아 있다. 운영자는 그걸 보고 또 누른다. 그래서 응답을 기다리지 않고
     * 먼저 지운다 — 등록은 성공했으므로 이 제거는 서버 상태와 어긋나지 않는다.
     */
    candidates.value = candidates.value.filter((c) => c.runId !== runId)
    candidateTotal.value = Math.max(0, candidateTotal.value - 1)

    await Promise.all([loadReport(), loadCandidates()])
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('admin.shortsPilot.enroll.failed'))
  } finally {
    enrolling.value = false
  }
}

// ---- 수기 기록 열람·취소 ----

const entriesOpen = ref(false)
const entriesRunId = ref<number | null>(null)
const entries = ref<ShortsPilotEntry[]>([])
const entriesLoading = ref(false)
/** 오류는 토스트로 흘리지 않고 목록 자리에 남긴다 — 다시 시도할 곳이 필요하다. */
const entriesError = ref(false)

const reverseConfirmOpen = ref(false)
const reverseTargetId = ref<number | null>(null)
const reversing = ref(false)

const entryTypeLabels: Record<string, string> = {
  OPERATOR_REVENUE_LOGGED: 'admin.shortsPilot.entries.revenue',
  OPERATOR_EXTERNAL_COST_LOGGED: 'admin.shortsPilot.entries.externalCost',
  OPERATOR_TIME_LOGGED: 'admin.shortsPilot.entries.operatorTime',
}

function entryTypeLabel(type: string): string {
  const key = entryTypeLabels[type]
  // 모르는 타입이면 서버가 준 원문을 그대로 보인다. 지어내지 않는다.
  return key ? t(key) : type
}

/** 금액 기록이면 원, 시간 기록이면 분. 둘 다 없으면 미입력이다. */
function entryValue(entry: ShortsPilotEntry): string {
  if (entry.amountKrw != null) return krw.value.format(entry.amountKrw)
  if (entry.operatorMinutes != null) return minuteFmt.value.format(entry.operatorMinutes)
  return notRecorded.value
}

async function loadEntries() {
  const runId = entriesRunId.value
  if (runId === null) return
  entriesLoading.value = true
  entriesError.value = false
  try {
    entries.value = (await adminShortsPilotApi.getEntries(runId)).entries
  } catch {
    entriesError.value = true
    entries.value = []
  } finally {
    entriesLoading.value = false
  }
}

async function openEntries(runId: number) {
  entriesRunId.value = runId
  entries.value = []
  entriesOpen.value = true
  await loadEntries()
}

/** 취소는 되돌릴 수 없다. 확인 없이 바로 보내지 않는다. */
function askReverse(entryId: number) {
  reverseTargetId.value = entryId
  reverseConfirmOpen.value = true
}

async function confirmReverse() {
  const runId = entriesRunId.value
  const entryId = reverseTargetId.value
  if (runId === null || entryId === null) return

  reversing.value = true
  try {
    const result = await adminShortsPilotApi.reverseEntry(runId, entryId)
    // 이미 취소된 건도 성공이지만 같은 문구로 알리면 운영자가 두 번 눌렀는지 모른다.
    notify.success(
      result.alreadyReversed
        ? t('admin.shortsPilot.entries.alreadyReversed')
        : t('admin.shortsPilot.entries.reverseSuccess'),
    )
    // 합계가 바뀌므로 보고서도 함께 다시 읽는다.
    await Promise.all([loadEntries(), loadReport()])
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('admin.shortsPilot.entries.reverseFailed'))
  } finally {
    reversing.value = false
    reverseTargetId.value = null
  }
}

// ---- 등록 후보 목록 ----

const CANDIDATE_PAGE_SIZE = 20

const candidates = ref<ShortsPilotCandidate[]>([])
const candidateTotal = ref(0)
const candidatePage = ref(0)
const candidatesLoading = ref(false)
/** 오류는 토스트로 흘리지 않고 목록 자리에 남긴다 — 다시 시도할 곳이 필요하다. */
const candidatesError = ref(false)

const candidateTotalPages = computed(() =>
  Math.max(1, Math.ceil(candidateTotal.value / CANDIDATE_PAGE_SIZE)),
)
const hasPrevCandidatePage = computed(() => candidatePage.value > 0)
const hasNextCandidatePage = computed(() => candidatePage.value + 1 < candidateTotalPages.value)

const candidateRange = computed(() => {
  if (candidateTotal.value === 0) return { from: 0, to: 0 }
  const from = candidatePage.value * CANDIDATE_PAGE_SIZE + 1
  return { from, to: Math.min(from + candidates.value.length - 1, candidateTotal.value) }
})

async function loadCandidates() {
  candidatesLoading.value = true
  candidatesError.value = false
  try {
    const page = await adminShortsPilotApi.getCandidates(candidatePage.value, CANDIDATE_PAGE_SIZE)
    candidates.value = page.candidates
    candidateTotal.value = page.total
    /*
     * 마지막 페이지의 유일한 항목을 등록하면 그 페이지가 비어 버린다. 빈 화면을 보여주는
     * 대신 한 페이지 앞으로 물러나 다시 읽는다.
     */
    if (page.candidates.length === 0 && candidatePage.value > 0) {
      candidatePage.value -= 1
      await loadCandidates()
    }
  } catch {
    candidatesError.value = true
    candidates.value = []
  } finally {
    candidatesLoading.value = false
  }
}

async function goToCandidatePage(delta: number) {
  const next = candidatePage.value + delta
  if (next < 0 || next >= candidateTotalPages.value) return
  candidatePage.value = next
  await loadCandidates()
}

onMounted(loadCandidates)

// ---- 기록 입력 ----

type LogKind = 'revenue' | 'externalCost' | 'operatorTime'

const MIN_AMOUNT_KRW = 1
const MAX_AMOUNT_KRW = 100_000_000
const MIN_MINUTES = 1
const MAX_MINUTES = 1440

const logOpen = ref(false)
const logPhase = ref<'input' | 'confirm'>('input')
const logKind = ref<LogKind>('revenue')
const logRunId = ref<number | null>(null)
const logValue = ref<string>('')
const logSubmitting = ref(false)

const isTimeLog = computed(() => logKind.value === 'operatorTime')

const logTitle = computed(() => {
  if (logKind.value === 'revenue') return t('admin.shortsPilot.log.revenue')
  if (logKind.value === 'externalCost') return t('admin.shortsPilot.log.externalCost')
  return t('admin.shortsPilot.log.operatorTime')
})

const logMin = computed(() => (isTimeLog.value ? MIN_MINUTES : MIN_AMOUNT_KRW))
const logMax = computed(() => (isTimeLog.value ? MAX_MINUTES : MAX_AMOUNT_KRW))

const parsedLogValue = computed(() => {
  const trimmed = logValue.value.trim()
  if (trimmed === '') return null
  const parsed = Number(trimmed)
  return Number.isInteger(parsed) ? parsed : null
})

/** 서버와 같은 경계를 화면에서도 막는다. 서버가 거절할 값을 확인 단계까지 보내지 않는다. */
const logValueValid = computed(() => {
  const parsed = parsedLogValue.value
  return parsed !== null && parsed >= logMin.value && parsed <= logMax.value
})

/** 확인 단계에서 다시 보여줄 서식 적용값. 자릿수 오타는 여기서 눈에 띈다. */
const logPreview = computed(() => {
  const parsed = parsedLogValue.value
  if (parsed === null) return ''
  return isTimeLog.value ? minuteFmt.value.format(parsed) : krw.value.format(parsed)
})

function openLog(runId: number, kind: LogKind) {
  logRunId.value = runId
  logKind.value = kind
  logValue.value = ''
  logPhase.value = 'input'
  logOpen.value = true
}

function goToConfirm() {
  if (!logValueValid.value) return
  logPhase.value = 'confirm'
}

async function submitLog() {
  const runId = logRunId.value
  const value = parsedLogValue.value
  if (runId === null || value === null || !logValueValid.value) return

  logSubmitting.value = true
  try {
    if (logKind.value === 'revenue') {
      await adminShortsPilotApi.logRevenue(runId, value)
    } else if (logKind.value === 'externalCost') {
      await adminShortsPilotApi.logExternalCost(runId, value)
    } else {
      await adminShortsPilotApi.logOperatorTime(runId, value)
    }
    notify.success(t('admin.shortsPilot.log.success'))
    logOpen.value = false
    await loadReport()
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('admin.shortsPilot.log.failed'))
    // 실패했으면 입력 화면으로 돌려보낸다. 확인 화면에 세워 두면 다시 눌러 중복 기록된다.
    logPhase.value = 'input'
  } finally {
    logSubmitting.value = false
  }
}
</script>

<template>
  <div>
    <PageHeader
      :title="$t('admin.shortsPilot.title')"
      :description="$t('admin.shortsPilot.description')"
    />

    <SectionCard :title="$t('admin.shortsPilot.enroll.title')" class="mb-4">
      <div class="flex flex-col gap-3 tablet:flex-row tablet:items-end">
        <div class="flex-1">
          <label
            for="pilot-enroll-run-id"
            class="mb-1 block text-body text-gray-700 dark:text-gray-300"
          >
            {{ $t('admin.shortsPilot.enroll.runIdLabel') }}
          </label>
          <!-- 위 금액 입력과 같은 이유로 type="number" 를 쓰지 않는다. -->
          <input
            id="pilot-enroll-run-id"
            v-model="enrollRunId"
            type="text"
            inputmode="numeric"
            autocomplete="off"
            class="input-field"
            :placeholder="$t('admin.shortsPilot.enroll.runIdPlaceholder')"
          />
        </div>
        <button
          type="button"
          class="btn-primary"
          :disabled="enrollDisabled"
          @click="submitEnroll"
        >
          {{ $t('admin.shortsPilot.enroll.submit') }}
        </button>
      </div>
    </SectionCard>

    <!--
      실행 ID 를 DB 나 고객에게 물어 알아내던 과정을 없앤다. 엉뚱한 실행을 코호트에
      넣으면 지표가 조용히 오염되고, 등록 이벤트는 append-only 라 되돌릴 수 없다.
    -->
    <SectionCard :title="$t('admin.shortsPilot.candidates.title')" class="mb-4">
      <p class="mb-3 text-body text-gray-500 dark:text-gray-400">
        {{ $t('admin.shortsPilot.candidates.description') }}
      </p>

      <LoadingSpinner v-if="candidatesLoading && candidates.length === 0" />

      <div
        v-else-if="candidatesError"
        class="flex flex-col items-start gap-3"
        role="alert"
      >
        <p class="text-body text-error-strong">
          {{ $t('admin.shortsPilot.candidates.loadFailed') }}
        </p>
        <button type="button" class="btn-secondary" @click="loadCandidates">
          {{ $t('action.retry') }}
        </button>
      </div>

      <EmptyState
        v-else-if="candidates.length === 0"
        :title="$t('admin.shortsPilot.candidates.empty')"
        :description="$t('admin.shortsPilot.candidates.emptyDescription')"
        variant="compact"
      />

      <template v-else>
        <div class="overflow-x-auto">
          <table class="w-full text-body">
            <thead>
              <tr class="border-b border-gray-200 text-left dark:border-gray-700">
                <th class="p-2">{{ $t('admin.shortsPilot.table.runId') }}</th>
                <th class="p-2">{{ $t('admin.shortsPilot.candidates.sourceVideoTitle') }}</th>
                <th class="p-2">{{ $t('admin.shortsPilot.table.createdAt') }}</th>
                <th class="p-2">{{ $t('admin.shortsPilot.candidates.status') }}</th>
                <th class="p-2">{{ $t('admin.shortsPilot.table.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="candidate in candidates"
                :key="candidate.runId"
                class="border-b border-gray-100 dark:border-gray-800"
              >
                <td class="p-2 font-medium">{{ candidate.runId }}</td>
                <!-- 제목이 없으면 지어내지 않고 "제목 없음"이라고 적는다. -->
                <td class="p-2">
                  {{ candidate.sourceVideoTitle ?? $t('admin.shortsPilot.candidates.untitled') }}
                </td>
                <td class="p-2">{{ showDate(candidate.createdAt) }}</td>
                <td class="p-2">{{ candidate.status }}</td>
                <td class="p-2">
                  <button
                    type="button"
                    class="btn-primary"
                    :disabled="enrolling"
                    @click="enroll(candidate.runId)"
                  >
                    {{ $t('admin.shortsPilot.enroll.submit') }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div
          v-if="candidateTotalPages > 1"
          class="mt-3 flex items-center justify-between gap-3"
        >
          <p class="text-caption text-gray-500 dark:text-gray-400">
            {{
              $t('admin.shortsPilot.candidates.pageInfo', {
                total: candidateTotal,
                from: candidateRange.from,
                to: candidateRange.to,
              })
            }}
          </p>
          <div class="flex gap-2">
            <button
              type="button"
              class="btn-secondary"
              :disabled="!hasPrevCandidatePage || candidatesLoading"
              @click="goToCandidatePage(-1)"
            >
              {{ $t('action.prev') }}
            </button>
            <button
              type="button"
              class="btn-secondary"
              :disabled="!hasNextCandidatePage || candidatesLoading"
              @click="goToCandidatePage(1)"
            >
              {{ $t('action.next') }}
            </button>
          </div>
        </div>
      </template>
    </SectionCard>

    <LoadingSpinner v-if="loading && !report" />

    <EmptyState
      v-else-if="report && !hasData"
      :title="$t('admin.shortsPilot.noData')"
      :description="$t('admin.shortsPilot.noDataDescription')"
    />

    <template v-else-if="hasData && summary">
      <OTabs v-model="activeTab" :tabs="tabs" />

      <!-- 요약 -->
      <div v-if="activeTab === 'summary'" class="mt-4 flex flex-col gap-4">
        <div class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
          <StatCard
            :label="$t('admin.shortsPilot.summary.enrolledRunCount')"
            :value="summary.enrolledRunCount"
          />
          <!--
            실행 수와 고객 수를 나란히 둔다. 둘이 갈라지면 아래 건당 수치가 반복 이용
            고객의 영향을 받을 수 있고, 그 가능성이 단위경제 해석을 바꾼다.
          -->
          <StatCard
            :label="$t('admin.shortsPilot.summary.enrolledCustomerCount')"
            :value="summary.enrolledCustomerCount"
          >
            <!--
              두 수가 같으면 표본이 고객당 1건씩이라 경고할 것이 없다. 늘 띄우면
              문구가 배경이 되어, 정작 갈라졌을 때도 눈에 들어오지 않는다.
            -->
            <p
              v-if="summary.enrolledRunCount !== summary.enrolledCustomerCount"
              class="mt-1 text-caption text-gray-500 dark:text-gray-400"
            >
              {{ $t('admin.shortsPilot.summary.customerCountHint') }}
            </p>
          </StatCard>
          <StatCard
            :label="$t('admin.shortsPilot.summary.repeatCustomerCount')"
            :value="summary.repeatCustomerCount"
          />
          <StatCard
            :label="$t('admin.shortsPilot.summary.startedRunCount')"
            :value="summary.startedRunCount"
          />
          <StatCard
            :label="$t('admin.shortsPilot.summary.deliveredRunCount')"
            :value="summary.deliveredRunCount"
          />
          <StatCard
            :label="$t('admin.shortsPilot.summary.totalStageReruns')"
            :value="summary.totalStageReruns"
          />
          <StatCard
            :label="$t('admin.shortsPilot.summary.totalRenderAttemptFailures')"
            :value="summary.totalRenderAttemptFailures"
          />
          <StatCard
            :label="$t('admin.shortsPilot.summary.totalOperatorMinutes')"
            :value="showMinutes(summary.totalOperatorMinutes)"
          />
          <StatCard
            :label="$t('admin.shortsPilot.summary.totalRevenue')"
            :value="showKrw(summary.totalOperatorReportedRevenueKrw)"
          />
          <StatCard
            :label="$t('admin.shortsPilot.summary.totalExternalCost')"
            :value="showKrw(summary.totalOperatorReportedExternalCostKrw)"
          />
          <StatCard
            :label="$t('admin.shortsPilot.summary.totalContribution')"
            :value="showKrw(summary.totalContributionExcludingExternalCostKrw)"
          >
            <!-- 몇 건으로 낸 합계인지 안 적으면 전체 등록 수 기준으로 오해한다. -->
            <p class="mt-1 text-caption text-gray-500 dark:text-gray-400">
              {{
                $t('admin.shortsPilot.summary.contributionBasis', {
                  count: summary.contributionObservedRunCount,
                })
              }}
            </p>
          </StatCard>
          <StatCard
            :label="$t('admin.shortsPilot.summary.leadTime')"
            :value="showLeadTime(summary.leadTime?.averageMs ?? null)"
          >
            <p v-if="summary.leadTime" class="mt-1 text-caption text-gray-500 dark:text-gray-400">
              {{
                $t('admin.shortsPilot.summary.leadTimeBasis', {
                  count: summary.leadTime.observedRunCount,
                })
              }}
            </p>
          </StatCard>
        </div>
      </div>

      <!-- 실행별 -->
      <div v-else class="mt-4 overflow-x-auto">
        <table class="w-full text-body">
          <thead>
            <tr class="border-b border-gray-200 text-left dark:border-gray-700">
              <th class="p-2">{{ $t('admin.shortsPilot.table.runId') }}</th>
              <th class="p-2">{{ $t('admin.shortsPilot.table.createdAt') }}</th>
              <th class="p-2">{{ $t('admin.shortsPilot.table.leadTime') }}</th>
              <th class="p-2">{{ $t('admin.shortsPilot.table.reruns') }}</th>
              <th class="p-2">{{ $t('admin.shortsPilot.table.renderFailures') }}</th>
              <th class="p-2">{{ $t('admin.shortsPilot.table.operatorMinutes') }}</th>
              <th class="p-2">{{ $t('admin.shortsPilot.table.revenue') }}</th>
              <th class="p-2">{{ $t('admin.shortsPilot.table.externalCost') }}</th>
              <th class="p-2">{{ $t('admin.shortsPilot.table.contribution') }}</th>
              <th class="p-2">{{ $t('admin.shortsPilot.table.contributionPerHour') }}</th>
              <th class="p-2">{{ $t('admin.shortsPilot.table.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in runs"
              :key="row.runId"
              class="border-b border-gray-100 dark:border-gray-800"
            >
              <td class="p-2 font-medium">
                <span class="flex flex-wrap items-center gap-2">
                  {{ row.runId }}
                  <!-- 같은 고객의 실행이 여럿 섞여 있음을 행에서 바로 보이게 한다. -->
                  <span
                    v-if="row.isRepeatCustomer"
                    class="rounded px-2 py-0.5 text-caption font-medium bg-warning-subtle text-warning-strong"
                  >
                    {{ $t('admin.shortsPilot.table.repeatCustomer') }}
                  </span>
                </span>
              </td>
              <td class="p-2">{{ showDate(row.createdAt) }}</td>
              <td class="p-2">{{ showLeadTime(row.leadTimeMs) }}</td>
              <td class="p-2">{{ showCount(row.stageRerunCount) }}</td>
              <td class="p-2">{{ showCount(row.renderAttemptFailureCount) }}</td>
              <td class="p-2">{{ showMinutes(row.operatorMinutes) }}</td>
              <td class="p-2">{{ showKrw(row.operatorReportedRevenueKrw) }}</td>
              <td class="p-2">{{ showKrw(row.operatorReportedExternalCostKrw) }}</td>
              <td class="p-2">{{ showKrw(row.contributionExcludingExternalCostKrw) }}</td>
              <td class="p-2">{{ showKrw(row.contributionPerOperatorHourKrw) }}</td>
              <td class="p-2">
                <div class="flex flex-wrap gap-2">
                  <button
                    type="button"
                    class="btn-secondary"
                    @click="openLog(row.runId, 'revenue')"
                  >
                    {{ $t('admin.shortsPilot.log.revenue') }}
                  </button>
                  <button
                    type="button"
                    class="btn-secondary"
                    @click="openLog(row.runId, 'externalCost')"
                  >
                    {{ $t('admin.shortsPilot.log.externalCost') }}
                  </button>
                  <button
                    type="button"
                    class="btn-secondary"
                    @click="openLog(row.runId, 'operatorTime')"
                  >
                    {{ $t('admin.shortsPilot.log.operatorTime') }}
                  </button>
                  <!-- 합계만으로는 어느 행이 잘못됐는지 알 수 없다. -->
                  <button type="button" class="btn-secondary" @click="openEntries(row.runId)">
                    {{ $t('admin.shortsPilot.table.viewEntries') }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <!--
      한계는 데이터가 있든 없든 늘 보인다. 이 목록이 없으면 운영자가 위 숫자를
      "측정된 원가·매출"로 읽는다. 실제로는 손으로 적은 값이다.
    -->
    <SectionCard :title="$t('admin.shortsPilot.limitations.title')" class="mt-4">
      <ul class="list-disc space-y-1 pl-5 text-body text-gray-600 dark:text-gray-400">
        <li v-for="code in report?.limitations ?? []" :key="code">
          {{ $t(`admin.shortsPilot.limitations.${code}`) }}
        </li>
      </ul>
    </SectionCard>

    <BaseModal
      v-model="entriesOpen"
      :title="$t('admin.shortsPilot.entries.title', { runId: entriesRunId ?? '' })"
      max-width="xl"
    >
      <LoadingSpinner v-if="entriesLoading && entries.length === 0" />

      <div v-else-if="entriesError" class="flex flex-col items-start gap-3" role="alert">
        <p class="text-body text-error-strong">
          {{ $t('admin.shortsPilot.entries.loadFailed') }}
        </p>
        <button type="button" class="btn-secondary" @click="loadEntries">
          {{ $t('action.retry') }}
        </button>
      </div>

      <EmptyState
        v-else-if="entries.length === 0"
        :title="$t('admin.shortsPilot.entries.empty')"
        :description="$t('admin.shortsPilot.entries.emptyDescription')"
        variant="compact"
      />

      <table v-else class="w-full text-body">
        <thead>
          <tr class="border-b border-gray-200 text-left dark:border-gray-700">
            <th class="p-2">{{ $t('admin.shortsPilot.entries.type') }}</th>
            <th class="p-2">{{ $t('admin.shortsPilot.entries.value') }}</th>
            <th class="p-2">{{ $t('admin.shortsPilot.entries.recordedAt') }}</th>
            <th class="p-2">{{ $t('admin.shortsPilot.table.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <!--
            취소된 기록도 지우지 않고 남긴다. 무엇을 잘못 적었었는지가 사라지면
            감사가 불가능하다. 합계에서만 빠진다.
          -->
          <tr
            v-for="entry in entries"
            :key="entry.entryId"
            class="border-b border-gray-100 dark:border-gray-800"
            :class="entry.isReversed ? 'text-gray-400 line-through dark:text-gray-500' : ''"
          >
            <td class="p-2">{{ entryTypeLabel(entry.type) }}</td>
            <td class="p-2">{{ entryValue(entry) }}</td>
            <td class="p-2">{{ showDate(entry.recordedAt) }}</td>
            <td class="p-2">
              <span
                v-if="entry.isReversed"
                class="rounded bg-warning-subtle px-2 py-0.5 text-caption font-medium text-warning-strong no-underline"
              >
                {{ $t('admin.shortsPilot.entries.reversed') }}
              </span>
              <button
                v-else
                type="button"
                class="btn-danger"
                :disabled="reversing"
                @click="askReverse(entry.entryId)"
              >
                {{ $t('admin.shortsPilot.entries.reverse') }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <template #footer>
        <button type="button" class="btn-secondary" @click="entriesOpen = false">
          {{ $t('action.close') }}
        </button>
      </template>
    </BaseModal>

    <ConfirmModal
      v-model="reverseConfirmOpen"
      :title="$t('admin.shortsPilot.entries.confirmTitle')"
      :message="$t('admin.shortsPilot.entries.confirmMessage')"
      :confirm-text="$t('admin.shortsPilot.entries.reverse')"
      :cancel-text="$t('action.cancel')"
      danger
      @confirm="confirmReverse"
    />

    <BaseModal v-model="logOpen" :title="logTitle">
      <div v-if="logPhase === 'input'" class="flex flex-col gap-3">
        <label for="pilot-log-value" class="text-body text-gray-700 dark:text-gray-300">
          {{
            isTimeLog
              ? $t('admin.shortsPilot.log.minutesLabel')
              : $t('admin.shortsPilot.log.amountLabel')
          }}
        </label>
        <!--
          type="number" 를 쓰지 않는다. v-model 이 값을 Number 로 바꿔 버려 문자열 검사가
          깨지고, 브라우저가 잘못된 입력을 조용히 빈 값으로 만들어 "1.5 를 넣었는데
          아무 일도 안 일어난다"가 된다. 경계 검사는 아래 logValueValid 가 명시적으로 한다.
        -->
        <input
          id="pilot-log-value"
          v-model="logValue"
          type="text"
          inputmode="numeric"
          autocomplete="off"
          class="input-field"
          :aria-describedby="`pilot-log-range`"
        />
        <p id="pilot-log-range" class="text-caption text-gray-500 dark:text-gray-400">
          {{ logMin }} ~ {{ logMax }}
        </p>
        <p class="text-caption text-warning-strong">
          {{ $t('admin.shortsPilot.log.irreversibleWarning') }}
        </p>
      </div>

      <div v-else class="flex flex-col gap-3">
        <p class="text-body text-gray-700 dark:text-gray-300">
          {{ $t('admin.shortsPilot.log.confirmBody', { runId: logRunId }) }}
        </p>
        <p class="text-h1 font-bold text-gray-900 dark:text-gray-100">{{ logPreview }}</p>
        <p class="text-caption text-warning-strong">
          {{ $t('admin.shortsPilot.log.irreversibleWarning') }}
        </p>
      </div>

      <template #footer>
        <button type="button" class="btn-secondary" @click="logOpen = false">
          {{ $t('action.cancel') }}
        </button>
        <button
          v-if="logPhase === 'input'"
          type="button"
          class="btn-primary"
          :disabled="!logValueValid"
          @click="goToConfirm"
        >
          {{ $t('action.next') }}
        </button>
        <button
          v-else
          type="button"
          class="btn-primary"
          :disabled="logSubmitting"
          @click="submitLog"
        >
          {{ $t('admin.shortsPilot.log.submit') }}
        </button>
      </template>
    </BaseModal>
  </div>
</template>

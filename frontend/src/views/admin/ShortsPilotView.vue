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
import type { ShortsPilotReport, ShortsPilotRunRow } from '@/types/adminShortsPilot'

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
  enrolling.value = true
  try {
    const result = await adminShortsPilotApi.enroll(Number(enrollRunId.value))
    // 이미 등록된 실행도 성공이지만 같은 문구로 알리면 운영자가 두 번 등록했는지 모른다.
    notify.success(
      result.alreadyEnrolled
        ? t('admin.shortsPilot.enroll.alreadyEnrolled')
        : t('admin.shortsPilot.enroll.success'),
    )
    enrollRunId.value = ''
    await loadReport()
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('admin.shortsPilot.enroll.failed'))
  } finally {
    enrolling.value = false
  }
}

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
              <td class="p-2 font-medium">{{ row.runId }}</td>
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

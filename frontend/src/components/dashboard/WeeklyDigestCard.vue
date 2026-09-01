<template>
  <SectionCard :title="t('redesign.today.weeklyDigest.title')" :meta="digest?.weekRange">
    <template #action>
      <button
        v-if="digest"
        type="button"
        class="rounded-md px-2 py-1 text-[11px] font-semibold text-accent transition-colors hover:bg-surface-raised focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
        @click="showDetail = true"
      >
        {{ t('redesign.today.weeklyDigest.viewAll') }}
      </button>
    </template>

    <div v-if="loading" class="space-y-3 px-[15px] py-8" role="status" :aria-label="t('action.loading')">
      <div class="h-3 w-3/4 animate-pulse rounded bg-surface-raised" />
      <div class="h-3 w-1/2 animate-pulse rounded bg-surface-raised" />
      <div class="h-3 w-2/3 animate-pulse rounded bg-surface-raised" />
    </div>

    <div v-else-if="loadError" class="px-[15px] py-8 text-center" role="alert">
      <p class="text-[12px] text-bad">{{ t('redesign.today.weeklyDigest.loadFailed') }}</p>
      <button type="button" class="mt-3 btn-secondary !text-[11px]" @click="loadDigest">
        {{ t('action.retry') }}
      </button>
    </div>

    <div v-else-if="!digest" class="px-[15px] py-8">
      <div class="flex items-start gap-3">
        <div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-accent-dim">
          <SparklesIcon class="h-4 w-4 text-accent" />
        </div>
        <div class="min-w-0">
          <p class="text-[12.5px] font-semibold text-content">
            {{ showUpgrade ? t('redesign.today.weeklyDigest.upgradeTitle') : t('redesign.today.weeklyDigest.waitingTitle') }}
          </p>
          <p class="mt-1 text-[11.5px] leading-5 text-content-tertiary">
            {{ showUpgrade ? t('redesign.today.weeklyDigest.upgradeDescription') : t('redesign.today.weeklyDigest.waitingDescription') }}
          </p>
          <router-link
            v-if="showUpgrade"
            to="/subscription"
            data-testid="weekly-digest-upgrade"
            class="mt-3 inline-flex btn-primary !text-[11px]"
          >
            {{ t('redesign.today.weeklyDigest.upgradeAction') }}
          </router-link>
        </div>
      </div>
    </div>

    <div v-else class="space-y-3 px-[15px] py-3.5">
      <p class="text-[12.5px] leading-5 text-content">{{ truncatedSummary }}</p>

      <div v-if="digest.topVideos.length > 0" class="rounded-lg bg-info-subtle px-3 py-2.5">
        <p class="text-[10px] font-bold uppercase tracking-[0.08em] text-info-strong">
          {{ t('redesign.today.weeklyDigest.topVideos') }}
        </p>
        <p class="mt-1 text-[11.5px] leading-5 text-info-strong">{{ digest.topVideos[0] }}</p>
      </div>

      <div v-if="digest.anomalies.length > 0" class="rounded-lg bg-warning-subtle px-3 py-2.5">
        <p class="text-[10px] font-bold uppercase tracking-[0.08em] text-warning-strong">
          {{ t('redesign.today.weeklyDigest.anomalies') }}
        </p>
        <p class="mt-1 text-[11.5px] leading-5 text-warning-strong">{{ digest.anomalies[0] }}</p>
      </div>

      <div v-if="digest.actionItems.length > 0">
        <p class="mb-2 text-[10px] font-bold uppercase tracking-[0.08em] text-content-tertiary">
          {{ t('redesign.today.weeklyDigest.actionItems') }}
        </p>
        <div class="space-y-1.5">
          <label v-for="(item, idx) in digest.actionItems.slice(0, 3)" :key="idx" class="flex cursor-pointer items-start gap-2">
            <input
              type="checkbox"
              class="mt-0.5 h-3.5 w-3.5 rounded border-line-control text-accent focus:ring-accent"
              :checked="checkedItems.has(idx)"
              @change="toggleItem(idx)"
            />
            <span class="text-[11.5px] leading-5 text-content-secondary" :class="{ 'line-through text-content-quaternary': checkedItems.has(idx) }">
              {{ item }}
            </span>
          </label>
        </div>
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="showDetail && digest"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
        role="dialog"
        aria-modal="true"
        :aria-label="t('redesign.today.weeklyDigest.title')"
        @click.self="showDetail = false"
      >
        <div class="relative max-h-[80vh] w-full max-w-lg overflow-y-auto rounded-xl border border-line bg-surface-card p-5 shadow-2xl">
          <button
            type="button"
            class="absolute right-3 top-3 flex h-11 w-11 items-center justify-center rounded-md text-content-tertiary transition-colors hover:bg-surface-raised hover:text-content focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
            :aria-label="t('action.close')"
            @click="showDetail = false"
          >
            <XMarkIcon class="h-5 w-5" />
          </button>

          <div class="pr-10">
            <h2 class="text-[15px] font-bold text-content">{{ t('redesign.today.weeklyDigest.title') }}</h2>
            <p class="mt-1 text-[11px] text-content-tertiary">{{ digest.weekRange }}</p>
          </div>

          <div class="mt-5 space-y-4">
            <div>
              <h3 class="text-[12.5px] font-semibold text-content">{{ t('redesign.today.weeklyDigest.summary') }}</h3>
              <p class="mt-1 text-[12px] leading-5 text-content-secondary">{{ digest.summary }}</p>
            </div>
            <div v-if="digest.topVideos.length > 0">
              <h3 class="text-[12.5px] font-semibold text-content">{{ t('redesign.today.weeklyDigest.topVideos') }}</h3>
              <ul class="mt-1 space-y-1">
                <li v-for="(video, idx) in digest.topVideos" :key="idx" class="flex items-start gap-2 text-[12px] leading-5 text-content-secondary">
                  <span class="shrink-0 font-mono text-info-strong">{{ idx + 1 }}.</span>{{ video }}
                </li>
              </ul>
            </div>
            <div v-if="digest.anomalies.length > 0">
              <h3 class="text-[12.5px] font-semibold text-content">{{ t('redesign.today.weeklyDigest.anomalies') }}</h3>
              <ul class="mt-1 space-y-1">
                <li v-for="(anomaly, idx) in digest.anomalies" :key="idx" class="text-[12px] leading-5 text-content-secondary">{{ anomaly }}</li>
              </ul>
            </div>
            <div v-if="digest.actionItems.length > 0">
              <h3 class="text-[12.5px] font-semibold text-content">{{ t('redesign.today.weeklyDigest.actionItems') }}</h3>
              <ul class="mt-1 space-y-1">
                <li v-for="(item, idx) in digest.actionItems" :key="idx" class="flex items-start gap-2 text-[12px] leading-5 text-content-secondary">
                  <span class="shrink-0 font-mono text-content-tertiary">{{ idx + 1 }}.</span>{{ item }}
                </li>
              </ul>
            </div>
          </div>

          <p class="mt-5 text-[10.5px] text-content-quaternary">
            {{ t('redesign.today.weeklyDigest.generatedAt', { date: dayjs(digest.generatedAt).format('YYYY.MM.DD HH:mm') }) }}
          </p>
        </div>
      </div>
    </Teleport>
  </SectionCard>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { SparklesIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import dayjs from 'dayjs'
import SectionCard from '@/components/redesign/SectionCard.vue'
import { aiApi } from '@/api/ai'
import { useAuthStore } from '@/stores/auth'
import type { WeeklyDigestResponse } from '@/types/ai'
import { useLocale } from '@/composables/useLocale'

const { t } = useLocale()
const authStore = useAuthStore()
const digest = ref<WeeklyDigestResponse | null>(null)
const loading = ref(true)
const loadError = ref(false)
const accessDenied = ref(false)
const showDetail = ref(false)
const checkedItems = ref(new Set<number>())

const isPremiumPlan = computed(() => {
  const plan = authStore.user?.planType
  return plan === 'PRO' || plan === 'BUSINESS'
})
const showUpgrade = computed(() => accessDenied.value || !isPremiumPlan.value)

const truncatedSummary = computed(() => {
  if (!digest.value) return ''
  return digest.value.summary.length > 160 ? `${digest.value.summary.slice(0, 160)}…` : digest.value.summary
})

function isExpectedUnavailable(error: unknown): boolean {
  const status = responseStatus(error)
  return status === 403 || status === 404
}

function responseStatus(error: unknown): number | undefined {
  return (error as { response?: { status?: number }; statusCode?: number })?.response?.status
    ?? (error as { statusCode?: number })?.statusCode
}

async function loadDigest() {
  loading.value = true
  loadError.value = false
  accessDenied.value = false
  try {
    digest.value = await aiApi.getLatestWeeklyDigest()
  } catch (error) {
    // 403(유료 플랜 아님)·404(아직 생성 전)는 빈 상태로 안내하고, 그 외 오류는 재시도한다.
    if (isExpectedUnavailable(error)) {
      accessDenied.value = responseStatus(error) === 403
      digest.value = null
    }
    else loadError.value = true
  } finally {
    loading.value = false
  }
}

function toggleItem(idx: number) {
  const next = new Set(checkedItems.value)
  if (next.has(idx)) next.delete(idx)
  else next.add(idx)
  checkedItems.value = next
}

onMounted(loadDigest)
</script>

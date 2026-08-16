<template>
  <!-- 테마 스토어가 documentElement 에 적용한 라이트/다크 토큰을 그대로 사용한다. -->
  <div class="redesign-app flex h-[100dvh] min-w-0 overflow-hidden bg-surface-base">
    <a href="#main-content" class="skip-link">
      {{ t('a11y.skipToContent') }}
    </a>
    <RedesignRail class="hidden tablet:flex" />

    <div class="flex min-w-0 flex-1 flex-col overflow-hidden bg-surface">
      <RedesignTopBar
        :title="title"
        :subtitle="subtitle"
        @open-import="openImport"
      />
      <div
        v-if="capabilityError"
        class="mx-4 mt-3 flex flex-wrap items-center gap-2 rounded-lg border border-warning-subtle bg-warning-subtle px-3 py-2 text-[11px] text-warning-strong tablet:mx-6"
        role="alert"
      >
        <span class="min-w-0 flex-1">{{ t('redesign.shell.capabilitiesFailed') }}</span>
        <button
          type="button"
          class="shrink-0 rounded-md border border-warning-strong px-2 py-1 font-semibold hover:bg-warning-strong hover:text-surface-base"
          @click="retryCapabilities"
        >
          {{ t('action.retry') }}
        </button>
      </div>
      <main
        id="main-content"
        tabindex="-1"
        aria-labelledby="page-title"
        class="min-h-0 min-w-0 flex-1 overflow-y-auto scrollbar-dark focus:outline-none"
        :class="isRedesignScreen
          ? 'pb-[calc(4.5rem+env(safe-area-inset-bottom))] tablet:pb-0'
          : 'px-4 pb-20 tablet:px-6 tablet:pb-8'"
      >
        <router-view v-slot="{ Component }">
          <component :is="Component" />
        </router-view>
      </main>
    </div>

    <MobileBottomNav class="tablet:hidden" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLocale } from '@/composables/useLocale'
import MobileBottomNav from '@/components/layout/MobileBottomNav.vue'
import RedesignRail from '@/components/layout/RedesignRail.vue'
import RedesignTopBar from '@/components/layout/RedesignTopBar.vue'
import { useRedesignShellStore } from '@/stores/redesignShell'
import { useNavigation } from '@/composables/useNavigation'

/**
 * 리디자인 셸 — 좌측 고정 레일 + 우측 본문 2열.
 *
 * 모든 인증 화면이 동일한 리디자인 셸을 사용한다. 레거시 URL은 라우터에서 호환하고,
 * 화면 제목이 아직 전용 키를 갖지 않은 경우 라우트 메타의 기존 제목을 사용한다.
 */
const route = useRoute()
const router = useRouter()
const { t } = useLocale()
const shell = useRedesignShellStore()
const { capabilityError, retryCapabilities } = useNavigation()

function openImport() {
  void router.push({ path: '/compose', query: { source: 'url' } })
}

/** 전용 리디자인 화면은 자체 여백을 갖고, 기존 화면은 셸에서 동일한 본문 여백을 받는다. */
const isRedesignScreen = computed(() => String(route.name ?? '').startsWith('redesign-'))

/** 라우트 이름 → i18n 키. 부제는 실제 카운트를 끼워 넣는다. */
const title = computed(() => {
  const key = String(route.name ?? '')
  const translated = t(`redesign.screen.${key}.title`)
  return translated === `redesign.screen.${key}.title` ? String(route.meta.breadcrumb ?? '') : translated
})

const subtitle = computed(() => {
  const key = String(route.name ?? '')
  const subtitleKey = `redesign.screen.${key}.subtitle`
  const translated = t(subtitleKey, {
    queue: shell.todayQueueCount,
    unanswered: shell.unansweredCount,
    scheduled: shell.scheduledCount,
  })
  return translated === subtitleKey ? '' : translated
})
</script>

<style>
/*
 * 기존 화면을 새 셸 안에 넣을 때도 표면·텍스트·구분선 체계가 분리되지 않도록
 * 레거시 Tailwind 별칭을 리디자인 토큰으로 연결한다. 기능과 데이터 컴포넌트는
 * 그대로 유지하고, 화면 전체에 공통인 시각 언어만 이 어댑터에서 통일한다.
 */
.redesign-app .bg-white,
.redesign-app [class~='dark:bg-gray-900'],
.redesign-app [class~='dark:bg-gray-800'] {
  background-color: var(--surface-elevated) !important;
}

.redesign-app .bg-gray-50,
.redesign-app .bg-gray-100,
.redesign-app [class~='dark:bg-gray-700'] {
  background-color: var(--surface-tertiary) !important;
}

.redesign-app .text-gray-900,
.redesign-app .text-gray-800,
.redesign-app [class~='dark:text-gray-100'],
.redesign-app [class~='dark:text-gray-200'] {
  color: var(--text-primary) !important;
}

.redesign-app .text-gray-700,
.redesign-app .text-gray-600,
.redesign-app [class~='dark:text-gray-300'],
.redesign-app [class~='dark:text-gray-400'] {
  color: var(--text-secondary) !important;
}

.redesign-app .text-gray-500,
.redesign-app .text-gray-400,
.redesign-app [class~='dark:text-gray-500'] {
  color: var(--text-tertiary) !important;
}

.redesign-app .border-gray-100,
.redesign-app .border-gray-200,
.redesign-app .border-gray-300,
.redesign-app [class~='dark:border-gray-600'],
.redesign-app [class~='dark:border-gray-700'] {
  border-color: var(--border-default) !important;
}

.redesign-app [class~='hover:bg-gray-50']:hover,
.redesign-app [class~='dark:hover:bg-gray-700']:hover,
.redesign-app [class~='dark:hover:bg-gray-800']:hover {
  background-color: var(--surface-tertiary) !important;
}
</style>

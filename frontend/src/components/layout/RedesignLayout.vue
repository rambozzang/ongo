<template>
  <!--
    리디자인 시안은 다크 전용이다. 라이트 모드에서 열면 색이 어긋나므로 이 셸 안에서만
    dark 를 강제한다. documentElement 를 건드리면 테마 스토어의 watcher 와 충돌하므로
    클래스를 여기에 둔다 — CSS 변수가 이 요소와 하위에만 적용되고 레거시 화면은 영향이 없다.
  -->
  <div class="redesign-app dark flex h-screen overflow-hidden bg-surface-base">
    <RedesignRail class="hidden tablet:flex" />

    <div class="flex min-w-0 flex-1 flex-col overflow-hidden bg-surface">
      <RedesignTopBar :title="title" :subtitle="subtitle" />
      <main
        id="main-content"
        class="min-h-0 flex-1 overflow-y-auto scrollbar-dark"
        :class="isRedesignScreen ? '' : 'px-4 pb-20 tablet:px-6 tablet:pb-8'"
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
import { useRoute } from 'vue-router'
import { useLocale } from '@/composables/useLocale'
import MobileBottomNav from '@/components/layout/MobileBottomNav.vue'
import RedesignRail from '@/components/layout/RedesignRail.vue'
import RedesignTopBar from '@/components/layout/RedesignTopBar.vue'
import { useRedesignShellStore } from '@/stores/redesignShell'

/**
 * 리디자인 셸 — 좌측 고정 레일 + 우측 본문 2열.
 *
 * 모든 인증 화면이 동일한 리디자인 셸을 사용한다. 레거시 URL은 라우터에서 호환하고,
 * 화면 제목이 아직 전용 키를 갖지 않은 경우 라우트 메타의 기존 제목을 사용한다.
 */
const route = useRoute()
const { t } = useLocale()
const shell = useRedesignShellStore()

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

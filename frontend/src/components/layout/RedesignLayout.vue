<template>
  <!--
    리디자인 시안은 다크 전용이다. 라이트 모드에서 열면 색이 어긋나므로 이 셸 안에서만
    dark 를 강제한다. documentElement 를 건드리면 테마 스토어의 watcher 와 충돌하므로
    클래스를 여기에 둔다 — CSS 변수가 이 요소와 하위에만 적용되고 레거시 화면은 영향이 없다.
  -->
  <div class="dark flex h-screen overflow-hidden bg-surface-base">
    <RedesignRail class="hidden tablet:flex" />

    <div class="flex min-w-0 flex-1 flex-col overflow-hidden bg-surface">
      <RedesignTopBar :title="title" :subtitle="subtitle" />
      <main id="main-content" class="min-h-0 flex-1 overflow-y-auto scrollbar-dark">
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
 * 기존 AppLayout 은 레거시 화면이 계속 쓰므로 건드리지 않고 별도 레이아웃으로 얹는다.
 * 화면 제목·부제는 라우트 이름으로 결정한다(핸드오프의 제목/부제 매핑 표).
 */
const route = useRoute()
const { t } = useLocale()
const shell = useRedesignShellStore()

/** 라우트 이름 → i18n 키. 부제는 실제 카운트를 끼워 넣는다. */
const title = computed(() => {
  const key = String(route.name ?? '')
  return t(`redesign.screen.${key}.title`)
})

const subtitle = computed(() => {
  const key = String(route.name ?? '')
  return t(`redesign.screen.${key}.subtitle`, {
    queue: shell.todayQueueCount,
    unanswered: shell.unansweredCount,
    scheduled: shell.scheduledCount,
  })
})
</script>

<template>
  <div class="flex h-screen overflow-hidden bg-surface-base">
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

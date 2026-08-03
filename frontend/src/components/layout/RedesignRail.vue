<template>
  <aside
    class="flex w-[216px] flex-none flex-col border-r border-line bg-surface-rail px-3 py-4"
    role="navigation"
    :aria-label="t('nav.mainNavigation')"
  >
    <!-- 워드마크 -->
    <router-link to="/today" class="mb-4 flex items-center gap-2 px-1" @click="emit('navigate')">
      <span class="h-[22px] w-[22px] rounded-md bg-accent" aria-hidden="true" />
      <span class="text-[16px] font-bold text-content">onGo</span>
    </router-link>

    <!-- 내비 -->
    <nav class="flex-1 space-y-0.5 overflow-y-auto">
      <router-link
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        class="flex items-center gap-2.5 rounded-lg px-[9px] py-2 text-[12.5px] transition-colors duration-150"
        :class="
          isActive(item.to)
            ? 'bg-accent-dim font-bold text-content shadow-[inset_0_0_0_1px_#2e3250]'
            : 'font-medium text-content-secondary hover:bg-surface-rail-raised hover:text-content'
        "
        :aria-current="isActive(item.to) ? 'page' : undefined"
        @click="emit('navigate')"
      >
        <component :is="item.icon" class="h-4 w-4 shrink-0" aria-hidden="true" />
        <span class="truncate">{{ item.label }}</span>
        <span
          v-if="item.badge"
          class="ml-auto shrink-0 font-mono text-[10px]"
          :class="item.badgeTone === 'bad' ? 'text-bad' : 'text-content-tertiary'"
        >
          {{ item.badge }}
        </span>
      </router-link>
    </nav>

    <!-- 이번 달 업로드 진행 -->
    <div class="mt-4 px-1">
      <div class="flex items-center justify-between text-[11px] text-content-tertiary">
        <span>{{ t('redesign.rail.monthlyUploads') }}</span>
        <span class="font-mono text-content">{{ quota.used }}/{{ quota.limit }}</span>
      </div>
      <div class="mt-1.5 h-1 overflow-hidden rounded-full bg-line">
        <div class="h-full rounded-full bg-accent transition-[width] duration-300" :style="{ width: quotaPercent }" />
      </div>
    </div>

    <!-- 계정 -->
    <router-link
      to="/settings-v2"
      class="mt-3.5 flex items-center gap-2 rounded-lg px-1 py-2 transition-colors hover:bg-surface-rail-raised"
      @click="emit('navigate')"
    >
      <span
        class="flex h-[26px] w-[26px] shrink-0 items-center justify-center rounded-full text-[11px] font-semibold text-content"
        style="background: #2b2f47"
      >
        {{ initial }}
      </span>
      <span class="min-w-0 flex-1">
        <span class="block truncate text-[12px] font-semibold text-content">{{ displayName }}</span>
        <span class="block truncate text-[10px] text-content-tertiary">{{ email }}</span>
      </span>
    </router-link>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  CalendarDaysIcon,
  ChartBarIcon,
  Cog6ToothIcon,
  InboxIcon,
  PlusIcon,
  SignalIcon,
  SunIcon,
} from '@heroicons/vue/24/outline'
import { useLocale } from '@/composables/useLocale'
import { useAuthStore } from '@/stores/auth'
import { useRedesignShellStore } from '@/stores/redesignShell'

/**
 * 좌측 고정 레일 (216px) — 2026-08 리디자인.
 *
 * 기능별 메뉴가 아니라 하루 작업 순서(오늘 → 만들기 → 응답 → 확인)로 배열한다.
 * 설정성 화면은 하단으로 분리한다.
 */
const emit = defineEmits<{ navigate: [] }>()

const route = useRoute()
const { t } = useLocale()
const authStore = useAuthStore()
const shell = useRedesignShellStore()

const items = computed(() => [
  { to: '/today', label: t('redesign.nav.today'), icon: SunIcon, badge: shell.badges.today },
  { to: '/compose', label: t('redesign.nav.compose'), icon: PlusIcon, badge: '' },
  { to: '/inbox-v2', label: t('redesign.nav.inbox'), icon: InboxIcon, badge: shell.badges.inbox },
  { to: '/calendar-v2', label: t('redesign.nav.calendar'), icon: CalendarDaysIcon, badge: shell.badges.calendar },
  { to: '/performance', label: t('redesign.nav.performance'), icon: ChartBarIcon, badge: '' },
  {
    to: '/channels-v2',
    label: t('redesign.nav.channels'),
    icon: SignalIcon,
    badge: shell.badges.channels,
    badgeTone: shell.badges.channels ? ('bad' as const) : undefined,
  },
  { to: '/settings-v2', label: t('redesign.nav.settings'), icon: Cog6ToothIcon, badge: '' },
])

// 하위 경로까지 활성으로 본다 (/compose/123 도 '새 업로드' 로 표시)
const isActive = (to: string) => route.path === to || route.path.startsWith(`${to}/`)

const quota = computed(() => shell.uploadQuota)
const quotaPercent = computed(() => {
  if (!quota.value.limit) return '0%'
  return `${Math.min(100, Math.round((quota.value.used / quota.value.limit) * 100))}%`
})

const displayName = computed(() => authStore.user?.name || authStore.user?.email || '—')
const email = computed(() => authStore.user?.email || '')
const initial = computed(() => displayName.value.charAt(0).toUpperCase())
</script>

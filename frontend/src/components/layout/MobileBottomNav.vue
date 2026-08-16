<template>
  <nav
    :aria-label="t('nav.mainNavigation')"
    class="fixed bottom-0 left-0 right-0 z-30 border-t border-line bg-surface pb-[env(safe-area-inset-bottom)]"
  >
    <div class="flex items-center justify-around">
      <router-link
        v-for="item in navItems"
        :key="item.to"
        :to="item.to"
        :aria-label="item.label"
        class="flex min-h-[56px] flex-1 flex-col items-center justify-center gap-1 py-2 text-[10.5px] text-content-tertiary transition-colors"
        active-class="!font-bold !text-content"
      >
        <template v-if="item.isCenter">
          <div class="flex h-10 w-10 -translate-y-1 items-center justify-center rounded-xl bg-accent text-accent-on">
            <component :is="item.icon" class="h-5 w-5" />
          </div>
        </template>
        <template v-else>
          <component :is="item.icon" class="h-[22px] w-[22px]" />
          <span>{{ item.label }}</span>
        </template>
      </router-link>

      <!-- 전체 메뉴 시트 열기 (설정 페이지로 직행하지 않는다) -->
      <button
        type="button"
        class="flex min-h-[56px] flex-1 flex-col items-center justify-center gap-1 py-2 text-[10.5px] transition-colors"
        :class="menuOpen ? 'font-bold text-content' : 'text-content-tertiary'"
        :aria-label="t('nav.more')"
        aria-haspopup="dialog"
        :aria-expanded="menuOpen"
        @click="menuOpen = true"
      >
        <EllipsisHorizontalIcon class="h-6 w-6" aria-hidden="true" />
        <span>{{ t('nav.more') }}</span>
      </button>
    </div>

    <MobileMenuSheet v-model="menuOpen" />
  </nav>
</template>

<script setup lang="ts">
import { computed, ref, type Component } from 'vue'
import {
  EllipsisHorizontalIcon,
  InboxIcon,
  PlusIcon,
  SignalIcon,
  SunIcon,
} from '@heroicons/vue/24/outline'
import { useLocale } from '@/composables/useLocale'
import { useNavigation } from '@/composables/useNavigation'
import MobileMenuSheet from '@/components/layout/MobileMenuSheet.vue'

interface NavItem {
  to: string
  label: string
  icon: Component
  isCenter?: boolean
}

const { t } = useLocale()
const { allNavItems } = useNavigation()
const menuOpen = ref(false)

/*
 * 리디자인 IA — 하루 작업 순서(오늘 → 응답 → 만들기 → 확인).
 * 성과·설정은 우측 전체 메뉴에서 진입한다(핸드오프 8절).
 */
const baseItems = computed<NavItem[]>(() => [
  { to: '/today', label: t('redesign.nav.today'), icon: SunIcon },
  { to: '/inbox-v2', label: t('redesign.nav.inbox'), icon: InboxIcon },
  { to: '/compose', label: t('redesign.nav.compose'), icon: PlusIcon, isCenter: true },
  { to: '/channels-v2', label: t('redesign.nav.channels'), icon: SignalIcon },
])

/*
 * 하단 바도 capability 게이팅을 따른다.
 *
 * useNavigation 은 서버가 활성 기능 목록을 주지 못하면 **fail-closed** 한다("비활성/WIP
 * 기능을 장애 중에 잘못 열 수 있다"). 그런데 이 바만 정적 목록을 그대로 렌더해서,
 * 레일과 전체 메뉴가 닫혀 있는 동안에도 모바일 주 내비게이션은 열려 있었다.
 * 모바일에서 이게 사실상 유일한 상시 내비라 영향이 가장 큰 자리다.
 *
 * 규칙을 여기서 다시 쓰지 않는다. 이미 필터링된 allNavItems 에 있는지만 확인해
 * 판정 로직이 갈라지지 않게 한다.
 */
const enabledPaths = computed(() => new Set(allNavItems.value.map((item) => item.to)))
const navItems = computed<NavItem[]>(() =>
  baseItems.value.filter((item) => enabledPaths.value.has(item.to)),
)
</script>

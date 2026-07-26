<template>
  <nav aria-label="모바일 하단 네비게이션" class="fixed bottom-0 left-0 right-0 z-30 border-t bg-white/95 dark:bg-gray-900/95" style="border-color: var(--border-default)">
    <div class="flex items-center justify-around">
      <router-link
        v-for="item in navItems"
        :key="item.to"
        :to="item.to"
        :aria-label="item.label"
        class="flex min-h-16 flex-1 flex-col items-center gap-0.5 py-2 text-xs text-gray-500 dark:text-gray-400"
        active-class="!text-primary-600 dark:!text-primary-400"
      >
        <template v-if="item.isCenter">
          <div class="flex h-10 w-10 -translate-y-1 items-center justify-center rounded-xl bg-primary-600 text-white shadow-md shadow-primary-600/25">
            <component :is="item.icon" class="h-5 w-5" />
          </div>
        </template>
        <template v-else>
          <component :is="item.icon" class="h-6 w-6" />
          <span>{{ item.label }}</span>
        </template>
      </router-link>

      <!-- 전체 메뉴 시트 열기 (설정 페이지로 직행하지 않는다) -->
      <button
        type="button"
        class="flex min-h-16 flex-1 flex-col items-center gap-0.5 py-2 text-xs transition-colors"
        :class="menuOpen ? 'text-primary-600 dark:text-primary-400' : 'text-gray-500 dark:text-gray-400'"
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
import { ref, type Component } from 'vue'
import {
  HomeIcon,
  ArrowUpTrayIcon,
  FilmIcon,
  ChartBarIcon,
  EllipsisHorizontalIcon,
} from '@heroicons/vue/24/outline'
import { useLocale } from '@/composables/useLocale'
import MobileMenuSheet from '@/components/layout/MobileMenuSheet.vue'

interface NavItem {
  to: string
  label: string
  icon: Component
  isCenter?: boolean
}

const { t } = useLocale()
const menuOpen = ref(false)

const navItems: NavItem[] = [
  { to: '/dashboard', label: '홈', icon: HomeIcon },
  { to: '/videos', label: '영상', icon: FilmIcon },
  { to: '/upload', label: '업로드', icon: ArrowUpTrayIcon, isCenter: true },
  { to: '/analytics', label: '분석', icon: ChartBarIcon },
]
</script>

<template>
  <nav aria-label="모바일 하단 네비게이션" class="fixed bottom-0 left-0 right-0 z-30 border-t border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-900">
    <div class="flex items-center justify-around">
      <router-link
        v-for="item in navItems"
        :key="item.to"
        :to="item.to"
        :aria-label="item.label"
        class="flex flex-1 flex-col items-center gap-0.5 py-2 text-xs text-gray-500 dark:text-gray-400"
        :class="item.isCenter ? '' : ''"
        active-class="!text-primary-600 dark:!text-primary-400"
      >
        <template v-if="item.isCenter">
          <div class="flex h-10 w-10 -translate-y-1 items-center justify-center rounded-full bg-gradient-to-r from-primary-500 to-primary-600 text-white shadow-lg shadow-primary-500/30">
            <component :is="item.icon" class="h-5 w-5" />
          </div>
        </template>
        <template v-else>
          <component :is="item.icon" class="h-6 w-6" />
          <span>{{ item.label }}</span>
        </template>
      </router-link>
    </div>
  </nav>
</template>

<script setup lang="ts">
import {
  HomeIcon,
  ArrowUpTrayIcon,
  FilmIcon,
  ChartBarIcon,
  EllipsisHorizontalIcon,
} from '@heroicons/vue/24/outline'
import type { Component } from 'vue'

interface NavItem {
  to: string
  label: string
  icon: Component
  isCenter?: boolean
}

const navItems: NavItem[] = [
  { to: '/dashboard', label: '홈', icon: HomeIcon },
  { to: '/videos', label: '영상', icon: FilmIcon },
  { to: '/upload', label: '업로드', icon: ArrowUpTrayIcon, isCenter: true },
  { to: '/analytics', label: '분석', icon: ChartBarIcon },
  { to: '/settings', label: '더보기', icon: EllipsisHorizontalIcon },
]
</script>

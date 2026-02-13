<template>
  <header class="flex h-16 shrink-0 items-center border-b border-gray-200 bg-white/80 backdrop-blur-xl px-4 dark:border-gray-700 dark:bg-gray-900/80 tablet:px-6">
    <!-- Mobile menu toggle -->
    <button
      aria-label="메뉴 열기"
      class="mr-3 rounded-lg p-2 text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800 tablet:hidden"
      @click="emit('toggleMenu')"
    >
      <Bars3Icon class="h-6 w-6" aria-hidden="true" />
    </button>

    <!-- Logo (mobile only) -->
    <router-link to="/dashboard" class="mr-4 tablet:hidden">
      <span class="text-xl font-bold text-primary-600">onGo</span>
    </router-link>

    <!-- Search Trigger -->
    <button
      aria-label="검색 열기 (단축키: Command+K)"
      class="relative mx-4 hidden flex-1 items-center gap-3 rounded-lg border border-gray-300 bg-white px-4 py-2 text-left text-sm text-gray-500 transition-colors hover:border-gray-400 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-400 dark:hover:border-gray-500 dark:hover:bg-gray-700 tablet:flex tablet:max-w-md"
      @click="searchOpen = true"
    >
      <MagnifyingGlassIcon class="h-5 w-5 flex-shrink-0" aria-hidden="true" />
      <span class="flex-1">검색</span>
      <kbd class="inline-flex h-5 items-center rounded border border-gray-300 bg-gray-100 px-2 font-mono text-xs font-semibold text-gray-700 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300" aria-hidden="true">
        ⌘K
      </kbd>
    </button>

    <div class="ml-auto flex items-center gap-3">
      <!-- AI Credit Display -->
      <CreditDisplay />

      <!-- Theme Toggle -->
      <div class="relative">
        <button
          :aria-label="getThemeButtonLabel()"
          :aria-expanded="themeMenuOpen"
          aria-haspopup="menu"
          class="rounded-lg p-2 text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
          @click="themeMenuOpen = !themeMenuOpen"
        >
          <SunIcon v-if="themeStore.mode === 'light'" class="h-5 w-5" aria-hidden="true" />
          <MoonIcon v-else-if="themeStore.mode === 'dark'" class="h-5 w-5" aria-hidden="true" />
          <ComputerDesktopIcon v-else class="h-5 w-5" aria-hidden="true" />
        </button>
        <div
          v-if="themeMenuOpen"
          role="menu"
          aria-label="테마 선택"
          class="absolute right-0 top-full z-50 mt-2 w-36 rounded-lg border border-gray-200 bg-white py-1 shadow-lg dark:border-gray-700 dark:bg-gray-800"
        >
          <button
            v-for="opt in themeOptions"
            :key="opt.value"
            role="menuitem"
            :aria-label="`${opt.label} 테마로 변경`"
            class="flex w-full items-center gap-2 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 dark:text-gray-200 dark:hover:bg-gray-700"
            :class="{ 'text-primary-600 dark:text-primary-400': themeStore.mode === opt.value }"
            @click="setTheme(opt.value)"
          >
            <component :is="opt.icon" class="h-4 w-4" aria-hidden="true" />
            {{ opt.label }}
          </button>
        </div>
      </div>

      <!-- Notifications -->
      <div class="relative" ref="notificationRef">
        <button
          :aria-label="getNotificationButtonLabel()"
          :aria-expanded="notificationOpen"
          aria-haspopup="dialog"
          class="relative rounded-lg p-2 text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
          @click="notificationOpen = !notificationOpen"
        >
          <BellIcon class="h-6 w-6" :class="{ 'notification-bell-pulse': notificationStore.hasUnread }" aria-hidden="true" />
          <span
            v-if="notificationStore.unreadCount > 0"
            class="absolute -right-0.5 -top-0.5 flex h-5 w-5 items-center justify-center rounded-full bg-red-500 text-xs text-white"
            aria-hidden="true"
          >
            {{ notificationStore.unreadCount > 9 ? '9+' : notificationStore.unreadCount }}
          </span>
        </button>
        <NotificationPanel :is-open="notificationOpen" @close="notificationOpen = false" />
      </div>

      <!-- Profile -->
      <div class="relative" ref="profileRef">
        <button
          :aria-label="getProfileButtonLabel()"
          :aria-expanded="profileOpen"
          aria-haspopup="menu"
          class="flex items-center gap-2 rounded-lg p-1.5 hover:bg-gray-100 dark:hover:bg-gray-800"
          @click="profileOpen = !profileOpen"
        >
          <div class="flex h-8 w-8 items-center justify-center rounded-full bg-primary-100 text-sm font-medium text-primary-600 dark:bg-primary-900/30 dark:text-primary-400" aria-hidden="true">
            {{ userInitial }}
          </div>
          <ChevronDownIcon class="hidden h-4 w-4 text-gray-400 tablet:block" aria-hidden="true" />
        </button>

        <!-- Profile dropdown -->
        <div
          v-if="profileOpen"
          role="menu"
          aria-label="프로필 메뉴"
          class="absolute right-0 top-full z-50 mt-2 w-56 rounded-xl border border-gray-200 bg-white py-2 shadow-lg dark:border-gray-700 dark:bg-gray-800"
        >
          <div class="border-b border-gray-100 px-4 py-3 dark:border-gray-700">
            <p class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ user?.nickname || user?.name }}</p>
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ user?.email }}</p>
            <span class="badge-blue mt-1">{{ user?.planType }}</span>
          </div>
          <router-link
            to="/settings"
            role="menuitem"
            class="flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 dark:text-gray-200 dark:hover:bg-gray-700"
            @click="profileOpen = false"
          >
            <Cog6ToothIcon class="mr-3 h-4 w-4" aria-hidden="true" />
            설정
          </router-link>
          <button
            role="menuitem"
            class="flex w-full items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 dark:text-gray-200 dark:hover:bg-gray-700"
            @click="handleLogout"
          >
            <ArrowRightOnRectangleIcon class="mr-3 h-4 w-4" aria-hidden="true" />
            로그아웃
          </button>
        </div>
      </div>
    </div>

    <!-- Search Overlay -->
    <SearchOverlay v-model="searchOpen" />
  </header>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, type Component } from 'vue'
import { onClickOutside } from '@vueuse/core'
import {
  Bars3Icon,
  MagnifyingGlassIcon,
  BellIcon,
  ChevronDownIcon,
  Cog6ToothIcon,
  ArrowRightOnRectangleIcon,
  SunIcon,
  MoonIcon,
  ComputerDesktopIcon,
} from '@heroicons/vue/24/outline'
import CreditDisplay from '@/components/common/CreditDisplay.vue'
import NotificationPanel from '@/components/common/NotificationPanel.vue'
import SearchOverlay from '@/components/common/SearchOverlay.vue'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore, type ThemeMode } from '@/stores/theme'
import { useNotificationCenterStore } from '@/stores/notificationCenter'

const emit = defineEmits<{
  toggleMenu: []
}>()

const authStore = useAuthStore()
const themeStore = useThemeStore()
const notificationStore = useNotificationCenterStore()
const user = computed(() => authStore.user)
const userInitial = computed(() => (user.value?.nickname || user.value?.name || 'U').charAt(0))
const notificationOpen = ref(false)
const profileOpen = ref(false)
const themeMenuOpen = ref(false)
const searchOpen = ref(false)

const themeOptions: { value: ThemeMode; label: string; icon: Component }[] = [
  { value: 'light', label: '라이트', icon: SunIcon },
  { value: 'dark', label: '다크', icon: MoonIcon },
  { value: 'system', label: '시스템', icon: ComputerDesktopIcon },
]

function setTheme(mode: ThemeMode) {
  themeStore.setMode(mode)
  themeMenuOpen.value = false
}

const notificationRef = ref<HTMLElement>()
const profileRef = ref<HTMLElement>()

onClickOutside(notificationRef, () => {
  notificationOpen.value = false
})

onClickOutside(profileRef, () => {
  profileOpen.value = false
})

onMounted(() => {
  notificationStore.fetchNotifications()
})

function handleLogout() {
  profileOpen.value = false
  authStore.logout()
}

function getThemeButtonLabel(): string {
  const themeLabels: Record<ThemeMode, string> = {
    light: '라이트 테마',
    dark: '다크 테마',
    system: '시스템 테마',
  }
  return `테마 변경 (현재: ${themeLabels[themeStore.mode]})`
}

function getNotificationButtonLabel(): string {
  const count = notificationStore.unreadCount
  return count > 0 ? `알림 (읽지 않음 ${count}개)` : '알림'
}

function getProfileButtonLabel(): string {
  return `프로필 메뉴 (${user.value?.nickname || user.value?.name || '사용자'})`
}
</script>

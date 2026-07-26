<template>
  <header class="flex h-[72px] shrink-0 items-center border-b bg-white px-4 dark:bg-gray-900 tablet:px-7" style="border-color: var(--border-default)">
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
      <span class="text-h2 font-bold tracking-[-0.06em] text-primary-600">onGo</span>
    </router-link>

    <!-- Search Trigger -->
    <button
      aria-label="검색 열기 (단축키: Command+K)"
      class="relative mx-4 hidden min-h-10 flex-1 items-center gap-3 rounded-lg border bg-gray-50 px-3.5 text-left text-body text-gray-500 transition-colors hover:border-gray-400 hover:bg-white dark:bg-gray-800 dark:text-gray-400 tablet:flex tablet:max-w-xl"
      @click="searchOpen = true"
    >
      <MagnifyingGlassIcon class="h-5 w-5 flex-shrink-0" aria-hidden="true" />
      <span class="flex-1">검색</span>
        <kbd class="inline-flex h-6 items-center rounded border border-gray-300 bg-white px-2 font-mono text-[11px] font-semibold text-gray-500 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300" aria-hidden="true">
        ⌘K
      </kbd>
    </button>

    <div class="ml-auto flex items-center gap-2 tablet:gap-3">
      <!-- AI Credit Display (tablet+) -->
      <CreditDisplay class="hidden tablet:flex" />

      <!-- Language Toggle (tablet+) -->
      <LanguageToggle class="hidden tablet:block" />

      <!-- Theme Toggle -->
      <ThemeToggle />

      <!-- Notifications -->
      <div ref="notificationRef" class="relative">
        <button
          :aria-label="getNotificationButtonLabel()"
          :aria-expanded="notificationOpen"
          aria-haspopup="dialog"
          class="relative rounded-lg p-2 text-gray-500 transition-colors hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
          @click="notificationOpen = !notificationOpen"
        >
          <BellIcon class="h-6 w-6" :class="{ 'notification-bell-pulse': notificationStore.hasUnread }" aria-hidden="true" />
          <!-- 흰 글씨를 얹는 솔리드 채움 — `--color-error-solid` 토큰이 없어 raw 유지 (가이드 §6) -->
          <span
            v-if="notificationStore.unreadCount > 0"
            class="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold text-white"
            aria-hidden="true"
          >
            {{ notificationStore.unreadCount > 9 ? '9+' : notificationStore.unreadCount }}
          </span>
        </button>
        <NotificationPanel :is-open="notificationOpen" @close="notificationOpen = false" />
      </div>

      <!-- Profile -->
      <div ref="profileRef" class="relative">
        <button
          :aria-label="getProfileButtonLabel()"
          :aria-expanded="profileOpen"
          aria-haspopup="menu"
          class="flex items-center gap-2 rounded-lg p-1.5 hover:bg-gray-100 dark:hover:bg-gray-800"
          @click="profileOpen = !profileOpen"
        >
          <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-primary-100 text-body font-bold text-primary-700 dark:bg-primary-900/30 dark:text-primary-300" aria-hidden="true">
            {{ userInitial }}
          </div>
          <ChevronDownIcon class="hidden h-4 w-4 text-gray-400 tablet:block" aria-hidden="true" />
        </button>

        <!-- Profile dropdown -->
        <div
          v-if="profileOpen"
          role="menu"
          aria-label="프로필 메뉴"
            class="absolute right-0 top-full z-50 mt-2 w-64 max-w-[calc(100vw-2rem)] rounded-xl border bg-white py-2 shadow-lg dark:bg-gray-800"
            style="border-color: var(--border-default)"
        >
          <div class="border-b border-gray-100 px-4 py-3 dark:border-gray-700">
            <p class="text-body font-medium text-gray-900 dark:text-gray-100">{{ user?.nickname || user?.name }}</p>
            <p class="text-body-xs text-gray-500 dark:text-gray-400">{{ user?.email }}</p>
            <span class="badge-blue mt-1">{{ user?.planType }}</span>
          </div>
          <router-link
            to="/settings"
            role="menuitem"
            class="flex items-center px-4 py-2 text-body text-gray-700 hover:bg-gray-50 dark:text-gray-200 dark:hover:bg-gray-700"
            @click="profileOpen = false"
          >
            <Cog6ToothIcon class="mr-3 h-4 w-4" aria-hidden="true" />
            설정
          </router-link>
          <button
            role="menuitem"
            class="flex w-full items-center px-4 py-2 text-body text-gray-700 hover:bg-gray-50 dark:text-gray-200 dark:hover:bg-gray-700"
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
import { ref, computed, onMounted } from 'vue'
import { onClickOutside } from '@vueuse/core'
import {
  Bars3Icon,
  MagnifyingGlassIcon,
  BellIcon,
  ChevronDownIcon,
  Cog6ToothIcon,
  ArrowRightOnRectangleIcon,
} from '@heroicons/vue/24/outline'
import CreditDisplay from '@/components/common/CreditDisplay.vue'
import LanguageToggle from '@/components/common/LanguageToggle.vue'
import ThemeToggle from '@/components/common/ThemeToggle.vue'
import NotificationPanel from '@/components/common/NotificationPanel.vue'
import SearchOverlay from '@/components/common/SearchOverlay.vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationCenterStore } from '@/stores/notificationCenter'

const emit = defineEmits<{
  toggleMenu: []
}>()

const authStore = useAuthStore()
const notificationStore = useNotificationCenterStore()
const user = computed(() => authStore.user)
const userInitial = computed(() => (user.value?.nickname || user.value?.name || 'U').charAt(0))
const notificationOpen = ref(false)
const profileOpen = ref(false)
const searchOpen = ref(false)

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

function getNotificationButtonLabel(): string {
  const count = notificationStore.unreadCount
  return count > 0 ? `알림 (읽지 않음 ${count}개)` : '알림'
}

function getProfileButtonLabel(): string {
  return `프로필 메뉴 (${user.value?.nickname || user.value?.name || '사용자'})`
}
</script>

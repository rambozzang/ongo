<template>
  <div class="flex h-screen overflow-hidden bg-gray-50 dark:bg-gray-950">
    <!-- Offline Indicator (before everything) -->
    <OfflineIndicator />

    <!-- Skip to main content link -->
    <SkipToContent />

    <!-- Desktop/Tablet Sidebar -->
    <SideNav
      :collapsed="sidebarCollapsed"
      class="hidden tablet:flex"
      @toggle="sidebarCollapsed = !sidebarCollapsed"
    />

    <!-- Mobile overlay -->
    <div
      v-if="mobileMenuOpen"
      class="fixed inset-0 z-40 bg-black/50 tablet:hidden"
      role="presentation"
      aria-hidden="true"
      @click="mobileMenuOpen = false"
    />
    <SideNav
      v-if="mobileMenuOpen"
      :collapsed="false"
      class="fixed inset-y-0 left-0 z-50 tablet:hidden"
      @navigate="mobileMenuOpen = false"
    />

    <!-- Main content -->
    <div class="flex flex-1 flex-col overflow-hidden" role="main">
      <TopBar
        @toggle-menu="mobileMenuOpen = !mobileMenuOpen"
      />
      <Breadcrumb role="navigation" aria-label="Breadcrumb" />

      <main id="main-content" class="flex-1 overflow-y-auto p-4 tablet:p-6 pb-20 tablet:pb-6">
        <ErrorBoundary>
          <router-view v-slot="{ Component, route }">
            <component :is="Component" :key="route.path" />
          </router-view>
        </ErrorBoundary>
      </main>
    </div>

    <!-- Mobile bottom nav -->
    <MobileBottomNav class="tablet:hidden" />

    <!-- Upload Queue Indicator (global) -->
    <UploadQueueIndicator @open="queuePanelOpen = true" />

    <!-- Upload Queue Panel (global) -->
    <UploadQueuePanel v-model="queuePanelOpen" />

    <!-- Aria Live Region for screen reader announcements -->
    <AriaLiveRegion />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import SideNav from './SideNav.vue'
import TopBar from './TopBar.vue'
import Breadcrumb from './Breadcrumb.vue'
import MobileBottomNav from './MobileBottomNav.vue'
import ErrorBoundary from '@/components/common/ErrorBoundary.vue'
import OfflineIndicator from '@/components/common/OfflineIndicator.vue'
import SkipToContent from '@/components/common/SkipToContent.vue'
import AriaLiveRegion from '@/components/common/AriaLiveRegion.vue'
import UploadQueueIndicator from '@/components/upload/UploadQueueIndicator.vue'
import UploadQueuePanel from '@/components/upload/UploadQueuePanel.vue'
import { useAuthStore } from '@/stores/auth'
import { useCreditStore } from '@/stores/credit'
import { useWebSocket } from '@/composables/useWebSocket'

const isDesktop = useMediaQuery('(min-width: 1280px)')
const sidebarCollapsed = ref(!isDesktop.value)
const mobileMenuOpen = ref(false)
const queuePanelOpen = ref(false)

const authStore = useAuthStore()
const creditStore = useCreditStore()
const { connect, disconnect } = useWebSocket()

onMounted(() => {
  // 병렬 실행 — 프로필 복원과 크레딧 조회를 동시에 시작
  authStore.initialize()
  creditStore.fetchBalance()

  // WebSocket 연결 (인증된 사용자)
  if (authStore.accessToken && authStore.user?.id) {
    connect(authStore.user.id, authStore.accessToken)
  }
})

onUnmounted(() => {
  disconnect()
})

// 로그인 후 연결 / 로그아웃 시 해제
watch(() => authStore.isAuthenticated, (isAuth) => {
  if (isAuth && authStore.accessToken && authStore.user?.id) {
    connect(authStore.user.id, authStore.accessToken)
  } else {
    disconnect()
  }
})
</script>

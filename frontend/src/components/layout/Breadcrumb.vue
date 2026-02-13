<template>
  <nav
    v-if="breadcrumbs.length > 0"
    aria-label="Breadcrumb"
    class="breadcrumb-container"
  >
    <ol class="flex items-center space-x-2 text-sm">
      <!-- Home icon (always first) -->
      <li class="hidden tablet:flex items-center">
        <router-link
          to="/dashboard"
          class="text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200 transition-colors"
          aria-label="대시보드로 이동"
        >
          <HomeIcon class="h-4 w-4" />
        </router-link>
      </li>

      <!-- Breadcrumb items (hidden on mobile, shown on tablet+) -->
      <template v-for="(item, index) in breadcrumbs" :key="item.path">
        <li class="hidden tablet:flex items-center">
          <ChevronRightIcon class="h-4 w-4 text-gray-400 dark:text-gray-600" />
        </li>
        <li class="hidden tablet:flex items-center">
          <router-link
            v-if="index < breadcrumbs.length - 1"
            :to="item.path"
            class="text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200 transition-colors"
          >
            {{ item.label }}
          </router-link>
          <span
            v-else
            class="font-semibold text-gray-900 dark:text-white"
            aria-current="page"
          >
            {{ item.label }}
          </span>
        </li>
      </template>

      <!-- Mobile: only show current page -->
      <li class="flex tablet:hidden items-center">
        <span class="font-semibold text-gray-900 dark:text-white">
          {{ currentPageLabel }}
        </span>
      </li>
    </ol>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { HomeIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'

interface BreadcrumbItem {
  label: string
  path: string
}

const route = useRoute()

// Route name to Korean label mapping
const routeLabels: Record<string, string> = {
  dashboard: '대시보드',
  upload: '영상 업로드',
  videos: '영상 관리',
  'video-detail': '영상 상세',
  'video-compare': '영상 비교',
  analytics: '통합 분석',
  schedule: '예약 관리',
  ai: 'AI 도구',
  channels: '채널 관리',
  subscription: '구독 관리',
  settings: '설정',
}

// Build breadcrumbs from current route
const breadcrumbs = computed<BreadcrumbItem[]>(() => {
  const items: BreadcrumbItem[] = []

  // Don't show breadcrumbs on dashboard (it's the home page)
  if (route.name === 'dashboard') {
    return []
  }

  // Get all matched routes (parent and current)
  const matched = route.matched.filter(r => r.name && r.name !== 'dashboard')

  matched.forEach(routeRecord => {
    const name = routeRecord.name as string
    const label = route.meta.breadcrumb as string || routeLabels[name] || name

    items.push({
      label,
      path: routeRecord.path === '/:pathMatch(.*)' ? route.path : routeRecord.path,
    })
  })

  // Handle dynamic routes (e.g., /videos/:id)
  if (route.name === 'video-detail' && route.params.id) {
    // Add parent route (videos list) first
    const parentIndex = items.findIndex(item => item.label === '영상 상세')
    if (parentIndex > 0) {
      items.splice(parentIndex, 0, {
        label: '영상 관리',
        path: '/videos',
      })
    }

    // Use video title from route meta if available, otherwise use default label
    const videoTitle = route.meta.videoTitle as string
    if (videoTitle) {
      items[items.length - 1].label = videoTitle
    }
  }

  // Handle analytics/compare nested route
  if (route.name === 'video-compare') {
    const compareIndex = items.findIndex(item => item.label === '영상 비교')
    if (compareIndex > 0) {
      items.splice(compareIndex, 0, {
        label: '통합 분석',
        path: '/analytics',
      })
    }
  }

  return items
})

const currentPageLabel = computed(() => {
  if (breadcrumbs.value.length === 0) {
    return '대시보드'
  }
  return breadcrumbs.value[breadcrumbs.value.length - 1].label
})
</script>

<style scoped>
.breadcrumb-container {
  @apply px-4 py-3 tablet:px-6 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900;
  animation: slideDown 0.2s ease-out;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>

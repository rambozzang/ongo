<template>
  <aside
    class="glass-sidebar flex flex-col transition-all duration-200"
    :class="collapsed ? 'w-[60px]' : 'w-[240px]'"
  >
    <!-- Logo -->
    <div class="flex h-16 items-center border-b border-gray-200 px-4 dark:border-gray-700">
      <router-link to="/dashboard" class="flex items-center gap-2" @click="emit('navigate')">
        <span class="text-2xl font-bold text-primary-600">on</span>
        <span v-if="!collapsed" class="text-2xl font-bold text-gray-900 dark:text-gray-100">Go</span>
      </router-link>
      <button
        v-if="!collapsed"
        :aria-label="collapsed ? '사이드바 펼치기' : '사이드바 접기'"
        :aria-expanded="!collapsed"
        class="ml-auto hidden rounded p-1 text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 desktop:block"
        @click="emit('toggle')"
      >
        <ChevronLeftIcon class="h-5 w-5" />
      </button>
      <button
        v-else
        :aria-label="collapsed ? '사이드바 펼치기' : '사이드바 접기'"
        :aria-expanded="!collapsed"
        class="ml-auto hidden rounded p-1 text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 desktop:block"
        @click="emit('toggle')"
      >
        <ChevronRightIcon class="h-5 w-5" />
      </button>
    </div>

    <!-- Navigation -->
    <nav role="navigation" aria-label="주요 네비게이션" class="flex-1 overflow-y-auto px-2 py-4">
      <div
        v-for="(group, groupIndex) in navGroups"
        :key="groupIndex"
        :class="groupIndex > 0 ? 'mt-4' : ''"
      >
        <!-- Group header (expanded) or divider (collapsed) -->
        <template v-if="group.label">
          <div
            v-if="collapsed"
            class="mx-2 my-2 border-t border-gray-200 dark:border-gray-700"
            aria-hidden="true"
          />
          <div
            v-else
            class="mb-1 px-3 py-1 text-[11px] font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500"
          >
            {{ group.label }}
          </div>
        </template>

        <router-link
          v-for="item in group.items"
          :key="item.to"
          :to="item.to"
          role="menuitem"
          :aria-label="item.label"
          :aria-current="isCurrentRoute(item.to) ? 'page' : undefined"
          class="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-gray-600 transition-colors hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-100"
          active-class="!bg-primary-50 !text-primary-700 dark:!bg-primary-900/30 dark:!text-primary-400"
          @click="emit('navigate')"
        >
          <component :is="item.icon" class="h-5 w-5 shrink-0" :aria-hidden="true" />
          <span v-if="!collapsed">{{ item.label }}</span>
        </router-link>
      </div>

      <div class="my-3 border-t border-gray-200 dark:border-gray-700" role="separator" aria-hidden="true" />

      <router-link
        v-for="item in bottomNavItems"
        :key="item.to"
        :to="item.to"
        role="menuitem"
        :aria-label="item.label"
        :aria-current="isCurrentRoute(item.to) ? 'page' : undefined"
        class="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-gray-600 transition-colors hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-100"
        active-class="!bg-primary-50 !text-primary-700 dark:!bg-primary-900/30 dark:!text-primary-400"
        @click="emit('navigate')"
      >
        <component :is="item.icon" class="h-5 w-5 shrink-0" :aria-hidden="true" />
        <span v-if="!collapsed">{{ item.label }}</span>
      </router-link>
    </nav>
  </aside>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import {
  HomeIcon,
  ArrowUpTrayIcon,
  CalendarDaysIcon,
  FilmIcon,
  ChartBarIcon,
  ChatBubbleLeftEllipsisIcon,
  SparklesIcon,
  LinkIcon,
  CreditCardIcon,
  Cog6ToothIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  BanknotesIcon,
  LightBulbIcon,
  UserGroupIcon,
  BoltIcon,
  InboxIcon,
  GlobeAltIcon,
  DocumentDuplicateIcon,
  BeakerIcon,
  PresentationChartLineIcon,
  SwatchIcon,
  FlagIcon,
  BellIcon,
  ArrowPathIcon,
  FolderIcon,
  CodeBracketIcon,
  ClockIcon,
} from '@heroicons/vue/24/outline'

defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  toggle: []
  navigate: []
}>()

const route = useRoute()

interface NavItem {
  to: string
  label: string
  icon: typeof HomeIcon
}

interface NavGroup {
  label?: string
  items: NavItem[]
}

const navGroups: NavGroup[] = [
  {
    items: [
      { to: '/dashboard', label: '대시보드', icon: HomeIcon },
    ],
  },
  {
    label: '콘텐츠',
    items: [
      { to: '/upload', label: '업로드', icon: ArrowUpTrayIcon },
      { to: '/videos', label: '영상 관리', icon: FilmIcon },
      { to: '/templates', label: '템플릿', icon: DocumentDuplicateIcon },
      { to: '/assets', label: '에셋', icon: FolderIcon },
      { to: '/brandkit', label: '브랜드 키트', icon: SwatchIcon },
      { to: '/recycling', label: '콘텐츠 재활용', icon: ArrowPathIcon },
    ],
  },
  {
    label: '게시',
    items: [
      { to: '/schedule', label: '예약 관리', icon: CalendarDaysIcon },
      { to: '/calendar', label: '캘린더', icon: CalendarDaysIcon },
      { to: '/automation', label: '자동화', icon: BoltIcon },
    ],
  },
  {
    label: '분석',
    items: [
      { to: '/analytics', label: '분석', icon: ChartBarIcon },
      { to: '/revenue', label: '수익', icon: BanknotesIcon },
      { to: '/abtest', label: 'A/B 테스트', icon: BeakerIcon },
      { to: '/competitor', label: '경쟁사 분석', icon: PresentationChartLineIcon },
      { to: '/goals', label: '목표', icon: FlagIcon },
    ],
  },
  {
    label: '소통',
    items: [
      { to: '/comments', label: '댓글', icon: ChatBubbleLeftEllipsisIcon },
      { to: '/inbox', label: '인박스', icon: InboxIcon },
      { to: '/notifications', label: '알림', icon: BellIcon },
    ],
  },
  {
    label: '도구',
    items: [
      { to: '/ai', label: 'AI 도구', icon: SparklesIcon },
      { to: '/ideas', label: '아이디어', icon: LightBulbIcon },
      { to: '/linkbio', label: '링크인바이오', icon: GlobeAltIcon },
    ],
  },
  {
    label: '관리',
    items: [
      { to: '/channels', label: '채널 관리', icon: LinkIcon },
      { to: '/team', label: '팀', icon: UserGroupIcon },
      { to: '/webhooks', label: '웹훅', icon: CodeBracketIcon },
      { to: '/activity-log', label: '활동 로그', icon: ClockIcon },
    ],
  },
]

const bottomNavItems = [
  { to: '/subscription', label: '구독/결제', icon: CreditCardIcon },
  { to: '/settings', label: '설정', icon: Cog6ToothIcon },
]

function isCurrentRoute(to: string): boolean {
  return route.path === to || route.path.startsWith(to + '/')
}
</script>

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
        :aria-label="collapsed ? t('nav.sidebarExpand') : t('nav.sidebarCollapse')"
        :aria-expanded="!collapsed"
        class="ml-auto hidden rounded p-1 text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 desktop:block"
        @click="emit('toggle')"
      >
        <ChevronLeftIcon class="h-5 w-5" />
      </button>
      <button
        v-else
        :aria-label="collapsed ? t('nav.sidebarExpand') : t('nav.sidebarCollapse')"
        :aria-expanded="!collapsed"
        class="ml-auto hidden rounded p-1 text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 desktop:block"
        @click="emit('toggle')"
      >
        <ChevronRightIcon class="h-5 w-5" />
      </button>
    </div>

    <!-- Workspace Switcher -->
    <WorkspaceSwitcher :collapsed="collapsed" />

    <!-- Navigation -->
    <nav role="navigation" :aria-label="t('nav.mainNavigation')" class="flex-1 overflow-y-auto px-2 py-4 scrollbar-dark">
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

        <!-- Direct items -->
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

        <!-- Sub-groups (collapsible) -->
        <template v-if="!collapsed && group.subGroups">
          <div v-for="sub in group.subGroups" :key="sub.key" class="mt-0.5">
            <button
              class="flex w-full items-center gap-2 rounded-lg px-3 py-1.5 text-xs font-medium text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:text-gray-500 dark:hover:bg-gray-800 dark:hover:text-gray-300"
              @click="toggleSubGroup(sub.key)"
            >
              <ChevronRightIcon
                class="h-3.5 w-3.5 shrink-0 transition-transform duration-200"
                :class="expandedSubGroups.has(sub.key) ? 'rotate-90' : ''"
              />
              <span>{{ sub.label }}</span>
              <span class="ml-auto text-[10px] text-gray-300 dark:text-gray-600">{{ sub.items.length }}</span>
            </button>
            <div v-if="expandedSubGroups.has(sub.key)" class="ml-2 border-l border-gray-200 pl-1 dark:border-gray-700">
              <router-link
                v-for="item in sub.items"
                :key="item.to"
                :to="item.to"
                role="menuitem"
                :aria-label="item.label"
                :aria-current="isCurrentRoute(item.to) ? 'page' : undefined"
                class="flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-gray-600 transition-colors hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-100"
                active-class="!bg-primary-50 !text-primary-700 dark:!bg-primary-900/30 dark:!text-primary-400"
                @click="emit('navigate')"
              >
                <component :is="item.icon" class="h-4 w-4 shrink-0" :aria-hidden="true" />
                <span>{{ item.label }}</span>
              </router-link>
            </div>
          </div>
        </template>

        <!-- Collapsed mode: show sub-group items as direct items -->
        <template v-if="collapsed && group.subGroups">
          <template v-for="sub in group.subGroups" :key="sub.key">
            <router-link
              v-for="item in sub.items"
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
            </router-link>
          </template>
        </template>
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
import { computed, ref, watch } from 'vue'
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
  InboxIcon,
  DocumentDuplicateIcon,
  SwatchIcon,
  BellIcon,
  ClockIcon,
  BookOpenIcon,
  ShieldCheckIcon,
} from '@heroicons/vue/24/outline'
import { useLocale } from '@/composables/useLocale'
import { useAuthStore } from '@/stores/auth'
import WorkspaceSwitcher from '@/components/layout/WorkspaceSwitcher.vue'

defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  toggle: []
  navigate: []
}>()

const route = useRoute()
const { t } = useLocale()
const authStore = useAuthStore()
const isAdmin = computed(() => authStore.user?.role === 'ADMIN')

interface NavItem {
  to: string
  label: string
  icon: typeof HomeIcon
}

interface NavSubGroup {
  key: string
  label: string
  items: NavItem[]
}

interface NavGroup {
  label?: string
  items: NavItem[]
  subGroups?: NavSubGroup[]
}

// --- Sub-group expand/collapse state ---
const expandedSubGroups = ref<Set<string>>(new Set())

function toggleSubGroup(key: string) {
  const next = new Set(expandedSubGroups.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  expandedSubGroups.value = next
}

const navGroups = computed<NavGroup[]>(() => [
  // ── 1. 대시보드 ──
  {
    items: [
      { to: '/dashboard', label: t('nav.dashboard'), icon: HomeIcon },
    ],
  },
  // ── 2. 콘텐츠 제작 ──
  {
    label: t('nav.groupCreate'),
    items: [
      { to: '/upload', label: t('nav.upload'), icon: ArrowUpTrayIcon },
      { to: '/videos', label: t('nav.videos'), icon: FilmIcon },
      { to: '/ai', label: t('nav.ai'), icon: SparklesIcon },
      { to: '/ideas', label: t('nav.ideas'), icon: LightBulbIcon },
      { to: '/templates', label: t('nav.templates'), icon: DocumentDuplicateIcon },
      { to: '/brandkit', label: t('nav.brandkit'), icon: SwatchIcon },
    ],
  },
  // ── 3. 게시 & 스케줄 ──
  {
    label: t('nav.groupPublish'),
    items: [
      { to: '/schedule', label: t('nav.schedule'), icon: CalendarDaysIcon },
      { to: '/calendar', label: t('nav.calendar'), icon: CalendarDaysIcon },
    ],
  },
  // ── 4. 분석 ──
  {
    label: t('nav.groupAnalytics'),
    items: [
      { to: '/analytics', label: t('nav.analytics'), icon: ChartBarIcon },
      { to: '/revenue', label: t('nav.revenue'), icon: BanknotesIcon },
    ],
  },
  // ── 5. 소통 ──
  {
    label: t('nav.groupAudience'),
    items: [
      { to: '/comments', label: t('nav.comments'), icon: ChatBubbleLeftEllipsisIcon },
      { to: '/inbox', label: t('nav.inbox'), icon: InboxIcon },
      { to: '/notifications', label: t('nav.notifications'), icon: BellIcon },
    ],
  },
  // ── 6. 채널 운영 ──
  {
    label: t('nav.groupOperations'),
    items: [
      { to: '/channels', label: t('nav.channels'), icon: LinkIcon },
      { to: '/activity-log', label: t('nav.activityLog'), icon: ClockIcon },
    ],
  },
])

// Auto-expand sub-group containing the current route
watch(
  () => route.path,
  (path) => {
    for (const group of navGroups.value) {
      if (!group.subGroups) continue
      for (const sub of group.subGroups) {
        if (sub.items.some((item) => path === item.to || path.startsWith(item.to + '/'))) {
          if (!expandedSubGroups.value.has(sub.key)) {
            const next = new Set(expandedSubGroups.value)
            next.add(sub.key)
            expandedSubGroups.value = next
          }
        }
      }
    }
  },
  { immediate: true },
)

const bottomNavItems = computed(() => {
  const items = [
    { to: '/manual', label: t('nav.manual'), icon: BookOpenIcon },
    { to: '/subscription', label: t('nav.subscription'), icon: CreditCardIcon },
    { to: '/settings', label: t('nav.settings'), icon: Cog6ToothIcon },
  ]
  if (isAdmin.value) {
    items.push({ to: '/admin', label: t('nav.admin'), icon: ShieldCheckIcon })
  }
  return items
})

function isCurrentRoute(to: string): boolean {
  return route.path === to || route.path.startsWith(to + '/')
}
</script>

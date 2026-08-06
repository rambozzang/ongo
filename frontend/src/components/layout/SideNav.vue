<template>
  <aside
    class="glass-sidebar flex flex-col transition-[width] duration-200"
    :class="collapsed ? 'w-[68px]' : 'w-[264px]'"
  >
    <!-- Logo -->
    <div class="flex h-[72px] items-center border-b px-4" style="border-color: rgba(255,255,255,0.08)">
      <router-link to="/dashboard" class="flex items-center gap-2" @click="emit('navigate')">
        <OnGoLogo size="lg" inverse :mark-only="collapsed" />
      </router-link>
      <button
        v-if="!collapsed"
        :aria-label="collapsed ? t('nav.sidebarExpand') : t('nav.sidebarCollapse')"
        :aria-expanded="!collapsed"
        class="ml-auto hidden rounded-md p-1.5 text-gray-500 hover:bg-white/10 hover:text-white desktop:block"
        @click="emit('toggle')"
      >
        <ChevronLeftIcon class="h-5 w-5" />
      </button>
      <button
        v-else
        :aria-label="collapsed ? t('nav.sidebarExpand') : t('nav.sidebarCollapse')"
        :aria-expanded="!collapsed"
        class="ml-auto hidden rounded-md p-1.5 text-gray-500 hover:bg-white/10 hover:text-white desktop:block"
        @click="emit('toggle')"
      >
        <ChevronRightIcon class="h-5 w-5" />
      </button>
    </div>

    <!-- Workspace Switcher -->
    <WorkspaceSwitcher :collapsed="collapsed" />

    <!-- Navigation -->
    <nav role="navigation" :aria-label="t('nav.mainNavigation')" class="flex-1 overflow-y-auto px-2 py-4 scrollbar-dark">
      <!-- Favorites (즐겨찾기가 있을 때만 노출) -->
      <template v-if="favoriteItems.length > 0">
        <div
          v-if="collapsed"
          class="sidebar-divider mx-2 mb-2 border-t"
          aria-hidden="true"
        />
        <div
          v-else
          class="sidebar-label mb-1 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.14em]"
        >
          {{ t('nav.favorites') }}
        </div>
        <div v-for="item in favoriteItems" :key="`fav-${item.to}`" class="group flex items-center gap-1">
          <router-link
            :to="item.to"
            role="menuitem"
            :aria-label="item.label"
            :aria-current="isCurrentRoute(item.to) ? 'page' : undefined"
            class="nav-link flex flex-1 items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors"
            active-class="nav-link-active"
            @click="emit('navigate')"
          >
            <component :is="item.icon" class="h-5 w-5 shrink-0" :aria-hidden="true" />
            <span v-if="!collapsed">{{ item.label }}</span>
          </router-link>
          <NavPinButton v-if="!collapsed" :path="item.to" :label="item.label" />
        </div>
        <div class="sidebar-divider my-3 border-t" role="separator" aria-hidden="true" />
      </template>

      <div
        v-for="(group, groupIndex) in navGroups"
        :key="groupIndex"
        :class="groupIndex > 0 ? 'mt-4' : ''"
      >
        <!-- Group header (expanded) or divider (collapsed) -->
        <template v-if="group.label">
          <div
            v-if="collapsed"
            class="sidebar-divider mx-2 my-2 border-t"
            aria-hidden="true"
          />
          <div
            v-else
            class="sidebar-label mb-1 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.14em]"
          >
            {{ group.label }}
          </div>
        </template>

        <!-- Direct items -->
        <div v-for="item in group.items" :key="item.to" class="group flex items-center gap-1">
          <router-link
            :to="item.to"
            role="menuitem"
            :aria-label="item.label"
            :aria-current="isCurrentRoute(item.to) ? 'page' : undefined"
            class="nav-link flex flex-1 items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors"
            active-class="nav-link-active"
            @click="emit('navigate')"
          >
            <component :is="item.icon" class="h-5 w-5 shrink-0" :aria-hidden="true" />
            <span v-if="!collapsed">{{ item.label }}</span>
          </router-link>
          <NavPinButton v-if="!collapsed" :path="item.to" :label="item.label" :class="pinVisibilityClass" />
        </div>

        <!-- Sub-groups (collapsible) -->
        <template v-if="!collapsed && group.subGroups">
          <div v-for="sub in group.subGroups" :key="sub.key" class="mt-0.5">
            <button
              class="nav-link flex w-full items-center gap-2 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors"
              :aria-expanded="isSubGroupExpanded(sub.key)"
              @click="toggleSubGroup(sub.key)"
            >
              <ChevronRightIcon
                class="h-3.5 w-3.5 shrink-0 transition-transform duration-200"
                :class="isSubGroupExpanded(sub.key) ? 'rotate-90' : ''"
              />
              <span>{{ sub.label }}</span>
              <span class="ml-auto text-[10px] text-gray-300 dark:text-gray-600">{{ sub.items.length }}</span>
            </button>
            <div v-if="isSubGroupExpanded(sub.key)" class="ml-2 border-l border-white/10 pl-1">
              <div v-for="item in sub.items" :key="item.to" class="group flex items-center gap-1">
                <router-link
                  :to="item.to"
                  role="menuitem"
                  :aria-label="item.label"
                  :aria-current="isCurrentRoute(item.to) ? 'page' : undefined"
                  class="nav-link flex flex-1 items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors"
                  active-class="nav-link-active"
                  @click="emit('navigate')"
                >
                  <component :is="item.icon" class="h-4 w-4 shrink-0" :aria-hidden="true" />
                  <span>{{ item.label }}</span>
                </router-link>
                <NavPinButton :path="item.to" :label="item.label" :class="pinVisibilityClass" />
              </div>
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
              class="nav-link flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors"
              active-class="nav-link-active"
              @click="emit('navigate')"
            >
              <component :is="item.icon" class="h-5 w-5 shrink-0" :aria-hidden="true" />
            </router-link>
          </template>
        </template>
      </div>

      <div class="sidebar-divider my-3 border-t" role="separator" aria-hidden="true" />

      <div v-for="item in bottomNavItems" :key="item.to" class="group flex items-center gap-1">
        <router-link
          :to="item.to"
          role="menuitem"
          :aria-label="item.label"
          :aria-current="isCurrentRoute(item.to) ? 'page' : undefined"
          class="nav-link flex flex-1 items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors"
          active-class="nav-link-active"
          @click="emit('navigate')"
        >
          <component :is="item.icon" class="h-5 w-5 shrink-0" :aria-hidden="true" />
          <span v-if="!collapsed">{{ item.label }}</span>
        </router-link>
        <NavPinButton v-if="!collapsed" :path="item.to" :label="item.label" :class="pinVisibilityClass" />
      </div>
    </nav>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ChevronLeftIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { useLocale } from '@/composables/useLocale'
import { useNavigation, type NavItem } from '@/composables/useNavigation'
import { useNavFavoritesStore } from '@/stores/navFavorites'
import NavPinButton from '@/components/layout/NavPinButton.vue'
import WorkspaceSwitcher from '@/components/layout/WorkspaceSwitcher.vue'
import OnGoLogo from '@/components/brand/OnGoLogo.vue'

defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  toggle: []
  navigate: []
}>()

const { t } = useLocale()
const { navGroups, bottomNavItems, allNavItems, isCurrentRoute, isSubGroupExpanded, toggleSubGroup } = useNavigation()
const navFavoritesStore = useNavFavoritesStore()

// 핀 버튼은 hover/focus 시에만 노출하고, 이미 즐겨찾기된 항목은 항상 노출
const pinVisibilityClass = 'opacity-0 group-hover:opacity-100 group-focus-within:opacity-100 aria-pressed:opacity-100'

// 저장된 즐겨찾기 경로 중 현재 네비게이션 정의에 존재하는 항목만 노출
// (라우트가 제거되면 조회에 실패하므로 안전하게 무시된다)
const favoriteItems = computed<NavItem[]>(() =>
  navFavoritesStore.favoritePaths
    .map((path) => allNavItems.value.find((item) => item.to === path))
    .filter((item): item is NavItem => item !== undefined),
)
</script>

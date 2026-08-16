<template>
  <Teleport to="body">
    <Transition name="menu-sheet">
      <div
        v-if="modelValue"
        class="fixed inset-0 z-50 flex flex-col justify-end"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="titleId"
      >
        <!-- Backdrop -->
        <div class="fixed inset-0 bg-black/50 backdrop-blur-sm" aria-hidden="true" @click="close" />

        <!-- Sheet panel -->
        <div
          ref="panelRef"
          class="menu-sheet-panel relative flex max-h-[86vh] w-full flex-col rounded-t-2xl border border-b-0 border-line bg-surface-card"
        >
          <!-- Header -->
          <div class="flex shrink-0 items-center gap-2 border-b px-4 py-3" style="border-color: var(--border-default)">
            <h2 :id="titleId" class="text-base font-semibold text-content">
              {{ t('nav.allMenus') }}
            </h2>
            <button
              type="button"
              class="ml-auto inline-flex h-11 w-11 items-center justify-center rounded-lg text-content-secondary transition-colors hover:bg-surface-raised"
              :aria-label="t('nav.closeMenu')"
              @click="close"
            >
              <XMarkIcon class="h-6 w-6" aria-hidden="true" />
            </button>
          </div>

          <!-- Menu tree -->
          <nav
            role="navigation"
            :aria-label="t('nav.mainNavigation')"
            class="min-h-0 flex-1 overflow-y-auto px-2 pb-6 pt-2"
          >
            <div v-if="capabilityLoading" class="space-y-2 px-2 py-4" aria-live="polite">
              <div v-for="row in 7" :key="row" class="h-11 animate-pulse rounded-lg bg-surface-raised" />
              <p class="pt-1 text-center text-[11px] text-content-tertiary">{{ t('redesign.rail.loading') }}</p>
            </div>
            <template v-else>
            <!-- Favorites -->
            <template v-if="favoriteItems.length > 0">
              <p class="px-3 py-1 text-[10px] font-bold uppercase tracking-[0.14em] text-content-tertiary">
                {{ t('nav.favorites') }}
              </p>
              <div v-for="item in favoriteItems" :key="`fav-${item.to}`" class="flex items-center gap-1">
                <router-link
                  :to="item.to"
                  role="menuitem"
                  :aria-label="item.label"
                  :aria-current="isCurrentRoute(item.to) ? 'page' : undefined"
                  class="flex min-h-[44px] flex-1 items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors"
                  :class="rowClass(item.to)"
                  @click="close"
                >
                  <component :is="item.icon" class="h-5 w-5 shrink-0" aria-hidden="true" />
                  <span>{{ item.label }}</span>
                </router-link>
                <NavPinButton :path="item.to" :label="item.label" size="md" />
              </div>
              <div class="my-2 border-t" style="border-color: var(--border-default)" role="separator" aria-hidden="true" />
            </template>

            <!-- Groups -->
            <div v-for="(group, groupIndex) in navGroups" :key="groupIndex" :class="groupIndex > 0 ? 'mt-3' : ''">
              <p
                v-if="group.label"
                class="px-3 py-1 text-[10px] font-bold uppercase tracking-[0.14em] text-content-tertiary"
              >
                {{ group.label }}
              </p>

              <div v-for="item in group.items" :key="item.to" class="flex items-center gap-1">
                <router-link
                  :to="item.to"
                  role="menuitem"
                  :aria-label="item.label"
                  :aria-current="isCurrentRoute(item.to) ? 'page' : undefined"
                  class="flex min-h-[44px] flex-1 items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors"
                  :class="rowClass(item.to)"
                  @click="close"
                >
                  <component :is="item.icon" class="h-5 w-5 shrink-0" aria-hidden="true" />
                  <span>{{ item.label }}</span>
                </router-link>
                <NavPinButton :path="item.to" :label="item.label" size="md" />
              </div>

              <!-- Sub-groups (collapsible, 사이드바와 상태 공유) -->
              <div v-for="sub in group.subGroups ?? []" :key="sub.key" class="mt-0.5">
                <button
                  type="button"
                  class="flex min-h-[44px] w-full items-center gap-2 rounded-lg px-3 py-2 text-xs font-medium text-content-secondary transition-colors hover:bg-surface-raised"
                  :aria-expanded="isSubGroupExpanded(sub.key)"
                  @click="toggleSubGroup(sub.key)"
                >
                  <ChevronRightIcon
                    class="h-3.5 w-3.5 shrink-0 transition-transform duration-200"
                    :class="isSubGroupExpanded(sub.key) ? 'rotate-90' : ''"
                    aria-hidden="true"
                  />
                  <span>{{ sub.label }}</span>
                  <span class="ml-auto text-[10px] text-content-tertiary">{{ sub.items.length }}</span>
                </button>
                <div v-if="isSubGroupExpanded(sub.key)" class="ml-2 border-l border-line pl-1">
                  <div v-for="item in sub.items" :key="item.to" class="flex items-center gap-1">
                    <router-link
                      :to="item.to"
                      role="menuitem"
                      :aria-label="item.label"
                      :aria-current="isCurrentRoute(item.to) ? 'page' : undefined"
                      class="flex min-h-[44px] flex-1 items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors"
                      :class="rowClass(item.to)"
                      @click="close"
                    >
                      <component :is="item.icon" class="h-5 w-5 shrink-0" aria-hidden="true" />
                      <span>{{ item.label }}</span>
                    </router-link>
                    <NavPinButton :path="item.to" :label="item.label" size="md" />
                  </div>
                </div>
              </div>
            </div>

            <div class="my-3 border-t" style="border-color: var(--border-default)" role="separator" aria-hidden="true" />

            <!-- Bottom items -->
            <div v-for="item in bottomNavItems" :key="item.to" class="flex items-center gap-1">
              <router-link
                :to="item.to"
                role="menuitem"
                :aria-label="item.label"
                :aria-current="isCurrentRoute(item.to) ? 'page' : undefined"
                class="flex min-h-[44px] flex-1 items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors"
                :class="rowClass(item.to)"
                @click="close"
              >
                <component :is="item.icon" class="h-5 w-5 shrink-0" aria-hidden="true" />
                <span>{{ item.label }}</span>
              </router-link>
              <NavPinButton :path="item.to" :label="item.label" size="md" />
            </div>
            </template>
          </nav>

          <!-- 모바일에서는 상단 프로필 메뉴가 보이지 않으므로 계정·로그아웃을 시트에 둔다. -->
          <div class="flex shrink-0 items-center gap-3 border-t border-line bg-surface-card px-4 py-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))]">
            <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-accent-dim text-[12px] font-bold text-accent">
              {{ userInitial }}
            </span>
            <div class="min-w-0 flex-1">
              <p class="truncate text-[12px] font-semibold text-content">{{ displayName }}</p>
              <p class="truncate text-[11px] text-content-tertiary">{{ userEmail }}</p>
            </div>
            <button
              type="button"
              class="inline-flex min-h-11 shrink-0 items-center gap-1.5 rounded-lg px-2.5 text-[11px] font-semibold text-content-secondary transition-colors hover:bg-error-subtle hover:text-error-strong"
              @click="handleLogout"
            >
              <ArrowRightOnRectangleIcon class="h-4 w-4" aria-hidden="true" />
              {{ t('nav.logout') }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick, onBeforeUnmount, useId } from 'vue'
import { ArrowRightOnRectangleIcon, ChevronRightIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { useFocusTrap } from '@/composables/useAccessibility'
import { useLocale } from '@/composables/useLocale'
import { useNavigation, type NavItem } from '@/composables/useNavigation'
import { useNavFavoritesStore } from '@/stores/navFavorites'
import { useAuthStore } from '@/stores/auth'
import NavPinButton from '@/components/layout/NavPinButton.vue'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const id = useId()
const titleId = `menu-sheet-title-${id}`

const { t } = useLocale()
const { navGroups, bottomNavItems, allNavItems, isCurrentRoute, isSubGroupExpanded, toggleSubGroup, capabilityLoading } = useNavigation()
const navFavoritesStore = useNavFavoritesStore()
const authStore = useAuthStore()

const panelRef = ref<HTMLElement | null>(null)
const previousActiveElement = ref<HTMLElement | null>(null)
const { activate: activateFocusTrap, deactivate: deactivateFocusTrap } = useFocusTrap(panelRef)

// 삭제된 라우트를 가리키는 즐겨찾기는 조회에서 자연스럽게 제외된다
const favoriteItems = computed<NavItem[]>(() =>
  navFavoritesStore.favoritePaths
    .map((path) => allNavItems.value.find((item) => item.to === path))
    .filter((item): item is NavItem => item !== undefined),
)

const displayName = computed(() => authStore.user?.nickname || authStore.user?.name || authStore.user?.email || '—')
const userEmail = computed(() => authStore.user?.email || '')
const userInitial = computed(() => displayName.value.charAt(0).toUpperCase())

function rowClass(to: string): string {
  return isCurrentRoute(to)
    ? 'bg-accent-dim font-semibold text-content'
    : 'text-content-secondary hover:bg-surface-raised hover:text-content'
}

function close() {
  emit('update:modelValue', false)
}

function handleLogout() {
  close()
  void authStore.logout()
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    close()
  }
}

function unlockScroll() {
  document.body.style.overflow = ''
}

watch(
  () => props.modelValue,
  async (isOpen) => {
    if (isOpen) {
      previousActiveElement.value = document.activeElement as HTMLElement
      document.addEventListener('keydown', handleKeydown)
      document.body.style.overflow = 'hidden'
      await nextTick()
      activateFocusTrap()
    } else {
      document.removeEventListener('keydown', handleKeydown)
      deactivateFocusTrap()
      unlockScroll()
      previousActiveElement.value?.focus()
    }
  },
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown)
  deactivateFocusTrap()
  unlockScroll()
})
</script>

<style scoped>
.menu-sheet-enter-active,
.menu-sheet-leave-active {
  transition: opacity 200ms ease;
}

.menu-sheet-enter-from,
.menu-sheet-leave-to {
  opacity: 0;
}

.menu-sheet-enter-active .menu-sheet-panel,
.menu-sheet-leave-active .menu-sheet-panel {
  transition: transform 200ms ease;
}

.menu-sheet-enter-from .menu-sheet-panel,
.menu-sheet-leave-to .menu-sheet-panel {
  transform: translateY(100%);
}

@media (prefers-reduced-motion: reduce) {
  .menu-sheet-enter-active,
  .menu-sheet-leave-active,
  .menu-sheet-enter-active .menu-sheet-panel,
  .menu-sheet-leave-active .menu-sheet-panel {
    transition: none;
  }
}
</style>

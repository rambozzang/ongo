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
          class="glass-elevated relative flex max-h-[86vh] w-full flex-col rounded-t-2xl"
        >
          <!-- Header -->
          <div class="flex shrink-0 items-center gap-2 border-b px-4 py-3" style="border-color: var(--border-default)">
            <h2 :id="titleId" class="text-base font-semibold text-gray-900 dark:text-gray-100">
              {{ t('nav.allMenus') }}
            </h2>
            <button
              type="button"
              class="ml-auto inline-flex h-11 w-11 items-center justify-center rounded-lg text-gray-500 transition-colors hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700"
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
            <!-- Favorites -->
            <template v-if="favoriteItems.length > 0">
              <p class="px-3 py-1 text-[10px] font-bold uppercase tracking-[0.14em] text-gray-400 dark:text-gray-500">
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
                class="px-3 py-1 text-[10px] font-bold uppercase tracking-[0.14em] text-gray-400 dark:text-gray-500"
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
                  class="flex min-h-[44px] w-full items-center gap-2 rounded-lg px-3 py-2 text-xs font-medium text-gray-500 transition-colors hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700"
                  :aria-expanded="isSubGroupExpanded(sub.key)"
                  @click="toggleSubGroup(sub.key)"
                >
                  <ChevronRightIcon
                    class="h-3.5 w-3.5 shrink-0 transition-transform duration-200"
                    :class="isSubGroupExpanded(sub.key) ? 'rotate-90' : ''"
                    aria-hidden="true"
                  />
                  <span>{{ sub.label }}</span>
                  <span class="ml-auto text-[10px] text-gray-400 dark:text-gray-500">{{ sub.items.length }}</span>
                </button>
                <div v-if="isSubGroupExpanded(sub.key)" class="ml-2 border-l border-gray-200 pl-1 dark:border-gray-700">
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
          </nav>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick, onBeforeUnmount, useId } from 'vue'
import { ChevronRightIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { useFocusTrap } from '@/composables/useAccessibility'
import { useLocale } from '@/composables/useLocale'
import { useNavigation, type NavItem } from '@/composables/useNavigation'
import { useNavFavoritesStore } from '@/stores/navFavorites'
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
const { navGroups, bottomNavItems, allNavItems, isCurrentRoute, isSubGroupExpanded, toggleSubGroup } = useNavigation()
const navFavoritesStore = useNavFavoritesStore()

const panelRef = ref<HTMLElement | null>(null)
const previousActiveElement = ref<HTMLElement | null>(null)
const { activate: activateFocusTrap, deactivate: deactivateFocusTrap } = useFocusTrap(panelRef)

// 삭제된 라우트를 가리키는 즐겨찾기는 조회에서 자연스럽게 제외된다
const favoriteItems = computed<NavItem[]>(() =>
  navFavoritesStore.favoritePaths
    .map((path) => allNavItems.value.find((item) => item.to === path))
    .filter((item): item is NavItem => item !== undefined),
)

function rowClass(to: string): string {
  return isCurrentRoute(to)
    ? 'bg-primary-50 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
    : 'text-gray-700 hover:bg-gray-100 dark:text-gray-200 dark:hover:bg-gray-700'
}

function close() {
  emit('update:modelValue', false)
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

.menu-sheet-enter-active .glass-elevated,
.menu-sheet-leave-active .glass-elevated {
  transition: transform 200ms ease;
}

.menu-sheet-enter-from .glass-elevated,
.menu-sheet-leave-to .glass-elevated {
  transform: translateY(100%);
}

@media (prefers-reduced-motion: reduce) {
  .menu-sheet-enter-active,
  .menu-sheet-leave-active,
  .menu-sheet-enter-active .glass-elevated,
  .menu-sheet-leave-active .glass-elevated {
    transition: none;
  }
}
</style>

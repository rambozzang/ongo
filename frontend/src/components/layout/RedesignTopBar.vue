<template>
  <header
    class="flex min-h-14 flex-none items-center gap-2.5 border-b border-line bg-surface px-3 tablet:gap-3.5 tablet:px-[18px]"
  >
    <router-link to="/today" class="tablet:hidden" :aria-label="t('nav.home')">
      <OnGoLogo size="sm" />
    </router-link>

    <!-- 화면 제목 -->
    <div class="flex min-w-0 items-baseline gap-2.5">
      <div id="page-title" role="heading" aria-level="1" class="truncate text-[14px] font-semibold text-content">{{ title }}</div>
      <p v-if="subtitle" class="truncate text-[12px] text-content-tertiary">{{ subtitle }}</p>
    </div>

    <!-- 우측 액션. 모두 nowrap — 한국어 라벨이 글자 단위로 쪼개지는 것을 막는다 -->
    <div class="ml-auto flex min-w-0 flex-none items-center gap-1.5 tablet:gap-2">
      <button
        ref="searchInput"
        type="button"
        class="group hidden min-h-9 flex-[0_1_240px] items-center gap-2 rounded-lg border border-line-soft bg-transparent px-2.5 text-left text-[12px] text-content-tertiary transition-colors hover:border-line-hover hover:text-content tablet:flex"
        :aria-label="t('redesign.topbar.searchPlaceholder')"
        @keydown.escape="blurSearch"
        @click="openSearch"
      >
        <MagnifyingGlassIcon class="h-4 w-4 shrink-0" aria-hidden="true" />
        <span class="min-w-0 flex-1 truncate">{{ t('redesign.topbar.searchPlaceholder') }}</span>
        <kbd class="shrink-0 rounded border border-line-soft px-1 font-mono text-[10px] text-content-tertiary group-hover:text-content">⌘K</kbd>
      </button>

      <button
        type="button"
        class="flex min-h-11 min-w-11 items-center justify-center rounded-lg text-content-tertiary transition-colors hover:bg-surface-raised hover:text-content tablet:hidden"
        :aria-label="t('redesign.topbar.searchPlaceholder')"
        @click="openSearch"
      >
        <MagnifyingGlassIcon class="h-5 w-5" aria-hidden="true" />
      </button>

      <button type="button" class="btn-secondary hidden whitespace-nowrap !text-[12px] desktop:inline-flex" @click="emit('open-import')">
        {{ t('redesign.topbar.importUrl') }}
      </button>

      <router-link to="/compose" class="btn-primary whitespace-nowrap !px-2.5 !text-[12px] tablet:!px-3.5">
        <ArrowUpTrayIcon class="h-4 w-4 tablet:hidden" aria-hidden="true" />
        <span class="hidden tablet:inline">{{ t('redesign.topbar.newUpload') }}</span>
        <span class="sr-only tablet:hidden">{{ t('redesign.topbar.newUpload') }}</span>
      </router-link>

      <!-- 현재 리디자인 셸에서도 항상 접근 가능한 계정 메뉴 -->
      <div ref="profileRef" class="relative">
        <button
          ref="profileButton"
          type="button"
          class="flex min-h-11 min-w-11 items-center gap-1.5 rounded-lg px-1.5 text-content transition-colors hover:bg-surface-raised focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
          :aria-label="t('redesign.topbar.profileMenu')"
          :aria-expanded="profileOpen"
          aria-haspopup="menu"
          @click="toggleProfileMenu"
        >
          <span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-accent-dim text-[11px] font-bold text-accent">
            {{ userInitial }}
          </span>
          <span class="hidden max-w-28 truncate text-[11px] font-semibold desktop:inline">{{ displayName }}</span>
          <ChevronDownIcon class="hidden h-3.5 w-3.5 text-content-tertiary desktop:block" aria-hidden="true" />
        </button>

        <div
          v-if="profileOpen"
          ref="profileMenu"
          role="menu"
          tabindex="-1"
          :aria-label="t('redesign.topbar.profileMenu')"
          class="absolute right-0 top-full z-50 mt-2 w-60 overflow-hidden rounded-xl border border-line bg-surface-card py-1 shadow-lg"
          @keydown="handleProfileKeydown"
        >
          <div class="border-b border-line px-3.5 py-3">
            <p class="truncate text-[12px] font-semibold text-content">{{ displayName }}</p>
            <p class="mt-0.5 truncate text-[11px] text-content-tertiary">{{ userEmail }}</p>
          </div>
          <router-link
            to="/settings-v2"
            role="menuitem"
            class="flex min-h-11 w-full items-center gap-2.5 px-3.5 py-2.5 text-[12px] text-content-secondary transition-colors hover:bg-surface-raised hover:text-content"
            @click="closeProfileMenu()"
          >
            <Cog6ToothIcon class="h-4 w-4" aria-hidden="true" />
            {{ t('nav.settings') }}
          </router-link>
          <button
            type="button"
            role="menuitem"
            class="flex min-h-11 w-full items-center gap-2.5 px-3.5 py-2.5 text-left text-[12px] text-content-secondary transition-colors hover:bg-surface-raised hover:text-content"
            @click="handleLogout"
          >
            <ArrowRightOnRectangleIcon class="h-4 w-4" aria-hidden="true" />
            {{ t('nav.logout') }}
          </button>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { onClickOutside } from '@vueuse/core'
import { ArrowRightOnRectangleIcon, ArrowUpTrayIcon, ChevronDownIcon, Cog6ToothIcon, MagnifyingGlassIcon } from '@heroicons/vue/24/outline'
import { useLocale } from '@/composables/useLocale'
import { useAuthStore } from '@/stores/auth'
import OnGoLogo from '@/components/brand/OnGoLogo.vue'

/**
 * 상단바 (56px) — 화면 제목/부제 + 검색 + 보조/주요 액션.
 *
 * `/` 키로 검색에 포커스한다. 입력 중에는 가로채지 않는다.
 */
defineProps<{
  title: string
  subtitle?: string
}>()

const emit = defineEmits<{ 'open-import': [] }>()

const { t } = useLocale()
const authStore = useAuthStore()
const searchInput = ref<HTMLButtonElement | null>(null)
const profileOpen = ref(false)
const profileRef = ref<HTMLElement | null>(null)
const profileButton = ref<HTMLButtonElement | null>(null)
const profileMenu = ref<HTMLElement | null>(null)
const user = computed(() => authStore.user)
const displayName = computed(() => user.value?.nickname || user.value?.name || 'User')
const userEmail = computed(() => user.value?.email || '')
const userInitial = computed(() => displayName.value.charAt(0).toUpperCase())

onClickOutside(profileRef, () => {
  closeProfileMenu()
})

function toggleProfileMenu() {
  if (profileOpen.value) {
    closeProfileMenu(true)
    return
  }
  profileOpen.value = true
  void nextTick(() => {
    profileMenu.value?.querySelector<HTMLElement>('[role="menuitem"]')?.focus()
  })
}

function closeProfileMenu(restoreFocus = false) {
  profileOpen.value = false
  // The trigger remains mounted while the menu is removed, so restore focus
  // immediately and keep keyboard users in the same interaction context.
  if (restoreFocus) {
    const trigger = profileButton.value ?? profileRef.value?.querySelector<HTMLButtonElement>('button')
    trigger?.focus()
  }
}

function handleProfileKeydown(event: KeyboardEvent) {
  const items = Array.from(profileMenu.value?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [])
  if (event.key === 'Escape') {
    event.preventDefault()
    closeProfileMenu(true)
    return
  }
  if (!items.length || !['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const current = items.indexOf(document.activeElement as HTMLElement)
  const nextIndex = event.key === 'Home'
    ? 0
    : event.key === 'End'
      ? items.length - 1
      : (current + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length
  items[nextIndex]?.focus()
}

function isTypingTarget(el: EventTarget | null): boolean {
  if (!(el instanceof HTMLElement)) return false
  return el.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(el.tagName)
}

function onKeydown(e: KeyboardEvent) {
  if (e.key !== '/' || e.metaKey || e.ctrlKey || e.altKey) return
  if (isTypingTarget(e.target)) return
  e.preventDefault()
  searchInput.value?.focus()
}

function blurSearch() {
  searchInput.value?.blur()
}

function openSearch() {
  window.dispatchEvent(new CustomEvent('ongo:open-search'))
}

function handleLogout() {
  closeProfileMenu()
  void authStore.logout()
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

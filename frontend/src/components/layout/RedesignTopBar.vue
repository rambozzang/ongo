<template>
  <header
    class="flex h-14 flex-none items-center gap-3.5 border-b border-line bg-surface px-[18px]"
  >
    <router-link to="/today" class="tablet:hidden" :aria-label="t('nav.home')">
      <OnGoLogo size="sm" />
    </router-link>

    <!-- 화면 제목 -->
    <div class="flex min-w-0 items-baseline gap-2.5">
      <h1 class="whitespace-nowrap text-[14px] font-semibold text-content">{{ title }}</h1>
      <p v-if="subtitle" class="truncate text-[12px] text-content-tertiary">{{ subtitle }}</p>
    </div>

    <!-- 우측 액션. 모두 nowrap — 한국어 라벨이 글자 단위로 쪼개지는 것을 막는다 -->
    <div class="ml-auto flex min-w-0 flex-none items-center gap-1.5 tablet:gap-2">
      <div class="relative hidden flex-[0_1_240px] tablet:block" style="min-width: 92px">
        <input
          ref="searchInput"
          type="search"
          readonly
          :placeholder="t('redesign.topbar.searchPlaceholder')"
          class="w-full rounded-lg border border-line-soft bg-transparent py-[7px] pl-[10px] pr-8 text-[12px] text-content placeholder:text-content-tertiary focus:border-accent focus:outline-none"
          @keydown.escape="blurSearch"
          @click="openSearch"
          @focus="openSearch"
        />
        <kbd
          class="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 rounded border border-line-soft px-1 font-mono text-[10px] text-content-tertiary"
        >
          /
        </kbd>
      </div>

      <button type="button" class="btn-secondary hidden whitespace-nowrap !text-[12px] desktop:inline-flex" @click="emit('open-import')">
        {{ t('redesign.topbar.importUrl') }}
      </button>

      <router-link to="/compose" class="btn-primary whitespace-nowrap !text-[12px]">
        {{ t('redesign.topbar.newUpload') }}
      </router-link>

      <!-- 현재 리디자인 셸에서도 항상 접근 가능한 계정 메뉴 -->
      <div ref="profileRef" class="relative">
        <button
          type="button"
          class="flex min-h-11 min-w-11 items-center gap-1.5 rounded-lg px-1.5 text-content transition-colors hover:bg-surface-tertiary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent"
          :aria-label="t('redesign.topbar.profileMenu')"
          :aria-expanded="profileOpen"
          aria-haspopup="menu"
          @click="profileOpen = !profileOpen"
        >
          <span class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-accent-dim text-[11px] font-bold text-accent">
            {{ userInitial }}
          </span>
          <span class="hidden max-w-28 truncate text-[11px] font-semibold desktop:inline">{{ displayName }}</span>
          <ChevronDownIcon class="hidden h-3.5 w-3.5 text-content-tertiary desktop:block" aria-hidden="true" />
        </button>

        <div
          v-if="profileOpen"
          role="menu"
          :aria-label="t('redesign.topbar.profileMenu')"
          class="absolute right-0 top-full z-50 mt-2 w-60 overflow-hidden rounded-xl border border-line bg-surface-card py-1 shadow-lg"
        >
          <div class="border-b border-line px-3.5 py-3">
            <p class="truncate text-[12px] font-semibold text-content">{{ displayName }}</p>
            <p class="mt-0.5 truncate text-[11px] text-content-tertiary">{{ userEmail }}</p>
          </div>
          <router-link
            to="/settings-v2"
            role="menuitem"
            class="flex min-h-11 w-full items-center gap-2.5 px-3.5 py-2.5 text-[12px] text-content-secondary transition-colors hover:bg-surface-tertiary hover:text-content"
            @click="profileOpen = false"
          >
            <Cog6ToothIcon class="h-4 w-4" aria-hidden="true" />
            {{ t('nav.settings') }}
          </router-link>
          <button
            type="button"
            role="menuitem"
            class="flex min-h-11 w-full items-center gap-2.5 px-3.5 py-2.5 text-left text-[12px] text-content-secondary transition-colors hover:bg-surface-tertiary hover:text-content"
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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onClickOutside } from '@vueuse/core'
import { ArrowRightOnRectangleIcon, ChevronDownIcon, Cog6ToothIcon } from '@heroicons/vue/24/outline'
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
const searchInput = ref<HTMLInputElement | null>(null)
const profileOpen = ref(false)
const profileRef = ref<HTMLElement | null>(null)
const user = computed(() => authStore.user)
const displayName = computed(() => user.value?.nickname || user.value?.name || 'User')
const userEmail = computed(() => user.value?.email || '')
const userInitial = computed(() => displayName.value.charAt(0).toUpperCase())

onClickOutside(profileRef, () => {
  profileOpen.value = false
})

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
  profileOpen.value = false
  void authStore.logout()
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

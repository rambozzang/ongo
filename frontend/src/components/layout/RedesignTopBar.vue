<template>
  <header
    class="flex h-14 flex-none items-center gap-3.5 border-b border-line bg-surface px-[18px]"
  >
    <!-- 화면 제목 -->
    <div class="flex min-w-0 items-baseline gap-2.5">
      <h1 class="whitespace-nowrap text-[14px] font-semibold text-content">{{ title }}</h1>
      <p v-if="subtitle" class="truncate text-[12px] text-content-tertiary">{{ subtitle }}</p>
    </div>

    <!-- 우측 액션. 모두 nowrap — 한국어 라벨이 글자 단위로 쪼개지는 것을 막는다 -->
    <div class="ml-auto flex flex-none items-center gap-2">
      <div class="relative hidden flex-[0_1_240px] tablet:block" style="min-width: 92px">
        <input
          ref="searchInput"
          v-model="query"
          type="search"
          :placeholder="t('redesign.topbar.searchPlaceholder')"
          class="w-full rounded-lg border border-line-soft bg-transparent py-[7px] pl-[10px] pr-8 text-[12px] text-content placeholder:text-content-tertiary focus:border-accent focus:outline-none"
          @keydown.escape="blurSearch"
        />
        <kbd
          class="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 rounded border border-line-soft px-1 font-mono text-[10px] text-content-tertiary"
        >
          /
        </kbd>
      </div>

      <button type="button" class="btn-secondary hidden whitespace-nowrap !text-[12px] desktop:inline-flex" @click="emit('bulk-import')">
        {{ t('redesign.topbar.bulkImport') }}
      </button>

      <router-link to="/compose" class="btn-primary whitespace-nowrap !text-[12px]">
        {{ t('redesign.topbar.newUpload') }}
      </router-link>
    </div>
  </header>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useLocale } from '@/composables/useLocale'

/**
 * 상단바 (56px) — 화면 제목/부제 + 검색 + 보조/주요 액션.
 *
 * `/` 키로 검색에 포커스한다. 입력 중에는 가로채지 않는다.
 */
defineProps<{
  title: string
  subtitle?: string
}>()

const emit = defineEmits<{ 'bulk-import': [] }>()

const { t } = useLocale()
const query = ref('')
const searchInput = ref<HTMLInputElement | null>(null)

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

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

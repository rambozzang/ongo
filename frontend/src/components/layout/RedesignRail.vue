<template>
  <aside
    class="flex w-[216px] flex-none flex-col border-r border-line bg-surface-rail px-3 py-4"
  >
    <!-- 워드마크 -->
    <router-link to="/today" class="mb-4 flex items-center px-1" @click="emit('navigate')">
      <OnGoLogo size="md" />
    </router-link>

    <!-- 내비: 자주 쓰는 작업은 고정하고, 나머지는 필요할 때만 펼친다. -->
    <nav class="min-h-0 flex-1 overflow-y-auto scrollbar-dark" :aria-label="t('nav.mainNavigation')">
      <section class="space-y-0.5">
        <p class="px-[9px] pb-1 text-[9.5px] font-bold uppercase tracking-[0.12em] text-content-rail-quaternary">
          {{ t('redesign.rail.coreTasks') }}
        </p>
        <router-link
          v-for="item in coreItems"
          :key="item.to"
          :to="item.to"
          class="flex items-center gap-2.5 rounded-lg px-[9px] py-2 text-[12.5px] transition-colors duration-150"
          :class="
            isActive(item.to)
              ? 'border border-line-control bg-accent-dim font-bold text-content'
              : 'font-medium text-content-rail-secondary hover:bg-surface-rail-raised hover:text-content-rail'
          "
          :aria-current="isActive(item.to) ? 'page' : undefined"
          @click="emit('navigate')"
        >
          <component :is="item.icon" class="h-4 w-4 shrink-0" aria-hidden="true" />
          <span class="truncate">{{ item.label }}</span>
          <span
            v-if="badgeFor(item.to)"
            class="ml-auto shrink-0 font-mono text-[10px]"
            :class="item.to === '/channels-v2' && shell.badges.channels ? 'text-bad' : 'text-content-rail-tertiary'"
          >
            {{ badgeFor(item.to) }}
          </span>
        </router-link>
      </section>

      <section class="mt-4 border-t border-line pt-3">
        <button
          type="button"
          class="flex min-h-9 w-full items-center gap-2 rounded-lg px-[9px] py-2 text-left text-[11px] font-bold uppercase tracking-[0.1em] text-content-rail-secondary transition-colors hover:bg-surface-rail-raised hover:text-content-rail"
          :aria-expanded="toolsExpanded"
          :aria-controls="toolsPanelId"
          @click="toolsExpanded = !toolsExpanded"
        >
          <ChevronRightIcon
            class="h-3.5 w-3.5 shrink-0 transition-transform duration-150"
            :class="toolsExpanded ? 'rotate-90' : ''"
            aria-hidden="true"
          />
          <span>{{ t('redesign.rail.allTools') }}</span>
          <span class="ml-auto font-mono text-[10px] font-normal normal-case tracking-normal text-content-rail-tertiary">
            {{ secondaryItemCount }}
          </span>
        </button>

        <div v-if="toolsExpanded" :id="toolsPanelId" class="mt-2 space-y-3">
          <section v-for="section in secondarySections" :key="section.key" class="space-y-0.5">
            <p
              v-if="section.label"
              class="px-[9px] pb-1 text-[9.5px] font-bold uppercase tracking-[0.12em] text-content-rail-quaternary"
            >
              {{ section.label }}
            </p>
            <router-link
              v-for="item in section.items"
              :key="item.to"
              :to="item.to"
              class="flex items-center gap-2.5 rounded-lg px-[9px] py-2 text-[12.5px] transition-colors duration-150"
              :class="
                isActive(item.to)
                  ? 'border border-line-control bg-accent-dim font-bold text-content'
                  : 'font-medium text-content-rail-secondary hover:bg-surface-rail-raised hover:text-content-rail'
              "
              :aria-current="isActive(item.to) ? 'page' : undefined"
              @click="emit('navigate')"
            >
              <component :is="item.icon" class="h-4 w-4 shrink-0" aria-hidden="true" />
              <span class="truncate">{{ item.label }}</span>
              <span
                v-if="badgeFor(item.to)"
                class="ml-auto shrink-0 font-mono text-[10px]"
                :class="item.to === '/channels-v2' && shell.badges.channels ? 'text-bad' : 'text-content-rail-tertiary'"
              >
                {{ badgeFor(item.to) }}
              </span>
            </router-link>
          </section>

          <p class="px-[9px] text-[10.5px] leading-4 text-content-rail-tertiary">
            {{ t('redesign.rail.allToolsHint') }}
          </p>
        </div>
      </section>
    </nav>

    <!-- 이번 달 업로드 진행 -->
    <div class="mt-4 px-1">
      <div class="flex items-center justify-between text-[11px] text-content-rail-tertiary">
        <span>{{ t('redesign.rail.monthlyUploads') }}</span>
        <span class="font-mono text-content-rail">{{ quota.used }}/{{ quota.limit }}</span>
      </div>
      <div class="mt-1.5 h-1 overflow-hidden rounded-full bg-line">
        <div class="h-full rounded-full bg-accent transition-[width] duration-300" :style="{ width: quotaPercent }" />
      </div>
    </div>

    <!-- 계정 -->
    <router-link
      to="/settings-v2"
      class="mt-3.5 flex items-center gap-2 rounded-lg px-1 py-2 transition-colors hover:bg-surface-rail-raised"
      @click="emit('navigate')"
    >
      <span
        class="flex h-[26px] w-[26px] shrink-0 items-center justify-center rounded-full bg-surface-raised text-[11px] font-semibold text-content"
      >
        {{ initial }}
      </span>
      <span class="min-w-0 flex-1">
        <span class="block truncate text-[12px] font-semibold text-content-rail">{{ displayName }}</span>
        <span class="block truncate text-[10px] text-content-rail-tertiary">{{ email }}</span>
      </span>
    </router-link>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronRightIcon } from '@heroicons/vue/24/outline'
import { useLocale } from '@/composables/useLocale'
import { useAuthStore } from '@/stores/auth'
import { useRedesignShellStore } from '@/stores/redesignShell'
import { useNavigation } from '@/composables/useNavigation'
import OnGoLogo from '@/components/brand/OnGoLogo.vue'

/**
 * 좌측 고정 레일 (216px) — 2026-08 리디자인.
 *
 * 기존 내비게이션 단일 소스를 재사용해 모든 메뉴를 리디자인 레일에서 접근한다.
 * 핵심 작업은 상단에, 나머지 기능은 같은 밀도와 토큰으로 이어진다.
 */
const emit = defineEmits<{ navigate: [] }>()

const route = useRoute()
const { t } = useLocale()
const authStore = useAuthStore()
const shell = useRedesignShellStore()
const { navGroups, bottomNavItems } = useNavigation()

const CORE_PATHS = ['/today', '/compose', '/calendar-v2', '/inbox-v2', '/performance', '/channels-v2']
const toolsPanelId = 'redesign-rail-tools'
const toolsExpanded = ref(false)

const sections = computed(() => {
  const grouped = navGroups.value.flatMap((group, groupIndex) => {
    const primary = group.items.length > 0
      ? [{ key: `group-${groupIndex}`, label: group.label, items: group.items }]
      : []
    const subGroups = (group.subGroups ?? []).map((sub, subIndex) => ({
      key: `group-${groupIndex}-${sub.key}`,
      // Empty groups (such as the review bucket) still need their parent
      // label in the compact rail; otherwise the group name disappears when
      // the shared navigation is flattened into sections.
      label: group.items.length === 0 && subIndex === 0 && group.label
        ? group.label
        : sub.label,
      items: sub.items,
    }))
    return [...primary, ...subGroups]
  })

  return [...grouped, { key: 'utility', label: undefined, items: bottomNavItems.value }]
})

const allItems = computed(() => sections.value.flatMap((section) => section.items))
const coreItems = computed(() => CORE_PATHS
  .map((path) => allItems.value.find((item) => item.to === path))
  .filter((item): item is NonNullable<typeof item> => Boolean(item)))
const secondarySections = computed(() => sections.value
  .map((section) => ({ ...section, items: section.items.filter((item) => !CORE_PATHS.includes(item.to)) }))
  .filter((section) => section.items.length > 0))
const secondaryItemCount = computed(() => secondarySections.value.reduce((count, section) => count + section.items.length, 0))

watch(
  () => route.path,
  (path) => {
    // Deep links remain discoverable: entering a secondary feature opens the tools drawer.
    if (!CORE_PATHS.includes(path) && !CORE_PATHS.some((corePath) => path.startsWith(`${corePath}/`))) {
      toolsExpanded.value = true
    }
  },
  { immediate: true },
)

function badgeFor(path: string): string {
  if (path === '/today') return shell.badges.today
  if (path === '/inbox-v2') return shell.badges.inbox
  if (path === '/calendar-v2') return shell.badges.calendar
  if (path === '/channels-v2') return shell.badges.channels
  return ''
}

// 하위 경로까지 활성으로 본다 (/compose/123 도 '새 업로드' 로 표시)
const isActive = (to: string) => route.path === to || route.path.startsWith(`${to}/`)

const quota = computed(() => shell.uploadQuota)
const quotaPercent = computed(() => {
  if (!quota.value.limit) return '0%'
  return `${Math.min(100, Math.round((quota.value.used / quota.value.limit) * 100))}%`
})

const displayName = computed(() => authStore.user?.name || authStore.user?.email || '—')
const email = computed(() => authStore.user?.email || '')
const initial = computed(() => displayName.value.charAt(0).toUpperCase())
</script>

<template>
  <div class="min-h-full bg-surface-base px-4 py-5 text-content tablet:px-[22px] tablet:py-6">
    <header class="mb-[18px]">
      <p class="font-mono text-[10px] uppercase tracking-[0.16em] text-content-tertiary">{{ t('nav.settings') }}</p>
      <h1 class="mt-1 text-[26px] font-bold tracking-[-0.02em] text-content">{{ t('settings.title') }}</h1>
      <p class="mt-1 text-[12px] text-content-tertiary">{{ t('settings.description') }}</p>
    </header>

    <div class="grid min-h-[620px] gap-0 overflow-hidden rounded-[11px] border border-line bg-surface-card desktop:grid-cols-[194px_minmax(0,1fr)]">
      <aside class="border-b border-line bg-surface-input p-3 desktop:border-b-0 desktop:border-r">
        <p class="px-2 pb-2 font-mono text-[10px] uppercase tracking-[0.14em] text-content-tertiary">{{ t('nav.settings') }}</p>
        <nav class="flex gap-1 overflow-x-auto desktop:flex-col">
          <button
            v-for="item in navigation"
            :key="item.key"
            type="button"
            class="shrink-0 rounded-lg px-2.5 py-2 text-left text-[12.5px] transition-colors duration-150"
            :class="activeSection === item.key ? 'bg-accent-dim font-bold text-accent' : 'font-medium text-content-secondary hover:bg-surface-raised hover:text-content'"
            @click="activeSection = item.key"
          >
            {{ item.label }}
          </button>
        </nav>
      </aside>

      <main class="min-w-0 max-w-[880px] p-4 tablet:p-6">
        <template v-if="activeSection === 'automation'">
          <div class="mb-4 flex items-end justify-between gap-3">
            <div>
              <h2 class="text-[15px] font-bold text-content">{{ t('automation.title') }}</h2>
              <p class="mt-1 text-[12px] text-content-tertiary">{{ t('automation.description') }}</p>
            </div>
            <RouterLink to="/automation" class="text-[11px] font-semibold text-accent hover:text-accent-hover">{{ t('automation.newRule') }}</RouterLink>
          </div>

          <div v-if="automationStore.loading" class="space-y-2">
            <div v-for="slot in 3" :key="slot" class="h-[70px] animate-pulse rounded-lg border border-line bg-surface-input" />
          </div>
          <div v-else-if="!automationStore.rules.length" class="rounded-lg border border-dashed border-line-control px-5 py-12 text-center">
            <AdjustmentsHorizontalIcon class="mx-auto mb-2 h-7 w-7 text-content-quaternary" />
            <p class="text-[12px] font-semibold text-content-secondary">{{ t('automation.emptyRulesTitle') }}</p>
            <p class="mt-1 text-[11px] text-content-tertiary">{{ t('automation.emptyRulesDesc') }}</p>
            <RouterLink to="/automation" class="mt-3 inline-block text-[11px] font-semibold text-accent">{{ t('automation.createFirstRule') }}</RouterLink>
          </div>
          <div v-else class="overflow-hidden rounded-[11px] border border-line">
            <div v-for="rule in automationStore.rules" :key="rule.id" class="flex min-h-[70px] items-center gap-4 border-b border-line-row px-4 py-3 last:border-0">
              <div class="min-w-0 flex-1">
                <h3 class="truncate text-[12.5px] font-semibold text-content">{{ rule.name }}</h3>
                <p class="mt-1 line-clamp-2 text-[11.5px] leading-4 text-content-tertiary">{{ rule.description || t('automation.description') }}</p>
              </div>
              <StatusPill :variant="rule.isEnabled ? 'success' : 'muted'">{{ rule.isEnabled ? t('automation.enabled') : t('automation.disabled') }}</StatusPill>
              <button
                type="button"
                role="switch"
                :aria-checked="rule.isEnabled"
                class="relative h-5 w-9 shrink-0 rounded-full p-0.5 transition-colors duration-150 focus:outline-none focus:ring-2 focus:ring-accent"
                :class="rule.isEnabled ? 'bg-accent' : 'bg-surface-raised'"
                @click="automationStore.toggleRule(rule.id)"
              >
                <span class="block h-4 w-4 rounded-full transition-transform duration-150" :class="rule.isEnabled ? 'translate-x-4 bg-surface-base' : 'translate-x-0 bg-content-tertiary'" />
              </button>
            </div>
          </div>
        </template>

        <template v-else-if="activeSection === 'account'">
          <div class="mb-5">
            <h2 class="text-[15px] font-bold text-content">{{ t('settings.account.title') }}</h2>
            <p class="mt-1 text-[12px] text-content-tertiary">{{ t('settings.description') }}</p>
          </div>
          <div v-if="authStore.user" class="space-y-2 rounded-[11px] border border-line p-4">
            <div class="flex items-center justify-between gap-3 border-b border-line-row pb-3">
              <span class="text-[11px] text-content-tertiary">{{ t('settings.profile.nickname') }}</span>
              <span class="text-[12px] font-semibold text-content">{{ authStore.user.nickname || authStore.user.name }}</span>
            </div>
            <div class="flex items-center justify-between gap-3 border-b border-line-row py-3">
              <span class="text-[11px] text-content-tertiary">{{ t('settings.account.email') }}</span>
              <span class="truncate text-[12px] text-content">{{ authStore.user.email }}</span>
            </div>
            <div class="flex items-center justify-between gap-3 pt-1">
              <span class="text-[11px] text-content-tertiary">{{ t('settings.account.currentPlan') }}</span>
              <span class="font-mono text-[12px] text-content">{{ authStore.user.planType }}</span>
            </div>
          </div>
        </template>

        <template v-else-if="activeSection === 'defaults'">
          <div class="mb-5">
            <h2 class="text-[15px] font-bold text-content">{{ t('settings.defaults.title') }}</h2>
            <p class="mt-1 text-[12px] text-content-tertiary">{{ t('settings.defaults.visibilityDesc') }}</p>
          </div>
          <div v-if="settingsData" class="space-y-4 rounded-[11px] border border-line p-4">
            <label class="block">
              <span class="text-[12.5px] font-semibold text-content">{{ t('settings.defaults.visibility') }}</span>
              <select v-model="defaults.visibility" class="input-field mt-2 w-full">
                <option value="PUBLIC">{{ t('settings.defaults.visibilityPublic') }}</option>
                <option value="PRIVATE">{{ t('settings.defaults.visibilityPrivate') }}</option>
                <option value="UNLISTED">{{ t('settings.defaults.visibilityUnlisted') }}</option>
              </select>
            </label>
            <div>
              <span class="text-[12.5px] font-semibold text-content">{{ t('settings.defaults.platforms') }}</span>
              <p class="mt-1 text-[11.5px] text-content-tertiary">{{ defaults.defaultPlatforms.length ? defaults.defaultPlatforms.map(platformLabel).join(', ') : t('empty.noData') }}</p>
            </div>
            <label class="block">
              <span class="text-[12.5px] font-semibold text-content">{{ t('settings.defaults.aiTone') }}</span>
              <select v-model="defaults.aiTone" class="input-field mt-2 w-full">
                <option value="FRIENDLY">{{ t('settings.defaults.toneFriendly') }}</option>
                <option value="PROFESSIONAL">{{ t('settings.defaults.toneProfessional') }}</option>
                <option value="HUMOROUS">{{ t('settings.defaults.toneHumor') }}</option>
              </select>
            </label>
            <button type="button" class="btn-primary !min-h-9 text-[11px]" :disabled="savingDefaults" @click="saveDefaults">{{ savingDefaults ? t('action.loading') : t('settings.save') }}</button>
          </div>
          <div v-else class="rounded-lg border border-dashed border-line-control px-5 py-12 text-center text-[12px] text-content-tertiary">{{ t('empty.noData') }}</div>
        </template>

        <div v-else class="rounded-lg border border-dashed border-line-control px-5 py-14 text-center">
          <Cog6ToothIcon class="mx-auto mb-2 h-7 w-7 text-content-quaternary" />
          <p class="text-[12px] font-semibold text-content-secondary">{{ t('empty.noData') }}</p>
          <p class="mt-1 text-[11px] text-content-tertiary">{{ t('settings.description') }}</p>
        </div>

        <div v-if="activeSection === 'automation'" class="mt-6 grid gap-2.5 tablet:grid-cols-3">
          <SectionCard :title="t('settings.defaults.title')" body-class="p-[15px]">
            <p class="text-[11.5px] leading-5 text-content-secondary">{{ settingsData ? settingsData.defaultPlatforms.map(platformLabel).join(', ') || t('empty.noData') : t('empty.noData') }}</p>
            <RouterLink to="/settings" class="mt-3 inline-block text-[11px] font-semibold text-accent">{{ t('settings.save') }}</RouterLink>
          </SectionCard>
          <SectionCard :title="t('subscription.currentPlan')" body-class="p-[15px]">
            <template v-if="subscriptionStore.subscription">
              <p class="font-mono text-[18px] text-content">{{ formatPrice(subscriptionStore.subscription.price) }}</p>
              <p class="mt-1 text-[11px] text-content-tertiary">{{ subscriptionStore.subscription.planType }} · {{ subscriptionStore.subscription.billingCycle }}</p>
            </template>
            <p v-else class="text-[11.5px] text-content-tertiary">{{ t('subscription.noSubscriptionInfo') }}</p>
            <RouterLink to="/subscription" class="mt-3 inline-block text-[11px] font-semibold text-accent">{{ t('subscription.changePlan') }}</RouterLink>
          </SectionCard>
          <SectionCard :title="t('settings.account.dangerZone')" body-class="border border-error-subtle bg-error-subtle p-[15px]">
            <p class="text-[11.5px] leading-5 text-error-strong">{{ t('settings.account.dangerDesc') }}</p>
            <button type="button" class="mt-3 rounded-lg border border-error-strong px-3 py-2 text-[11px] font-semibold text-error-strong transition-colors duration-150 hover:bg-error-strong hover:text-surface-base" @click="deleteConfirmOpen = true">{{ t('settings.account.deleteBtn') }}</button>
          </SectionCard>
        </div>
      </main>
    </div>

    <ConfirmModal
      v-model="deleteConfirmOpen"
      :title="t('settings.account.deleteBtn')"
      :message="t('settings.account.deleteConfirm')"
      :confirm-text="t('settings.account.deleteBtn')"
      :cancel-text="t('action.cancel')"
      danger
      @confirm="deleteAccount"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { AdjustmentsHorizontalIcon, Cog6ToothIcon } from '@heroicons/vue/24/outline'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import SectionCard from '@/components/redesign/SectionCard.vue'
import StatusPill from '@/components/redesign/StatusPill.vue'
import { settingsApi, type UserSettingsResponse } from '@/api/settings'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { useAutomationStore } from '@/stores/automation'
import { useSubscriptionStore } from '@/stores/subscription'

const { t, locale } = useI18n()
const authStore = useAuthStore()
const automationStore = useAutomationStore()
const subscriptionStore = useSubscriptionStore()
const activeSection = ref('automation')
const settingsData = ref<UserSettingsResponse | null>(null)
const savingDefaults = ref(false)
const deleteConfirmOpen = ref(false)
const defaults = reactive({ visibility: '', defaultPlatforms: [] as string[], aiTone: '', aiProvider: '' })
const navigation = computed(() => [
  { key: 'account', label: t('settings.tabs.account') },
  { key: 'automation', label: t('nav.automation') },
  { key: 'notifications', label: t('settings.tabs.notifications') },
  { key: 'team', label: t('nav.team') },
  { key: 'billing', label: t('nav.subscription') },
  { key: 'security', label: t('settings.tabs.profile') },
])

function platformLabel(platform: string): string {
  const labels: Record<string, string> = {
    YOUTUBE: t('platform.youtube'),
    TIKTOK: t('platform.tiktok'),
    INSTAGRAM: t('platform.instagram'),
    NAVER_CLIP: t('platform.naverClip'),
    FACEBOOK: t('platform.facebook'),
    THREADS: t('platform.threads'),
  }
  return labels[platform] ?? platform
}
function formatPrice(value: number): string {
  return new Intl.NumberFormat(locale.value, { style: 'currency', currency: 'KRW', maximumFractionDigits: 0 }).format(value)
}
async function saveDefaults() {
  if (!settingsData.value) return
  savingDefaults.value = true
  try {
    await settingsApi.updateDefaults({
      visibility: defaults.visibility,
      platforms: defaults.defaultPlatforms,
      aiTone: defaults.aiTone,
      aiProvider: defaults.aiProvider,
    })
  } finally {
    savingDefaults.value = false
  }
}
async function deleteAccount() {
  try {
    await authApi.deleteAccount()
    await authStore.logout()
  } catch {
    // 공용 알림 토스트가 오류를 표시하므로 화면 상태를 임의로 바꾸지 않는다.
  }
}

onMounted(async () => {
  await Promise.allSettled([
    settingsApi.getSettings().then((data) => {
      settingsData.value = data
      defaults.visibility = data.defaultVisibility
      defaults.defaultPlatforms = data.defaultPlatforms
      defaults.aiTone = data.defaultAiTone
      defaults.aiProvider = data.defaultAiProvider
    }),
    automationStore.fetchRules(),
    subscriptionStore.fetchSubscription(),
    subscriptionStore.fetchPlans(),
  ])
  if (!authStore.user) await authStore.fetchProfile()
})
</script>

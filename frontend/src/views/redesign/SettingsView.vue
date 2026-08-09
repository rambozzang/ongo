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
        <div v-if="settingsLoadError" class="mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-warning-subtle bg-warning-subtle px-3 py-2.5 text-[12px] text-warning-strong" role="alert">
          <span class="min-w-0 flex-1">{{ t('settings.loadFailed') }}</span>
          <button type="button" class="shrink-0 rounded-md border border-warning-strong px-2 py-1 text-[11px] font-semibold" @click="reloadSettings">{{ t('action.retry') }}</button>
        </div>
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

        <template v-else-if="activeSection === 'workspaces'">
          <div class="mb-5">
            <h2 class="text-[15px] font-bold text-content">{{ t('workspace.manage') }}</h2>
            <p class="mt-1 text-[12px] text-content-tertiary">{{ t('workspace.description') }}</p>
          </div>

          <div v-if="workspaceStore.loadError" class="mb-4 rounded-lg border border-error-subtle bg-error-subtle px-4 py-3 text-[12px] text-error-strong">
            <span>{{ t('workspace.loadFailed') }}</span>
            <button type="button" class="ml-2 font-semibold underline" :disabled="workspaceStore.loading" @click="workspaceStore.fetchWorkspaces(true)">{{ t('action.retry') }}</button>
          </div>

          <form class="mb-4 grid gap-2 rounded-[11px] border border-line p-4" @submit.prevent="saveWorkspace">
            <div class="grid gap-2 tablet:grid-cols-2">
              <label class="block">
                <span class="text-[11px] font-semibold text-content-secondary">{{ t('workspace.name') }}</span>
                <input v-model="workspaceForm.name" required maxlength="80" class="input-field mt-2 w-full" :placeholder="t('workspace.namePlaceholder')">
              </label>
              <label class="block">
                <span class="text-[11px] font-semibold text-content-secondary">{{ t('workspace.slug') }}</span>
                <input v-model="workspaceForm.slug" required maxlength="80" pattern="[a-z0-9-]+" class="input-field mt-2 w-full" :placeholder="t('workspace.slugPlaceholder')">
              </label>
            </div>
            <label class="block">
              <span class="text-[11px] font-semibold text-content-secondary">{{ t('workspace.descriptionField') }}</span>
              <textarea v-model="workspaceForm.description" maxlength="300" rows="2" class="input-field mt-2 w-full resize-y" />
            </label>
            <div class="flex flex-wrap gap-2">
              <button type="submit" class="btn-primary !min-h-9 text-[11px]" :disabled="savingWorkspace">
                {{ savingWorkspace ? t('action.loading') : (editingWorkspaceId ? t('action.save') : t('workspace.create')) }}
              </button>
              <button v-if="editingWorkspaceId" type="button" class="btn-secondary !min-h-9 text-[11px]" @click="resetWorkspaceForm">{{ t('action.cancel') }}</button>
            </div>
          </form>

          <div v-if="workspaceStore.loading && !workspaceStore.workspaces.length" class="rounded-lg border border-dashed border-line-control px-5 py-12 text-center text-[12px] text-content-tertiary">{{ t('action.loading') }}</div>
          <div v-else-if="!workspaceStore.workspaces.length" class="rounded-lg border border-dashed border-line-control px-5 py-12 text-center text-[12px] text-content-tertiary">{{ t('workspace.empty') }}</div>
          <div v-else class="overflow-hidden rounded-[11px] border border-line">
            <div v-for="workspace in workspaceStore.workspaces" :key="workspace.id" class="flex flex-wrap items-center gap-3 border-b border-line-row px-4 py-3 last:border-0">
              <div class="min-w-0 flex-1">
                <p class="truncate text-[12px] font-semibold text-content">{{ workspace.name }}</p>
                <p class="mt-1 truncate font-mono text-[10.5px] text-content-tertiary">{{ workspace.slug }}</p>
              </div>
              <span class="text-[10.5px] text-content-tertiary">{{ workspace.memberCount ?? 0 }}명</span>
              <button type="button" class="btn-secondary !min-h-8 text-[11px]" @click="editWorkspace(workspace)">{{ t('action.edit') }}</button>
              <button type="button" class="rounded-lg border border-error-subtle px-3 py-2 text-[11px] font-semibold text-error-strong hover:bg-error-subtle" @click="removeWorkspace(workspace.id)">{{ t('action.delete') }}</button>
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

        <template v-else-if="activeSection === 'security'">
          <div class="mb-5 flex flex-wrap items-end justify-between gap-3">
            <div>
              <h2 class="text-[15px] font-bold text-content">{{ t('settings.apiKeys.title') }}</h2>
              <p class="mt-1 max-w-[650px] text-[12px] leading-5 text-content-tertiary">{{ t('settings.apiKeys.description') }}</p>
            </div>
          </div>

          <div v-if="newApiKeyToken" class="mb-4 rounded-[11px] border border-success-subtle bg-success-subtle p-4">
            <p class="text-[12px] font-bold text-success-strong">{{ t('settings.apiKeys.createdTitle') }}</p>
            <p class="mt-1 text-[11px] leading-5 text-success-strong">{{ t('settings.apiKeys.createdDescription') }}</p>
            <div class="mt-3 flex items-center gap-2">
              <code class="min-w-0 flex-1 overflow-x-auto rounded-lg bg-surface-base px-3 py-2 text-[11px] text-content">{{ newApiKeyToken }}</code>
              <button type="button" class="btn-secondary shrink-0 !min-h-9 text-[11px]" @click="copyApiKey">{{ t('settings.apiKeys.copy') }}</button>
            </div>
          </div>

          <form class="mb-4 grid gap-2 rounded-[11px] border border-line p-4 tablet:grid-cols-[minmax(0,1fr)_220px_auto] tablet:items-end" @submit.prevent="createApiKey">
            <label class="block">
              <span class="text-[11px] font-semibold text-content-secondary">{{ t('settings.apiKeys.name') }}</span>
              <input v-model="apiKeyForm.name" required maxlength="80" class="input-field mt-2 w-full" :placeholder="t('settings.apiKeys.namePlaceholder')">
            </label>
            <label class="block">
              <span class="text-[11px] font-semibold text-content-secondary">{{ t('settings.apiKeys.expiry') }}</span>
              <input v-model="apiKeyForm.expiresAt" type="datetime-local" class="input-field mt-2 w-full">
            </label>
            <button type="submit" class="btn-primary !min-h-9 text-[11px]" :disabled="creatingApiKey">{{ creatingApiKey ? t('action.loading') : t('settings.apiKeys.create') }}</button>
          </form>

          <div v-if="apiKeys.length" class="overflow-hidden rounded-[11px] border border-line">
            <div v-for="apiKey in apiKeys" :key="apiKey.id" class="flex flex-wrap items-center gap-3 border-b border-line-row px-4 py-3 last:border-0">
              <div class="min-w-0 flex-1">
                <p class="truncate text-[12px] font-semibold text-content">{{ apiKey.name }}</p>
                <p class="mt-1 font-mono text-[10.5px] text-content-tertiary">{{ apiKey.keyPrefix }}••••••••</p>
              </div>
              <span class="text-[10.5px] text-content-tertiary">{{ apiKey.revokedAt ? t('settings.apiKeys.revoked') : (apiKey.expiresAt ? formatDate(apiKey.expiresAt) : t('settings.apiKeys.neverExpires')) }}</span>
              <button v-if="!apiKey.revokedAt" type="button" class="rounded-lg border border-error-subtle px-3 py-2 text-[11px] font-semibold text-error-strong hover:bg-error-subtle" @click="revokeApiKey(apiKey.id)">{{ t('settings.apiKeys.revoke') }}</button>
            </div>
          </div>
          <div v-else class="rounded-lg border border-dashed border-line-control px-5 py-12 text-center text-[12px] text-content-tertiary">{{ t('settings.apiKeys.empty') }}</div>
        </template>

        <div v-else class="rounded-lg border border-dashed border-line-control px-5 py-14 text-center">
          <Cog6ToothIcon class="mx-auto mb-2 h-7 w-7 text-content-quaternary" />
          <p class="text-[12px] font-semibold text-content-secondary">{{ t('empty.noData') }}</p>
          <p class="mt-1 text-[11px] text-content-tertiary">{{ t('settings.description') }}</p>
        </div>

        <div v-if="activeSection === 'automation'" class="mt-6 grid gap-2.5 tablet:grid-cols-3">
          <SectionCard :title="t('settings.defaults.title')" body-class="p-[15px]">
            <p class="text-[11.5px] leading-5 text-content-secondary">{{ settingsData ? settingsData.defaultPlatforms.map(platformLabel).join(', ') || t('empty.noData') : t('empty.noData') }}</p>
            <RouterLink to="/settings-v2?tab=defaults" class="mt-3 inline-block text-[11px] font-semibold text-accent">{{ t('settings.save') }}</RouterLink>
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
            <p class="text-[11.5px] leading-5 text-error-strong">{{ t('settings.account.deleteUnavailable') }}</p>
            <p class="mt-2 text-[11px] leading-5 text-error-strong">{{ t('settings.account.deleteUnavailableDesc') }}</p>
          </SectionCard>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { AdjustmentsHorizontalIcon, Cog6ToothIcon } from '@heroicons/vue/24/outline'
import SectionCard from '@/components/redesign/SectionCard.vue'
import StatusPill from '@/components/redesign/StatusPill.vue'
import { settingsApi, type ApiKey, type UserSettingsResponse } from '@/api/settings'
import { useAuthStore } from '@/stores/auth'
import { useAutomationStore } from '@/stores/automation'
import { useSubscriptionStore } from '@/stores/subscription'
import { useWorkspaceStore } from '@/stores/workspace'

const { t, locale } = useI18n()
const route = useRoute()
const authStore = useAuthStore()
const automationStore = useAutomationStore()
const subscriptionStore = useSubscriptionStore()
const workspaceStore = useWorkspaceStore()
const requestedTab = typeof route.query.tab === 'string' ? route.query.tab : ''
const activeSection = ref(['account', 'automation', 'defaults', 'security', 'workspaces'].includes(requestedTab) ? requestedTab : 'automation')
const settingsData = ref<UserSettingsResponse | null>(null)
const settingsLoadError = ref(false)
const savingDefaults = ref(false)
const defaults = reactive({ visibility: '', defaultPlatforms: [] as string[], aiTone: '', aiProvider: '' })
const apiKeys = ref<ApiKey[]>([])
const apiKeyForm = reactive({ name: '', expiresAt: '' })
const creatingApiKey = ref(false)
const newApiKeyToken = ref<string | null>(null)
const editingWorkspaceId = ref<number | null>(null)
const workspaceForm = reactive({ name: '', slug: '', description: '' })
const savingWorkspace = ref(false)
const navigation = computed(() => [
  { key: 'account', label: t('settings.tabs.account') },
  { key: 'automation', label: t('nav.automation') },
  { key: 'defaults', label: t('settings.defaults.title') },
  { key: 'security', label: t('settings.tabs.apiKeys') },
  { key: 'workspaces', label: t('workspace.manage') },
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
function formatDate(value: string): string {
  return new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}
function reloadSettings() {
  window.location.reload()
}
async function fetchApiKeys() {
  apiKeys.value = await settingsApi.listApiKeys()
}
async function createApiKey() {
  if (!apiKeyForm.name.trim()) return
  creatingApiKey.value = true
  try {
    const created = await settingsApi.createApiKey({
      name: apiKeyForm.name.trim(),
      ...(apiKeyForm.expiresAt ? { expiresAt: apiKeyForm.expiresAt } : {}),
    })
    newApiKeyToken.value = created.token ?? null
    apiKeyForm.name = ''
    apiKeyForm.expiresAt = ''
    await fetchApiKeys()
  } finally {
    creatingApiKey.value = false
  }
}
async function revokeApiKey(id: number) {
  if (!window.confirm(t('settings.apiKeys.revokeConfirm'))) return
  await settingsApi.revokeApiKey(id)
  await fetchApiKeys()
}
async function copyApiKey() {
  if (newApiKeyToken.value) await navigator.clipboard?.writeText(newApiKeyToken.value).catch(() => undefined)
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
function resetWorkspaceForm() {
  editingWorkspaceId.value = null
  workspaceForm.name = ''
  workspaceForm.slug = ''
  workspaceForm.description = ''
}
function editWorkspace(workspace: { id: number; name: string; slug: string; description?: string | null }) {
  editingWorkspaceId.value = workspace.id
  workspaceForm.name = workspace.name
  workspaceForm.slug = workspace.slug
  workspaceForm.description = workspace.description ?? ''
}
async function saveWorkspace() {
  if (!workspaceForm.name.trim() || !workspaceForm.slug.trim()) return
  savingWorkspace.value = true
  try {
    if (editingWorkspaceId.value) {
      await workspaceStore.updateWorkspace(editingWorkspaceId.value, {
        name: workspaceForm.name.trim(),
        slug: workspaceForm.slug.trim(),
        description: workspaceForm.description.trim() || null,
      })
    } else {
      await workspaceStore.createWorkspace({
        name: workspaceForm.name.trim(),
        slug: workspaceForm.slug.trim(),
        description: workspaceForm.description.trim() || null,
      })
    }
    resetWorkspaceForm()
  } finally {
    savingWorkspace.value = false
  }
}
async function removeWorkspace(id: number) {
  if (!window.confirm(t('workspace.deleteConfirm'))) return
  await workspaceStore.removeWorkspace(id)
}

onMounted(async () => {
  const results = await Promise.allSettled([
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
    fetchApiKeys(),
    workspaceStore.fetchWorkspaces(),
  ])
  settingsLoadError.value = results.some((result) => result.status === 'rejected')
  if (!authStore.user) await authStore.fetchProfile()
})
</script>

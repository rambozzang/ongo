<template>
  <div class="max-w-4xl">
    <button class="mb-4 inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400" @click="router.push(`/ugc/campaigns/${campaignId}`)">
      <ChevronLeftIcon class="h-4 w-4" />{{ $t('ugc.backToCampaign') }}
    </button>

    <PageHeader :title="$t('ugc.applicationsTitle')" :description="$t('ugc.applicationsDescription')">
      <template #actions>
        <button class="btn-primary inline-flex items-center gap-2" :disabled="creatingInvite" @click="createInvite">
          <LinkIcon class="h-5 w-5" />{{ $t('ugc.createInvite') }}
        </button>
      </template>
    </PageHeader>

    <!-- Generated invite link -->
    <div v-if="inviteUrl" class="card mb-4 flex flex-col gap-2 mobile:flex-row mobile:items-center">
      <div class="min-w-0 flex-1">
        <p class="text-xs text-gray-400">{{ $t('ugc.inviteLink') }}</p>
        <p class="truncate font-mono text-sm text-gray-900 dark:text-gray-100">{{ inviteUrl }}</p>
      </div>
      <button class="btn-secondary shrink-0" @click="copyInvite">{{ copied ? $t('ugc.copied') : $t('ugc.copy') }}</button>
    </div>

    <div v-if="loading" class="py-16 text-center text-sm text-gray-400">{{ $t('action.loading') }}</div>

    <div v-else-if="applications.length === 0" class="card py-16 text-center text-sm text-gray-500 dark:text-gray-400">
      {{ $t('ugc.noApplications') }}
    </div>

    <div v-else class="space-y-3">
      <div v-for="a in applications" :key="a.id" class="card flex items-start justify-between gap-4">
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <span class="font-semibold text-gray-900 dark:text-gray-100">{{ $t('ugc.creator') }} #{{ a.creatorId }}</span>
            <span :class="['rounded-full px-2 py-0.5 text-xs font-medium', appStatusClass(a.status)]">
              {{ $t(`ugc.appStatus.${a.status}`) }}
            </span>
          </div>
          <p v-if="a.message" class="mt-1 text-sm text-gray-600 dark:text-gray-300">{{ a.message }}</p>
          <a v-if="a.portfolioUrl" :href="a.portfolioUrl" target="_blank" rel="noopener noreferrer" class="mt-1 inline-block text-xs text-primary-600 hover:underline">
            {{ a.portfolioUrl }}
          </a>
          <p class="mt-1 text-xs text-gray-400">{{ a.createdAt?.slice(0, 10) }}</p>
        </div>
        <div v-if="a.status === 'APPLIED'" class="flex shrink-0 gap-2">
          <button class="btn-secondary text-xs" :disabled="acting === a.id" @click="decide(a.id, 'reject')">{{ $t('ugc.reject') }}</button>
          <button class="btn-primary text-xs" :disabled="acting === a.id" @click="decide(a.id, 'accept')">{{ $t('ugc.accept') }}</button>
        </div>
      </div>
    </div>

    <div v-if="!loading && totalPages > 1" class="mt-6 flex items-center justify-center gap-3">
      <button class="btn-secondary" :disabled="page === 0" @click="changePage(page - 1)">{{ $t('ugc.prev') }}</button>
      <span class="text-sm text-gray-500 dark:text-gray-400">{{ page + 1 }} / {{ totalPages }}</span>
      <button class="btn-secondary" :disabled="page + 1 >= totalPages" @click="changePage(page + 1)">{{ $t('ugc.next') }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useWorkspaceStore } from '@/stores/workspace'
import { useNotificationStore } from '@/stores/notification'
import { ugcParticipationApi, type ApplicationResponse, type ApplicationStatus } from '@/api/ugcParticipation'
import PageHeader from '@/components/common/PageHeader.vue'
import { ChevronLeftIcon, LinkIcon } from '@heroicons/vue/24/outline'

const { t } = useI18n({ useScope: 'global' })
const route = useRoute()
const router = useRouter()
const workspaceStore = useWorkspaceStore()
const notify = useNotificationStore()

const campaignId = Number(route.params.id)
const applications = ref<ApplicationResponse[]>([])
const totalElements = ref(0)
const page = ref(0)
const size = ref(20)
const loading = ref(true)
const acting = ref<number | null>(null)
const inviteUrl = ref('')
const copied = ref(false)
const creatingInvite = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(totalElements.value / size.value)))

function workspaceId(): number {
  const id = workspaceStore.activeWorkspaceId
  if (id == null) throw new Error(t('ugc.noWorkspace'))
  return id
}

function appStatusClass(status: ApplicationStatus): string {
  switch (status) {
    case 'ACCEPTED':
      return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300'
    case 'REJECTED':
      return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300'
    case 'APPLIED':
      return 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
    default:
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
  }
}

async function fetchApplications() {
  loading.value = true
  try {
    const res = await ugcParticipationApi.listApplications(workspaceId(), campaignId, { page: page.value, size: size.value })
    applications.value = res.items
    totalElements.value = res.totalElements
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.loadFailed'))
  } finally {
    loading.value = false
  }
}

function changePage(next: number) {
  page.value = next
  fetchApplications()
}

async function createInvite() {
  creatingInvite.value = true
  try {
    const invite = await ugcParticipationApi.createInvite(workspaceId(), campaignId, { expiresInDays: 30 })
    inviteUrl.value = `${window.location.origin}/ugc/invite/${invite.token}`
    copied.value = false
    notify.success(t('ugc.inviteCreated'))
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.inviteFailed'))
  } finally {
    creatingInvite.value = false
  }
}

async function copyInvite() {
  try {
    await navigator.clipboard.writeText(inviteUrl.value)
    copied.value = true
  } catch {
    notify.error(t('ugc.copyFailed'))
  }
}

async function decide(applicationId: number, action: 'accept' | 'reject') {
  acting.value = applicationId
  try {
    if (action === 'accept') await ugcParticipationApi.accept(workspaceId(), applicationId)
    else await ugcParticipationApi.reject(workspaceId(), applicationId)
    notify.success(action === 'accept' ? t('ugc.accepted') : t('ugc.rejected'))
    await fetchApplications()
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.actionFailed'))
  } finally {
    acting.value = null
  }
}

onMounted(fetchApplications)
</script>

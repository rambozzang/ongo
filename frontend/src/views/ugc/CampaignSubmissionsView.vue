<template>
  <div class="max-w-5xl">
    <button class="mb-4 inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400" @click="router.push(`/ugc/campaigns/${campaignId}`)">
      <ChevronLeftIcon class="h-4 w-4" />{{ $t('ugc.backToCampaign') }}
    </button>

    <PageHeader :title="$t('ugc.reviewTitle')" :description="$t('ugc.reviewDescription')" />

    <div v-if="loading" class="py-16 text-center text-sm text-gray-400">{{ $t('action.loading') }}</div>
    <div v-else-if="submissions.length === 0" class="card py-16 text-center text-sm text-gray-500 dark:text-gray-400">
      {{ $t('ugc.noSubmissions') }}
    </div>

    <div v-else class="grid grid-cols-1 gap-4 desktop:grid-cols-2">
      <!-- List -->
      <div class="space-y-3">
        <button
          v-for="s in submissions"
          :key="s.id"
          :class="['card w-full text-left transition-colors', selected?.submission.id === s.id ? 'border-primary-400 dark:border-primary-600' : 'hover:border-primary-300']"
          @click="openDetail(s.id)"
        >
          <div class="flex items-center justify-between gap-2">
            <span class="font-semibold text-gray-900 dark:text-gray-100">{{ $t('ugc.creator') }} #{{ s.creatorId }}</span>
            <span :class="['rounded-full px-2 py-0.5 text-xs font-medium', subStatusClass(s.status)]">
              {{ $t(`ugc.subStatus.${s.status}`) }} · v{{ s.revision }}
            </span>
          </div>
          <p class="mt-1 truncate text-sm text-gray-500 dark:text-gray-400">{{ s.caption || $t('ugc.noCaption') }}</p>
        </button>
      </div>

      <!-- Detail -->
      <div v-if="selected" class="card space-y-4">
        <div>
          <h3 class="font-semibold text-gray-900 dark:text-gray-100">{{ $t('ugc.creator') }} #{{ selected.submission.creatorId }} · v{{ selected.submission.revision }}</h3>
          <p class="mt-1 text-sm text-gray-700 dark:text-gray-300">{{ selected.submission.caption || $t('ugc.noCaption') }}</p>
        </div>
        <div>
          <p class="mb-1 text-xs font-medium text-gray-400">{{ $t('ugc.attachments') }}</p>
          <ul class="space-y-1">
            <li v-for="(a, i) in selected.submission.assets" :key="i">
              <a :href="a.externalUrl || '#'" target="_blank" rel="noopener noreferrer" class="truncate text-sm text-primary-600 hover:underline">{{ a.externalUrl || `${a.resourceType}#${a.resourceId}` }}</a>
            </li>
          </ul>
        </div>

        <!-- Review actions -->
        <div v-if="selected.submission.status === 'SUBMITTED'" class="space-y-2 border-t border-gray-100 pt-3 dark:border-gray-700">
          <textarea v-model="reason" rows="2" class="input-field" :placeholder="$t('ugc.reviewReasonPlaceholder')" />
          <div class="flex justify-end gap-2">
            <button class="btn-secondary" :disabled="acting" @click="requestChanges">{{ $t('ugc.requestChanges') }}</button>
            <button class="btn-primary" :disabled="acting" @click="approve">{{ $t('ugc.approve') }}</button>
          </div>
        </div>

        <!-- Review history -->
        <div v-if="selected.reviews.length" class="border-t border-gray-100 pt-3 dark:border-gray-700">
          <p class="mb-2 text-xs font-medium text-gray-400">{{ $t('ugc.reviewHistory') }}</p>
          <ul class="space-y-2">
            <li v-for="r in selected.reviews" :key="r.id" class="text-sm">
              <span :class="['rounded px-1.5 py-0.5 text-xs font-medium', r.decision === 'APPROVED' ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300' : 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300']">
                {{ $t(`ugc.decision.${r.decision}`) }}
              </span>
              <span class="ml-2 text-gray-600 dark:text-gray-300">{{ r.comment }}</span>
              <span class="ml-2 text-xs text-gray-400">{{ r.createdAt?.slice(0, 10) }}</span>
            </li>
          </ul>
        </div>

        <!-- Publish -->
        <div v-if="canPublish" class="border-t border-gray-100 pt-3 dark:border-gray-700">
          <p class="mb-2 text-xs font-medium text-gray-400">{{ $t('ugc.publishSection') }}</p>
          <div class="mb-2 flex flex-wrap gap-3">
            <label v-for="p in publishablePlatforms" :key="p" class="inline-flex items-center gap-1 text-sm text-gray-700 dark:text-gray-300">
              <input v-model="selectedPlatforms" type="checkbox" :value="p" class="rounded" /> {{ p }}
            </label>
          </div>
          <button class="btn-primary text-xs" :disabled="publishing || selectedPlatforms.length === 0" @click="publish">
            {{ $t('ugc.publishNow') }}
          </button>
        </div>

        <!-- Posts -->
        <div v-if="selectedPosts.length" class="border-t border-gray-100 pt-3 dark:border-gray-700">
          <p class="mb-2 text-xs font-medium text-gray-400">{{ $t('ugc.posts') }}</p>
          <ul class="space-y-1">
            <li v-for="post in selectedPosts" :key="post.id" class="flex items-center justify-between text-sm">
              <span class="text-gray-700 dark:text-gray-300">{{ post.platform }} <span v-if="post.postType === 'EXTERNAL'" class="text-xs text-gray-400">({{ $t('ugc.external') }})</span></span>
              <span :class="['rounded px-1.5 py-0.5 text-xs font-medium', postStatusClass(post.status)]">{{ $t(`ugc.postStatus.${post.status}`) }}</span>
            </li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useWorkspaceStore } from '@/stores/workspace'
import { useNotificationStore } from '@/stores/notification'
import { ugcSubmissionApi, type SubmissionResponse, type SubmissionDetailResponse, type SubmissionStatus } from '@/api/ugcSubmission'
import { ugcPublishingApi, type CampaignPostResponse, type PostStatus } from '@/api/ugcPublishing'
import PageHeader from '@/components/common/PageHeader.vue'
import { ChevronLeftIcon } from '@heroicons/vue/24/outline'

const { t } = useI18n({ useScope: 'global' })
const route = useRoute()
const router = useRouter()
const workspaceStore = useWorkspaceStore()
const notify = useNotificationStore()

const campaignId = Number(route.params.id)
const submissions = ref<SubmissionResponse[]>([])
const selected = ref<SubmissionDetailResponse | null>(null)
const reason = ref('')
const loading = ref(true)
const acting = ref(false)
const allPosts = ref<CampaignPostResponse[]>([])
const selectedPlatforms = ref<string[]>([])
const publishing = ref(false)
const publishablePlatforms = ['YOUTUBE', 'TIKTOK', 'INSTAGRAM']

const canPublish = computed(() =>
  selected.value != null && ['APPROVED', 'PUBLISHING', 'PUBLISHED'].includes(selected.value.submission.status),
)
const selectedPosts = computed<CampaignPostResponse[]>(() =>
  selected.value ? allPosts.value.filter((p) => p.submissionId === selected.value!.submission.id) : [],
)

function workspaceId(): number {
  const id = workspaceStore.activeWorkspaceId
  if (id == null) throw new Error(t('ugc.noWorkspace'))
  return id
}

function subStatusClass(status: SubmissionStatus): string {
  switch (status) {
    case 'APPROVED':
    case 'PUBLISHED':
      return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300'
    case 'SUBMITTED':
      return 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
    case 'CHANGES_REQUESTED':
      return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300'
    case 'REJECTED':
    case 'PUBLISH_FAILED':
      return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300'
    default:
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
  }
}

function postStatusClass(status: PostStatus): string {
  switch (status) {
    case 'PUBLISHED':
    case 'EXTERNAL':
      return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300'
    case 'PUBLISHING':
    case 'PENDING':
      return 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
    default:
      return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300'
  }
}

async function refreshPosts() {
  const res = await ugcPublishingApi.listCampaignPosts(workspaceId(), campaignId)
  allPosts.value = res.items
}

async function fetchList() {
  loading.value = true
  try {
    const res = await ugcSubmissionApi.list(workspaceId(), campaignId, { size: 50 })
    submissions.value = res.items
    await refreshPosts()
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function publish() {
  if (!selected.value) return
  publishing.value = true
  try {
    await ugcPublishingApi.publish(workspaceId(), selected.value.submission.id, { platforms: selectedPlatforms.value })
    notify.success(t('ugc.publishStarted'))
    selectedPlatforms.value = []
    await refresh(selected.value.submission.id)
    await refreshPosts()
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.actionFailed'))
  } finally {
    publishing.value = false
  }
}

async function openDetail(submissionId: number) {
  reason.value = ''
  try {
    selected.value = await ugcSubmissionApi.detail(workspaceId(), submissionId)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.loadFailed'))
  }
}

async function requestChanges() {
  if (!selected.value) return
  if (!reason.value.trim()) {
    notify.error(t('ugc.reasonRequired'))
    return
  }
  acting.value = true
  try {
    await ugcSubmissionApi.requestChanges(workspaceId(), selected.value.submission.id, { comment: reason.value.trim() })
    notify.success(t('ugc.changesRequested'))
    await refresh(selected.value.submission.id)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.actionFailed'))
  } finally {
    acting.value = false
  }
}

async function approve() {
  if (!selected.value) return
  acting.value = true
  try {
    await ugcSubmissionApi.approve(workspaceId(), selected.value.submission.id, { comment: reason.value.trim() || null })
    notify.success(t('ugc.approved'))
    await refresh(selected.value.submission.id)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.actionFailed'))
  } finally {
    acting.value = false
  }
}

async function refresh(submissionId: number) {
  await fetchList()
  await openDetail(submissionId)
}

onMounted(fetchList)
</script>

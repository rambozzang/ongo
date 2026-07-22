<template>
  <div class="max-w-xl">
    <button class="mb-4 inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400" @click="router.push('/creator/campaigns')">
      <ChevronLeftIcon class="h-4 w-4" />{{ $t('ugc.myCampaigns') }}
    </button>

    <PageHeader :title="$t('ugc.submitTitle')" :description="$t('ugc.submitDescription')">
      <template #title-suffix>
        <span v-if="submission" :class="['rounded-full px-2.5 py-0.5 text-xs font-medium', subStatusClass(submission.status)]">
          {{ $t(`ugc.subStatus.${submission.status}`) }} · v{{ submission.revision }}
        </span>
      </template>
    </PageHeader>

    <div v-if="loading" class="py-16 text-center text-sm text-gray-400">{{ $t('action.loading') }}</div>

    <template v-else>
      <!-- Changes requested notice -->
      <div v-if="submission?.status === 'CHANGES_REQUESTED'" class="card mb-4 border-l-4 border-yellow-400 bg-yellow-50 dark:bg-yellow-900/20">
        <p class="text-sm font-medium text-yellow-800 dark:text-yellow-300">{{ $t('ugc.changesRequestedNotice') }}</p>
      </div>

      <!-- Read-only when not editable -->
      <div v-if="submission && !editable" class="card space-y-3">
        <p class="text-sm text-gray-500 dark:text-gray-400">{{ $t('ugc.submissionLocked') }}</p>
        <p class="font-medium text-gray-900 dark:text-gray-100">{{ submission.caption || $t('ugc.noCaption') }}</p>
        <ul class="space-y-1">
          <li v-for="(a, i) in submission.assets" :key="i" class="truncate text-sm text-primary-600">{{ a.externalUrl }}</li>
        </ul>
      </div>

      <!-- Editable form -->
      <div v-else class="card space-y-5">
        <div>
          <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('ugc.caption') }}</label>
          <textarea v-model="caption" rows="3" class="input-field" :placeholder="$t('ugc.captionPlaceholder')" />
        </div>
        <div>
          <div class="mb-2 flex items-center justify-between">
            <label class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('ugc.attachments') }}</label>
            <button class="btn-secondary inline-flex items-center gap-1 text-xs" @click="assets.push({ url: '' })">
              <PlusIcon class="h-4 w-4" />{{ $t('ugc.addAttachment') }}
            </button>
          </div>
          <div v-if="assets.length === 0" class="rounded-lg border border-dashed border-gray-300 py-4 text-center text-xs text-gray-400 dark:border-gray-600">
            {{ $t('ugc.noAttachments') }}
          </div>
          <div v-for="(a, i) in assets" :key="i" class="mb-2 flex items-center gap-2">
            <input v-model="a.url" type="url" class="input-field flex-1" placeholder="https://" />
            <button class="rounded p-1.5 text-gray-400 hover:bg-gray-100 hover:text-red-500 dark:hover:bg-gray-700" @click="assets.splice(i, 1)">
              <TrashIcon class="h-4 w-4" />
            </button>
          </div>
          <p class="mt-1 text-xs text-gray-400">{{ $t('ugc.attachmentHint') }}</p>
        </div>

        <div class="flex items-center justify-end gap-2 border-t border-gray-100 pt-4 dark:border-gray-700">
          <button class="btn-secondary" :disabled="busy" @click="save">{{ $t('ugc.saveDraft') }}</button>
          <button class="btn-primary" :disabled="busy || !hasContent" @click="saveAndSubmit">{{ $t('ugc.submitNow') }}</button>
        </div>
      </div>

      <!-- External post registration (after approval) -->
      <div v-if="canRegisterExternal" class="card mt-4 space-y-3">
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ $t('ugc.externalTitle') }}</h3>
        <p class="text-xs text-gray-400">{{ $t('ugc.externalHint') }}</p>
        <div class="grid grid-cols-1 gap-2 mobile:grid-cols-3">
          <select v-model="extPlatform" class="input-field">
            <option v-for="p in externalPlatforms" :key="p" :value="p">{{ p }}</option>
          </select>
          <input v-model="extUrl" type="url" class="input-field mobile:col-span-2" placeholder="https://" />
        </div>
        <div class="flex justify-end">
          <button class="btn-primary text-xs" :disabled="registering || !extUrl.trim()" @click="registerExternal">
            {{ $t('ugc.registerExternal') }}
          </button>
        </div>
        <ul v-if="myPosts.length" class="space-y-1 border-t border-gray-100 pt-2 dark:border-gray-700">
          <li v-for="post in myPosts" :key="post.id" class="flex items-center justify-between text-sm">
            <span class="truncate text-gray-700 dark:text-gray-300">{{ post.platform }}</span>
            <span class="text-xs text-gray-400">{{ $t(`ugc.postStatus.${post.status}`) }}</span>
          </li>
        </ul>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notification'
import { ugcSubmissionApi, type SubmissionResponse, type SubmissionStatus } from '@/api/ugcSubmission'
import { ugcPublishingApi, type CampaignPostResponse } from '@/api/ugcPublishing'
import PageHeader from '@/components/common/PageHeader.vue'
import { ChevronLeftIcon, PlusIcon, TrashIcon } from '@heroicons/vue/24/outline'

const { t } = useI18n({ useScope: 'global' })
const route = useRoute()
const router = useRouter()
const notify = useNotificationStore()

const campaignId = Number(route.params.id)
const submission = ref<SubmissionResponse | null>(null)
const caption = ref('')
const assets = ref<{ url: string }[]>([])
const loading = ref(true)
const busy = ref(false)
const extPlatform = ref('YOUTUBE')
const extUrl = ref('')
const registering = ref(false)
const myPosts = ref<CampaignPostResponse[]>([])
const externalPlatforms = ['YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'NAVER_CLIP', 'TWITTER', 'FACEBOOK', 'THREADS']

const editable = computed(() => !submission.value || submission.value.status === 'DRAFT' || submission.value.status === 'CHANGES_REQUESTED')
const hasContent = computed(() => assets.value.some((a) => a.url.trim()))
const canRegisterExternal = computed(() =>
  submission.value != null && ['APPROVED', 'PUBLISHING', 'PUBLISHED'].includes(submission.value.status),
)

async function loadMyPosts() {
  if (!submission.value) return
  try {
    const res = await ugcPublishingApi.myPosts(submission.value.id)
    myPosts.value = res.items
  } catch {
    // 게시물 로드 실패는 조용히 무시
  }
}

async function registerExternal() {
  if (!submission.value) return
  registering.value = true
  try {
    await ugcPublishingApi.registerExternal(submission.value.id, { platform: extPlatform.value, externalPostUrl: extUrl.value.trim() })
    notify.success(t('ugc.externalRegistered'))
    extUrl.value = ''
    await loadMyPosts()
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.saveFailed'))
  } finally {
    registering.value = false
  }
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

function payload() {
  return {
    caption: caption.value.trim() || null,
    assets: assets.value
      .filter((a) => a.url.trim())
      .map((a) => ({ assetType: 'EXTERNAL', externalUrl: a.url.trim() })),
  }
}

async function save(): Promise<SubmissionResponse | null> {
  busy.value = true
  try {
    const result = await ugcSubmissionApi.saveDraft(campaignId, payload())
    submission.value = result
    notify.success(t('ugc.draftSaved'))
    return result
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.saveFailed'))
    return null
  } finally {
    busy.value = false
  }
}

async function saveAndSubmit() {
  const saved = await save()
  if (!saved) return
  busy.value = true
  try {
    submission.value = await ugcSubmissionApi.submit(saved.id)
    notify.success(t('ugc.submitDone'))
    router.push('/creator/campaigns')
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.submitFailed'))
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  try {
    const res = await ugcSubmissionApi.listMine(campaignId)
    if (res.items.length > 0) {
      submission.value = res.items[0]
      caption.value = res.items[0].caption ?? ''
      assets.value = res.items[0].assets
        .filter((a) => a.externalUrl)
        .map((a) => ({ url: a.externalUrl as string }))
      await loadMyPosts()
    }
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.loadFailed'))
  } finally {
    loading.value = false
  }
})
</script>

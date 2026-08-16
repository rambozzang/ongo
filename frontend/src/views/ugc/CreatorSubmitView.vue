<template>
  <div class="min-h-full max-w-xl space-y-5 py-5 text-content">
    <button class="mb-4 inline-flex items-center gap-1 text-body text-gray-500 hover:text-gray-700 dark:text-gray-400" @click="router.push('/creator/campaigns')">
      <ChevronLeftIcon class="h-4 w-4" />{{ $t('ugc.myCampaigns') }}
    </button>

    <PageHeader :title="$t('ugc.submitTitle')" :description="$t('ugc.submitDescription')">
      <template #title-suffix>
        <span v-if="submission" :class="['rounded-full px-2.5 py-0.5 text-caption', subStatusClass(submission.status)]">
          {{ $t(`ugc.subStatus.${submission.status}`) }} · v{{ submission.revision }}
        </span>
      </template>
    </PageHeader>

    <div v-if="loading" class="py-16 text-center text-body text-gray-400">{{ $t('action.loading') }}</div>

    <template v-else>
      <!-- Changes requested notice -->
      <div v-if="submission?.status === 'CHANGES_REQUESTED'" class="card mb-4 border-l-4 border-warning bg-warning-subtle">
        <p class="text-body font-medium text-warning-strong">{{ $t('ugc.changesRequestedNotice') }}</p>
      </div>

      <!-- Read-only when not editable -->
      <div v-if="submission && !editable" class="card space-y-3">
        <p class="text-body text-gray-500 dark:text-gray-400">{{ $t('ugc.submissionLocked') }}</p>
        <p class="font-medium text-gray-900 dark:text-gray-100">{{ submission.caption || $t('ugc.noCaption') }}</p>
          <ul class="space-y-1">
            <li v-for="(a, i) in submission.assets" :key="i" class="truncate text-body text-primary-600">
              {{ a.resourceId != null ? `${$t('ugc.videoAsset')} #${a.resourceId}` : a.externalUrl }}
            </li>
          </ul>
      </div>

      <!-- Editable form -->
      <div v-else class="card space-y-5">
        <div>
          <label class="mb-1.5 block text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('ugc.caption') }}</label>
          <textarea v-model="caption" rows="3" class="input-field" :placeholder="$t('ugc.captionPlaceholder')" />
        </div>
        <div>
          <div class="mb-2 flex items-center justify-between">
            <label class="text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('ugc.attachments') }}</label>
            <button type="button" class="btn-secondary inline-flex items-center gap-1 text-body-xs" @click="assets.push({ kind: 'EXTERNAL', url: '' })">
              <PlusIcon class="h-4 w-4" />{{ $t('ugc.addAttachment') }}
            </button>
          </div>
          <div class="mb-3 rounded-lg border border-primary-200 bg-primary-50/50 p-3 dark:border-primary-800 dark:bg-primary-900/10">
            <label class="block text-body-xs font-medium text-gray-700 dark:text-gray-300" for="ugc-video-upload">
              {{ $t('ugc.uploadVideo') }}
            </label>
            <input
              id="ugc-video-upload"
              ref="videoInput"
              type="file"
              accept="video/mp4,video/quicktime,video/webm,video/x-matroska"
              class="mt-2 block w-full text-body-xs text-gray-600 file:mr-3 file:rounded-md file:border-0 file:bg-primary-600 file:px-3 file:py-2 file:text-body-xs file:font-medium file:text-white hover:file:bg-primary-700 dark:text-gray-300"
              :disabled="uploading || !editable"
              @change="handleVideoSelected"
            />
            <div v-if="uploading" class="mt-2" aria-live="polite">
              <div class="mb-1 flex justify-between text-body-xs text-gray-500 dark:text-gray-400">
                <span>{{ $t('ugc.uploadingVideo') }}</span>
                <span>{{ uploadProgress }}%</span>
              </div>
              <div class="h-1.5 overflow-hidden rounded-full bg-primary-100 dark:bg-primary-900/40">
                <div class="h-full rounded-full bg-primary-600 transition-[width]" :style="{ width: `${uploadProgress}%` }" />
              </div>
            </div>
            <p v-if="uploadError" class="mt-2 text-body-xs text-error-strong" role="alert">{{ uploadError }}</p>
          </div>
          <div v-if="assets.length === 0" class="rounded-lg border border-dashed border-gray-300 py-4 text-center text-body-xs text-gray-400 dark:border-gray-600">
            {{ $t('ugc.noAttachments') }}
          </div>
          <div v-for="(a, i) in assets" :key="i" class="mb-2 flex items-center gap-2">
            <span v-if="a.kind === 'VIDEO'" class="flex-1 truncate rounded-md border border-success-subtle bg-success-subtle px-3 py-2 text-body-xs text-success-strong">
              {{ a.name }}
            </span>
            <input v-else v-model="a.url" type="url" class="input-field flex-1" placeholder="https://" />
            <button type="button" :aria-label="$t('ugc.removeAttachment')" class="min-h-11 min-w-11 rounded p-1.5 text-gray-400 hover:bg-gray-100 hover:text-error-strong dark:hover:bg-gray-700" @click="assets.splice(i, 1)">
              <TrashIcon class="h-4 w-4" />
            </button>
          </div>
          <p class="mt-1 text-body-xs text-gray-400">{{ $t('ugc.attachmentHint') }}</p>
        </div>

        <div class="flex items-center justify-end gap-2 border-t border-gray-100 pt-4 dark:border-gray-700">
          <button type="button" class="btn-secondary" :disabled="busy || uploading" @click="save">{{ $t('ugc.saveDraft') }}</button>
          <button type="button" class="btn-primary" :disabled="busy || uploading || !hasContent" @click="saveAndSubmit">{{ $t('ugc.submitNow') }}</button>
        </div>
      </div>

      <!-- External post registration (after approval) -->
      <div v-if="canRegisterExternal" class="card mt-4 space-y-3">
        <h3 class="text-body font-semibold text-gray-900 dark:text-gray-100">{{ $t('ugc.externalTitle') }}</h3>
        <p class="text-body-xs text-gray-400">{{ $t('ugc.externalHint') }}</p>
        <div class="grid grid-cols-1 gap-2 mobile:grid-cols-3">
          <select v-model="extPlatform" class="input-field">
            <option v-for="p in externalPlatforms" :key="p" :value="p">{{ p }}</option>
          </select>
          <input v-model="extUrl" type="url" class="input-field mobile:col-span-2" placeholder="https://" />
        </div>
        <div class="flex justify-end">
          <button class="btn-primary text-body-xs" :disabled="registering || !extUrl.trim()" @click="registerExternal">
            {{ $t('ugc.registerExternal') }}
          </button>
        </div>
        <div v-if="postsLoadError" class="rounded-lg border border-error-subtle bg-error-subtle px-3 py-2 text-body-xs text-error-strong" role="alert">
          {{ postsLoadError }}
          <button type="button" class="ml-2 font-semibold underline" @click="loadMyPosts">{{ $t('action.retry') }}</button>
        </div>
        <ul v-if="myPosts.length" class="space-y-1 border-t border-gray-100 pt-2 dark:border-gray-700">
          <li v-for="post in myPosts" :key="post.id" class="flex items-center justify-between text-body">
            <span class="truncate text-gray-700 dark:text-gray-300">{{ post.platform }}</span>
            <span class="text-body-xs text-gray-400">{{ $t(`ugc.postStatus.${post.status}`) }}</span>
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
import { usePresignedUpload } from '@/composables/usePresignedUpload'
import PageHeader from '@/components/common/PageHeader.vue'
import { ChevronLeftIcon, PlusIcon, TrashIcon } from '@heroicons/vue/24/outline'

const { t } = useI18n({ useScope: 'global' })
const route = useRoute()
const router = useRouter()
const notify = useNotificationStore()

const campaignId = Number(route.params.id)
const submission = ref<SubmissionResponse | null>(null)
const caption = ref('')
type SubmissionAssetForm =
  | { kind: 'EXTERNAL'; url: string }
  | { kind: 'VIDEO'; videoId: number; name: string }

const assets = ref<SubmissionAssetForm[]>([])
const loading = ref(true)
const busy = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadError = ref<string | null>(null)
const videoInput = ref<HTMLInputElement | null>(null)
const extPlatform = ref('YOUTUBE')
const extUrl = ref('')
const registering = ref(false)
const myPosts = ref<CampaignPostResponse[]>([])
const postsLoadError = ref<string | null>(null)
const externalPlatforms = ['YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'TWITTER', 'FACEBOOK', 'THREADS']
const presignedUploader = usePresignedUpload({
  onProgress: (_id, progress) => {
    uploadProgress.value = progress
  },
})

const editable = computed(() => !submission.value || submission.value.status === 'DRAFT' || submission.value.status === 'CHANGES_REQUESTED')
const hasContent = computed(() => assets.value.some((a) => a.kind === 'VIDEO' || a.url.trim()))
const canRegisterExternal = computed(() =>
  submission.value != null && ['APPROVED', 'PUBLISHING', 'PUBLISHED'].includes(submission.value.status),
)

async function loadMyPosts() {
  if (!submission.value) return
  postsLoadError.value = null
  try {
    const res = await ugcPublishingApi.myPosts(submission.value.id)
    myPosts.value = res.items
  } catch (e) {
    postsLoadError.value = e instanceof Error ? e.message : t('ugc.postsLoadFailed')
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

async function handleVideoSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  uploadError.value = null
  uploadProgress.value = 0
  uploading.value = true
  try {
    const videoId = await presignedUploader.upload({
      id: `ugc-${campaignId}-${Date.now()}`,
      file,
      fileName: file.name,
      fileSize: file.size,
      status: 'UPLOADING',
      progress: 0,
      metadata: {
        title: file.name.replace(/\.[^.]+$/, '').slice(0, 200),
        description: caption.value.trim() || undefined,
      },
    })
    if (videoId == null) throw new Error(t('ugc.uploadFailed'))
    assets.value.push({ kind: 'VIDEO', videoId, name: file.name })
    notify.success(t('ugc.videoUploaded'))
  } catch (e) {
    uploadError.value = e instanceof Error ? e.message : t('ugc.uploadFailed')
  } finally {
    uploading.value = false
  }
}

function subStatusClass(status: SubmissionStatus): string {
  switch (status) {
    case 'APPROVED':
    case 'PUBLISHED':
      return 'bg-success-subtle text-success-strong'
    case 'SUBMITTED':
      return 'bg-info-subtle text-info-strong'
    case 'CHANGES_REQUESTED':
      return 'bg-warning-subtle text-warning-strong'
    case 'REJECTED':
    case 'PUBLISH_FAILED':
      return 'bg-error-subtle text-error-strong'
    default:
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
  }
}

function payload() {
  return {
    caption: caption.value.trim() || null,
    assets: assets.value
      .filter((a) => a.kind === 'VIDEO' || a.url.trim())
      .map((a) => a.kind === 'VIDEO'
        ? { assetType: 'VIDEO', resourceType: 'VIDEO', resourceId: a.videoId }
        : { assetType: 'EXTERNAL', externalUrl: a.url.trim() }),
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
      assets.value = res.items[0].assets.reduce<SubmissionAssetForm[]>((result, a) => {
          if (a.assetType === 'VIDEO' && a.resourceId != null) {
            result.push({ kind: 'VIDEO', videoId: a.resourceId, name: `video-${a.resourceId}` })
            return result
          }
          if (a.externalUrl) result.push({ kind: 'EXTERNAL', url: a.externalUrl })
          return result
        }, [])
      await loadMyPosts()
    }
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.loadFailed'))
  } finally {
    loading.value = false
  }
})
</script>

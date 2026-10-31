<script setup lang="ts">
/**
 * 화자 추적 적용 버튼. 클립 장면을 보고 말하는 사람을 따라 움직이는 9:16 크롭을 서버가 저장한다.
 * 저장은 편집기와 같은 길이라 렌더 지시서가 새로 만들어지고 이전 렌더 결과는 버려진다.
 * 크레딧은 적용됐을 때만 차감된다 — 실패 문구는 서버가 준 그대로 보여 준다.
 */
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ugcShortsTrackingApi, type SpeakerTrackingResponse } from '@/api/ugcShortsTracking'

const props = defineProps<{
  workspaceId: number
  runId: number
  clipId: number
  disabled?: boolean
}>()

const emit = defineEmits<{ applied: [result: SpeakerTrackingResponse] }>()

const { t } = useI18n()
const credits = ref<number | null>(null)
const running = ref(false)
const error = ref<string | null>(null)
const lastResult = ref<SpeakerTrackingResponse | null>(null)

onMounted(async () => {
  try {
    credits.value = (await ugcShortsTrackingApi.cost(props.workspaceId, props.runId, props.clipId)).credits
  } catch {
    credits.value = null // 크레딧을 모르면 숫자를 지어내지 않고 표시하지 않는다
  }
})

async function track() {
  running.value = true
  error.value = null
  try {
    const result = await ugcShortsTrackingApi.track(props.workspaceId, props.runId, props.clipId)
    lastResult.value = result
    emit('applied', result)
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('ugc.shorts.caption.trackFailed')
  } finally {
    running.value = false
  }
}
</script>

<template>
  <div class="space-y-1">
    <button
      type="button"
      class="btn-secondary min-h-[44px] w-full"
      :disabled="disabled || running"
      data-testid="speaker-track"
      @click="track"
    >
      {{ running ? $t('ugc.shorts.caption.tracking') : $t('ugc.shorts.caption.track') }}
      <span v-if="credits !== null && !running" class="ml-1 text-caption text-gray-500 dark:text-gray-400">
        {{ $t('ugc.shorts.caption.trackCredits', { credits }) }}
      </span>
    </button>
    <p class="text-caption text-gray-500 dark:text-gray-400">{{ $t('ugc.shorts.caption.trackHint') }}</p>
    <p v-if="lastResult" class="text-caption text-gray-700 dark:text-gray-300" role="status" data-testid="speaker-track-result">
      {{ $t('ugc.shorts.caption.trackApplied', { found: lastResult.foundTiles, total: lastResult.totalTiles }) }}
    </p>
    <p v-if="error" class="text-caption text-red-600 dark:text-red-400" role="alert" data-testid="speaker-track-error">{{ error }}</p>
  </div>
</template>

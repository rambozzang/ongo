<script setup lang="ts">
import { type WorkflowActionType, WORKFLOW_ACTION_OPTIONS } from '@/types/automation'

const actionType = defineModel<WorkflowActionType>('actionType', { required: true })
const config = defineModel<Record<string, unknown>>('config', { required: true })
const delayMinutes = defineModel<number>('delayMinutes', { required: true })
</script>

<template>
  <div class="space-y-4">
    <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-2">
      <span class="w-2 h-2 rounded-full bg-blue-500"></span>
      액션 설정
    </h3>

    <div>
      <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">액션 유형</label>
      <select
        :value="actionType"
        class="input"
        @change="actionType = ($event.target as HTMLSelectElement).value as WorkflowActionType"
      >
        <option v-for="opt in WORKFLOW_ACTION_OPTIONS" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </option>
      </select>
    </div>

    <!-- Action-specific config -->
    <div v-if="actionType === 'SEND_NOTIFICATION'" class="space-y-3">
      <div>
        <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">알림 메시지</label>
        <textarea
          :value="(config.message as string) ?? ''"
          rows="2"
          class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm"
          placeholder="알림 메시지 입력"
          @input="config = { ...config, message: ($event.target as HTMLTextAreaElement).value }"
        />
      </div>
    </div>

    <div v-if="actionType === 'AUTO_PUBLISH'" class="space-y-3">
      <div>
        <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">플랫폼</label>
        <select
          :value="(config.platform as string) ?? 'YOUTUBE'"
          class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm"
          @change="config = { ...config, platform: ($event.target as HTMLSelectElement).value }"
        >
          <option value="YOUTUBE">YouTube</option>
          <option value="TIKTOK">TikTok</option>
          <option value="INSTAGRAM">Instagram</option>
          <option value="NAVER_CLIP">Naver Clip</option>
        </select>
      </div>
    </div>

    <div v-if="actionType === 'ADD_TAG'" class="space-y-3">
      <div>
        <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">태그 (쉼표로 구분)</label>
        <input
          type="text"
          :value="(config.tags as string) ?? ''"
          class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm"
          placeholder="tag1, tag2, tag3"
          @input="config = { ...config, tags: ($event.target as HTMLInputElement).value }"
        />
      </div>
    </div>

    <div v-if="actionType === 'CROSS_POST'" class="space-y-3">
      <div>
        <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">대상 플랫폼</label>
        <div class="space-y-1">
          <label v-for="platform in ['YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'NAVER_CLIP']" :key="platform" class="flex items-center gap-2">
            <input
              type="checkbox"
              :checked="((config.platforms as string[]) ?? []).includes(platform)"
              class="h-4 w-4 rounded border-gray-300 text-primary-600"
              @change="
                ($event.target as HTMLInputElement).checked
                  ? (config = { ...config, platforms: [...((config.platforms as string[]) ?? []), platform] })
                  : (config = { ...config, platforms: ((config.platforms as string[]) ?? []).filter((p: string) => p !== platform) })
              "
            />
            <span class="text-sm text-gray-700 dark:text-gray-300">{{ platform }}</span>
          </label>
        </div>
      </div>
    </div>

    <div v-if="actionType === 'MOVE_STATUS'" class="space-y-3">
      <div>
        <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">대상 상태</label>
        <select
          :value="(config.status as string) ?? 'PUBLISHED'"
          class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm"
          @change="config = { ...config, status: ($event.target as HTMLSelectElement).value }"
        >
          <option value="DRAFT">임시저장</option>
          <option value="PUBLISHED">게시됨</option>
          <option value="ARCHIVED">보관</option>
        </select>
      </div>
    </div>

    <!-- Delay -->
    <div class="pt-2 border-t border-gray-200 dark:border-gray-700">
      <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">실행 지연 (분)</label>
      <div class="flex items-center gap-2">
        <input
          type="number"
          min="0"
          :value="delayMinutes"
          class="w-24 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm"
          @input="delayMinutes = Number(($event.target as HTMLInputElement).value) || 0"
        />
        <span class="text-xs text-gray-500 dark:text-gray-400">0 = 즉시 실행</span>
      </div>
    </div>
  </div>
</template>

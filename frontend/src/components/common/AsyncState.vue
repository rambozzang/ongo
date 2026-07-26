<template>
  <div :aria-busy="loading ? 'true' : undefined">
    <!-- 1) 로딩 -->
    <slot v-if="loading" name="loading">
      <LoadingSpinner v-if="skeleton === 'none'" :full-page="fullPage" />

      <!-- 리스트형 스켈레톤 -->
      <div v-else-if="skeleton === 'list'" class="space-y-3">
        <div v-for="i in skeletonCount" :key="i" class="flex items-center gap-3">
          <OSkeleton type="circle" width="36px" height="36px" />
          <div class="flex-1 space-y-2">
            <OSkeleton type="text" width="60%" />
            <OSkeleton type="text" width="30%" height="12px" />
          </div>
        </div>
      </div>

      <!-- 카드형 스켈레톤 -->
      <div v-else-if="skeleton === 'card'" class="grid gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
        <div
          v-for="i in skeletonCount"
          :key="i"
          class="space-y-3 rounded-xl border border-gray-200 p-4 dark:border-gray-700"
        >
          <OSkeleton type="text" width="45%" height="16px" />
          <OSkeleton type="card" height="88px" />
          <OSkeleton type="text" width="70%" height="12px" />
        </div>
      </div>

      <!-- 테이블형 스켈레톤 -->
      <div v-else class="space-y-2">
        <div class="flex gap-3 border-b border-gray-200 pb-2 dark:border-gray-700">
          <OSkeleton v-for="c in 4" :key="`h-${c}`" type="text" height="12px" class="flex-1" />
        </div>
        <div v-for="i in skeletonCount" :key="i" class="flex gap-3 py-1.5">
          <OSkeleton v-for="c in 4" :key="`c-${c}`" type="text" height="14px" class="flex-1" />
        </div>
      </div>
    </slot>

    <!-- 2) 에러 -->
    <slot v-else-if="error" name="error" :error="error" :retry="handleRetry">
      <div
        role="alert"
        class="rounded-lg border border-error bg-error-subtle p-6"
      >
        <div class="flex items-start gap-3">
          <ExclamationCircleIcon
            class="h-6 w-6 shrink-0 text-error-strong"
            aria-hidden="true"
          />
          <div class="flex-1">
            <h3 class="mb-1 font-semibold text-error-strong">
              {{ errorTitle || t('status.error') }}
            </h3>
            <p class="text-body leading-relaxed text-error-strong">{{ error }}</p>
          </div>
        </div>
        <button
          v-if="retryable"
          type="button"
          class="btn-primary mt-4 inline-flex items-center gap-1.5 text-body"
          @click="handleRetry"
        >
          <ArrowPathIcon class="h-4 w-4" aria-hidden="true" />
          {{ t('action.retry') }}
        </button>
      </div>
    </slot>

    <!-- 3) 빈 상태 -->
    <slot v-else-if="empty" name="empty">
      <EmptyState
        :icon="emptyIcon"
        :title="emptyTitle || t('empty.noData')"
        :description="emptyDescription"
        :variant="emptyVariant"
        :action-label="emptyActionLabel"
        :action-to="emptyActionTo"
        :secondary-action-label="emptySecondaryActionLabel"
        :secondary-action-to="emptySecondaryActionTo"
        @action="emit('empty-action')"
      >
        <template #action>
          <slot name="empty-action" />
        </template>
      </EmptyState>
    </slot>

    <!-- 4) 정상 -->
    <slot v-else />
  </div>
</template>

<script setup lang="ts">
import { type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import { ExclamationCircleIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import OSkeleton from '@/components/ui/OSkeleton.vue'

/**
 * 비동기 영역의 로딩·에러·빈 상태·정상 4가지를 한 곳에서 처리하는 공통 컴포넌트.
 *
 * 기존 컴포넌트를 대체하지 않고 조합한다.
 * - 로딩: `OSkeleton` 조합 (skeleton="none"이면 `LoadingSpinner`)
 * - 빈 상태: `EmptyState`
 * - 에러: 내장 alert 패널 + `@retry`
 *
 * 렌더 우선순위는 로딩 → 에러 → 빈 상태 → 정상 순으로 고정된다.
 * (로딩 중에는 빈 상태·에러가 절대 보이지 않는다.)
 *
 * 주의: 버튼 내부의 작은 스피너처럼 "영역 전체"가 아닌 로딩 표시는 대상이 아니다.
 * 그런 경우는 `LoadingSpinner size="sm"`을 직접 쓴다.
 */
withDefaults(
  defineProps<{
    /** 로딩 중 여부 — 가장 높은 우선순위 */
    loading?: boolean
    /** 에러 메시지. null/빈 문자열이면 에러 없음 */
    error?: string | null
    /** 데이터가 비었는지 여부 */
    empty?: boolean
    /** 로딩 표현 방식. 'none'이면 LoadingSpinner */
    skeleton?: 'list' | 'card' | 'table' | 'none'
    /** 스켈레톤 반복 개수 */
    skeletonCount?: number
    /** skeleton="none"일 때 LoadingSpinner에 전달 */
    fullPage?: boolean
    /** 빈 상태 제목 (기본값 t('empty.noData')) */
    emptyTitle?: string
    /** 빈 상태 설명 */
    emptyDescription?: string
    /** 빈 상태 아이콘 */
    emptyIcon?: Component
    /** 빈 상태 크기 */
    emptyVariant?: 'default' | 'compact'
    /** 빈 상태 CTA 라벨 (EmptyState에 그대로 전달) */
    emptyActionLabel?: string
    /** 빈 상태 CTA 링크 (EmptyState에 그대로 전달) */
    emptyActionTo?: string
    /** 빈 상태 보조 CTA 라벨 */
    emptySecondaryActionLabel?: string
    /** 빈 상태 보조 CTA 링크 */
    emptySecondaryActionTo?: string
    /** 에러 제목 (기본값 t('status.error')) */
    errorTitle?: string
    /** 에러 시 재시도 버튼 노출 여부 */
    retryable?: boolean
  }>(),
  {
    loading: false,
    error: null,
    empty: false,
    skeleton: 'list',
    skeletonCount: 3,
    fullPage: false,
    emptyTitle: undefined,
    emptyDescription: '',
    emptyIcon: undefined,
    emptyVariant: 'default',
    emptyActionLabel: undefined,
    emptyActionTo: undefined,
    emptySecondaryActionLabel: undefined,
    emptySecondaryActionTo: undefined,
    errorTitle: undefined,
    retryable: true,
  },
)

const emit = defineEmits<{
  retry: []
  'empty-action': []
}>()

const { t } = useI18n({ useScope: 'global' })

const handleRetry = () => emit('retry')
</script>

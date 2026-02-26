<script setup lang="ts">
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  MegaphoneIcon,
  UsersIcon,
  EnvelopeOpenIcon,
  SignalIcon,
} from '@heroicons/vue/24/outline'
import { useFanSegmentCampaignStore } from '@/stores/fanSegmentCampaign'
import CampaignCard from '@/components/fansegmentcampaign/CampaignCard.vue'
import SegmentRow from '@/components/fansegmentcampaign/SegmentRow.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const store = useFanSegmentCampaignStore()
const { campaigns, segments, summary, loading } = storeToRefs(store)

onMounted(() => {
  store.fetchCampaigns()
  store.fetchSegments()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          팬 세그먼트 캠페인
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          팬 세그먼트별 맞춤 캠페인 관리
        </p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="loading" :full-page="true" size="lg" />

    <div v-else class="space-y-8">
      <!-- Summary Cards -->
      <div v-if="summary" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100 dark:bg-blue-900/30">
              <MegaphoneIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">총 캠페인</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.totalCampaigns }}개
              </p>
            </div>
          </div>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-green-100 dark:bg-green-900/30">
              <SignalIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">활성 캠페인</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.activeCampaigns }}개
              </p>
            </div>
          </div>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-100 dark:bg-purple-900/30">
              <EnvelopeOpenIcon class="h-5 w-5 text-purple-600 dark:text-purple-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">평균 오픈률</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.avgOpenRate }}%
              </p>
            </div>
          </div>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-orange-100 dark:bg-orange-900/30">
              <UsersIcon class="h-5 w-5 text-orange-600 dark:text-orange-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">총 도달</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.totalReach.toLocaleString('ko-KR') }}
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Campaigns Grid -->
      <section>
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          캠페인 목록
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">({{ campaigns.length }})</span>
        </h2>

        <div v-if="campaigns.length > 0" class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          <CampaignCard
            v-for="campaign in campaigns"
            :key="campaign.id"
            :campaign="campaign"
          />
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <MegaphoneIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">캠페인이 없습니다</h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">새 캠페인을 만들어 팬과 소통하세요</p>
        </div>
      </section>

      <!-- Segments Table -->
      <section>
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          팬 세그먼트
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">({{ segments.length }})</span>
        </h2>

        <div v-if="segments.length > 0" class="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="overflow-x-auto">
            <table class="w-full">
              <thead>
                <tr class="border-b border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-800">
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">세그먼트</th>
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">기준</th>
                  <th class="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">팬 수</th>
                  <th class="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">평균 참여도</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-200 dark:divide-gray-700">
                <SegmentRow
                  v-for="segment in segments"
                  :key="segment.id"
                  :segment="segment"
                />
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

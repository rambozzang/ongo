<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  RectangleGroupIcon,
  DocumentDuplicateIcon,
  ChartBarIcon,
  TagIcon,
  ArrowLeftIcon,
} from '@heroicons/vue/24/outline'
import { useContentClusterStore } from '@/stores/contentCluster'
import type { ContentCluster } from '@/types/contentCluster'
import ClusterCard from '@/components/contentcluster/ClusterCard.vue'
import ClusterContentRow from '@/components/contentcluster/ClusterContentRow.vue'
import TagCloud from '@/components/contentcluster/TagCloud.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const store = useContentClusterStore()
const { clusters, clusterContents, summary, isLoading } = storeToRefs(store)

const selectedCluster = ref<ContentCluster | null>(null)

const tagCloudData = computed(() => {
  const tagMap = new Map<string, number>()
  clusters.value.forEach((c) => {
    c.tags.forEach((tag) => {
      tagMap.set(tag, (tagMap.get(tag) || 0) + 1)
    })
  })
  return Array.from(tagMap.entries())
    .map(([tag, count]) => ({ tag, count }))
    .sort((a, b) => b.count - a.count)
})

const handleSelectCluster = (cluster: ContentCluster) => {
  selectedCluster.value = cluster
  store.fetchClusterContents(cluster.id)
}

const handleBack = () => {
  selectedCluster.value = null
}

onMounted(() => {
  store.fetchClusters()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
            콘텐츠 클러스터
          </h1>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          유사한 콘텐츠를 자동으로 그룹화하여 성과 패턴을 분석합니다
        </p>
      </div>
    </div>

    <!-- Summary Stats -->
    <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <RectangleGroupIcon class="h-5 w-5 text-primary-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">전체 클러스터</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalClusters }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <DocumentDuplicateIcon class="h-5 w-5 text-blue-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">전체 콘텐츠</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalContents }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ChartBarIcon class="h-5 w-5 text-green-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">최고 클러스터</p>
        </div>
        <p class="mt-1 text-lg font-bold text-gray-900 dark:text-gray-100 truncate">
          {{ summary.bestCluster || '-' }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <TagIcon class="h-5 w-5 text-purple-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">최다 태그</p>
        </div>
        <p class="mt-1 text-lg font-bold text-gray-900 dark:text-gray-100 truncate">
          {{ summary.mostCommonTag || '-' }}
        </p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <template v-else>
      <!-- Cluster Detail View -->
      <template v-if="selectedCluster">
        <div class="mb-4">
          <button
            @click="handleBack"
            class="inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
          >
            <ArrowLeftIcon class="h-4 w-4" />
            클러스터 목록으로
          </button>
        </div>

        <div class="mb-6 rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div class="flex items-start gap-3 mb-3">
            <RectangleGroupIcon class="h-6 w-6 flex-shrink-0 text-blue-500" />
            <div>
              <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">
                {{ selectedCluster.name }}
              </h2>
              <p class="mt-1 text-sm text-gray-600 dark:text-gray-400">
                {{ selectedCluster.description }}
              </p>
            </div>
          </div>
          <div class="flex flex-wrap items-center gap-3 text-sm text-gray-600 dark:text-gray-400">
            <span>콘텐츠 {{ selectedCluster.contentCount }}개</span>
            <span class="text-gray-300 dark:text-gray-600">|</span>
            <span>평균 조회수 {{ selectedCluster.avgViews.toLocaleString() }}</span>
            <span class="text-gray-300 dark:text-gray-600">|</span>
            <span>평균 참여율 {{ selectedCluster.avgEngagement }}%</span>
          </div>
          <div class="mt-3 flex flex-wrap gap-1.5">
            <span
              v-for="tag in selectedCluster.tags"
              :key="tag"
              class="inline-flex items-center rounded bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-700 dark:bg-gray-700 dark:text-gray-300"
            >
              #{{ tag }}
            </span>
          </div>
        </div>

        <!-- Cluster Contents List -->
        <div>
          <h3 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
            클러스터 내 콘텐츠
          </h3>
          <div v-if="clusterContents.length > 0" class="space-y-3">
            <ClusterContentRow
              v-for="item in clusterContents"
              :key="item.id"
              :content="item"
            />
          </div>
          <div
            v-else
            class="rounded-xl border border-gray-200 bg-white py-10 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
          >
            <DocumentDuplicateIcon class="mx-auto mb-2 h-10 w-10 text-gray-400 dark:text-gray-600" />
            <p class="text-sm text-gray-500 dark:text-gray-400">클러스터 내 콘텐츠가 없습니다</p>
          </div>
        </div>
      </template>

      <!-- Cluster List View -->
      <template v-else>
        <!-- Tag Cloud -->
        <div class="mb-8">
          <TagCloud :tags="tagCloudData" />
        </div>

        <!-- Cluster Cards Grid -->
        <div>
          <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
            클러스터 목록
          </h2>
          <div v-if="clusters.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
            <ClusterCard
              v-for="c in clusters"
              :key="c.id"
              :cluster="c"
              @select="handleSelectCluster"
            />
          </div>
          <div
            v-else
            class="rounded-xl border border-gray-200 bg-white py-10 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
          >
            <RectangleGroupIcon class="mx-auto mb-2 h-10 w-10 text-gray-400 dark:text-gray-600" />
            <p class="text-sm text-gray-500 dark:text-gray-400">콘텐츠 클러스터 데이터가 없습니다</p>
          </div>
        </div>
      </template>
    </template>
  </div>
</template>

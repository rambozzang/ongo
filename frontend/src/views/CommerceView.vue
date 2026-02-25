<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ $t('commerce.title') }}</h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('commerce.description') }}
        </p>
      </div>
      <div class="flex items-center gap-2">
        <button
          v-for="option in periodOptions"
          :key="option.value"
          class="rounded-lg px-4 py-2 text-sm font-medium transition-colors"
          :class="
            selectedPeriod === option.value
              ? 'bg-primary-600 text-white'
              : 'bg-white text-gray-700 hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
          "
          @click="selectedPeriod = option.value"
        >
          {{ option.label }}
        </button>
      </div>
    </div>

    <PageGuide :title="$t('commerce.pageGuideTitle')" :items="($tm('commerce.pageGuide') as string[])" />

    <!-- 탭 네비게이션 -->
    <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
      <nav class="flex gap-6 overflow-x-auto">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          :class="[
            'whitespace-nowrap border-b-2 pb-3 text-sm font-medium transition-colors',
            activeTab === tab.key
              ? 'border-primary-600 text-primary-600 dark:border-primary-400 dark:text-primary-400'
              : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300',
          ]"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- 로딩 상태 -->
    <div v-if="store.loading" class="flex items-center justify-center py-12">
      <div class="text-gray-500 dark:text-gray-400">{{ $t('commerce.loading') }}</div>
    </div>

    <template v-else>
      <!-- ========== 대시보드 탭 ========== -->
      <template v-if="activeTab === 'dashboard'">
        <!-- KPI 카드 -->
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <CommerceKpiCard
            :label="$t('commerce.totalRevenue')"
            :formatted-value="formatCurrency(store.kpi.totalRevenue)"
            :growth="store.kpi.revenueGrowth"
            :icon="BanknotesIcon"
            border-color-class="border-primary-600"
            icon-bg-class="bg-primary-50 dark:bg-primary-900/20"
            icon-color-class="text-primary-600"
          />
          <CommerceKpiCard
            :label="$t('commerce.totalClicks')"
            :formatted-value="store.kpi.totalClicks.toLocaleString()"
            :growth="store.kpi.clicksGrowth"
            :icon="CursorArrowRaysIcon"
            border-color-class="border-blue-600"
            icon-bg-class="bg-blue-50 dark:bg-blue-900/20"
            icon-color-class="text-blue-600"
          />
          <CommerceKpiCard
            :label="$t('commerce.conversionRate')"
            :formatted-value="`${store.kpi.conversionRate.toFixed(1)}%`"
            :icon="ArrowTrendingUpIcon"
            border-color-class="border-green-600"
            icon-bg-class="bg-green-50 dark:bg-green-900/20"
            icon-color-class="text-green-600"
          />
          <CommerceKpiCard
            :label="$t('commerce.linkedProducts')"
            :formatted-value="`${store.kpi.linkedProductCount}개`"
            :icon="ShoppingBagIcon"
            border-color-class="border-purple-600"
            icon-bg-class="bg-purple-50 dark:bg-purple-900/20"
            icon-color-class="text-purple-600"
          />
        </div>

        <!-- 수익 트렌드 차트 -->
        <div class="card mt-6">
          <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('commerce.revenueTrend') }}
          </h2>
          <div class="h-[400px]">
            <CommerceRevenueChart :data="store.revenueTrends" />
          </div>
        </div>

        <!-- 플랫폼별 성과 & 상위 상품 -->
        <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
          <!-- 플랫폼별 성과 -->
          <div class="card">
            <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('commerce.platformPerformance') }}
            </h2>
            <div class="space-y-4">
              <div
                v-for="perf in store.platformPerformance"
                :key="perf.platform"
                class="rounded-lg border border-gray-100 p-4 dark:border-gray-700"
              >
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-2">
                    <span
                      class="h-3 w-3 rounded-full"
                      :style="{ backgroundColor: getPlatformColor(perf.platform) }"
                    />
                    <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
                      {{ getPlatformLabel(perf.platform) }}
                    </span>
                  </div>
                  <span class="text-sm font-bold text-gray-900 dark:text-gray-100">
                    {{ formatCurrency(perf.revenue) }}
                  </span>
                </div>
                <div class="mt-2 grid grid-cols-3 gap-2 text-xs">
                  <div>
                    <span class="text-gray-500 dark:text-gray-400">{{ $t('commerce.clicks') }}</span>
                    <p class="font-semibold text-gray-700 dark:text-gray-300">{{ perf.clicks.toLocaleString() }}</p>
                  </div>
                  <div>
                    <span class="text-gray-500 dark:text-gray-400">{{ $t('commerce.conversions') }}</span>
                    <p class="font-semibold text-gray-700 dark:text-gray-300">{{ perf.conversions.toLocaleString() }}</p>
                  </div>
                  <div>
                    <span class="text-gray-500 dark:text-gray-400">{{ $t('commerce.conversionRate') }}</span>
                    <p class="font-semibold text-gray-700 dark:text-gray-300">{{ perf.conversionRate.toFixed(1) }}%</p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 상위 상품 -->
          <div class="card">
            <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('commerce.topProducts') }}
            </h2>
            <div class="space-y-3">
              <div
                v-for="(product, idx) in topProducts"
                :key="product.id"
                class="flex items-center justify-between rounded-lg border border-gray-100 p-3 dark:border-gray-700"
              >
                <div class="flex items-center gap-3">
                  <span class="flex h-6 w-6 items-center justify-center rounded-full bg-gray-100 text-xs font-bold text-gray-600 dark:bg-gray-700 dark:text-gray-300">
                    {{ idx + 1 }}
                  </span>
                  <div class="min-w-0">
                    <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">{{ product.name }}</p>
                    <p class="text-xs text-gray-500 dark:text-gray-400">
                      {{ $t('commerce.conversions') }} {{ product.conversions }}건
                    </p>
                  </div>
                </div>
                <span class="shrink-0 text-sm font-bold text-primary-600 dark:text-primary-400">
                  {{ formatCurrency(product.revenue) }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- 영상-상품 연결 현황 -->
        <div class="card mt-6">
          <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('commerce.videoProductLinks') }}
          </h2>
          <VideoProductLinkComponent :links="store.videoProductLinks" />
        </div>
      </template>

      <!-- ========== 상품 관리 탭 ========== -->
      <template v-if="activeTab === 'products'">
        <!-- 검색 / 필터 -->
        <div class="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center">
          <div class="relative flex-1">
            <MagnifyingGlassIcon class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
            <input
              v-model="productSearch"
              type="text"
              :placeholder="$t('commerce.searchProducts')"
              class="w-full rounded-lg border border-gray-300 bg-white py-2 pl-10 pr-4 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
            />
          </div>
          <select
            v-model="productPlatformFilter"
            class="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-700 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300"
          >
            <option value="">{{ $t('commerce.allPlatforms') }}</option>
            <option value="COUPANG">{{ $t('commerce.platformCoupang') }}</option>
            <option value="NAVER_SMARTSTORE">{{ $t('commerce.platformNaver') }}</option>
            <option value="TIKTOK_SHOP">{{ $t('commerce.platformTiktok') }}</option>
          </select>
        </div>

        <!-- 상품 그리드 -->
        <div v-if="filteredProducts.length > 0" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          <ProductCard
            v-for="product in filteredProducts"
            :key="product.id"
            :product="product"
          />
        </div>
        <div v-else class="py-12 text-center text-gray-400 dark:text-gray-500">
          <ShoppingBagIcon class="mx-auto h-10 w-10 mb-2" />
          <p class="text-sm">{{ $t('commerce.emptyProducts') }}</p>
        </div>
      </template>

      <!-- ========== 링크 관리 탭 ========== -->
      <template v-if="activeTab === 'links'">
        <ProductLinkManager :links="store.affiliateLinks" />
      </template>

      <!-- ========== 연동 설정 탭 ========== -->
      <template v-if="activeTab === 'settings'">
        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <CommercePlatformCard
            v-for="conn in store.platforms"
            :key="conn.platform"
            :connection="conn"
            @connect="handleConnect"
            @disconnect="handleDisconnect"
          />
        </div>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  BanknotesIcon,
  CursorArrowRaysIcon,
  ArrowTrendingUpIcon,
  ShoppingBagIcon,
  MagnifyingGlassIcon,
} from '@heroicons/vue/24/outline'
import { useCommerceStore } from '@/stores/commerce'
import type { CommercePlatform } from '@/types/commerce'
import PageGuide from '@/components/common/PageGuide.vue'
import CommerceKpiCard from '@/components/commerce/CommerceKpiCard.vue'
import CommerceRevenueChart from '@/components/commerce/CommerceRevenueChart.vue'
import ProductCard from '@/components/commerce/ProductCard.vue'
import ProductLinkManager from '@/components/commerce/ProductLinkManager.vue'
import VideoProductLinkComponent from '@/components/commerce/VideoProductLink.vue'
import CommercePlatformCard from '@/components/commerce/CommercePlatformCard.vue'
import { COMMERCE_PLATFORM_CONFIG } from '@/components/commerce/commerceConstants'

const { t } = useI18n()
const store = useCommerceStore()

// ----- 탭 -----
type CommerceTab = 'dashboard' | 'products' | 'links' | 'settings'
const activeTab = ref<CommerceTab>('dashboard')

const tabs: { key: CommerceTab; label: string }[] = [
  { key: 'dashboard', label: t('commerce.tabDashboard') },
  { key: 'products', label: t('commerce.tabProducts') },
  { key: 'links', label: t('commerce.tabLinks') },
  { key: 'settings', label: t('commerce.tabSettings') },
]

// ----- 기간 필터 -----
const periodOptions = [
  { value: '7', label: t('commerce.period7Days') },
  { value: '30', label: t('commerce.period30Days') },
  { value: '90', label: t('commerce.period90Days') },
]
const selectedPeriod = ref('30')

// ----- 상품 관리 필터 -----
const productSearch = ref('')
const productPlatformFilter = ref('')

const filteredProducts = computed(() => {
  let list = store.products
  if (productPlatformFilter.value) {
    list = list.filter(p => p.platform === productPlatformFilter.value)
  }
  if (productSearch.value.trim()) {
    const q = productSearch.value.trim().toLowerCase()
    list = list.filter(p => p.name.toLowerCase().includes(q))
  }
  return list
})

// ----- 상위 상품 (수익 기준 상위 5개) -----
const topProducts = computed(() =>
  [...store.products].sort((a, b) => b.revenue - a.revenue).slice(0, 5)
)

// ----- 플랫폼 정보 헬퍼 -----
function getPlatformColor(platform: CommercePlatform): string {
  return COMMERCE_PLATFORM_CONFIG[platform]?.color ?? '#6B7280'
}

function getPlatformLabel(platform: CommercePlatform): string {
  return COMMERCE_PLATFORM_CONFIG[platform]?.label ?? platform
}

function formatCurrency(value: number): string {
  if (value >= 100000000) return `₩${(value / 100000000).toFixed(1)}억`
  if (value >= 10000) return `₩${(value / 10000).toFixed(0)}만`
  return `₩${value.toLocaleString('ko-KR')}`
}

// ----- 탭 전환 시 데이터 로드 -----
watch(activeTab, (tab) => {
  if (tab === 'products' && store.products.length === 0) {
    store.fetchProducts()
  } else if (tab === 'links' && store.affiliateLinks.length === 0) {
    store.fetchAffiliateLinks()
  } else if (tab === 'settings' && store.platforms.length === 0) {
    store.fetchPlatforms()
  }
})

// ----- 기간 변경 시 KPI 재로드 -----
watch(selectedPeriod, (val) => {
  const days = parseInt(val)
  store.fetchKpi(days)
  store.fetchRevenueTrends(days)
  store.fetchPlatformPerformance(days)
})

// ----- 플랫폼 연결/해제 -----
async function handleConnect(platform: string) {
  await store.connectPlatform(platform as CommercePlatform)
}

async function handleDisconnect(platform: string) {
  await store.disconnectPlatform(platform as CommercePlatform)
}

onMounted(async () => {
  await store.fetchDashboard()
  store.fetchProducts()
  store.fetchVideoProductLinks()
})
</script>

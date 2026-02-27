<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  ShoppingBagIcon,
  ClipboardDocumentListIcon,
  CurrencyDollarIcon,
  StarIcon,
} from '@heroicons/vue/24/outline'
import { useCreatorMarketplaceStore } from '@/stores/creatorMarketplace'
import ListingCard from '@/components/creatormarketplace/ListingCard.vue'
import OrderRow from '@/components/creatormarketplace/OrderRow.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { useLocale } from '@/composables/useLocale'

const { t } = useLocale()

const store = useCreatorMarketplaceStore()

type ServiceFilter = 'ALL' | 'EDITING' | 'THUMBNAIL' | 'SCRIPT' | 'VOICEOVER' | 'CONSULTING' | 'COLLAB'
const activeFilter = ref<ServiceFilter>('ALL')

const filterTabs = computed<{ value: ServiceFilter; label: string }[]>(() => [
  { value: 'ALL', label: t('creatorMarketplace.filterAll') },
  { value: 'EDITING', label: t('creatorMarketplace.filterEditing') },
  { value: 'THUMBNAIL', label: t('creatorMarketplace.filterThumbnail') },
  { value: 'SCRIPT', label: t('creatorMarketplace.filterScript') },
  { value: 'VOICEOVER', label: t('creatorMarketplace.filterVoiceover') },
  { value: 'CONSULTING', label: t('creatorMarketplace.filterConsulting') },
  { value: 'COLLAB', label: t('creatorMarketplace.filterCollab') },
])

const filteredListings = computed(() => {
  if (activeFilter.value === 'ALL') return store.listings
  return store.listings.filter((l) => l.serviceType === activeFilter.value)
})

const formatPrice = (price: number): string => {
  if (price >= 100000000) {
    return `${(price / 100000000).toFixed(1)}${t('creatorMarketplace.billionUnit')}`
  }
  if (price >= 10000) {
    return `${(price / 10000).toFixed(0)}${t('creatorMarketplace.tenThousandUnit')}`
  }
  return `${price.toLocaleString('ko-KR')}${t('creatorMarketplace.wonUnit')}`
}

function handleOrder(listingId: number) {
  if (confirm(t('creatorMarketplace.confirmOrder'))) {
    store.placeOrder(listingId)
  }
}

onMounted(() => {
  store.fetchListings()
  store.fetchOrders()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('creatorMarketplace.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('creatorMarketplace.description') }}
        </p>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="store.loading" class="flex items-center justify-center py-20">
      <LoadingSpinner size="lg" />
    </div>

    <template v-else>
      <!-- Summary Cards -->
      <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
        <!-- Total Listings -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <ShoppingBagIcon class="h-5 w-5 text-gray-400 dark:text-gray-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('creatorMarketplace.totalListings') }}</p>
          </div>
          <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary?.totalListings?.toLocaleString() ?? 0 }}
          </p>
        </div>

        <!-- Active Orders -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <ClipboardDocumentListIcon class="h-5 w-5 text-blue-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('creatorMarketplace.activeOrders') }}</p>
          </div>
          <p class="mt-1 text-2xl font-bold text-blue-600 dark:text-blue-400">
            {{ store.summary?.activeOrders ?? 0 }}
          </p>
        </div>

        <!-- Total Revenue -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <CurrencyDollarIcon class="h-5 w-5 text-green-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('creatorMarketplace.totalRevenue') }}</p>
          </div>
          <p class="mt-1 text-2xl font-bold text-green-600 dark:text-green-400">
            {{ formatPrice(store.summary?.totalRevenue ?? 0) }}
          </p>
        </div>

        <!-- Average Rating -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <StarIcon class="h-5 w-5 text-yellow-400" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('creatorMarketplace.avgRating') }}</p>
          </div>
          <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary?.avgRating?.toFixed(1) ?? '0.0' }}
            <span class="text-sm font-normal text-gray-400">/ 5.0</span>
          </p>
        </div>
      </div>

      <!-- Service Type Filter Tabs -->
      <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
        <nav class="-mb-px flex flex-wrap gap-4">
          <button
            v-for="tab in filterTabs"
            :key="tab.value"
            @click="activeFilter = tab.value"
            :class="[
              'whitespace-nowrap border-b-2 px-1 py-3 text-sm font-medium transition-colors',
              activeFilter === tab.value
                ? 'border-primary-500 text-primary-600 dark:border-primary-400 dark:text-primary-400'
                : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400 dark:hover:border-gray-600 dark:hover:text-gray-300',
            ]"
          >
            {{ tab.label }}
          </button>
        </nav>
      </div>

      <!-- Listing Card Grid -->
      <div v-if="filteredListings.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
        <ListingCard
          v-for="listing in filteredListings"
          :key="listing.id"
          :listing="listing"
          @order="handleOrder"
        />
      </div>

      <!-- Empty state -->
      <div
        v-else
        class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
      >
        <ShoppingBagIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
        <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('creatorMarketplace.noListings') }}
        </h3>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('creatorMarketplace.noListingsHint') }}
        </p>
      </div>

      <!-- My Orders Section -->
      <div class="mt-8">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('creatorMarketplace.myOrders') }}
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">
            ({{ store.orders.length }})
          </span>
        </h2>

        <div
          v-if="store.orders.length > 0"
          class="rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-gray-200 text-xs uppercase text-gray-500 dark:border-gray-700 dark:text-gray-400">
                  <th class="px-4 py-3 text-left font-medium">{{ $t('creatorMarketplace.orderId') }}</th>
                  <th class="px-4 py-3 text-left font-medium">{{ $t('creatorMarketplace.buyer') }}</th>
                  <th class="px-4 py-3 text-left font-medium">{{ $t('creatorMarketplace.seller') }}</th>
                  <th class="px-4 py-3 text-left font-medium">{{ $t('creatorMarketplace.status') }}</th>
                  <th class="px-4 py-3 text-right font-medium">{{ $t('creatorMarketplace.price') }}</th>
                  <th class="px-4 py-3 text-left font-medium">{{ $t('creatorMarketplace.orderDate') }}</th>
                  <th class="px-4 py-3 text-left font-medium">{{ $t('creatorMarketplace.deliveryDate') }}</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
                <OrderRow
                  v-for="order in store.orders"
                  :key="order.id"
                  :order="order"
                />
              </tbody>
            </table>
          </div>
        </div>

        <!-- Empty orders -->
        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-12 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <ClipboardDocumentListIcon class="mx-auto mb-3 h-10 w-10 text-gray-400 dark:text-gray-600" />
          <p class="text-sm text-gray-500 dark:text-gray-400">
            {{ $t('creatorMarketplace.noOrders') }}
          </p>
        </div>
      </div>
    </template>
  </div>
</template>

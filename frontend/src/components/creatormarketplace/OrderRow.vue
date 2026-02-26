<script setup lang="ts">
import type { MarketplaceOrder } from '@/types/creatorMarketplace'

defineProps<{
  order: MarketplaceOrder
}>()

const statusConfig: Record<string, { bg: string; text: string; label: string }> = {
  PENDING: { bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300', label: '대기중' },
  ACCEPTED: { bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-300', label: '수락됨' },
  IN_PROGRESS: { bg: 'bg-indigo-100 dark:bg-indigo-900/30', text: 'text-indigo-700 dark:text-indigo-300', label: '진행중' },
  DELIVERED: { bg: 'bg-purple-100 dark:bg-purple-900/30', text: 'text-purple-700 dark:text-purple-300', label: '배송완료' },
  COMPLETED: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300', label: '완료' },
  CANCELLED: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300', label: '취소됨' },
}

const getStatusConfig = (status: string) => statusConfig[status] ?? statusConfig.PENDING

const formatDate = (iso: string) => {
  try {
    const date = new Date(iso)
    return date.toLocaleDateString('ko-KR', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    })
  } catch {
    return iso
  }
}

const formatPrice = (price: number): string => {
  if (price >= 10000) {
    return `${(price / 10000).toFixed(0)}만원`
  }
  return `${price.toLocaleString('ko-KR')}원`
}
</script>

<template>
  <tr class="hover:bg-gray-50 dark:hover:bg-gray-700/50">
    <!-- Order ID -->
    <td class="whitespace-nowrap px-4 py-3 text-sm font-medium text-gray-900 dark:text-gray-100">
      #{{ order.id }}
    </td>

    <!-- Buyer -->
    <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">
      {{ order.buyerName }}
    </td>

    <!-- Seller -->
    <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">
      {{ order.sellerName }}
    </td>

    <!-- Status badge -->
    <td class="px-4 py-3">
      <span
        :class="[getStatusConfig(order.status).bg, getStatusConfig(order.status).text]"
        class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
      >
        {{ getStatusConfig(order.status).label }}
      </span>
    </td>

    <!-- Price -->
    <td class="whitespace-nowrap px-4 py-3 text-right text-sm font-bold text-gray-900 dark:text-gray-100">
      {{ formatPrice(order.totalPrice) }}
    </td>

    <!-- Order date -->
    <td class="whitespace-nowrap px-4 py-3 text-sm text-gray-500 dark:text-gray-400">
      {{ formatDate(order.orderDate) }}
    </td>

    <!-- Delivery date -->
    <td class="whitespace-nowrap px-4 py-3 text-sm text-gray-500 dark:text-gray-400">
      {{ order.deliveryDate ? formatDate(order.deliveryDate) : '-' }}
    </td>
  </tr>
</template>

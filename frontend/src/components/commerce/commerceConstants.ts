import type { CommercePlatform } from '@/types/commerce'

export const COMMERCE_PLATFORM_CONFIG: Record<CommercePlatform, {
  label: string
  color: string
  badgeClass: string
  iconBg: string
}> = {
  COUPANG: {
    label: '쿠팡 파트너스',
    color: '#E4002B',
    badgeClass: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
    iconBg: 'bg-red-50 dark:bg-red-900/20',
  },
  NAVER_SMARTSTORE: {
    label: '네이버 스마트스토어',
    color: '#03C75A',
    badgeClass: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    iconBg: 'bg-green-50 dark:bg-green-900/20',
  },
  TIKTOK_SHOP: {
    label: 'TikTok Shop',
    color: '#000000',
    badgeClass: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
    iconBg: 'bg-gray-50 dark:bg-gray-800',
  },
}

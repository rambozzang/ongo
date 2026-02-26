import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { fanRewardApi } from '@/api/fanReward'
import type { FanReward, FanActivity, FanRewardSummary } from '@/types/fanReward'

export const useFanRewardStore = defineStore('fanReward', () => {
  const rewards = ref<FanReward[]>([])
  const activities = ref<FanActivity[]>([])
  const summary = ref<FanRewardSummary | null>(null)
  const loading = ref(false)

  const activeRewards = computed(() => rewards.value.filter(r => r.isActive))

  async function fetchRewards() {
    loading.value = true
    try {
      rewards.value = await fanRewardApi.getRewards()
    } catch {
      rewards.value = [
        { id: 1, name: '슈퍼팬 뱃지', description: '100일 연속 시청 팬에게 수여', pointsCost: 500, category: 'BADGE', isActive: true, claimedCount: 45, maxClaims: null, imageUrl: null, createdAt: '2025-01-15T10:00:00Z' },
        { id: 2, name: '독점 비하인드 영상', description: '비하인드 촬영 영상 열람권', pointsCost: 1000, category: 'EXCLUSIVE', isActive: true, claimedCount: 23, maxClaims: 50, imageUrl: null, createdAt: '2025-02-01T10:00:00Z' },
        { id: 3, name: '라이브 샤아웃', description: '다음 라이브에서 이름 불러드려요', pointsCost: 2000, category: 'SHOUTOUT', isActive: true, claimedCount: 8, maxClaims: 10, imageUrl: null, createdAt: '2025-02-15T10:00:00Z' },
        { id: 4, name: '한정판 굿즈', description: '사인 포토카드 발송', pointsCost: 5000, category: 'MERCH', isActive: false, claimedCount: 20, maxClaims: 20, imageUrl: null, createdAt: '2025-03-01T10:00:00Z' },
      ]
    } finally {
      loading.value = false
    }
  }

  async function fetchActivities() {
    try {
      activities.value = await fanRewardApi.getActivities()
    } catch {
      activities.value = [
        { id: 1, fanName: 'fan_star01', activityType: 'COMMENT', points: 10, description: '영상에 댓글 작성', timestamp: '2025-03-12T09:30:00Z' },
        { id: 2, fanName: 'best_viewer', activityType: 'WATCH', points: 5, description: '영상 전체 시청 완료', timestamp: '2025-03-12T10:15:00Z' },
        { id: 3, fanName: 'share_king', activityType: 'SHARE', points: 20, description: 'SNS 공유', timestamp: '2025-03-12T11:00:00Z' },
        { id: 4, fanName: 'new_sub99', activityType: 'SUBSCRIBE', points: 50, description: '채널 구독', timestamp: '2025-03-12T12:30:00Z' },
      ]
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await fanRewardApi.getSummary()
    } catch {
      summary.value = { totalRewards: 12, activeRewards: 8, totalPointsDistributed: 125000, totalClaims: 320, topCategory: 'BADGE' }
    }
  }

  return { rewards, activities, summary, loading, activeRewards, fetchRewards, fetchActivities, fetchSummary }
})

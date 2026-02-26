import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { FanPoll, PollSummary, PollCreateRequest } from '@/types/fanPoll'
import { fanPollApi } from '@/api/fanPoll'

function generateMockSummary(): PollSummary {
  return {
    totalPolls: 12,
    activePolls: 3,
    closedPolls: 8,
    totalVotes: 4520,
    avgVotesPerPoll: 376,
  }
}

function generateMockPolls(): FanPoll[] {
  return [
    {
      id: 1,
      title: '다음 영상 주제 투표',
      description: '다음 주에 어떤 주제의 영상을 보고 싶으신가요?',
      type: 'SINGLE_CHOICE',
      status: 'ACTIVE',
      options: [
        { id: 1, text: '일상 브이로그', voteCount: 245, percentage: 42.3 },
        { id: 2, text: '맛집 탐방', voteCount: 189, percentage: 32.6 },
        { id: 3, text: '제품 리뷰', voteCount: 98, percentage: 16.9 },
        { id: 4, text: 'Q&A 라이브', voteCount: 47, percentage: 8.1 },
      ],
      totalVotes: 579,
      deadline: new Date(Date.now() + 3 * 86400000).toISOString(),
      createdAt: new Date(Date.now() - 2 * 86400000).toISOString(),
      updatedAt: new Date().toISOString(),
      allowMultiple: false,
    },
    {
      id: 2,
      title: '굿즈 디자인 선호도',
      description: '어떤 굿즈 디자인이 가장 마음에 드시나요? (복수 선택 가능)',
      type: 'MULTIPLE_CHOICE',
      status: 'ACTIVE',
      options: [
        { id: 5, text: '미니멀 로고 스티커', voteCount: 312, percentage: 38.2 },
        { id: 6, text: '캐릭터 아크릴 키링', voteCount: 287, percentage: 35.1 },
        { id: 7, text: '시그니처 에코백', voteCount: 218, percentage: 26.7 },
      ],
      totalVotes: 817,
      deadline: new Date(Date.now() + 7 * 86400000).toISOString(),
      createdAt: new Date(Date.now() - 5 * 86400000).toISOString(),
      updatedAt: new Date().toISOString(),
      allowMultiple: true,
      maxSelections: 2,
    },
    {
      id: 3,
      title: '촬영 장소 투표',
      description: '다음 야외 촬영 장소를 골라주세요!',
      type: 'SINGLE_CHOICE',
      status: 'CLOSED',
      options: [
        { id: 8, text: '한강공원', voteCount: 523, percentage: 45.8 },
        { id: 9, text: '북촌한옥마을', voteCount: 398, percentage: 34.9 },
        { id: 10, text: '성수동 카페거리', voteCount: 220, percentage: 19.3 },
      ],
      totalVotes: 1141,
      deadline: new Date(Date.now() - 2 * 86400000).toISOString(),
      createdAt: new Date(Date.now() - 10 * 86400000).toISOString(),
      updatedAt: new Date(Date.now() - 2 * 86400000).toISOString(),
      allowMultiple: false,
    },
    {
      id: 4,
      title: '라이브 방송 시간대',
      description: '어떤 시간대에 라이브 방송을 시청하기 편하신가요?',
      type: 'RANKING',
      status: 'SCHEDULED',
      options: [
        { id: 11, text: '오후 2시', voteCount: 0, percentage: 0 },
        { id: 12, text: '오후 7시', voteCount: 0, percentage: 0 },
        { id: 13, text: '오후 9시', voteCount: 0, percentage: 0 },
        { id: 14, text: '오후 11시', voteCount: 0, percentage: 0 },
      ],
      totalVotes: 0,
      deadline: new Date(Date.now() + 14 * 86400000).toISOString(),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      allowMultiple: false,
    },
    {
      id: 5,
      title: '콜라보 파트너 선호',
      description: '어떤 크리에이터와의 콜라보를 보고 싶으신가요?',
      type: 'SINGLE_CHOICE',
      status: 'CLOSED',
      options: [
        { id: 15, text: '뷰티 크리에이터 A', voteCount: 634, percentage: 33.1 },
        { id: 16, text: '게이밍 크리에이터 B', voteCount: 512, percentage: 26.7 },
        { id: 17, text: '먹방 크리에이터 C', voteCount: 445, percentage: 23.2 },
        { id: 18, text: '여행 크리에이터 D', voteCount: 325, percentage: 17.0 },
      ],
      totalVotes: 1916,
      deadline: new Date(Date.now() - 7 * 86400000).toISOString(),
      createdAt: new Date(Date.now() - 21 * 86400000).toISOString(),
      updatedAt: new Date(Date.now() - 7 * 86400000).toISOString(),
      allowMultiple: false,
    },
  ]
}

export const useFanPollStore = defineStore('fanPoll', () => {
  const polls = ref<FanPoll[]>([])
  const summary = ref<PollSummary | null>(null)
  const loading = ref(false)

  const activePolls = computed(() => polls.value.filter((p) => p.status === 'ACTIVE'))
  const closedPolls = computed(() => polls.value.filter((p) => p.status === 'CLOSED'))
  const scheduledPolls = computed(() => polls.value.filter((p) => p.status === 'SCHEDULED'))
  const draftPolls = computed(() => polls.value.filter((p) => p.status === 'DRAFT'))

  async function fetchSummary() {
    try {
      summary.value = await fanPollApi.getSummary()
    } catch {
      summary.value = generateMockSummary()
    }
  }

  async function fetchPolls(status?: string) {
    loading.value = true
    try {
      polls.value = await fanPollApi.getPolls(status)
    } catch {
      polls.value = generateMockPolls()
    } finally {
      loading.value = false
    }
  }

  async function createPoll(request: PollCreateRequest) {
    try {
      const poll = await fanPollApi.createPoll(request)
      polls.value.unshift(poll)
    } catch {
      const newPoll: FanPoll = {
        id: Date.now(),
        title: request.title,
        description: request.description,
        type: request.type,
        status: 'ACTIVE',
        options: request.options.map((text, i) => ({
          id: Date.now() + i,
          text,
          voteCount: 0,
          percentage: 0,
        })),
        totalVotes: 0,
        deadline: request.deadline,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        allowMultiple: request.allowMultiple,
        maxSelections: request.maxSelections,
      }
      polls.value.unshift(newPoll)
    }
  }

  async function closePoll(id: number) {
    try {
      const updated = await fanPollApi.closePoll(id)
      const idx = polls.value.findIndex((p) => p.id === id)
      if (idx >= 0) polls.value[idx] = updated
    } catch {
      const poll = polls.value.find((p) => p.id === id)
      if (poll) poll.status = 'CLOSED'
    }
  }

  async function deletePoll(id: number) {
    try { await fanPollApi.deletePoll(id) } catch { /* local */ }
    polls.value = polls.value.filter((p) => p.id !== id)
  }

  return {
    polls,
    summary,
    loading,
    activePolls,
    closedPolls,
    scheduledPolls,
    draftPolls,
    fetchSummary,
    fetchPolls,
    createPoll,
    closePoll,
    deletePoll,
  }
})

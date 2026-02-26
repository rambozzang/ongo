import { defineStore } from 'pinia'
import { ref } from 'vue'
import { fanCommunityApi } from '@/api/fanCommunity'
import type {
  CommunityPost,
  CommunitySummary,
  CreatePostRequest,
} from '@/types/fanCommunity'

export const useFanCommunityStore = defineStore('fanCommunity', () => {
  const posts = ref<CommunityPost[]>([])
  const summary = ref<CommunitySummary>({
    totalPosts: 0,
    totalMembers: 0,
    activeMembersToday: 0,
    totalLikes: 0,
    postsThisWeek: 0,
  })
  const isLoading = ref(false)

  const mockPosts: CommunityPost[] = [
    {
      id: 1,
      type: 'ANNOUNCEMENT',
      title: '3월 라이브 방송 일정 안내',
      content: '안녕하세요 여러분! 3월 라이브 방송 일정을 알려드립니다. 매주 금요일 저녁 8시에 만나요!',
      authorName: '김크리에이터',
      isCreator: true,
      likes: 342,
      comments: 56,
      shares: 23,
      isPinned: true,
      tags: ['공지', '라이브'],
      attachments: [],
      createdAt: '2025-03-01T10:00:00Z',
      updatedAt: '2025-03-01T10:00:00Z',
    },
    {
      id: 2,
      type: 'BEHIND_SCENES',
      title: '촬영 비하인드 - 제주도 여행 브이로그',
      content: '지난주 제주도 여행 브이로그 촬영 비하인드입니다. 영상에서 보지 못한 장면들을 공유해요 😊',
      authorName: '김크리에이터',
      isCreator: true,
      likes: 567,
      comments: 89,
      shares: 45,
      isPinned: false,
      tags: ['비하인드', '제주도', '여행'],
      attachments: [
        { id: 1, type: 'IMAGE', url: '/placeholder.jpg', name: 'behind1.jpg', size: 2048000 },
        { id: 2, type: 'IMAGE', url: '/placeholder.jpg', name: 'behind2.jpg', size: 1536000 },
      ],
      createdAt: '2025-03-08T14:00:00Z',
      updatedAt: '2025-03-08T14:00:00Z',
    },
    {
      id: 3,
      type: 'POLL',
      title: '다음 영상 주제 투표',
      content: '다음 주에 올릴 영상 주제를 골라주세요! 가장 많은 투표를 받은 주제로 촬영합니다.',
      authorName: '김크리에이터',
      isCreator: true,
      likes: 234,
      comments: 67,
      shares: 12,
      isPinned: false,
      tags: ['투표', '다음영상'],
      attachments: [],
      createdAt: '2025-03-09T16:00:00Z',
      updatedAt: '2025-03-09T16:00:00Z',
    },
    {
      id: 4,
      type: 'QNA',
      title: '장비 추천 부탁드려요',
      content: '유튜브 시작하려는 초보인데요, 카메라와 마이크 추천해주실 수 있나요?',
      authorName: '영상초보',
      isCreator: false,
      likes: 45,
      comments: 23,
      shares: 5,
      isPinned: false,
      tags: ['질문', '장비'],
      attachments: [],
      createdAt: '2025-03-10T09:00:00Z',
      updatedAt: '2025-03-10T09:00:00Z',
    },
    {
      id: 5,
      type: 'FAN_ART',
      title: '크리에이터님 팬아트 그려봤어요!',
      content: '항상 좋은 영상 감사합니다. 팬아트 그려봤는데 마음에 드셨으면 좋겠어요!',
      authorName: '아트팬',
      isCreator: false,
      likes: 189,
      comments: 34,
      shares: 18,
      isPinned: false,
      tags: ['팬아트'],
      attachments: [
        { id: 3, type: 'IMAGE', url: '/placeholder.jpg', name: 'fanart.png', size: 3072000 },
      ],
      createdAt: '2025-03-10T11:00:00Z',
      updatedAt: '2025-03-10T11:00:00Z',
    },
  ]

  const mockSummary: CommunitySummary = {
    totalPosts: 156,
    totalMembers: 12450,
    activeMembersToday: 843,
    totalLikes: 45230,
    postsThisWeek: 23,
  }

  async function fetchPosts(type?: string) {
    isLoading.value = true
    try {
      posts.value = await fanCommunityApi.getPosts(type)
    } catch {
      posts.value = type
        ? mockPosts.filter((p) => p.type === type)
        : mockPosts
    } finally {
      isLoading.value = false
    }
  }

  async function createPost(request: CreatePostRequest) {
    try {
      const post = await fanCommunityApi.createPost(request)
      posts.value.unshift(post)
    } catch {
      const newPost: CommunityPost = {
        id: Date.now(),
        ...request,
        authorName: '김크리에이터',
        isCreator: true,
        likes: 0,
        comments: 0,
        shares: 0,
        attachments: [],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      }
      posts.value.unshift(newPost)
    }
  }

  async function deletePost(id: number) {
    try {
      await fanCommunityApi.deletePost(id)
    } catch {
      // mock fallback
    }
    posts.value = posts.value.filter((p) => p.id !== id)
  }

  async function likePost(id: number) {
    try {
      await fanCommunityApi.likePost(id)
    } catch {
      // mock fallback
    }
    const post = posts.value.find((p) => p.id === id)
    if (post) post.likes++
  }

  async function pinPost(id: number) {
    try {
      await fanCommunityApi.pinPost(id)
    } catch {
      // mock fallback
    }
    posts.value.forEach((p) => {
      p.isPinned = p.id === id ? !p.isPinned : p.isPinned
    })
  }

  async function fetchSummary() {
    try {
      summary.value = await fanCommunityApi.getSummary()
    } catch {
      summary.value = mockSummary
    }
  }

  return {
    posts,
    summary,
    isLoading,
    fetchPosts,
    createPost,
    deletePost,
    likePost,
    pinPost,
    fetchSummary,
  }
})

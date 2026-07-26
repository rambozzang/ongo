import { ref, computed, watch, type Component } from 'vue'
import { useRouter } from 'vue-router'
import {
  VideoCameraIcon,
  RectangleGroupIcon,
  Cog6ToothIcon,
  PlusCircleIcon,
  ChartBarIcon,
  SparklesIcon,
  CreditCardIcon,
  Squares2X2Icon,
} from '@heroicons/vue/24/outline'
import { videoApi } from '@/api/video'
import { channelApi } from '@/api/channel'
import { PLATFORM_CONFIG, type Channel } from '@/types/channel'

export interface SearchResult {
  id: string
  title: string
  subtitle?: string
  category: '영상' | '채널' | '일정' | '페이지' | '설정'
  icon: Component
  route: string
}

export interface QuickAction {
  id: string
  title: string
  icon: Component
  iconColor: string
  route: string
}

// Static settings entries (always available for search)
const settingsEntries = [
  { id: 's1', title: '계정 설정', subtitle: '설정 · 프로필 및 보안', route: '/settings' },
  { id: 's2', title: '채널 연동', subtitle: '설정 · 플랫폼 관리', route: '/channels' },
  { id: 's3', title: '알림 설정', subtitle: '설정 · 알림 및 이메일', route: '/settings' },
  { id: 's4', title: '구독 관리', subtitle: '설정 · 플랜 및 결제', route: '/subscription' },
]

// Static page index — every navigable page in the sidebar (keeps deep pages reachable via ⌘K)
// Routes covered by settingsEntries (/settings, /channels, /subscription) are intentionally omitted.
interface PageEntry {
  id: string
  title: string
  group: string
  route: string
  keywords: string
}

const pageEntries: PageEntry[] = [
  { id: 'p1', title: '대시보드', group: '홈', route: '/dashboard', keywords: 'dashboard home 홈' },
  { id: 'p2', title: '업로드', group: '콘텐츠 제작', route: '/upload', keywords: 'upload 영상 올리기 게시' },
  { id: 'p3', title: '영상 관리', group: '콘텐츠 제작', route: '/videos', keywords: 'videos 비디오 목록' },
  { id: 'p4', title: 'AI 도구', group: '콘텐츠 제작', route: '/ai', keywords: 'ai tools 인공지능 생성' },
  { id: 'p5', title: '아이디어', group: '콘텐츠 제작', route: '/ideas', keywords: 'ideas 기획 소재' },
  { id: 'p6', title: 'A/B 테스트', group: '콘텐츠 제작', route: '/abtest', keywords: 'abtest ab 실험' },
  { id: 'p7', title: '템플릿', group: '콘텐츠 제작 · 콘텐츠 관리', route: '/templates', keywords: 'templates 양식' },
  { id: 'p8', title: '브랜드 키트', group: '콘텐츠 제작 · 콘텐츠 관리', route: '/brandkit', keywords: 'brandkit brand 로고 색상' },
  { id: 'p9', title: '에셋', group: '콘텐츠 제작 · 콘텐츠 관리', route: '/assets', keywords: 'assets 라이브러리 소재 파일 이미지' },
  { id: 'p10', title: '콘텐츠 재활용', group: '콘텐츠 제작 · 콘텐츠 관리', route: '/recycling', keywords: 'recycling repurpose 재사용 리퍼포징' },
  { id: 'p12', title: '캘린더 · 예약 관리', group: '게시 & 자동화', route: '/calendar', keywords: 'calendar 달력 일정 schedule 스케줄 예약 게시' },
  { id: 'p13', title: '자동화', group: '게시 & 자동화', route: '/automation', keywords: 'automation 규칙 rule 워크플로우' },
  { id: 'p14', title: '통합 분석', group: '분석', route: '/analytics', keywords: 'analytics 통계 지표 성과' },
  { id: 'p15', title: '영상 비교', group: '분석', route: '/analytics/compare', keywords: 'compare 비교 크로스' },
  { id: 'p16', title: '수익', group: '분석', route: '/revenue', keywords: 'revenue 매출 정산 수입' },
  { id: 'p17', title: '경쟁사 분석', group: '분석 · 경쟁사/벤치마크', route: '/competitor', keywords: 'competitor 벤치마크 라이벌' },
  { id: 'p18', title: '채널 오디트', group: '분석 · 경쟁사/벤치마크', route: '/channel-audit', keywords: 'channel audit 진단 점검' },
  { id: 'p19', title: '키워드 리서치', group: '분석 · 성장/트렌드', route: '/keyword-research', keywords: 'keyword research seo 검색어' },
  { id: 'p20', title: '트렌드', group: '분석 · 성장/트렌드', route: '/trends', keywords: 'trends 트렌드 인기 급상승' },
  { id: 'p22', title: '소통 허브 (댓글 · 메시지)', group: '오디언스', route: '/inbox', keywords: 'inbox 메시지 dm comments 댓글 코멘트 답글' },
  { id: 'p23', title: '알림', group: '오디언스', route: '/notifications', keywords: 'notifications 알림함' },
  { id: 'p24', title: '오디언스', group: '오디언스', route: '/audience', keywords: 'audience crm 구독자 팬 세그먼트' },
  { id: 'p25', title: '팀', group: '채널 운영', route: '/team', keywords: 'team 팀원 멤버 초대 권한' },
  { id: 'p26', title: '웹훅', group: '채널 운영', route: '/webhooks', keywords: 'webhooks webhook 연동 콜백 api' },
  { id: 'p27', title: '활동 로그', group: '채널 운영', route: '/activity-log', keywords: 'activity log 히스토리 이력 감사' },
  { id: 'p28', title: '목표', group: '비즈니스', route: '/goals', keywords: 'goals 목표 kpi 달성' },
  { id: 'p29', title: '브랜드 딜', group: '비즈니스', route: '/brand-deals', keywords: 'brand deal 협찬 광고 제휴' },
  { id: 'p30', title: '링크인바이오', group: '비즈니스', route: '/linkbio', keywords: 'linkbio link in bio 링크' },
  { id: 'p31', title: 'UGC 캠페인', group: '비즈니스 · UGC 캠페인', route: '/ugc/campaigns', keywords: 'ugc campaign 캠페인 모집' },
  { id: 'p32', title: '내 캠페인', group: '비즈니스 · UGC 캠페인', route: '/creator/campaigns', keywords: 'creator campaign 크리에이터 참여' },
  { id: 'p33', title: '사용자 매뉴얼', group: '도움말', route: '/manual', keywords: 'manual 도움말 가이드 help' },
]

const quickActions: QuickAction[] = [
  { id: '1', title: '새 영상 업로드', icon: PlusCircleIcon, iconColor: 'text-primary-500', route: '/upload' },
  { id: '2', title: '분석 보기', icon: ChartBarIcon, iconColor: 'text-blue-500', route: '/analytics' },
  { id: '3', title: 'AI 도구', icon: SparklesIcon, iconColor: 'text-purple-500', route: '/ai' },
  { id: '4', title: '설정', icon: Cog6ToothIcon, iconColor: 'text-gray-500', route: '/settings' },
  { id: '5', title: '구독 관리', icon: CreditCardIcon, iconColor: 'text-green-500', route: '/subscription' },
]

const RECENT_SEARCHES_KEY = 'ongo_recent_searches'
const MAX_RECENT_SEARCHES = 5

let debounceTimer: ReturnType<typeof setTimeout> | null = null

export function useSearch() {
  const router = useRouter()
  const query = ref('')
  const results = ref<SearchResult[]>([])
  const isLoading = ref(false)
  const recentSearches = ref<string[]>([])

  // Load recent searches from localStorage
  const loadRecentSearches = () => {
    try {
      const stored = localStorage.getItem(RECENT_SEARCHES_KEY)
      if (stored) {
        recentSearches.value = JSON.parse(stored)
      }
    } catch {
      recentSearches.value = []
    }
  }

  // Save recent searches to localStorage
  const saveRecentSearches = () => {
    try {
      localStorage.setItem(RECENT_SEARCHES_KEY, JSON.stringify(recentSearches.value))
    } catch {
      // silently ignore localStorage errors
    }
  }

  // Add a search to recent searches
  const addRecentSearch = (searchQuery: string) => {
    if (!searchQuery.trim()) return

    // Remove if already exists
    const filtered = recentSearches.value.filter((q) => q !== searchQuery)

    // Add to beginning
    recentSearches.value = [searchQuery, ...filtered].slice(0, MAX_RECENT_SEARCHES)

    saveRecentSearches()
  }

  // Clear all recent searches
  const clearRecentSearches = () => {
    recentSearches.value = []
    localStorage.removeItem(RECENT_SEARCHES_KEY)
  }

  // Perform search (debounced) — fetches from real APIs
  const performSearch = (searchQuery: string) => {
    if (debounceTimer) {
      clearTimeout(debounceTimer)
    }

    if (!searchQuery.trim()) {
      results.value = []
      isLoading.value = false
      return
    }

    isLoading.value = true

    debounceTimer = setTimeout(async () => {
      const lowerQuery = searchQuery.toLowerCase()
      const searchResults: SearchResult[] = []

      try {
        // Fetch videos and channels from the API in parallel
        const [videosPage, channelsList] = await Promise.all([
          videoApi.list({ keyword: searchQuery, page: 0, size: 10 }).catch(() => null),
          channelApi.list().then(res => res.channels).catch(() => [] as Channel[]),
        ])

        // Map video results
        if (videosPage) {
          videosPage.content.forEach((video) => {
            searchResults.push({
              id: String(video.id),
              title: video.title,
              subtitle: `영상 · ${video.createdAt.slice(0, 10)}`,
              category: '영상',
              icon: VideoCameraIcon,
              route: `/videos/${video.id}`,
            })
          })
        }

        // Filter channels client-side (channel API doesn't support keyword search)
        channelsList.forEach((channel) => {
          if (channel.channelName.toLowerCase().includes(lowerQuery)) {
            const platformLabel = PLATFORM_CONFIG[channel.platform]?.label ?? channel.platform
            searchResults.push({
              id: String(channel.id),
              title: channel.channelName,
              subtitle: `채널 · ${platformLabel}`,
              category: '채널',
              icon: RectangleGroupIcon,
              route: '/channels',
            })
          }
        })
      } catch {
        // Silently fall through — still show settings results
      }

      // Search the static page index (sidebar pages)
      pageEntries.forEach((page) => {
        const haystack = `${page.title} ${page.group} ${page.keywords}`.toLowerCase()
        if (haystack.includes(lowerQuery)) {
          searchResults.push({
            id: page.id,
            title: page.title,
            subtitle: `페이지 · ${page.group}`,
            category: '페이지',
            icon: Squares2X2Icon,
            route: page.route,
          })
        }
      })

      // Search static settings entries
      settingsEntries.forEach((setting) => {
        if (setting.title.toLowerCase().includes(lowerQuery) || setting.subtitle.toLowerCase().includes(lowerQuery)) {
          searchResults.push({
            id: setting.id,
            title: setting.title,
            subtitle: setting.subtitle,
            category: '설정',
            icon: Cog6ToothIcon,
            route: setting.route,
          })
        }
      })

      results.value = searchResults
      isLoading.value = false
    }, 300)
  }

  // Watch query changes
  watch(query, (newQuery) => {
    performSearch(newQuery)
  })

  // Navigate to a result
  const navigateToResult = (result: SearchResult) => {
    addRecentSearch(query.value)
    router.push(result.route)
  }

  // Navigate to quick action
  const navigateToQuickAction = (action: QuickAction) => {
    router.push(action.route)
  }

  // Get quick actions
  const getQuickActions = () => quickActions

  // Group results by category
  const groupedResults = computed(() => {
    const groups: Record<string, SearchResult[]> = {}

    results.value.forEach((result) => {
      if (!groups[result.category]) {
        groups[result.category] = []
      }
      groups[result.category].push(result)
    })

    return groups
  })

  // Initialize
  loadRecentSearches()

  return {
    query,
    results,
    groupedResults,
    isLoading,
    recentSearches,
    quickActions: getQuickActions(),
    addRecentSearch,
    clearRecentSearches,
    navigateToResult,
    navigateToQuickAction,
  }
}

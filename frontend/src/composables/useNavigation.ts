import { computed, ref, watch, type Component } from 'vue'
import { useRoute } from 'vue-router'
import {
  HomeIcon,
  ArrowUpTrayIcon,
  CalendarDaysIcon,
  FilmIcon,
  ChartBarIcon,
  SparklesIcon,
  LinkIcon,
  CreditCardIcon,
  Cog6ToothIcon,
  BanknotesIcon,
  LightBulbIcon,
  InboxIcon,
  DocumentDuplicateIcon,
  SwatchIcon,
  BellIcon,
  ClockIcon,
  BookOpenIcon,
  ShieldCheckIcon,
  BeakerIcon,
  UsersIcon,
  ClipboardDocumentCheckIcon,
  MagnifyingGlassIcon,
  MegaphoneIcon,
  HandRaisedIcon,
  BoltIcon,
  PhotoIcon,
  ArrowPathIcon,
  ArrowTrendingUpIcon,
  FlagIcon,
  IdentificationIcon,
  BriefcaseIcon,
  GlobeAltIcon,
  UserGroupIcon,
  SignalIcon,
} from '@heroicons/vue/24/outline'
import { useLocale } from '@/composables/useLocale'
import { useAuthStore } from '@/stores/auth'

export interface NavItem {
  to: string
  label: string
  icon: Component
}

export interface NavSubGroup {
  key: string
  label: string
  items: NavItem[]
}

export interface NavGroup {
  label?: string
  items: NavItem[]
  subGroups?: NavSubGroup[]
}

const SUBGROUP_STORAGE_KEY = 'ongo-nav-subgroups'

// 모듈 로드 시점에 실행되므로 localStorage 접근 자체를 방어한다 (사파리 프라이빗 모드 등)
function loadExpandedSubGroups(): Set<string> {
  try {
    const stored = localStorage.getItem(SUBGROUP_STORAGE_KEY)
    if (!stored) return new Set<string>()
    const parsed: unknown = JSON.parse(stored)
    if (!Array.isArray(parsed)) return new Set<string>()
    return new Set(parsed.filter((key): key is string => typeof key === 'string'))
  } catch {
    return new Set<string>()
  }
}

// 사이드바(데스크톱/모바일 드로어)와 모바일 전체 메뉴 시트가 공유하는 단일 상태
const expandedSubGroups = ref<Set<string>>(loadExpandedSubGroups())

function persistExpandedSubGroups() {
  try {
    localStorage.setItem(SUBGROUP_STORAGE_KEY, JSON.stringify([...expandedSubGroups.value]))
  } catch {
    // 저장 실패는 UI 동작에 영향을 주지 않는다
  }
}

/**
 * 네비게이션 단일 소스.
 * SideNav / MobileMenuSheet 가 동일한 그룹 정의·서브그룹 상태를 공유한다.
 */
export function useNavigation() {
  const route = useRoute()
  const { t } = useLocale()
  const authStore = useAuthStore()
  const isAdmin = computed(() => authStore.user?.role === 'ADMIN')

  const navGroups = computed<NavGroup[]>(() => [
    // ── 1. 대시보드 ──
    {
      items: [
        { to: '/dashboard', label: t('nav.dashboard'), icon: HomeIcon },
      ],
    },
    // ── 2. 콘텐츠 제작 ──
    {
      label: t('nav.groupCreate'),
      items: [
        { to: '/upload', label: t('nav.upload'), icon: ArrowUpTrayIcon },
        { to: '/videos', label: t('nav.videos'), icon: FilmIcon },
        { to: '/ai', label: t('nav.ai'), icon: SparklesIcon },
        { to: '/ideas', label: t('nav.ideas'), icon: LightBulbIcon },
        { to: '/abtest', label: t('nav.abtest'), icon: BeakerIcon },
      ],
      subGroups: [
        {
          key: 'create-library',
          label: t('nav.subContentManage'),
          items: [
            { to: '/templates', label: t('nav.templates'), icon: DocumentDuplicateIcon },
            { to: '/brandkit', label: t('nav.brandkit'), icon: SwatchIcon },
            { to: '/assets', label: t('nav.assets'), icon: PhotoIcon },
            { to: '/recycling', label: t('nav.recycling'), icon: ArrowPathIcon },
          ],
        },
      ],
    },
    // ── 3. 게시 & 스케줄 ──
    {
      label: t('nav.groupPublish'),
      items: [
        { to: '/calendar', label: t('nav.calendar'), icon: CalendarDaysIcon },
        { to: '/automation', label: t('nav.automation'), icon: BoltIcon },
      ],
    },
    // ── 4. 분석 ──
    {
      label: t('nav.groupAnalytics'),
      items: [
        { to: '/analytics', label: t('nav.analytics'), icon: ChartBarIcon },
        { to: '/revenue', label: t('nav.revenue'), icon: BanknotesIcon },
      ],
      subGroups: [
        {
          key: 'analytics-competitor',
          label: t('nav.subCompetitor'),
          items: [
            { to: '/competitor', label: t('nav.competitor'), icon: UsersIcon },
            { to: '/channel-audit', label: t('nav.channelAudit'), icon: ClipboardDocumentCheckIcon },
          ],
        },
        {
          key: 'analytics-growth',
          label: t('nav.subGrowth'),
          items: [
            { to: '/keyword-research', label: t('nav.keywordResearch'), icon: MagnifyingGlassIcon },
            { to: '/trends', label: t('nav.trends'), icon: ArrowTrendingUpIcon },
          ],
        },
      ],
    },
    // ── 5. 소통 ──
    {
      label: t('nav.groupAudience'),
      items: [
        { to: '/inbox', label: t('nav.inbox'), icon: InboxIcon },
        { to: '/notifications', label: t('nav.notifications'), icon: BellIcon },
        { to: '/audience', label: t('nav.audience'), icon: IdentificationIcon },
      ],
    },
    // ── 6. 채널 운영 ──
    {
      label: t('nav.groupOperations'),
      items: [
        { to: '/channels', label: t('nav.channels'), icon: LinkIcon },
        { to: '/team', label: t('nav.team'), icon: UserGroupIcon },
        { to: '/webhooks', label: t('nav.webhooks'), icon: SignalIcon },
        { to: '/activity-log', label: t('nav.activityLog'), icon: ClockIcon },
      ],
    },
    // ── 7. 비즈니스 (UGC 캠페인 포함) ──
    {
      label: t('nav.groupBusiness'),
      items: [
        { to: '/goals', label: t('nav.goals'), icon: FlagIcon },
        { to: '/brand-deals', label: t('nav.brandDeals'), icon: BriefcaseIcon },
        { to: '/linkbio', label: t('nav.linkbio'), icon: GlobeAltIcon },
      ],
      subGroups: [
        {
          key: 'business-ugc',
          label: t('nav.groupUgc'),
          items: [
            { to: '/ugc/campaigns', label: t('nav.ugcCampaigns'), icon: MegaphoneIcon },
            { to: '/creator/campaigns', label: t('nav.creatorCampaigns'), icon: HandRaisedIcon },
          ],
        },
      ],
    },
  ])

  const bottomNavItems = computed<NavItem[]>(() => {
    const items: NavItem[] = [
      { to: '/manual', label: t('nav.manual'), icon: BookOpenIcon },
      { to: '/subscription', label: t('nav.subscription'), icon: CreditCardIcon },
      { to: '/settings', label: t('nav.settings'), icon: Cog6ToothIcon },
    ]
    if (isAdmin.value) {
      items.push({ to: '/admin', label: t('nav.admin'), icon: ShieldCheckIcon })
    }
    return items
  })

  /** 그룹/서브그룹/하단 메뉴를 모두 펼친 평면 목록 (즐겨찾기 조회용) */
  const allNavItems = computed<NavItem[]>(() => {
    const items: NavItem[] = []
    for (const group of navGroups.value) {
      items.push(...group.items)
      for (const sub of group.subGroups ?? []) {
        items.push(...sub.items)
      }
    }
    items.push(...bottomNavItems.value)
    return items
  })

  function isCurrentRoute(to: string): boolean {
    return route.path === to || route.path.startsWith(to + '/')
  }

  function isSubGroupExpanded(key: string): boolean {
    return expandedSubGroups.value.has(key)
  }

  function toggleSubGroup(key: string) {
    const next = new Set(expandedSubGroups.value)
    if (next.has(key)) {
      next.delete(key)
    } else {
      next.add(key)
    }
    expandedSubGroups.value = next
    persistExpandedSubGroups()
  }

  // 현재 라우트가 속한 서브그룹은 저장된 상태보다 우선해서 자동 펼침
  watch(
    () => route.path,
    (path) => {
      const next = new Set(expandedSubGroups.value)
      let changed = false
      for (const group of navGroups.value) {
        for (const sub of group.subGroups ?? []) {
          const matched = sub.items.some((item) => path === item.to || path.startsWith(item.to + '/'))
          if (matched && !next.has(sub.key)) {
            next.add(sub.key)
            changed = true
          }
        }
      }
      if (changed) {
        expandedSubGroups.value = next
        persistExpandedSubGroups()
      }
    },
    { immediate: true },
  )

  return {
    navGroups,
    bottomNavItems,
    allNavItems,
    isCurrentRoute,
    isSubGroupExpanded,
    toggleSubGroup,
  }
}

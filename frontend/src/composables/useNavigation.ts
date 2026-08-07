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
  BookOpenIcon,
  ShieldCheckIcon,
  ClipboardDocumentCheckIcon,
  MagnifyingGlassIcon,
  MegaphoneIcon,
  HandRaisedIcon,
  BoltIcon,
  PhotoIcon,
  ArrowTrendingUpIcon,
  IdentificationIcon,
  BriefcaseIcon,
  GlobeAltIcon,
  UserGroupIcon,
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
        { to: '/today', label: t('redesign.nav.today'), icon: HomeIcon },
      ],
    },
    // ── 2. 기획: 유입부터 제작 결정을 돕는 기능 ──
    {
      label: t('nav.groupPlan'),
      items: [
        { to: '/ideas', label: t('nav.ideas'), icon: LightBulbIcon },
        { to: '/keyword-research', label: t('nav.keywordResearch'), icon: MagnifyingGlassIcon },
        { to: '/trends', label: t('nav.trends'), icon: ArrowTrendingUpIcon },
      ],
    },
    // ── 3. 콘텐츠 제작: 가장 빈번하고 유료 전환에 직접 연결되는 흐름 ──
    {
      label: t('nav.groupCreate'),
      items: [
        { to: '/compose', label: t('redesign.nav.compose'), icon: ArrowUpTrayIcon },
        { to: '/videos', label: t('nav.videos'), icon: FilmIcon },
        { to: '/ai', label: t('nav.ai'), icon: SparklesIcon },
      ],
      subGroups: [
        {
          key: 'create-library',
          label: t('nav.subContentManage'),
          items: [
            { to: '/templates', label: t('nav.templates'), icon: DocumentDuplicateIcon },
            { to: '/brandkit', label: t('nav.brandkit'), icon: SwatchIcon },
            { to: '/assets', label: t('nav.assets'), icon: PhotoIcon },
          ],
        },
      ],
    },
    // ── 4. 게시 & 스케줄: 멀티채널 운영의 핵심 가치 ──
    {
      label: t('nav.groupPublish'),
      items: [
        { to: '/calendar-v2', label: t('redesign.nav.calendar'), icon: CalendarDaysIcon },
        { to: '/automation', label: t('nav.automation'), icon: BoltIcon },
        { to: '/channels-v2', label: t('redesign.nav.channels'), icon: LinkIcon },
      ],
    },
    // ── 5. 성과 & 개선: 결과를 확인하고 다음 콘텐츠에 반영 ──
    {
      label: t('nav.groupAnalytics'),
      items: [
        { to: '/performance', label: t('redesign.nav.performance'), icon: ChartBarIcon },
        { to: '/revenue', label: t('nav.revenue'), icon: BanknotesIcon },
      ],
    },
    // ── 6. 소통: 게시 후 관계를 유지하는 핵심 기능 ──
    {
      label: t('nav.groupAudience'),
      items: [
        { to: '/inbox-v2', label: t('redesign.nav.inbox'), icon: InboxIcon },
        { to: '/audience', label: t('nav.audience'), icon: IdentificationIcon },
      ],
    },
    // ── 7. 추가 기능 · 정리 검토: 사용 빈도와 유료 전환을 확인한 뒤 유지 여부 결정 ──
    {
      label: t('nav.groupReview'),
      items: [],
      subGroups: [
        {
          key: 'review-advanced',
          label: t('nav.subReviewAdvanced'),
          items: [
            { to: '/channel-audit', label: t('nav.channelAudit'), icon: ClipboardDocumentCheckIcon },
            { to: '/brand-deals', label: t('nav.brandDeals'), icon: BriefcaseIcon },
            { to: '/linkbio', label: t('nav.linkbio'), icon: GlobeAltIcon },
            { to: '/ugc/campaigns', label: t('nav.ugcCampaigns'), icon: MegaphoneIcon },
            { to: '/creator/campaigns', label: t('nav.creatorCampaigns'), icon: HandRaisedIcon },
            { to: '/team', label: t('nav.team'), icon: UserGroupIcon },
          ],
        },
      ],
    },
    // ── 8. 협업 ──
    {
      label: t('nav.groupWorkspace'),
      items: [
        { to: '/team', label: t('nav.team'), icon: UserGroupIcon },
      ],
    },
  ])

  const bottomNavItems = computed<NavItem[]>(() => {
    const items: NavItem[] = [
      { to: '/manual', label: t('nav.manual'), icon: BookOpenIcon },
      { to: '/subscription', label: t('nav.subscription'), icon: CreditCardIcon },
      { to: '/settings-v2', label: t('redesign.nav.settings'), icon: Cog6ToothIcon },
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

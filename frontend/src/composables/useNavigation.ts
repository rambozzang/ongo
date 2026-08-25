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
  InboxIcon,
  BellIcon,
  DocumentDuplicateIcon,
  SwatchIcon,
  BookOpenIcon,
  ShieldCheckIcon,
  ClipboardDocumentCheckIcon,
  MegaphoneIcon,
  HandRaisedIcon,
  BoltIcon,
  PhotoIcon,
  IdentificationIcon,
  BriefcaseIcon,
  GlobeAltIcon,
  UserGroupIcon,
  BeakerIcon,
} from '@heroicons/vue/24/outline'
import { useLocale } from '@/composables/useLocale'
import { useAuthStore } from '@/stores/auth'
import { capabilitiesApi } from '@/api/capabilities'

export interface NavItem {
  to: string
  label: string
  icon: Component
  capabilityKey?: string
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
// Capability sync is authoritative. Start empty so a slow/failed response
// never flashes the static menu before the server has confirmed it.
const enabledCapabilityKeys = ref<Set<string>>(new Set())
const capabilityError = ref<string | null>(null)
const capabilityLoading = ref(false)
let capabilityRequest: Promise<void> | null = null

async function loadCapabilities() {
  if (capabilityRequest) return capabilityRequest
  capabilityLoading.value = true
  capabilityRequest = capabilitiesApi.list()
    .then((items) => {
      enabledCapabilityKeys.value = new Set(items.filter((item) => item.enabled).map((item) => item.key))
      capabilityError.value = null
    })
    .catch((error: unknown) => {
      // 서버가 활성 기능 목록을 주지 못한 상태에서 정적 메뉴를 계속 노출하면,
      // 비활성/WIP 기능을 장애 중에 잘못 열 수 있다. 현재 라우트는 유지하되
      // 메뉴는 fail-closed 하고, 셸의 재시도 버튼으로만 다시 동기화한다.
      enabledCapabilityKeys.value = new Set<string>()
      capabilityError.value = error instanceof Error ? error.message : 'capability request failed'
    })
    .finally(() => {
      capabilityLoading.value = false
      capabilityRequest = null
    })
  return capabilityRequest
}

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

  watch(
    () => authStore.isAuthenticated,
    (authenticated) => {
      if (authenticated) {
        enabledCapabilityKeys.value = new Set<string>()
        void loadCapabilities()
      }
      else {
        enabledCapabilityKeys.value = new Set<string>()
        capabilityError.value = null
      }
    },
    { immediate: true },
  )

  const withCapability = (item: NavItem): NavItem => ({
    ...item,
    capabilityKey: item.capabilityKey ?? item.to.replace(/^\//, ''),
  })
  const visibleItems = (items: NavItem[]) => items.map(withCapability).filter((item) => {
    return enabledCapabilityKeys.value.has(item.capabilityKey!)
  })

  const navGroups = computed<NavGroup[]>(() => {
    const groups: NavGroup[] = [
    // ── 1. 대시보드 ──
    {
      items: [
        { to: '/today', label: t('redesign.nav.today'), icon: HomeIcon },
      ],
    },
    // ── 2. 콘텐츠 제작: 가장 빈번하고 유료 전환에 직접 연결되는 흐름 ──
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
            { to: '/subtitle-editor', label: t('nav.subtitleEditor'), icon: DocumentDuplicateIcon },
            { to: '/recycling', label: t('nav.recycling'), icon: ArrowUpTrayIcon },
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
        { to: '/analytics/compare', label: t('nav.videoCompare'), icon: ChartBarIcon },
        { to: '/revenue', label: t('nav.revenue'), icon: BanknotesIcon },
        { to: '/ab-tests', label: t('nav.abtest'), icon: BeakerIcon },
        { to: '/goals', label: t('nav.goals'), icon: IdentificationIcon },
      ],
    },
    // ── 6. 소통: 게시 후 관계를 유지하는 핵심 기능 ──
    {
      label: t('nav.groupAudience'),
      items: [
        { to: '/inbox-v2', label: t('redesign.nav.inbox'), icon: InboxIcon },
        { to: '/audience', label: t('nav.audience'), icon: IdentificationIcon },
        { to: '/notifications', label: t('nav.notifications'), icon: BellIcon },
      ],
    },
    // ── 7. UGC: 브랜드 캠페인과 크리에이터 제출을 하나의 흐름으로 묶는다 ──
    {
      label: t('nav.groupUgc'),
      items: [
        { to: '/ugc/campaigns', label: t('nav.ugcCampaigns'), icon: MegaphoneIcon },
        { to: '/creator/campaigns', label: t('nav.creatorCampaigns'), icon: HandRaisedIcon },
      ],
      subGroups: [
        {
          key: 'ugc-shorts',
          label: t('nav.subUgcShorts'),
          items: [
            { to: '/ugc/shorts/prompts', label: t('nav.ugcShortsPrompts'), icon: DocumentDuplicateIcon },
            { to: '/ugc/shorts/templates', label: t('nav.ugcShortsTemplates'), icon: PhotoIcon },
            { to: '/ugc/shorts/runs', label: t('nav.ugcShortsRuns'), icon: BoltIcon },
          ],
        },
      ],
    },
    // ── 8. 비즈니스 ──
    {
      label: t('nav.groupBusiness'),
      items: [
        { to: '/channel-audit', label: t('nav.channelAudit'), icon: ClipboardDocumentCheckIcon },
        { to: '/brand-deals', label: t('nav.brandDeals'), icon: BriefcaseIcon },
        { to: '/linkbio', label: t('nav.linkbio'), icon: GlobeAltIcon },
      ],
    },
    // ── 9. 협업·운영 ──
    {
      label: t('nav.groupOperations'),
      items: [
        { to: '/team', label: t('nav.team'), icon: UserGroupIcon },
        { to: '/webhooks', label: t('nav.webhooks'), icon: BoltIcon },
        { to: '/activity-log', label: t('nav.activityLog'), icon: ClipboardDocumentCheckIcon },
      ],
    },
    ]
    return groups.map((group) => ({
      ...group,
      items: visibleItems(group.items),
      subGroups: group.subGroups?.map((subGroup) => ({
        ...subGroup,
        items: visibleItems(subGroup.items),
      })).filter((subGroup) => subGroup.items.length > 0),
    })).filter((group) => group.items.length > 0 || (group.subGroups?.length ?? 0) > 0)
  })

  const bottomNavItems = computed<NavItem[]>(() => {
    const items: NavItem[] = [
      { to: '/manual', label: t('nav.manual'), icon: BookOpenIcon },
      { to: '/subscription', label: t('nav.subscription'), icon: CreditCardIcon },
      { to: '/settings-v2', label: t('redesign.nav.settings'), icon: Cog6ToothIcon },
    ]
    if (isAdmin.value) {
      items.push({ to: '/admin', label: t('nav.admin'), icon: ShieldCheckIcon })
      /*
       * capabilityKey 를 명시한다. 생략하면 경로에서 'admin/shorts-pilot' 이 파생되는데
       * 서버 능력 목록에 그런 키가 없어 메뉴가 조용히 사라진다. 이 화면은 admin 능력에
       * 딸린 하위 화면이므로 같은 키를 쓴다.
       */
      items.push({
        to: '/admin/shorts-pilot',
        label: t('admin.shortsPilot.title'),
        icon: ChartBarIcon,
        capabilityKey: 'admin',
      })
    }
    return visibleItems(items)
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
    capabilityError,
    capabilityLoading,
    retryCapabilities: loadCapabilities,
  }
}

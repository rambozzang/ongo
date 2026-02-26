<template>
  <aside
    class="glass-sidebar flex flex-col transition-all duration-200"
    :class="collapsed ? 'w-[60px]' : 'w-[240px]'"
  >
    <!-- Logo -->
    <div class="flex h-16 items-center border-b border-gray-200 px-4 dark:border-gray-700">
      <router-link to="/dashboard" class="flex items-center gap-2" @click="emit('navigate')">
        <span class="text-2xl font-bold text-primary-600">on</span>
        <span v-if="!collapsed" class="text-2xl font-bold text-gray-900 dark:text-gray-100">Go</span>
      </router-link>
      <button
        v-if="!collapsed"
        :aria-label="collapsed ? t('nav.sidebarExpand') : t('nav.sidebarCollapse')"
        :aria-expanded="!collapsed"
        class="ml-auto hidden rounded p-1 text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 desktop:block"
        @click="emit('toggle')"
      >
        <ChevronLeftIcon class="h-5 w-5" />
      </button>
      <button
        v-else
        :aria-label="collapsed ? t('nav.sidebarExpand') : t('nav.sidebarCollapse')"
        :aria-expanded="!collapsed"
        class="ml-auto hidden rounded p-1 text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 desktop:block"
        @click="emit('toggle')"
      >
        <ChevronRightIcon class="h-5 w-5" />
      </button>
    </div>

    <!-- Workspace Switcher -->
    <WorkspaceSwitcher :collapsed="collapsed" />

    <!-- Navigation -->
    <nav role="navigation" :aria-label="t('nav.mainNavigation')" class="flex-1 overflow-y-auto px-2 py-4">
      <div
        v-for="(group, groupIndex) in navGroups"
        :key="groupIndex"
        :class="groupIndex > 0 ? 'mt-4' : ''"
      >
        <!-- Group header (expanded) or divider (collapsed) -->
        <template v-if="group.label">
          <div
            v-if="collapsed"
            class="mx-2 my-2 border-t border-gray-200 dark:border-gray-700"
            aria-hidden="true"
          />
          <div
            v-else
            class="mb-1 px-3 py-1 text-[11px] font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500"
          >
            {{ group.label }}
          </div>
        </template>

        <router-link
          v-for="item in group.items"
          :key="item.to"
          :to="item.to"
          role="menuitem"
          :aria-label="item.label"
          :aria-current="isCurrentRoute(item.to) ? 'page' : undefined"
          class="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-gray-600 transition-colors hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-100"
          active-class="!bg-primary-50 !text-primary-700 dark:!bg-primary-900/30 dark:!text-primary-400"
          @click="emit('navigate')"
        >
          <component :is="item.icon" class="h-5 w-5 shrink-0" :aria-hidden="true" />
          <span v-if="!collapsed">{{ item.label }}</span>
        </router-link>
      </div>

      <div class="my-3 border-t border-gray-200 dark:border-gray-700" role="separator" aria-hidden="true" />

      <router-link
        v-for="item in bottomNavItems"
        :key="item.to"
        :to="item.to"
        role="menuitem"
        :aria-label="item.label"
        :aria-current="isCurrentRoute(item.to) ? 'page' : undefined"
        class="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-gray-600 transition-colors hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-100"
        active-class="!bg-primary-50 !text-primary-700 dark:!bg-primary-900/30 dark:!text-primary-400"
        @click="emit('navigate')"
      >
        <component :is="item.icon" class="h-5 w-5 shrink-0" :aria-hidden="true" />
        <span v-if="!collapsed">{{ item.label }}</span>
      </router-link>
    </nav>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  HomeIcon,
  ArrowUpTrayIcon,
  CalendarDaysIcon,
  FilmIcon,
  ChartBarIcon,
  ChatBubbleLeftEllipsisIcon,
  SparklesIcon,
  LinkIcon,
  CreditCardIcon,
  Cog6ToothIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  BanknotesIcon,
  LightBulbIcon,
  UserGroupIcon,
  BoltIcon,
  InboxIcon,
  GlobeAltIcon,
  DocumentDuplicateIcon,
  BeakerIcon,
  PresentationChartLineIcon,
  SwatchIcon,
  FlagIcon,
  BellIcon,
  ArrowPathIcon,
  FolderIcon,
  CodeBracketIcon,
  ClockIcon,
  BookOpenIcon,
  ShieldCheckIcon,
  FireIcon,
  UsersIcon,
  BriefcaseIcon,
  ScissorsIcon,
  SignalIcon,
  IdentificationIcon,
  ShoppingCartIcon,
  BuildingOffice2Icon,
  CalendarIcon,
  CubeTransparentIcon,
  FilmIcon as VideoResizerIcon,
  LanguageIcon,
  MagnifyingGlassCircleIcon,
  HashtagIcon,
  SpeakerWaveIcon,
  ArrowsPointingOutIcon,
  AcademicCapIcon,
  DocumentMagnifyingGlassIcon,
  PhotoIcon,
  TableCellsIcon,
  BoltIcon as LiveBoltIcon,
  CurrencyDollarIcon,
  DocumentTextIcon,
  MegaphoneIcon,
  UserPlusIcon,
  ClipboardDocumentCheckIcon,
  DocumentArrowUpIcon,
  HeartIcon,
  RectangleStackIcon,
  ChatBubbleBottomCenterTextIcon,
  ShieldExclamationIcon,
  PencilSquareIcon,
  ViewColumnsIcon,
  GiftIcon,
  QueueListIcon,
  FaceSmileIcon,
  DocumentChartBarIcon,
  TrophyIcon,
  CpuChipIcon,
  CalendarIcon as AiCalendarIcon2,
  ScaleIcon,
  CircleStackIcon,
  EyeIcon,
  ArrowPathRoundedSquareIcon,
  Square3Stack3DIcon,
  MagnifyingGlassIcon,
  PaintBrushIcon,
  PhotoIcon as ThumbnailAbIcon,
  CurrencyDollarIcon as RevenueGoalIcon,
  ChatBubbleLeftRightIcon,
  HeartIcon as HealthIcon,
  MicrophoneIcon,
  FolderOpenIcon,
  HandRaisedIcon,
  UserCircleIcon,
  ArrowTrendingUpIcon,
  DocumentChartBarIcon as ReportIcon2,
  VideoCameraIcon,
  ShoppingBagIcon,
  MusicalNoteIcon,
  AdjustmentsHorizontalIcon,
  GiftIcon as RewardIcon2,
  CogIcon,
  ClockIcon as OptimizerIcon,
  RectangleGroupIcon,
  MegaphoneIcon as CampaignIcon,
  FunnelIcon,
} from '@heroicons/vue/24/outline'
import { useLocale } from '@/composables/useLocale'
import { useAuthStore } from '@/stores/auth'
import WorkspaceSwitcher from '@/components/layout/WorkspaceSwitcher.vue'

defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  toggle: []
  navigate: []
}>()

const route = useRoute()
const { t } = useLocale()
const authStore = useAuthStore()
const isAdmin = computed(() => authStore.user?.role === 'ADMIN')

interface NavItem {
  to: string
  label: string
  icon: typeof HomeIcon
}

interface NavGroup {
  label?: string
  items: NavItem[]
}

const navGroups = computed<NavGroup[]>(() => [
  {
    items: [
      { to: '/dashboard', label: t('nav.dashboard'), icon: HomeIcon },
    ],
  },
  {
    label: t('nav.groupContent'),
    items: [
      { to: '/upload', label: t('nav.upload'), icon: ArrowUpTrayIcon },
      { to: '/videos', label: t('nav.videos'), icon: FilmIcon },
      { to: '/content-studio', label: t('nav.contentStudio'), icon: ScissorsIcon },
      { to: '/video-resizer', label: t('nav.videoResizer'), icon: VideoResizerIcon },
      { to: '/subtitle-editor', label: t('nav.subtitleEditor'), icon: LanguageIcon },
      { to: '/subtitle-translation', label: t('nav.subtitleTranslation'), icon: LanguageIcon },
      { to: '/templates', label: t('nav.templates'), icon: DocumentDuplicateIcon },
      { to: '/assets', label: t('nav.assets'), icon: FolderIcon },
      { to: '/brandkit', label: t('nav.brandkit'), icon: SwatchIcon },
      { to: '/thumbnail-generator', label: t('nav.thumbnailGenerator'), icon: PhotoIcon },
      { to: '/content-rewriter', label: t('nav.contentRewriter'), icon: DocumentTextIcon },
      { to: '/content-series', label: t('nav.contentSeries'), icon: RectangleStackIcon },
      { to: '/recycling', label: t('nav.recycling'), icon: ArrowPathIcon },
      { to: '/content-versioning', label: t('nav.contentVersioning'), icon: DocumentChartBarIcon },
      { to: '/content-cluster', label: t('nav.contentCluster'), icon: CircleStackIcon },
      { to: '/content-repurposer', label: t('nav.contentRepurposer'), icon: ArrowPathRoundedSquareIcon },
      { to: '/subtitle-generator', label: t('nav.subtitleGenerator'), icon: MicrophoneIcon },
      { to: '/content-library', label: t('nav.contentLibrary'), icon: FolderOpenIcon },
    ],
  },
  {
    label: t('nav.groupPublish'),
    items: [
      { to: '/schedule', label: t('nav.schedule'), icon: CalendarDaysIcon },
      { to: '/calendar', label: t('nav.calendar'), icon: CalendarDaysIcon },
      { to: '/ai-calendar', label: t('nav.aiCalendar'), icon: CalendarIcon },
      { to: '/automation', label: t('nav.automation'), icon: BoltIcon },
      { to: '/content-calendar-ai', label: t('nav.contentCalendarAi'), icon: AiCalendarIcon2 },
      { to: '/workflow-builder', label: t('nav.workflowBuilder'), icon: CubeTransparentIcon },
    ],
  },
  {
    label: t('nav.groupAnalytics'),
    items: [
      { to: '/analytics', label: t('nav.analytics'), icon: ChartBarIcon },
      { to: '/prediction', label: t('nav.prediction'), icon: SignalIcon },
      { to: '/revenue', label: t('nav.revenue'), icon: BanknotesIcon },
      { to: '/commerce', label: t('nav.commerce'), icon: ShoppingCartIcon },
      { to: '/abtest', label: t('nav.abtest'), icon: BeakerIcon },
      { to: '/competitor', label: t('nav.competitor'), icon: PresentationChartLineIcon },
      { to: '/competitor-analysis', label: t('nav.competitorAnalysis'), icon: MagnifyingGlassCircleIcon },
      { to: '/cross-analytics', label: t('nav.crossAnalytics'), icon: ArrowsPointingOutIcon },
      { to: '/trends', label: t('nav.trends'), icon: FireIcon },
      { to: '/goals', label: t('nav.goals'), icon: FlagIcon },
      { to: '/growth-coach', label: t('nav.growthCoach'), icon: AcademicCapIcon },
      { to: '/performance-heatmap', label: t('nav.performanceHeatmap'), icon: TableCellsIcon },
      { to: '/live-dashboard', label: t('nav.liveDashboard'), icon: LiveBoltIcon },
      { to: '/revenue-forecaster', label: t('nav.revenueForecaster'), icon: CurrencyDollarIcon },
      { to: '/fan-funding', label: t('nav.fanFunding'), icon: HeartIcon },
      { to: '/ab-test-results', label: t('nav.abTestResults'), icon: BeakerIcon },
      { to: '/revenue-split', label: t('nav.revenueSplit'), icon: BanknotesIcon },
      { to: '/calendar-insights', label: t('nav.calendarInsights'), icon: CalendarDaysIcon },
      { to: '/creator-milestone', label: t('nav.creatorMilestone'), icon: TrophyIcon },
      { to: '/algorithm-insights', label: t('nav.algorithmInsights'), icon: CpuChipIcon },
      { to: '/creator-benchmark', label: t('nav.creatorBenchmark'), icon: ScaleIcon },
      { to: '/audience-overlap', label: t('nav.audienceOverlap'), icon: Square3Stack3DIcon },
      { to: '/thumbnail-ab-test', label: t('nav.thumbnailAbTest'), icon: ThumbnailAbIcon },
      { to: '/revenue-goal', label: t('nav.revenueGoal'), icon: RevenueGoalIcon },
      { to: '/platform-health', label: t('nav.platformHealth'), icon: HealthIcon },
      { to: '/trend-predictor', label: t('nav.trendPredictor'), icon: ArrowTrendingUpIcon },
      { to: '/performance-report', label: t('nav.performanceReport'), icon: ReportIcon2 },
      { to: '/content-ab-analyzer', label: t('nav.contentAbAnalyzer'), icon: AdjustmentsHorizontalIcon },
      { to: '/channel-health', label: t('nav.channelHealth'), icon: HealthIcon },
      { to: '/revenue-analyzer', label: t('nav.revenueAnalyzer'), icon: BanknotesIcon },
    ],
  },
  {
    label: t('nav.groupCommunication'),
    items: [
      { to: '/comments', label: t('nav.comments'), icon: ChatBubbleLeftEllipsisIcon },
      { to: '/inbox', label: t('nav.inbox'), icon: InboxIcon },
      { to: '/notifications', label: t('nav.notifications'), icon: BellIcon },
      { to: '/audience', label: t('nav.audience'), icon: UsersIcon },
      { to: '/social-listening', label: t('nav.socialListening'), icon: MegaphoneIcon },
      { to: '/influencer-match', label: t('nav.influencerMatch'), icon: UserPlusIcon },
      { to: '/smart-reply', label: t('nav.smartReply'), icon: ChatBubbleBottomCenterTextIcon },
      { to: '/fan-community', label: t('nav.fanCommunity'), icon: UserGroupIcon },
      { to: '/audience-segments', label: t('nav.audienceSegments'), icon: UsersIcon },
      { to: '/fan-insights', label: t('nav.fanInsights'), icon: EyeIcon },
      { to: '/comment-summary', label: t('nav.commentSummary'), icon: ChatBubbleLeftRightIcon },
      { to: '/fan-poll', label: t('nav.fanPoll'), icon: HandRaisedIcon },
      { to: '/creator-network', label: t('nav.creatorNetwork'), icon: UserCircleIcon },
      { to: '/live-stream', label: t('nav.liveStream'), icon: VideoCameraIcon },
      { to: '/creator-marketplace', label: t('nav.creatorMarketplace'), icon: ShoppingBagIcon },
      { to: '/fan-reward', label: t('nav.fanReward'), icon: RewardIcon2 },
    ],
  },
  {
    label: t('nav.groupTools'),
    items: [
      { to: '/ai', label: t('nav.ai'), icon: SparklesIcon },
      { to: '/ideas', label: t('nav.ideas'), icon: LightBulbIcon },
      { to: '/portfolio', label: t('nav.portfolio'), icon: IdentificationIcon },
      { to: '/linkbio', label: t('nav.linkbio'), icon: GlobeAltIcon },
      { to: '/brand-deals', label: t('nav.brandDeals'), icon: BriefcaseIcon },
      { to: '/hashtag-strategy', label: t('nav.hashtagStrategy'), icon: HashtagIcon },
      { to: '/hashtag-analytics', label: t('nav.hashtagAnalytics'), icon: HashtagIcon },
      { to: '/brand-voice', label: t('nav.brandVoice'), icon: SpeakerWaveIcon },
      { to: '/brand-safety', label: t('nav.brandSafety'), icon: ShieldExclamationIcon },
      { to: '/copyright-check', label: t('nav.copyrightCheck'), icon: DocumentMagnifyingGlassIcon },
      { to: '/quality-score', label: t('nav.qualityScore'), icon: ClipboardDocumentCheckIcon },
      { to: '/media-kit', label: t('nav.mediaKit'), icon: DocumentArrowUpIcon },
      { to: '/content-rights', label: t('nav.contentRights'), icon: ShieldExclamationIcon },
      { to: '/creator-academy', label: t('nav.creatorAcademy'), icon: BookOpenIcon },
      { to: '/script-writer', label: t('nav.scriptWriter'), icon: PencilSquareIcon },
      { to: '/sponsorship-tracker', label: t('nav.sponsorshipTracker'), icon: GiftIcon },
      { to: '/playlist-manager', label: t('nav.playlistManager'), icon: QueueListIcon },
      { to: '/sentiment-analyzer', label: t('nav.sentimentAnalyzer'), icon: FaceSmileIcon },
      { to: '/video-seo', label: t('nav.videoSeo'), icon: MagnifyingGlassIcon },
      { to: '/mood-board', label: t('nav.moodBoard'), icon: PaintBrushIcon },
      { to: '/music-recommender', label: t('nav.musicRecommender'), icon: MusicalNoteIcon },
      { to: '/platform-automation-pro', label: t('nav.platformAutomationPro'), icon: CogIcon },
      { to: '/video-script-assistant', label: t('nav.videoScriptAssistant'), icon: PencilSquareIcon },
      { to: '/content-translator', label: t('nav.contentTranslator'), icon: LanguageIcon },
      { to: '/schedule-optimizer', label: t('nav.scheduleOptimizer'), icon: OptimizerIcon },
      { to: '/portfolio-builder', label: t('nav.portfolioBuilder'), icon: RectangleGroupIcon },
      { to: '/content-funnel', label: t('nav.contentFunnel'), icon: FunnelIcon },
    ],
  },
  {
    label: t('nav.groupManagement'),
    items: [
      { to: '/channels', label: t('nav.channels'), icon: LinkIcon },
      { to: '/team', label: t('nav.team'), icon: UserGroupIcon },
      { to: '/agency', label: t('nav.agency'), icon: BuildingOffice2Icon },
      { to: '/multi-brand-calendar', label: t('nav.multiBrandCalendar'), icon: CalendarDaysIcon },
      { to: '/collaboration-board', label: t('nav.collaborationBoard'), icon: ViewColumnsIcon },
      { to: '/webhooks', label: t('nav.webhooks'), icon: CodeBracketIcon },
      { to: '/activity-log', label: t('nav.activityLog'), icon: ClockIcon },
      { to: '/team-performance', label: t('nav.teamPerformance'), icon: ChartBarIcon },
      { to: '/fan-segment-campaign', label: t('nav.fanSegmentCampaign'), icon: CampaignIcon },
    ],
  },
])

const bottomNavItems = computed(() => {
  const items = [
    { to: '/manual', label: t('nav.manual'), icon: BookOpenIcon },
    { to: '/subscription', label: t('nav.subscription'), icon: CreditCardIcon },
    { to: '/settings', label: t('nav.settings'), icon: Cog6ToothIcon },
  ]
  if (isAdmin.value) {
    items.push({ to: '/admin', label: t('nav.admin'), icon: ShieldCheckIcon })
  }
  return items
})

function isCurrentRoute(to: string): boolean {
  return route.path === to || route.path.startsWith(to + '/')
}
</script>

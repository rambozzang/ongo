<template>
  <div class="flex flex-col desktop:flex-row gap-6">
    <!-- Mobile TOC Toggle -->
    <button
      class="flex items-center gap-2 rounded-lg border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 dark:border-gray-700 dark:text-gray-300 desktop:hidden"
      @click="showToc = !showToc"
    >
      <ListBulletIcon class="h-5 w-5" />
      {{ t('manual.toc') }}
      <ChevronDownIcon
        class="ml-auto h-4 w-4 transition-transform"
        :class="showToc ? 'rotate-180' : ''"
      />
    </button>

    <!-- Table of Contents Sidebar -->
    <nav
      v-show="showToc || !isMobile"
      class="w-full shrink-0 desktop:w-56"
    >
      <div class="sticky top-6 rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
        <h2 class="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ t('manual.toc') }}
        </h2>
        <ul class="space-y-1">
          <li v-for="section in sections" :key="section.id">
            <a
              :href="'#' + section.id"
              class="block rounded px-2 py-1.5 text-sm transition-colors"
              :class="
                activeSection === section.id
                  ? 'bg-primary-50 font-medium text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
                  : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-100'
              "
              @click="showToc = false"
            >
              {{ section.title }}
            </a>
          </li>
        </ul>
      </div>
    </nav>

    <!-- Manual Content -->
    <div class="min-w-0 flex-1">
      <h1 class="mb-6 text-2xl font-bold text-gray-900 dark:text-gray-100">
        {{ t('manual.title') }}
      </h1>

      <div class="space-y-10">
        <section
          v-for="section in sections"
          :id="section.id"
          :key="section.id"
          class="scroll-mt-6"
        >
          <div class="card">
            <h2 class="mb-4 flex items-center gap-2 text-lg font-semibold text-gray-900 dark:text-gray-100">
              <component :is="section.icon" class="h-5 w-5 text-primary-600" />
              {{ section.title }}
            </h2>
            <div class="prose prose-sm max-w-none text-gray-700 dark:text-gray-300">
              <div v-for="(block, i) in section.content" :key="i" class="mb-4 last:mb-0">
                <h3
                  v-if="block.subtitle"
                  class="mb-2 text-sm font-semibold text-gray-800 dark:text-gray-200"
                >
                  {{ block.subtitle }}
                </h3>
                <p class="text-sm leading-relaxed whitespace-pre-line">{{ block.text }}</p>
                <ol
                  v-if="block.steps"
                  class="mt-2 list-decimal space-y-1 pl-5 text-sm"
                >
                  <li v-for="(step, j) in block.steps" :key="j">{{ step }}</li>
                </ol>
                <ul
                  v-if="block.items"
                  class="mt-2 list-disc space-y-1 pl-5 text-sm"
                >
                  <li v-for="(item, j) in block.items" :key="j">{{ item }}</li>
                </ul>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, type Component } from 'vue'
import {
  RocketLaunchIcon,
  LinkIcon,
  ArrowUpTrayIcon,
  CalendarDaysIcon,
  SparklesIcon,
  ChartBarIcon,
  UserGroupIcon,
  CreditCardIcon,
  Cog6ToothIcon,
  QuestionMarkCircleIcon,
  ListBulletIcon,
  ChevronDownIcon,
} from '@heroicons/vue/24/outline'
import { useLocale } from '@/composables/useLocale'

const { isKorean, t } = useLocale()

const showToc = ref(false)
const isMobile = ref(false)
const activeSection = ref('getting-started')

interface ContentBlock {
  subtitle?: string
  text: string
  steps?: string[]
  items?: string[]
}

interface ManualSection {
  id: string
  title: string
  icon: Component
  content: ContentBlock[]
}

const sections = computed<ManualSection[]>(() =>
  isKorean.value ? sectionsKo : sectionsEn,
)

const sectionsKo: ManualSection[] = [
  {
    id: 'getting-started',
    title: '시작하기',
    icon: RocketLaunchIcon,
    content: [
      {
        subtitle: '회원가입 및 로그인',
        text: 'onGo는 소셜 로그인만 지원합니다. Google 또는 Kakao 계정으로 간편하게 시작하세요.',
        steps: [
          '로그인 페이지에서 Google 또는 Kakao 버튼을 클릭합니다.',
          '해당 소셜 계정으로 인증을 완료합니다.',
          '최초 로그인 시 온보딩 화면이 표시됩니다.',
        ],
      },
      {
        subtitle: '온보딩',
        text: '처음 가입하면 온보딩 과정을 통해 기본 설정을 완료합니다.',
        steps: [
          '닉네임과 크리에이터 카테고리를 설정합니다.',
          '사용할 플랫폼 채널을 연동합니다.',
          '기본 업로드 설정을 구성합니다.',
        ],
      },
    ],
  },
  {
    id: 'channels',
    title: '채널 연동',
    icon: LinkIcon,
    content: [
      {
        text: 'onGo는 YouTube, TikTok, Instagram Reels, Naver Clip 4개 플랫폼을 지원합니다. 각 플랫폼의 공식 OAuth 인증을 통해 안전하게 채널을 연동할 수 있습니다.',
        steps: [
          '사이드바에서 "채널 관리"를 클릭합니다.',
          '"채널 추가" 버튼을 클릭하고 연동할 플랫폼을 선택합니다.',
          '해당 플랫폼의 인증 페이지에서 권한을 승인합니다.',
          '연동 완료 후 채널 목록에서 상태를 확인합니다.',
        ],
      },
      {
        subtitle: '플랫폼별 참고사항',
        text: '',
        items: [
          'YouTube: Google 계정 인증 후 YouTube 채널을 선택합니다.',
          'TikTok: TikTok 비즈니스 또는 크리에이터 계정이 필요합니다.',
          'Instagram Reels: Facebook/Meta 비즈니스 계정과 연결된 Instagram 계정이 필요합니다.',
          'Naver Clip: 네이버 계정 인증 후 클립 채널을 연동합니다.',
        ],
      },
    ],
  },
  {
    id: 'upload',
    title: '영상 업로드',
    icon: ArrowUpTrayIcon,
    content: [
      {
        subtitle: '업로드 프로세스',
        text: 'onGo는 Tus 프로토콜 기반의 이어받기 업로드를 지원합니다. 네트워크가 끊겨도 이어서 업로드할 수 있습니다.',
        steps: [
          '사이드바에서 "업로드"를 클릭합니다.',
          '영상 파일을 드래그 앤 드롭하거나 파일 선택으로 업로드합니다.',
          '제목, 설명, 태그 등 메타데이터를 입력합니다.',
          '업로드할 플랫폼을 선택합니다 (복수 선택 가능).',
          '"업로드" 버튼을 클릭하여 업로드를 시작합니다.',
        ],
      },
      {
        subtitle: '멀티 플랫폼 동시 업로드',
        text: '여러 플랫폼에 동시에 업로드할 수 있습니다. 각 플랫폼별로 제목/설명을 개별 수정할 수도 있고, AI를 활용해 플랫폼에 최적화된 메타데이터를 자동 생성할 수도 있습니다.',
      },
      {
        subtitle: '업로드 상태',
        text: '',
        items: [
          '초안 (DRAFT): 업로드 전 상태',
          '업로드 중 (UPLOADING): 파일 업로드 진행 중',
          '처리 중 (PROCESSING): 플랫폼에서 영상 처리 중',
          '검토 중 (REVIEW): 플랫폼 검토 대기 중',
          '게시됨 (PUBLISHED): 업로드 완료',
          '실패 (FAILED): 업로드 실패',
        ],
      },
    ],
  },
  {
    id: 'scheduling',
    title: '예약 관리',
    icon: CalendarDaysIcon,
    content: [
      {
        subtitle: '예약 업로드',
        text: '영상을 특정 날짜와 시간에 자동으로 게시되도록 예약할 수 있습니다.',
        steps: [
          '업로드 화면에서 "예약 업로드"를 선택합니다.',
          '원하는 날짜와 시간을 설정합니다.',
          '플랫폼별로 다른 시간을 설정할 수도 있습니다.',
          '예약을 확인하고 저장합니다.',
        ],
      },
      {
        subtitle: '캘린더',
        text: '캘린더 뷰에서 예약된 영상과 게시된 영상을 한눈에 확인할 수 있습니다. 드래그 앤 드롭으로 예약 일정을 변경할 수도 있습니다.',
      },
    ],
  },
  {
    id: 'ai-tools',
    title: 'AI 도구',
    icon: SparklesIcon,
    content: [
      {
        subtitle: '제목/설명 생성',
        text: 'AI가 영상 내용을 분석하여 최적화된 제목과 설명을 자동 생성합니다. 플랫폼별 특성에 맞춰 각각 다른 버전을 제안합니다.',
      },
      {
        subtitle: '해시태그 추천',
        text: '영상 주제와 트렌드를 분석하여 효과적인 해시태그를 추천합니다.',
      },
      {
        subtitle: 'AI 크레딧 시스템',
        text: 'AI 기능은 크레딧을 소비합니다.',
        items: [
          '매월 무료 크레딧이 제공됩니다 (플랜별 상이).',
          '무료 크레딧 소진 시 구매한 크레딧이 사용됩니다.',
          '크레딧 잔량은 상단 바 또는 AI 도구 페이지에서 확인할 수 있습니다.',
          '크레딧이 모두 소진되면 AI 기능만 비활성화되며, 다른 기능은 정상 이용 가능합니다.',
        ],
      },
    ],
  },
  {
    id: 'analytics',
    title: '분석',
    icon: ChartBarIcon,
    content: [
      {
        subtitle: '통합 분석',
        text: '모든 연동된 플랫폼의 성과 데이터를 한 화면에서 확인할 수 있습니다. 조회수, 좋아요, 댓글 수, 구독자 변화 등을 통합적으로 모니터링하세요.',
      },
      {
        subtitle: '수익 분석',
        text: '플랫폼별 수익 현황과 추이를 확인할 수 있습니다.',
      },
      {
        subtitle: 'A/B 테스트',
        text: '같은 영상에 대해 다른 썸네일이나 제목을 테스트하여 최적의 조합을 찾을 수 있습니다.',
      },
      {
        subtitle: '경쟁사 분석',
        text: '경쟁 채널의 성과를 모니터링하고, 자신의 채널과 비교 분석할 수 있습니다.',
      },
    ],
  },
  {
    id: 'team',
    title: '팀 관리',
    icon: UserGroupIcon,
    content: [
      {
        text: '팀원을 초대하여 함께 채널을 관리할 수 있습니다.',
        items: [
          '팀원에게 역할별 권한을 부여할 수 있습니다 (관리자, 편집자, 뷰어).',
          '팀원별 활동 로그를 확인할 수 있습니다.',
          '업로드 승인 워크플로를 설정할 수 있습니다.',
        ],
      },
    ],
  },
  {
    id: 'subscription',
    title: '구독 및 결제',
    icon: CreditCardIcon,
    content: [
      {
        subtitle: '플랜 종류',
        text: '',
        items: [
          'Free: 기본 기능, 월 3회 업로드, 1GB 저장 공간',
          'Starter (9,900원/월): 월 30회 업로드, 10GB 저장 공간, 2개 플랫폼',
          'Pro (19,900원/월): 무제한 업로드, 50GB 저장 공간, 4개 플랫폼, AI 크레딧 포함',
          'Business (49,900원/월): 모든 기능, 무제한 저장 공간, 팀 관리, 우선 지원',
        ],
      },
      {
        subtitle: '결제 관리',
        text: '사이드바의 "구독/결제" 메뉴에서 현재 플랜 확인, 플랜 변경, 결제 내역 조회가 가능합니다.',
      },
    ],
  },
  {
    id: 'settings',
    title: '설정',
    icon: Cog6ToothIcon,
    content: [
      {
        text: '설정 페이지에서 다양한 옵션을 구성할 수 있습니다.',
        items: [
          '프로필: 닉네임, 프로필 이미지, 크리에이터 카테고리 설정',
          '알림: 이메일/푸시 알림, 댓글 알림 빈도, 크레딧 알림 설정',
          '기본 설정: 기본 공개 설정, 기본 업로드 플랫폼, AI 톤 설정',
          '언어: 한국어 / English 전환',
          '계정: 연동된 소셜 계정 확인, 회원 탈퇴',
        ],
      },
    ],
  },
  {
    id: 'faq',
    title: '자주 묻는 질문',
    icon: QuestionMarkCircleIcon,
    content: [
      {
        subtitle: '업로드가 중간에 끊기면 어떻게 하나요?',
        text: 'onGo는 이어받기 업로드(Tus 프로토콜)를 지원합니다. 네트워크가 복구되면 자동으로 이어서 업로드됩니다. 수동으로 재시도하려면 업로드 목록에서 "재시도" 버튼을 클릭하세요.',
      },
      {
        subtitle: '한 플랫폼에서만 업로드가 실패하면?',
        text: '다른 플랫폼의 업로드에는 영향이 없습니다. 실패한 플랫폼만 개별적으로 재시도할 수 있습니다.',
      },
      {
        subtitle: 'AI 크레딧이 소진되면 어떻게 되나요?',
        text: 'AI 기능(제목 생성, 해시태그 추천 등)만 비활성화됩니다. 영상 업로드, 예약, 분석 등 다른 기능은 정상적으로 사용할 수 있습니다. 크레딧은 구독/결제 페이지에서 추가 구매할 수 있습니다.',
      },
      {
        subtitle: '플랫폼 연동을 해제하면 기존 영상은 어떻게 되나요?',
        text: '플랫폼 연동을 해제해도 이미 업로드된 영상에는 영향이 없습니다. 해당 플랫폼에서 직접 관리해야 합니다. onGo에서의 분석 데이터는 연동 해제 시점까지만 유지됩니다.',
      },
      {
        subtitle: '비밀번호를 잊어버렸어요.',
        text: 'onGo는 소셜 로그인만 지원하므로 별도의 비밀번호가 없습니다. Google 또는 Kakao 계정의 비밀번호를 해당 서비스에서 재설정해 주세요.',
      },
    ],
  },
]

const sectionsEn: ManualSection[] = [
  {
    id: 'getting-started',
    title: 'Getting Started',
    icon: RocketLaunchIcon,
    content: [
      {
        subtitle: 'Sign Up & Login',
        text: 'onGo supports social login only. Get started easily with your Google or Kakao account.',
        steps: [
          'Click the Google or Kakao button on the login page.',
          'Complete authentication with your social account.',
          'The onboarding screen will appear on your first login.',
        ],
      },
      {
        subtitle: 'Onboarding',
        text: 'When you first sign up, the onboarding process helps you complete your initial setup.',
        steps: [
          'Set your nickname and creator category.',
          'Connect your platform channels.',
          'Configure default upload settings.',
        ],
      },
    ],
  },
  {
    id: 'channels',
    title: 'Connecting Channels',
    icon: LinkIcon,
    content: [
      {
        text: 'onGo supports 4 platforms: YouTube, TikTok, Instagram Reels, and Naver Clip. You can securely connect channels through each platform\'s official OAuth authentication.',
        steps: [
          'Click "Channels" in the sidebar.',
          'Click "Add Channel" and select the platform to connect.',
          'Approve permissions on the platform\'s authentication page.',
          'Check the status in the channel list after connection.',
        ],
      },
      {
        subtitle: 'Platform Notes',
        text: '',
        items: [
          'YouTube: Select your YouTube channel after Google account authentication.',
          'TikTok: A TikTok Business or Creator account is required.',
          'Instagram Reels: An Instagram account linked to a Facebook/Meta Business account is required.',
          'Naver Clip: Connect your Clip channel after Naver account authentication.',
        ],
      },
    ],
  },
  {
    id: 'upload',
    title: 'Uploading Videos',
    icon: ArrowUpTrayIcon,
    content: [
      {
        subtitle: 'Upload Process',
        text: 'onGo supports resumable uploads based on the Tus protocol. Your upload can continue even if the network is interrupted.',
        steps: [
          'Click "Upload" in the sidebar.',
          'Drag and drop a video file or use the file selector.',
          'Enter metadata such as title, description, and tags.',
          'Select target platforms (multiple selection available).',
          'Click "Upload" to start the upload.',
        ],
      },
      {
        subtitle: 'Multi-Platform Simultaneous Upload',
        text: 'You can upload to multiple platforms simultaneously. You can customize titles/descriptions for each platform individually, or use AI to automatically generate platform-optimized metadata.',
      },
      {
        subtitle: 'Upload Status',
        text: '',
        items: [
          'Draft (DRAFT): Before upload',
          'Uploading (UPLOADING): File upload in progress',
          'Processing (PROCESSING): Platform is processing the video',
          'Review (REVIEW): Awaiting platform review',
          'Published (PUBLISHED): Upload complete',
          'Failed (FAILED): Upload failed',
        ],
      },
    ],
  },
  {
    id: 'scheduling',
    title: 'Scheduling',
    icon: CalendarDaysIcon,
    content: [
      {
        subtitle: 'Scheduled Uploads',
        text: 'You can schedule videos to be published automatically at a specific date and time.',
        steps: [
          'Select "Schedule Upload" on the upload screen.',
          'Set your desired date and time.',
          'You can set different times for each platform.',
          'Confirm and save the schedule.',
        ],
      },
      {
        subtitle: 'Calendar',
        text: 'View all scheduled and published videos at a glance in the calendar view. You can also reschedule by dragging and dropping.',
      },
    ],
  },
  {
    id: 'ai-tools',
    title: 'AI Tools',
    icon: SparklesIcon,
    content: [
      {
        subtitle: 'Title & Description Generation',
        text: 'AI analyzes your video content and automatically generates optimized titles and descriptions. It suggests different versions tailored to each platform\'s characteristics.',
      },
      {
        subtitle: 'Hashtag Recommendations',
        text: 'Effective hashtags are recommended based on video topic analysis and trends.',
      },
      {
        subtitle: 'AI Credit System',
        text: 'AI features consume credits.',
        items: [
          'Free monthly credits are provided (varies by plan).',
          'Purchased credits are used after free credits are depleted.',
          'Check your credit balance in the top bar or AI Tools page.',
          'When all credits are depleted, only AI features are disabled; other features work normally.',
        ],
      },
    ],
  },
  {
    id: 'analytics',
    title: 'Analytics',
    icon: ChartBarIcon,
    content: [
      {
        subtitle: 'Unified Analytics',
        text: 'View performance data from all connected platforms on one screen. Monitor views, likes, comments, subscriber changes, and more in an integrated dashboard.',
      },
      {
        subtitle: 'Revenue Analytics',
        text: 'Check revenue status and trends by platform.',
      },
      {
        subtitle: 'A/B Testing',
        text: 'Test different thumbnails or titles for the same video to find the optimal combination.',
      },
      {
        subtitle: 'Competitor Analysis',
        text: 'Monitor competitor channel performance and compare it with your own channel.',
      },
    ],
  },
  {
    id: 'team',
    title: 'Team Management',
    icon: UserGroupIcon,
    content: [
      {
        text: 'Invite team members to manage channels together.',
        items: [
          'Assign role-based permissions to team members (Admin, Editor, Viewer).',
          'View activity logs for each team member.',
          'Set up upload approval workflows.',
        ],
      },
    ],
  },
  {
    id: 'subscription',
    title: 'Subscription & Billing',
    icon: CreditCardIcon,
    content: [
      {
        subtitle: 'Plans',
        text: '',
        items: [
          'Free: Basic features, 3 uploads/month, 1GB storage',
          'Starter (KRW 9,900/mo): 30 uploads/month, 10GB storage, 2 platforms',
          'Pro (KRW 19,900/mo): Unlimited uploads, 50GB storage, 4 platforms, AI credits included',
          'Business (KRW 49,900/mo): All features, unlimited storage, team management, priority support',
        ],
      },
      {
        subtitle: 'Billing Management',
        text: 'Check your current plan, change plans, and view payment history from the "Subscription" menu in the sidebar.',
      },
    ],
  },
  {
    id: 'settings',
    title: 'Settings',
    icon: Cog6ToothIcon,
    content: [
      {
        text: 'Configure various options on the Settings page.',
        items: [
          'Profile: Nickname, profile image, creator category',
          'Notifications: Email/push notifications, comment notification frequency, credit alerts',
          'Defaults: Default visibility, default upload platforms, AI tone',
          'Language: Switch between Korean / English',
          'Account: View connected social accounts, delete account',
        ],
      },
    ],
  },
  {
    id: 'faq',
    title: 'FAQ',
    icon: QuestionMarkCircleIcon,
    content: [
      {
        subtitle: 'What if my upload gets interrupted?',
        text: 'onGo supports resumable uploads (Tus protocol). When the network recovers, the upload resumes automatically. To retry manually, click the "Retry" button in the upload list.',
      },
      {
        subtitle: 'What if the upload fails on only one platform?',
        text: 'Uploads on other platforms are not affected. You can retry the failed platform individually.',
      },
      {
        subtitle: 'What happens when AI credits run out?',
        text: 'Only AI features (title generation, hashtag recommendations, etc.) are disabled. Other features like video uploading, scheduling, and analytics continue to work normally. You can purchase additional credits on the Subscription page.',
      },
      {
        subtitle: 'What happens to existing videos if I disconnect a platform?',
        text: 'Disconnecting a platform does not affect already uploaded videos. You\'ll need to manage them directly on the platform. Analytics data in onGo is retained only up to the disconnection date.',
      },
      {
        subtitle: 'I forgot my password.',
        text: 'onGo uses social login only, so there is no separate password. Please reset your Google or Kakao account password through their respective services.',
      },
    ],
  },
]

// Intersection Observer for active section tracking
let observer: IntersectionObserver | null = null

function checkMobile() {
  isMobile.value = window.innerWidth < 1024
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)

  observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          activeSection.value = entry.target.id
        }
      }
    },
    { rootMargin: '-20% 0px -60% 0px' },
  )

  setTimeout(() => {
    const sectionElements = document.querySelectorAll('section[id]')
    sectionElements.forEach((el) => observer?.observe(el))
  }, 100)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
  observer?.disconnect()
})
</script>

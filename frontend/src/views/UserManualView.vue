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
  PhotoIcon,
  CalendarDaysIcon,
  SparklesIcon,
  ChartBarIcon,
  UserGroupIcon,
  CreditCardIcon,
  Cog6ToothIcon,
  QuestionMarkCircleIcon,
  ListBulletIcon,
  ChevronDownIcon,
  FilmIcon,
  PresentationChartLineIcon,
  BriefcaseIcon,
  ShoppingCartIcon,
  BuildingOfficeIcon,
  ClockIcon,
  CubeTransparentIcon,
  ScissorsIcon,
  LanguageIcon,
  MagnifyingGlassCircleIcon,
  BeakerIcon,
  ChatBubbleBottomCenterTextIcon,
  ArrowsPointingOutIcon,
  AcademicCapIcon,
  ShieldCheckIcon,
  TableCellsIcon,
  BoltIcon,
  CurrencyDollarIcon,
  DocumentTextIcon,
  MegaphoneIcon,
  UserPlusIcon,
  ClipboardDocumentCheckIcon,
  RectangleStackIcon,
  HeartIcon,
  DocumentArrowUpIcon,
  UsersIcon,
  ShieldExclamationIcon,
  PencilSquareIcon,
  ViewColumnsIcon,
  GiftIcon,
  QueueListIcon,
  HashtagIcon,
  BanknotesIcon,
  ChatBubbleLeftRightIcon,
  FaceSmileIcon,
  CalendarIcon as AiCalIcon,
  ScaleIcon as BenchmarkIcon,
  CircleStackIcon,
  EyeIcon,
  DocumentChartBarIcon,
  TrophyIcon,
  CpuChipIcon,
  ArrowPathRoundedSquareIcon,
  Square3Stack3DIcon,
  MagnifyingGlassIcon,
  PaintBrushIcon,
  PhotoIcon as ThumbnailAbIcon2,
  CurrencyDollarIcon as RevenueGoalIcon2,
  ChatBubbleLeftRightIcon as CommentSummaryIcon,
  HeartIcon as HealthIcon2,
  MicrophoneIcon,
  FolderOpenIcon,
  HandRaisedIcon,
  UserCircleIcon,
  ArrowTrendingUpIcon,
  DocumentChartBarIcon as ReportIcon3,
  VideoCameraIcon,
  ShoppingBagIcon,
  MusicalNoteIcon,
  AdjustmentsHorizontalIcon,
  GiftIcon as RewardIcon4,
  CogIcon as AutoIcon,
  ClockIcon as OptimizerIcon2,
  RectangleGroupIcon,
  MegaphoneIcon as CampaignIcon2,
  FunnelIcon,
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
        text: 'onGo는 YouTube, TikTok, Instagram Reels, Naver Clip을 포함한 총 13개 플랫폼을 지원합니다. 각 플랫폼의 공식 OAuth 인증을 통해 안전하게 채널을 연동할 수 있습니다.',
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
          'X (Twitter): Twitter/X 개발자 계정이 필요합니다. OAuth 2.0 PKCE를 사용합니다.',
          'Facebook: Facebook 페이지 관리자 권한이 필요합니다.',
          'Threads: Meta/Instagram 비즈니스 계정이 필요합니다.',
          'Pinterest: Pinterest 비즈니스 계정이 필요합니다.',
          'LinkedIn: LinkedIn 계정으로 비디오 포스트를 게시합니다.',
          'WordPress: WordPress.com 블로그가 필요합니다.',
          'Tumblr: Tumblr 블로그에 비디오 포스트를 게시합니다.',
          'Vimeo: Vimeo 계정으로 고품질 영상을 업로드합니다.',
          'Dailymotion: Dailymotion 계정으로 영상을 업로드합니다.',
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
      {
        subtitle: '미디어 분석 (FFprobe)',
        text: '업로드된 영상은 FFprobe를 통해 자동으로 분석됩니다. 영상 상세 페이지의 "미디어 분석 정보" 탭에서 결과를 확인할 수 있습니다.',
        items: [
          '비디오 트랙: 코덱(H.264/H.265 등), 해상도, FPS, 비트레이트, 프로파일',
          '오디오 트랙: 코덱(AAC 등), 비트레이트, 샘플레이트, 채널 수',
          '색상 정보: 색 공간, 픽셀 포맷',
          '플랫폼 호환성: YouTube, TikTok, Instagram, Naver Clip 등 13개 플랫폼과의 호환성 여부를 자동 판단하여 배지로 표시합니다.',
        ],
      },
      {
        subtitle: '다이렉트 원본 업로드',
        text: '원본 영상 파일이 별도의 트랜스코딩 없이 각 플랫폼에 직접 업로드됩니다. 플랫폼이 자체적으로 최적 처리를 수행하므로 품질 손실 없이 빠르게 업로드할 수 있습니다.',
      },
      {
        subtitle: '실시간 처리 진행률',
        text: '업로드 후 영상 처리 과정을 실시간으로 확인할 수 있습니다. SSE(Server-Sent Events)를 통해 각 처리 단계의 진행률이 자동 업데이트됩니다.',
        items: [
          '처리 단계: 미디어 분석 → 썸네일 생성 → 자막 생성 → 플랫폼 업로드',
          '전체 진행률과 단계별 진행률을 동시에 확인할 수 있습니다.',
          '예상 남은 시간이 자동 계산되어 표시됩니다.',
          '연결이 끊긴 경우 자동 재접속을 시도합니다.',
        ],
      },
      {
        subtitle: 'AI 썸네일 생성',
        text: '업로드 후 FFmpeg를 활용하여 자동으로 썸네일 후보를 생성합니다.',
        items: [
          '씬 감지: 장면 전환 지점에서 시각적으로 흥미로운 프레임을 자동 추출합니다.',
          '균등 간격 추출: 영상 전체에 걸쳐 균등한 간격으로 프레임을 추출합니다.',
          '총 10개 내외의 후보가 생성되며, 그리드에서 원하는 썸네일을 클릭하여 대표 이미지로 선택합니다.',
          '커스텀 썸네일: JPG, PNG, WebP 형식의 이미지를 직접 업로드할 수 있습니다 (최대 5MB).',
        ],
      },
      {
        subtitle: 'AI 자동 자막 (Whisper STT)',
        text: 'OpenAI Whisper를 사용하여 영상의 음성을 자동으로 텍스트로 변환합니다.',
        items: [
          '한국어와 영어 자막 생성을 지원합니다.',
          '생성된 SRT 형식 자막을 인라인 편집기에서 직접 수정할 수 있습니다.',
          '수정된 자막은 저장 버튼으로 서버에 저장됩니다.',
          'SRT 또는 VTT 형식으로 다운로드할 수 있습니다.',
          '자막 생성에는 AI 크레딧 5개가 차감됩니다 (생성 전 확인 팝업 표시).',
        ],
      },
      {
        subtitle: '향상된 업로드',
        text: 'Tus 프로토콜 기반 업로드가 더욱 개선되었습니다.',
        items: [
          '적응형 청크 사이즈: 네트워크 속도에 따라 2MB~20MB 사이에서 청크 크기가 자동 조절됩니다.',
          '업로드 속도와 남은 시간이 실시간으로 표시됩니다.',
          '24시간 이내 중단된 업로드를 자동으로 이어받기할 수 있습니다.',
        ],
      },
    ],
  },
  {
    id: 'image-upload',
    title: '이미지/사진 업로드',
    icon: PhotoIcon,
    content: [
      {
        subtitle: '사진 업로드',
        text: 'onGo는 영상뿐만 아니라 사진/이미지 콘텐츠도 지원합니다. Instagram 등 사진 게시가 가능한 플랫폼에 이미지를 업로드할 수 있습니다.',
        steps: [
          '업로드 페이지에서 이미지 파일을 선택하면 자동으로 이미지 모드로 전환됩니다.',
          '여러 장의 이미지를 한 번에 선택하여 멀티 이미지 스토리를 만들 수 있습니다.',
          '제목, 설명, 태그 등 메타데이터를 입력합니다.',
          '게시할 플랫폼을 선택하고 업로드합니다.',
        ],
      },
      {
        subtitle: '멀티 이미지 스토리',
        text: 'Instagram 스토리처럼 여러 장의 이미지를 하나의 게시물로 묶어 업로드할 수 있습니다.',
        items: [
          '최대 10장까지 이미지를 추가할 수 있습니다.',
          '드래그 앤 드롭으로 이미지 순서를 변경할 수 있습니다.',
          '각 이미지는 최대 50MB까지 지원됩니다.',
          '지원 형식: JPG, PNG, WebP, GIF, HEIC',
        ],
      },
      {
        subtitle: '이미지와 영상의 차이',
        text: '',
        items: [
          '이미지 콘텐츠는 영상과 동일하게 원본이 바로 업로드됩니다.',
          '자동 썸네일 생성과 자막 생성은 영상에만 적용됩니다.',
          '이미지 콘텐츠는 영상 목록에서 이미지 아이콘으로 구분됩니다.',
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
  {
    id: 'ai-content-studio',
    title: 'AI 콘텐츠 스튜디오',
    icon: FilmIcon,
    content: [
      {
        subtitle: '개요',
        text: 'AI 콘텐츠 스튜디오는 영상 클립 생성, 캡션 자동 생성, AI 썸네일 생성 등 콘텐츠 제작에 필요한 AI 도구를 통합 제공하는 공간입니다.',
      },
      {
        subtitle: '클립 생성',
        text: 'AI가 원본 영상에서 하이라이트 구간을 자동으로 감지하여 숏폼 클립을 생성합니다.',
        steps: [
          '영상 상세 페이지에서 "AI 클립 생성" 버튼을 클릭합니다.',
          'AI가 영상을 분석하여 핵심 구간을 자동으로 추출합니다.',
          '생성된 클립 목록에서 원하는 클립을 선택합니다.',
          '시작/종료 지점을 직접 미세 조정할 수 있습니다.',
          '선택한 클립을 TikTok, Instagram Reels 등에 바로 업로드할 수 있습니다.',
        ],
      },
      {
        subtitle: '캡션 생성',
        text: 'AI가 영상 내용을 분석하여 SNS에 적합한 캡션(게시글 텍스트)을 자동 생성합니다.',
        steps: [
          '"캡션 생성" 탭에서 대상 영상을 선택합니다.',
          '톤(전문적, 캐주얼, 유머 등)과 길이를 설정합니다.',
          '플랫폼별 최적화된 캡션이 생성됩니다.',
          '생성된 캡션을 편집하거나 바로 적용할 수 있습니다.',
        ],
      },
      {
        subtitle: '썸네일 생성',
        text: 'AI가 영상의 핵심 장면을 분석하여 클릭률 높은 썸네일 후보를 자동 생성합니다.',
        items: [
          '영상에서 시각적으로 가장 매력적인 프레임을 자동 추출합니다.',
          '텍스트 오버레이, 필터 등 기본 편집이 가능합니다.',
          '생성된 썸네일을 업로드 시 바로 적용할 수 있습니다.',
        ],
      },
      {
        subtitle: '크레딧 소모 안내',
        text: 'AI 콘텐츠 스튜디오의 각 기능은 AI 크레딧을 소모합니다.',
        items: [
          '클립 생성: 10 크레딧',
          '캡션 생성: 3 크레딧',
          '썸네일 생성: 5 크레딧',
          '각 기능 실행 전 크레딧 차감 안내 팝업이 표시됩니다.',
          '잔여 크레딧이 부족하면 기능이 비활성화됩니다.',
        ],
      },
    ],
  },
  {
    id: 'ai-performance-prediction',
    title: 'AI 성과 예측',
    icon: PresentationChartLineIcon,
    content: [
      {
        subtitle: '개요',
        text: 'AI 성과 예측은 업로드 전 영상의 예상 조회수, 참여율, 최적 게시 시간 등을 예측하여 콘텐츠 전략 수립을 돕는 기능입니다.',
      },
      {
        subtitle: '예측 실행 방법',
        text: '영상의 메타데이터와 과거 채널 데이터를 기반으로 AI가 성과를 예측합니다.',
        steps: [
          '영상 상세 페이지 또는 업로드 화면에서 "성과 예측" 버튼을 클릭합니다.',
          'AI가 제목, 설명, 태그, 썸네일 등 메타데이터를 분석합니다.',
          '채널의 과거 성과 데이터와 현재 트렌드를 비교합니다.',
          '예상 조회수, 좋아요, 댓글 수, 최적 게시 시간을 결과로 표시합니다.',
        ],
      },
      {
        subtitle: '히트맵 읽는 법',
        text: '성과 예측 결과에는 시간대별 히트맵이 포함됩니다.',
        items: [
          '색이 진할수록 해당 시간대에 높은 참여율이 예상됨을 의미합니다.',
          '가로축은 요일, 세로축은 시간대를 나타냅니다.',
          '히트맵을 클릭하면 해당 시간으로 예약 업로드를 바로 설정할 수 있습니다.',
          '플랫폼별로 다른 히트맵이 제공되므로 각 플랫폼의 최적 시간을 개별 확인하세요.',
        ],
      },
      {
        subtitle: '경쟁사 비교',
        text: '나의 예측 성과를 경쟁 채널의 평균 성과와 비교할 수 있습니다.',
        items: [
          '경쟁 채널 대비 예상 성과 지표를 비교 차트로 확인할 수 있습니다.',
          '카테고리 평균 대비 강점/약점 분석을 제공합니다.',
          '메타데이터 개선 제안을 통해 성과를 높일 수 있는 방법을 안내합니다.',
        ],
      },
    ],
  },
  {
    id: 'creator-portfolio',
    title: '크리에이터 포트폴리오',
    icon: BriefcaseIcon,
    content: [
      {
        subtitle: '개요',
        text: '크리에이터 포트폴리오는 브랜드 협업을 위한 미디어킷을 제작하고, 브랜드 이력을 관리하며, PDF로 내보내기할 수 있는 기능입니다.',
      },
      {
        subtitle: '미디어킷 편집',
        text: '전문적인 미디어킷을 직접 편집하여 브랜드에 어필할 수 있습니다.',
        steps: [
          '사이드바에서 "포트폴리오"를 클릭합니다.',
          '"미디어킷 편집" 버튼으로 편집 모드에 진입합니다.',
          '프로필 사진, 자기 소개, 채널 통계를 입력합니다.',
          '대표 콘텐츠와 협업 사례를 추가합니다.',
          '테마와 레이아웃을 선택하여 디자인을 완성합니다.',
        ],
      },
      {
        subtitle: '브랜드 이력 관리',
        text: '과거 브랜드 협업 이력을 체계적으로 관리할 수 있습니다.',
        items: [
          '협업 브랜드명, 기간, 캠페인 내용을 기록합니다.',
          '협업 콘텐츠의 성과 데이터(조회수, 참여율 등)를 연동하여 표시합니다.',
          '브랜드 협업 포트폴리오를 공개 링크로 공유할 수 있습니다.',
        ],
      },
      {
        subtitle: 'PDF 내보내기',
        text: '완성된 미디어킷을 PDF 파일로 내보내기할 수 있습니다.',
        items: [
          '"PDF 내보내기" 버튼을 클릭하면 미디어킷이 PDF로 변환됩니다.',
          'A4 또는 레터 사이즈를 선택할 수 있습니다.',
          '다운로드된 PDF를 브랜드에 직접 전달할 수 있습니다.',
        ],
      },
    ],
  },
  {
    id: 'social-commerce',
    title: '소셜 커머스 대시보드',
    icon: ShoppingCartIcon,
    content: [
      {
        subtitle: '개요',
        text: '소셜 커머스 대시보드는 각 소셜 플랫폼의 쇼핑 기능과 연동하여 상품 관리, 판매 추적, 수익 분석을 통합적으로 수행할 수 있는 기능입니다.',
      },
      {
        subtitle: '플랫폼 연동',
        text: '소셜 커머스 기능을 사용하려면 플랫폼의 쇼핑 기능을 연동해야 합니다.',
        steps: [
          '소셜 커머스 대시보드에서 "플랫폼 연동" 버튼을 클릭합니다.',
          '연동할 커머스 플랫폼(YouTube Shopping, TikTok Shop, Instagram Shopping 등)을 선택합니다.',
          '해당 플랫폼의 쇼핑 계정 인증을 완료합니다.',
          '연동 완료 후 상품 카탈로그가 자동 동기화됩니다.',
        ],
      },
      {
        subtitle: '상품 관리',
        text: '연동된 플랫폼의 상품을 통합 관리할 수 있습니다.',
        items: [
          '모든 플랫폼의 상품을 한 화면에서 조회하고 관리합니다.',
          '상품별 판매량, 클릭률, 전환율 등 핵심 지표를 확인합니다.',
          '영상에 상품 태그를 연결하여 영상 내 쇼핑 기능을 활성화합니다.',
          '재고 상태와 가격 변동을 실시간으로 모니터링합니다.',
        ],
      },
      {
        subtitle: '수익 추적',
        text: '소셜 커머스를 통한 수익을 상세하게 추적할 수 있습니다.',
        items: [
          '플랫폼별, 상품별, 기간별 수익 현황을 차트로 확인합니다.',
          '영상별 매출 기여도를 분석하여 고성과 콘텐츠를 파악합니다.',
          '수수료, 환불 등을 포함한 순수익을 자동 계산합니다.',
          '월별/주별 수익 리포트를 자동 생성합니다.',
        ],
      },
    ],
  },
  {
    id: 'agency-dashboard',
    title: '에이전시 대시보드',
    icon: BuildingOfficeIcon,
    content: [
      {
        subtitle: '개요',
        text: '에이전시 대시보드는 다수의 크리에이터를 관리하는 MCN/에이전시를 위한 통합 관리 기능입니다. 워크스페이스 단위로 크리에이터를 그룹화하고, 성과를 비교하며, 클라이언트에게 보고서를 제공할 수 있습니다.',
      },
      {
        subtitle: '워크스페이스',
        text: '에이전시 내에서 프로젝트 또는 팀 단위로 워크스페이스를 생성하여 관리합니다.',
        steps: [
          '"워크스페이스 생성" 버튼으로 새 워크스페이스를 만듭니다.',
          '워크스페이스에 소속 크리에이터를 초대합니다.',
          '워크스페이스별 권한(관리자, 매니저, 뷰어)을 설정합니다.',
          '워크스페이스 대시보드에서 소속 크리에이터의 통합 성과를 확인합니다.',
        ],
      },
      {
        subtitle: '크리에이터 비교',
        text: '소속 크리에이터 간 성과를 비교 분석할 수 있습니다.',
        items: [
          '구독자 수, 조회수, 참여율 등 핵심 지표를 크리에이터별로 비교합니다.',
          '성장률 기준으로 상위/하위 크리에이터를 자동 분류합니다.',
          '크리에이터별 콘텐츠 발행 빈도와 성과 추이를 시각화합니다.',
          '맞춤형 비교 리포트를 생성할 수 있습니다.',
        ],
      },
      {
        subtitle: '클라이언트 포탈',
        text: '광고주/브랜드 클라이언트에게 전용 포탈을 제공할 수 있습니다.',
        items: [
          '클라이언트별 전용 로그인 링크를 생성합니다.',
          '캠페인 성과 데이터를 실시간으로 공유합니다.',
          '클라이언트가 볼 수 있는 데이터 범위를 세밀하게 제어합니다.',
          '자동화된 정기 리포트를 클라이언트에게 발송합니다.',
        ],
      },
    ],
  },
  {
    id: 'ai-content-calendar',
    title: 'AI 콘텐츠 캘린더',
    icon: ClockIcon,
    content: [
      {
        subtitle: '개요',
        text: 'AI 콘텐츠 캘린더는 AI가 채널 데이터와 트렌드를 분석하여 최적의 콘텐츠 발행 일정을 자동으로 제안하는 기능입니다.',
      },
      {
        subtitle: '기간/빈도 설정',
        text: '콘텐츠 캘린더 생성을 위한 기본 설정을 구성합니다.',
        steps: [
          'AI 콘텐츠 캘린더 페이지에서 "새 캘린더 생성"을 클릭합니다.',
          '계획 기간을 설정합니다 (1주, 2주, 1개월, 3개월).',
          '주당 발행 빈도를 설정합니다 (예: 주 3회).',
          '대상 플랫폼과 콘텐츠 유형(숏폼, 롱폼 등)을 선택합니다.',
          'AI가 최적의 발행 일정을 자동 생성합니다.',
        ],
      },
      {
        subtitle: '시즌 이벤트',
        text: 'AI가 시즌별 이벤트와 트렌드를 반영하여 캘린더를 최적화합니다.',
        items: [
          '공휴일, 기념일, 시즌 이벤트를 자동으로 캘린더에 표시합니다.',
          '이벤트 관련 콘텐츠 주제를 AI가 자동 제안합니다.',
          '업종/카테고리별 맞춤 시즌 이벤트를 제공합니다.',
          '글로벌 이벤트와 국내 이벤트를 모두 지원합니다.',
        ],
      },
      {
        subtitle: '스케줄 적용',
        text: 'AI가 생성한 캘린더를 실제 업로드 스케줄에 적용합니다.',
        items: [
          '캘린더의 일정을 개별적으로 또는 일괄적으로 예약 업로드에 적용할 수 있습니다.',
          '드래그 앤 드롭으로 일정을 자유롭게 변경할 수 있습니다.',
          '기존 예약 업로드와 충돌 시 경고를 표시합니다.',
          '캘린더를 팀원과 공유하여 협업할 수 있습니다.',
        ],
      },
    ],
  },
  {
    id: 'workflow-builder',
    title: '워크플로우 빌더',
    icon: CubeTransparentIcon,
    content: [
      {
        subtitle: '개요',
        text: '워크플로우 빌더는 반복적인 콘텐츠 발행 과정을 자동화할 수 있는 비주얼 워크플로우 편집기입니다. 노드 기반 인터페이스로 복잡한 자동화를 코딩 없이 구성할 수 있습니다.',
      },
      {
        subtitle: '노드 추가',
        text: '워크플로우의 각 단계를 노드로 추가합니다.',
        steps: [
          '워크플로우 빌더에서 "새 워크플로우"를 생성합니다.',
          '왼쪽 패널에서 노드를 드래그하여 캔버스에 추가합니다.',
          '사용 가능한 노드: 트리거(업로드 완료, 예약 시간 등), 작업(AI 처리, 플랫폼 업로드 등), 조건(성과 기준 분기 등)',
          '각 노드를 클릭하여 상세 설정을 구성합니다.',
        ],
      },
      {
        subtitle: '연결선',
        text: '노드 간의 실행 순서와 데이터 흐름을 연결선으로 정의합니다.',
        items: [
          '노드의 출력 포트에서 다음 노드의 입력 포트로 드래그하여 연결합니다.',
          '조건 노드에서는 "예/아니오" 분기를 각각 다른 노드에 연결할 수 있습니다.',
          '병렬 실행을 위해 하나의 출력에서 여러 노드로 연결할 수 있습니다.',
          '연결선을 클릭하면 데이터 매핑을 설정할 수 있습니다.',
        ],
      },
      {
        subtitle: '템플릿',
        text: '자주 사용하는 워크플로우를 템플릿으로 저장하고 재사용할 수 있습니다.',
        items: [
          '기본 제공 템플릿: 멀티 플랫폼 자동 업로드, AI 메타데이터 생성 후 업로드, 성과 기반 자동 리포스트 등',
          '내 워크플로우를 템플릿으로 저장하여 재사용할 수 있습니다.',
          '팀원과 템플릿을 공유할 수 있습니다.',
        ],
      },
      {
        subtitle: '테스트 실행',
        text: '워크플로우를 실제 적용하기 전에 테스트할 수 있습니다.',
        items: [
          '"테스트 실행" 버튼으로 워크플로우를 시뮬레이션합니다.',
          '각 노드의 실행 결과와 데이터 흐름을 실시간으로 확인합니다.',
          '오류 발생 시 해당 노드에 빨간색 표시로 원인을 안내합니다.',
          '테스트 통과 후 "활성화" 버튼으로 워크플로우를 실제 적용합니다.',
        ],
      },
    ],
  },
  {
    id: 'ai-video-resizer',
    title: 'AI 비디오 리사이저',
    icon: ScissorsIcon,
    content: [
      {
        subtitle: '개요',
        text: 'AI 비디오 리사이저는 하나의 원본 영상을 각 플랫폼에 최적화된 비율과 해상도로 자동 변환하는 기능입니다. AI가 피사체를 인식하여 스마트 크롭을 수행합니다.',
      },
      {
        subtitle: '플랫폼 프리셋',
        text: '각 플랫폼의 권장 사양에 맞는 프리셋을 제공합니다.',
        items: [
          'YouTube: 16:9 (1920x1080), Shorts 9:16 (1080x1920)',
          'TikTok: 9:16 (1080x1920)',
          'Instagram Reels: 9:16 (1080x1920), 피드 1:1 (1080x1080), 피드 4:5 (1080x1350)',
          'Naver Clip: 9:16 (1080x1920)',
          '커스텀 비율과 해상도를 직접 설정할 수도 있습니다.',
        ],
      },
      {
        subtitle: '스마트 크롭',
        text: 'AI가 영상 내 피사체(얼굴, 사물 등)를 인식하여 중요한 부분이 잘리지 않도록 자동으로 크롭 영역을 조정합니다.',
        items: [
          '얼굴 인식: 인물이 포함된 영상에서 얼굴이 항상 프레임 내에 위치하도록 합니다.',
          '모션 트래킹: 움직이는 피사체를 따라 크롭 영역이 자동으로 이동합니다.',
          '수동 조정: AI의 크롭 결과를 미리보기에서 확인하고 수동으로 조정할 수 있습니다.',
        ],
      },
      {
        subtitle: '작업 관리',
        text: '리사이즈 작업을 효율적으로 관리할 수 있습니다.',
        items: [
          '여러 프리셋을 선택하여 한 번에 일괄 리사이즈를 실행할 수 있습니다.',
          '작업 대기열에서 진행 상황을 실시간으로 확인합니다.',
          '완료된 리사이즈 영상을 바로 플랫폼에 업로드할 수 있습니다.',
          '작업 이력에서 이전 리사이즈 결과를 다시 다운로드할 수 있습니다.',
        ],
      },
    ],
  },
  {
    id: 'subtitle-editor',
    title: '자막 에디터',
    icon: LanguageIcon,
    content: [
      {
        subtitle: '개요',
        text: '자막 에디터는 AI 자동 자막 생성부터 타임라인 기반 편집, 다양한 형식 내보내기까지 자막 작업의 전 과정을 지원하는 전문 도구입니다.',
      },
      {
        subtitle: 'AI 자막 생성',
        text: 'OpenAI Whisper STT를 활용하여 영상의 음성을 자동으로 자막으로 변환합니다.',
        steps: [
          '자막 에디터에서 대상 영상을 선택합니다.',
          '자막 언어(한국어, 영어 등)를 선택합니다.',
          '"AI 자막 생성" 버튼을 클릭합니다 (5 크레딧 소모).',
          '생성이 완료되면 타임라인에 자막이 자동으로 배치됩니다.',
        ],
      },
      {
        subtitle: '편집',
        text: '생성된 자막을 타임라인 기반 에디터에서 정밀하게 편집할 수 있습니다.',
        items: [
          '타임라인에서 자막 블록을 드래그하여 시작/종료 시간을 조정합니다.',
          '자막 텍스트를 직접 수정하거나 분할/병합할 수 있습니다.',
          '영상 미리보기와 동기화하여 자막 타이밍을 실시간 확인합니다.',
          '글꼴, 크기, 색상, 위치 등 스타일을 설정할 수 있습니다.',
          '단축키를 활용하여 빠르게 편집할 수 있습니다.',
        ],
      },
      {
        subtitle: '내보내기 (SRT/VTT/ASS)',
        text: '편집 완료된 자막을 다양한 형식으로 내보낼 수 있습니다.',
        items: [
          'SRT: 가장 범용적인 자막 형식, 대부분의 플랫폼에서 지원합니다.',
          'VTT (WebVTT): 웹 표준 자막 형식, HTML5 비디오와 호환됩니다.',
          'ASS (Advanced SubStation Alpha): 스타일링이 풍부한 자막 형식, 복잡한 디자인이 가능합니다.',
          '내보내기 시 인코딩(UTF-8, EUC-KR 등)을 선택할 수 있습니다.',
        ],
      },
    ],
  },
  {
    id: 'competitor-analysis',
    title: '경쟁사 심층 분석',
    icon: MagnifyingGlassCircleIcon,
    content: [
      {
        subtitle: '개요',
        text: '경쟁사 심층 분석은 경쟁 채널의 콘텐츠 전략, 성과 트렌드, 미발굴 주제 등을 심층적으로 분석하여 콘텐츠 차별화 전략을 수립할 수 있는 기능입니다.',
      },
      {
        subtitle: '경쟁자 추가',
        text: '분석할 경쟁 채널을 추가하고 관리합니다.',
        steps: [
          '경쟁사 분석 페이지에서 "경쟁자 추가" 버튼을 클릭합니다.',
          '경쟁 채널의 URL 또는 이름으로 검색합니다.',
          '분석 대상 채널을 선택하고 추가합니다.',
          '최대 10개 채널까지 경쟁자로 등록할 수 있습니다.',
        ],
      },
      {
        subtitle: '콘텐츠 갭 분석',
        text: '경쟁 채널이 다루고 있지만 내 채널에서는 아직 다루지 않은 주제(콘텐츠 갭)를 AI가 분석합니다.',
        items: [
          '경쟁 채널의 인기 주제와 내 채널의 주제를 비교합니다.',
          '미발굴 주제에 대한 콘텐츠 제안을 제공합니다.',
          '예상 성과와 경쟁 강도를 함께 표시합니다.',
          '추천 주제를 바로 콘텐츠 캘린더에 추가할 수 있습니다.',
        ],
      },
      {
        subtitle: '트렌드 토픽',
        text: '카테고리 내 트렌드 토픽과 급상승 주제를 실시간으로 모니터링합니다.',
        items: [
          '현재 급상승 중인 주제와 키워드를 실시간으로 표시합니다.',
          '트렌드 시작 시점과 예상 지속 기간을 AI가 분석합니다.',
          '트렌드 주제에 대한 경쟁 채널의 콘텐츠 발행 현황을 확인합니다.',
          '얼리 어답터 기회: 아직 경쟁이 적은 새로운 트렌드를 우선 추천합니다.',
        ],
      },
    ],
  },
  {
    id: 'brand-voice',
    title: 'AI 브랜드 보이스 엔진',
    icon: ChatBubbleBottomCenterTextIcon,
    content: [
      {
        subtitle: '개요',
        text: '나만의 브랜드 톤앤매너를 AI로 학습시키고, 일관된 브랜드 보이스로 콘텐츠를 생성하세요.',
      },
      {
        subtitle: '보이스 프로필 관리',
        text: '기존 콘텐츠를 기반으로 AI가 작성 스타일을 학습합니다.',
        items: [
          '프로필 훈련: 기존 콘텐츠 텍스트를 입력하여 AI가 작성 스타일을 학습합니다.',
          '톤 설정: 캐주얼, 전문적, 유머러스, 교육적, 영감 5가지 톤 중 선택합니다.',
          '어휘 관리: 자주 사용할 단어와 피해야 할 단어를 설정합니다.',
          '이모지 설정: 없음/최소/보통/많음 4단계로 이모지 사용 빈도를 조절합니다.',
        ],
      },
      {
        subtitle: 'AI 텍스트 생성',
        text: '학습된 보이스 프로필을 선택하고 주제를 입력하면 브랜드 톤에 맞는 텍스트를 생성합니다.',
        items: [
          '플랫폼별 최적화 (YouTube, TikTok, Instagram, Naver Clip)를 지원합니다.',
          '해시태그 자동 생성 옵션을 제공합니다.',
          '신뢰도 점수로 브랜드 일관성을 확인할 수 있습니다.',
        ],
      },
      {
        subtitle: '텍스트 분석',
        text: '기존 텍스트를 붙여넣으면 톤, 문장 길이, 자주 쓰는 단어, 이모지 빈도 등을 분석합니다.',
        items: [
          '가독성 점수와 격식 점수를 제공합니다.',
        ],
      },
      {
        subtitle: '크레딧 비용',
        text: '텍스트 생성 3크레딧, 분석 2크레딧이 소모됩니다.',
      },
    ],
  },
  {
    id: 'cross-analytics',
    title: '크로스플랫폼 분석',
    icon: ArrowsPointingOutIcon,
    content: [
      {
        subtitle: '개요',
        text: '여러 플랫폼에 업로드한 같은 콘텐츠의 성과를 한눈에 비교 분석하세요.',
      },
      {
        subtitle: '개요 대시보드',
        text: '플랫폼별 성과를 한눈에 파악할 수 있습니다.',
        items: [
          '플랫폼별 총 조회수/좋아요 요약 카드를 제공합니다.',
          '플랫폼 점유율 차트: 각 플랫폼이 전체 조회수에서 차지하는 비중을 시각화합니다.',
          'AI 추천 전략: 데이터 기반의 플랫폼별 최적화 제안을 받을 수 있습니다.',
        ],
      },
      {
        subtitle: '콘텐츠 비교',
        text: '같은 콘텐츠의 플랫폼별 성과를 표로 비교합니다.',
        items: [
          '조회수, 좋아요, CTR을 플랫폼별로 나란히 표시합니다.',
          '지표별 최고 성과 플랫폼을 자동으로 하이라이트합니다.',
        ],
      },
      {
        subtitle: '오디언스 중복 분석',
        text: '플랫폼 쌍별 오디언스 중복률을 시각화합니다.',
        items: [
          '중복률이 높은 플랫폼은 차별화 전략을 추천합니다.',
          '중복률이 낮은 플랫폼은 크로스 프로모션을 추천합니다.',
        ],
      },
      {
        subtitle: '기간 설정',
        text: '7일, 30일, 90일 단위로 분석 기간을 선택할 수 있습니다.',
      },
    ],
  },
  {
    id: 'growth-coach',
    title: '크리에이터 성장 코치',
    icon: AcademicCapIcon,
    content: [
      {
        subtitle: '개요',
        text: 'AI 기반 성장 코칭으로 채널 성장을 가속화하세요.',
        items: [
          '종합 점수: 채널 성장 상태를 0-100점으로 한눈에 파악합니다.',
          '성장 지표: 구독자, 조회수, 참여율 변화를 실시간으로 추적합니다.',
          '우선 인사이트: 가장 영향력 높은 인사이트 3개를 바로 확인합니다.',
        ],
      },
      {
        subtitle: '목표 관리',
        text: '구독자, 조회수, 참여율, 수익, 업로드 빈도 등 목표를 설정합니다.',
        items: [
          '진행률을 시각적 프로그레스 바로 확인합니다.',
          '마감일까지 남은 일수를 자동 계산합니다.',
        ],
      },
      {
        subtitle: '주간 리포트',
        text: 'AI가 매주 채널 성과를 자동 분석하여 리포트를 생성합니다.',
        items: [
          '하이라이트(좋은 점)와 우려사항(개선점)을 구분하여 표시합니다.',
          '실행 가능한 액션 아이템을 체크리스트로 제공합니다.',
          '과거 리포트와 비교하여 추세를 파악할 수 있습니다.',
        ],
      },
      {
        subtitle: 'AI 인사이트',
        text: '영향도(높음/보통/낮음)별로 필터링하여 인사이트를 확인합니다.',
        items: [
          '실행 가능한 인사이트에는 구체적인 추천 행동이 포함됩니다.',
        ],
      },
      {
        subtitle: '크레딧 비용',
        text: '리포트 생성 시 10크레딧이 소모됩니다.',
      },
    ],
  },
  {
    id: 'copyright-check',
    title: '저작권 사전검증',
    icon: ShieldCheckIcon,
    content: [
      {
        subtitle: '개요',
        text: '영상 업로드 전에 저작권 이슈를 미리 확인하고 수익화 영향을 예측하세요.',
      },
      {
        subtitle: '영상 선택 및 검사',
        text: '업로드 예정 영상 목록에서 검사할 영상을 선택합니다.',
        items: [
          '음악 검사: BGM의 저작권 상태를 확인합니다.',
          '콘텐츠 검사: 이미지, 영상 내 콘텐츠 정책 위반을 확인합니다.',
          '브랜드 검사: 브랜드 로고, 상표 노출을 감지합니다.',
        ],
      },
      {
        subtitle: '플랫폼별 검증',
        text: 'YouTube, TikTok, Instagram, Naver Clip 각 플랫폼의 정책에 맞춰 검증합니다.',
        items: [
          '플랫폼별 통과/경고/차단 상태를 한눈에 확인할 수 있습니다.',
        ],
      },
      {
        subtitle: '검사 결과',
        text: '검사 결과를 3단계로 분류하여 표시합니다.',
        items: [
          '통과(PASSED): 모든 검사 항목 문제 없음 (녹색)',
          '경고(WARNING): 일부 이슈가 있으나 업로드 가능 (노란색)',
          '차단(BLOCKED): 심각한 저작권 이슈로 업로드 시 제재 가능 (빨간색)',
        ],
      },
      {
        subtitle: '이슈 상세',
        text: '각 이슈의 상세 정보를 확인하고 해결할 수 있습니다.',
        items: [
          '각 이슈의 심각도(정보/경고/심각)를 색상으로 구분합니다.',
          '발생 시간 구간을 표시합니다 (음악의 경우).',
          'AI 제안 해결 방법을 제공합니다.',
          '자동 수정: 일부 이슈는 AI가 자동으로 수정할 수 있습니다.',
        ],
      },
      {
        subtitle: '수익화 적격성',
        text: '검사 결과에 따른 수익화 가능 여부를 표시합니다.',
      },
      {
        subtitle: '크레딧 비용',
        text: '검사 1회당 3크레딧이 소모됩니다.',
      },
    ],
  },
  {
    id: 'ab-test-manager',
    title: 'A/B 테스트',
    icon: BeakerIcon,
    content: [
      {
        subtitle: '개요',
        text: 'A/B 테스트 매니저는 영상의 썸네일, 제목, 설명 등 메타데이터의 서로 다른 변형을 비교 테스트하여 최적의 조합을 데이터 기반으로 결정할 수 있는 기능입니다.',
      },
      {
        subtitle: '테스트 생성',
        text: '새로운 A/B 테스트를 생성합니다.',
        steps: [
          'A/B 테스트 페이지에서 "새 테스트 생성" 버튼을 클릭합니다.',
          '테스트 대상 영상을 선택합니다.',
          '테스트 유형을 선택합니다 (썸네일, 제목, 설명).',
          '각 변형(A, B, 최대 4개)의 내용을 입력합니다.',
          '테스트 기간과 성공 지표(CTR, 조회수, 참여율 등)를 설정합니다.',
          '"테스트 시작" 버튼으로 테스트를 시작합니다.',
        ],
      },
      {
        subtitle: '변형 비교',
        text: '진행 중인 테스트의 각 변형 성과를 실시간으로 비교합니다.',
        items: [
          '각 변형의 노출수, 클릭수, CTR, 조회수 등을 실시간 차트로 비교합니다.',
          '통계적 유의성(Confidence Level)을 자동 계산하여 표시합니다.',
          '충분한 데이터가 수집되면 AI가 자동으로 우승 변형을 판별합니다.',
          '테스트 중간에도 각 변형의 성과 추이를 모니터링할 수 있습니다.',
        ],
      },
      {
        subtitle: '승자 적용',
        text: '테스트가 완료되면 가장 성과가 좋은 변형을 적용합니다.',
        items: [
          'AI가 우승 변형을 자동으로 추천합니다 (통계적 유의성 기반).',
          '"승자 적용" 버튼으로 우승 변형을 해당 영상에 바로 적용합니다.',
          '테스트 결과 리포트를 저장하고 과거 테스트 이력을 조회할 수 있습니다.',
          '테스트 인사이트를 향후 콘텐츠 전략에 활용할 수 있습니다.',
        ],
      },
    ],
  },
  {
    id: 'thumbnail-generator',
    title: 'AI 썸네일 생성기',
    icon: PhotoIcon,
    content: [
      {
        subtitle: '개요',
        text: 'AI 썸네일 생성기는 영상 콘텐츠에 최적화된 고품질 썸네일을 AI가 자동으로 디자인하고 생성하는 기능입니다. 클릭률(CTR)을 극대화하는 시각적 요소를 분석하여 전문 디자이너 수준의 썸네일을 만들어 줍니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          'AI 자동 디자인: 영상 내용과 제목을 분석하여 최적의 썸네일 레이아웃을 자동 생성합니다.',
          '텍스트 오버레이: 제목, 부제목 등 텍스트를 자동 배치하며 가독성 높은 폰트와 색상을 추천합니다.',
          '배경 제거/합성: 인물 사진의 배경을 자동으로 제거하고 새로운 배경과 합성합니다.',
          '스타일 템플릿: 카테고리별(게임, 뷰티, 먹방, 교육 등) 인기 썸네일 스타일 템플릿을 제공합니다.',
          'CTR 예측: 생성된 썸네일의 예상 클릭률을 AI가 점수로 표시합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '영상 상세 페이지 또는 업로드 화면에서 "AI 썸네일 생성" 버튼을 클릭합니다.',
          '썸네일 스타일(모던, 임팩트, 미니멀 등)을 선택합니다.',
          '텍스트 오버레이 내용을 입력하거나 AI 자동 생성을 선택합니다.',
          'AI가 4~6개의 썸네일 후보를 생성합니다.',
          '원하는 썸네일을 선택하고 필요 시 미세 편집 후 적용합니다.',
        ],
      },
      {
        subtitle: '크레딧 비용',
        text: '썸네일 생성 1회당 5크레딧이 소모됩니다.',
      },
    ],
  },
  {
    id: 'performance-heatmap',
    title: '성과 히트맵',
    icon: TableCellsIcon,
    content: [
      {
        subtitle: '개요',
        text: '성과 히트맵은 요일(7일)과 시간대(24시간)를 기준으로 7x24 격자 형태의 히트맵을 제공하여, 콘텐츠 업로드에 가장 효과적인 시간대를 시각적으로 파악할 수 있는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '7x24 히트맵: 요일별, 시간대별 조회수/참여율/CTR 등 성과 지표를 색상 강도로 시각화합니다.',
          '플랫폼별 필터: YouTube, TikTok, Instagram, Naver Clip 등 플랫폼별로 개별 히트맵을 확인할 수 있습니다.',
          '지표 전환: 조회수, 좋아요, 댓글, CTR, 참여율 등 원하는 지표를 선택하여 히트맵을 전환합니다.',
          '기간 설정: 최근 7일, 30일, 90일, 1년 등 분석 기간을 자유롭게 설정합니다.',
          '최적 시간 추천: AI가 데이터를 분석하여 최적의 업로드 시간 Top 5를 자동 추천합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '분석 메뉴에서 "성과 히트맵"을 클릭합니다.',
          '분석할 플랫폼과 지표를 선택합니다.',
          '히트맵에서 색이 진한 셀(높은 성과 시간대)을 확인합니다.',
          '원하는 셀을 클릭하면 해당 시간대의 상세 성과 데이터를 볼 수 있습니다.',
          'AI 추천 시간을 참고하여 예약 업로드를 설정합니다.',
        ],
      },
    ],
  },
  {
    id: 'live-dashboard',
    title: '실시간 대시보드',
    icon: BoltIcon,
    content: [
      {
        subtitle: '개요',
        text: '실시간 대시보드는 모든 연동 플랫폼의 핵심 지표를 실시간으로 모니터링하고, 이상 징후 발생 시 즉각적인 알림을 받을 수 있는 통합 모니터링 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '실시간 지표: 조회수, 구독자, 좋아요, 댓글 수 등 핵심 지표가 자동으로 갱신됩니다.',
          '자동 새로고침: 30초/1분/5분 간격으로 데이터가 자동 갱신되며 간격을 직접 설정할 수 있습니다.',
          '알림 시스템: 구독자 급증, 조회수 이상 변동, 부정 댓글 급증 등 이상 징후 발생 시 즉시 알림을 전송합니다.',
          '맞춤 위젯: 대시보드에 표시할 위젯을 자유롭게 추가/제거/배치할 수 있습니다.',
          '멀티채널 뷰: 여러 채널의 지표를 하나의 화면에서 동시에 모니터링합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "실시간 대시보드"를 클릭합니다.',
          '모니터링할 채널과 지표를 선택합니다.',
          '알림 조건(임계값)을 설정합니다 (예: 구독자 1시간 내 100명 이상 증가 시 알림).',
          '대시보드 레이아웃을 자유롭게 커스터마이징합니다.',
          '실시간 데이터 스트림이 자동으로 시작됩니다.',
        ],
      },
    ],
  },
  {
    id: 'revenue-forecaster',
    title: '수익 예측기',
    icon: CurrencyDollarIcon,
    content: [
      {
        subtitle: '개요',
        text: '수익 예측기는 채널의 과거 수익 데이터와 성장 트렌드를 AI가 분석하여, 향후 예상 수익을 예측하고 수익 최적화 전략을 제안하는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          'AI 수익 예측: 과거 수익 패턴, 구독자 성장률, 콘텐츠 발행 빈도 등을 종합 분석하여 1개월/3개월/6개월/1년 후 예상 수익을 산출합니다.',
          '시나리오 분석: 낙관적/보통/비관적 3가지 시나리오별 예상 수익을 비교할 수 있습니다.',
          '수익원 분류: 광고 수익, 후원/슈퍼챗, 멤버십, 커머스 등 수익원별 예측을 제공합니다.',
          '성장 요인 분석: 수익에 가장 큰 영향을 미치는 요인(업로드 빈도, 참여율, 구독자 등)을 분석합니다.',
          '최적화 제안: 수익을 극대화하기 위한 구체적인 액션 아이템을 AI가 추천합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '분석 메뉴에서 "수익 예측기"를 클릭합니다.',
          '예측 기간(1개월~1년)을 선택합니다.',
          '예측에 포함할 수익원을 선택합니다.',
          'AI가 수익 예측 리포트를 생성합니다.',
          '시나리오별 예측 결과와 최적화 제안을 확인합니다.',
        ],
      },
      {
        subtitle: '크레딧 비용',
        text: '수익 예측 리포트 생성 1회당 8크레딧이 소모됩니다.',
      },
    ],
  },
  {
    id: 'content-rewriter',
    title: 'AI 콘텐츠 리라이터',
    icon: DocumentTextIcon,
    content: [
      {
        subtitle: '개요',
        text: 'AI 콘텐츠 리라이터는 하나의 콘텐츠를 여러 플랫폼에 맞게 자동으로 변환하는 기능입니다. 각 플랫폼의 특성(글자 수 제한, 해시태그 스타일, 톤 등)에 최적화된 텍스트를 생성합니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '플랫폼 최적화: YouTube 설명, TikTok 캡션, Instagram 캡션, Naver 블로그 등 각 플랫폼에 맞는 포맷으로 자동 변환합니다.',
          '톤 변환: 같은 내용을 전문적, 캐주얼, 유머러스, 교육적 등 다양한 톤으로 변환합니다.',
          '길이 조절: 숏폼(30자 이내), 미디엄(100자), 롱폼(500자 이상) 등 원하는 길이로 조절합니다.',
          '해시태그 자동 생성: 플랫폼별 최적의 해시태그를 자동으로 추가합니다.',
          '다국어 변환: 한국어 콘텐츠를 영어, 일본어 등 다른 언어로 자동 변환합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          'AI 도구 메뉴에서 "콘텐츠 리라이터"를 클릭합니다.',
          '원본 텍스트를 입력하거나 기존 영상의 설명을 불러옵니다.',
          '변환할 대상 플랫폼을 선택합니다 (복수 선택 가능).',
          '톤과 길이 옵션을 설정합니다.',
          '"변환하기" 버튼을 클릭하면 플랫폼별 최적화된 텍스트가 생성됩니다.',
          '생성 결과를 확인하고 편집 후 바로 적용할 수 있습니다.',
        ],
      },
      {
        subtitle: '크레딧 비용',
        text: '콘텐츠 변환 1회당 3크레딧이 소모됩니다 (플랫폼 수에 관계없이 동일).',
      },
    ],
  },
  {
    id: 'social-listening',
    title: '소셜 리스닝',
    icon: MegaphoneIcon,
    content: [
      {
        subtitle: '개요',
        text: '소셜 리스닝은 소셜 미디어 전반에서 브랜드 멘션(언급)을 모니터링하고, 감성 분석을 통해 여론 동향을 실시간으로 파악할 수 있는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '브랜드 멘션 모니터링: 채널명, 브랜드명, 관련 키워드가 언급된 게시물을 실시간으로 수집합니다.',
          '감성 분석: AI가 멘션의 감성(긍정/중립/부정)을 자동으로 분류하고 비율을 시각화합니다.',
          '트렌드 추적: 멘션 볼륨의 시계열 변화를 추적하여 급증/급감 시점을 감지합니다.',
          '경쟁사 비교: 경쟁 채널의 멘션 볼륨 및 감성과 비교 분석합니다.',
          '알림 설정: 부정 멘션 급증, 특정 키워드 감지 등 조건별 알림을 설정할 수 있습니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '분석 메뉴에서 "소셜 리스닝"을 클릭합니다.',
          '모니터링할 키워드(채널명, 브랜드명 등)를 등록합니다.',
          '모니터링 범위(플랫폼, 기간)를 설정합니다.',
          '대시보드에서 멘션 현황, 감성 분포, 트렌드 차트를 확인합니다.',
          '개별 멘션을 클릭하여 원문을 확인하고 직접 대응할 수 있습니다.',
        ],
      },
    ],
  },
  {
    id: 'influencer-match',
    title: '인플루언서 매칭',
    icon: UserPlusIcon,
    content: [
      {
        subtitle: '개요',
        text: '인플루언서 매칭은 AI가 채널 데이터, 콘텐츠 유형, 오디언스 특성 등을 분석하여 최적의 협업 파트너를 자동으로 추천하는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          'AI 매칭 알고리즘: 카테고리, 오디언스 규모, 참여율, 콘텐츠 스타일 등을 종합 분석하여 호환성 점수를 산출합니다.',
          '오디언스 겹침 분석: 두 채널의 시청자 중복률을 분석하여 시너지 효과를 예측합니다.',
          '카테고리 필터: 뷰티, 게임, 먹방, 교육, 테크 등 카테고리별로 필터링하여 검색합니다.',
          '협업 이력 관리: 과거 협업 파트너의 성과를 기록하고 추적합니다.',
          '연락처 관리: 관심 인플루언서의 연락처와 협업 메모를 관리합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "인플루언서 매칭"을 클릭합니다.',
          '원하는 협업 조건(카테고리, 구독자 범위, 플랫폼 등)을 설정합니다.',
          'AI가 조건에 맞는 인플루언서 목록을 호환성 점수 순으로 추천합니다.',
          '추천된 인플루언서의 상세 프로필과 채널 통계를 확인합니다.',
          '관심 인플루언서를 즐겨찾기에 추가하고 협업을 제안합니다.',
        ],
      },
      {
        subtitle: '크레딧 비용',
        text: '인플루언서 검색 1회당 5크레딧이 소모됩니다.',
      },
    ],
  },
  {
    id: 'quality-score',
    title: '콘텐츠 퀄리티 스코어',
    icon: ClipboardDocumentCheckIcon,
    content: [
      {
        subtitle: '개요',
        text: '콘텐츠 퀄리티 스코어는 영상 업로드 전에 AI가 콘텐츠의 품질을 종합적으로 평가하여 0~100점의 점수를 부여하고, 개선 포인트를 제안하는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '종합 품질 점수: 영상, 오디오, 메타데이터, SEO 등 다방면에서 평가하여 0~100점의 종합 점수를 산출합니다.',
          '카테고리별 세부 점수: 영상 품질(해상도, FPS, 안정성), 오디오 품질(음량, 잡음), 메타데이터(제목, 설명, 태그), SEO 최적화 등 카테고리별 점수를 제공합니다.',
          '개선 제안: AI가 점수가 낮은 항목에 대해 구체적인 개선 방법을 제안합니다.',
          '벤치마크 비교: 같은 카테고리 상위 크리에이터의 평균 점수와 비교합니다.',
          '이력 추적: 과거 콘텐츠의 퀄리티 점수 변화 추이를 확인할 수 있습니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '영상 상세 페이지 또는 업로드 화면에서 "퀄리티 스코어" 버튼을 클릭합니다.',
          'AI가 영상 파일과 메타데이터를 종합 분석합니다.',
          '종합 점수와 카테고리별 세부 점수를 확인합니다.',
          '개선 제안을 참고하여 콘텐츠를 보완합니다.',
          '수정 후 재검사하여 점수 향상을 확인합니다.',
        ],
      },
      {
        subtitle: '크레딧 비용',
        text: '퀄리티 스코어 검사 1회당 3크레딧이 소모됩니다.',
      },
    ],
  },
  {
    id: 'content-series',
    title: '콘텐츠 시리즈 매니저',
    icon: RectangleStackIcon,
    content: [
      {
        subtitle: '개요',
        text: '콘텐츠 시리즈 매니저는 시리즈 형태의 콘텐츠를 체계적으로 기획, 관리, 분석할 수 있는 기능입니다. 에피소드별 성과를 추적하고 시리즈 전체의 트렌드를 한눈에 파악할 수 있습니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '시리즈 생성 및 관리: 시리즈 제목, 설명, 플랫폼, 업로드 주기(일간/주간/격주/월간) 등을 설정하여 시리즈를 생성하고 관리합니다.',
          '에피소드 추적: 각 에피소드의 기획(PLANNED), 초안(DRAFTED), 게시(PUBLISHED), 건너뜀(SKIPPED) 상태를 추적합니다.',
          '시리즈 분석: 에피소드별 조회수 트렌드, 구독자 성장률, 평균 참여율, 이탈률 등 핵심 지표를 분석합니다.',
          '베스트 에피소드: 가장 성과가 좋은 에피소드를 자동으로 식별하여 보여줍니다.',
          '태그 관리: 시리즈에 태그를 추가하여 분류하고 빠르게 검색할 수 있습니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "시리즈 매니저"를 클릭합니다.',
          '"새 시리즈 만들기" 버튼을 클릭하여 시리즈 정보를 입력합니다.',
          '생성된 시리즈에 에피소드를 추가하고 일정을 설정합니다.',
          '에피소드 게시 후 시리즈 분석 패널에서 성과를 확인합니다.',
          '시리즈 상태를 활성(ACTIVE), 일시중지(PAUSED), 완료(COMPLETED)로 변경할 수 있습니다.',
        ],
      },
    ],
  },
  {
    id: 'fan-funding',
    title: '팬 펀딩 트래커',
    icon: HeartIcon,
    content: [
      {
        subtitle: '개요',
        text: '팬 펀딩 트래커는 슈퍼챗, 멤버십, 슈퍼땡스, 틱톡 선물, 인스타그램 배지, 네이버 별풍선 등 다양한 플랫폼의 팬 후원 수익을 통합 관리하고 분석하는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '통합 수익 대시보드: 전체 수익, 이번 달/지난 달 수익, 성장률 등 핵심 지표를 한눈에 확인합니다.',
          '거래 내역 관리: 소스(슈퍼챗, 멤버십 등)와 플랫폼별로 거래 내역을 필터링하여 조회합니다.',
          '일별 트렌드: 일별 수익 변화 추이를 차트로 시각화합니다.',
          '상위 후원자: 가장 많이 후원한 팬 순위를 확인합니다.',
          '펀딩 목표 설정: 수익 목표를 설정하고 달성률을 실시간으로 추적합니다.',
          '기간별 조회: 7일/30일/90일 단위로 기간을 선택하여 분석합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "팬 펀딩 트래커"를 클릭합니다.',
          '개요 탭에서 전체 수익 현황과 트렌드를 확인합니다.',
          '거래 내역 탭에서 소스와 플랫폼별로 필터링하여 상세 내역을 조회합니다.',
          '목표 탭에서 "새 목표" 버튼을 클릭하여 펀딩 목표를 설정합니다.',
          '목표 달성률을 모니터링하고 필요 시 목표를 수정하거나 삭제합니다.',
        ],
      },
    ],
  },
  {
    id: 'media-kit',
    title: '미디어 킷 생성기',
    icon: DocumentArrowUpIcon,
    content: [
      {
        subtitle: '개요',
        text: '미디어 킷 생성기는 AI가 채널 데이터를 분석하여 브랜드 협업용 전문 미디어 킷을 자동으로 생성하는 기능입니다. 플랫폼 통계, 시청자 인구통계, 캠페인 실적, 광고 단가표 등을 포함한 미디어 킷을 만들 수 있습니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '템플릿 선택: 모던, 클래식, 미니멀, 크리에이티브 4가지 스타일의 템플릿을 제공합니다.',
          '플랫폼 통계 자동 수집: 각 연결된 플랫폼의 팔로워 수, 평균 조회수, 참여율, 성장률을 자동으로 수집합니다.',
          '광고 단가표(Rate Card): 협찬 영상, 제품 배치, 스토리, 숏폼, 라이브, 번들 등 유형별 단가를 설정합니다.',
          '캠페인 실적: 과거 브랜드 협업 실적(조회수, 참여율, ROI)을 포함할 수 있습니다.',
          '발행 및 공유: 미디어 킷을 온라인으로 발행하고 공유 링크를 생성합니다.',
          'PDF 다운로드: 미디어 킷을 PDF로 다운로드하여 브랜드에 직접 전달할 수 있습니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "미디어 킷 생성기"를 클릭합니다.',
          '원하는 템플릿 스타일을 선택합니다.',
          '광고 단가표와 캠페인 실적 포함 여부를 설정합니다.',
          '"미디어 킷 생성" 버튼을 클릭하면 AI가 자동으로 킷을 생성합니다.',
          '생성된 킷을 편집하고 "발행" 버튼을 클릭하여 공유 링크를 생성합니다.',
        ],
      },
      {
        subtitle: '크레딧 비용',
        text: '미디어 킷 생성 1회당 5크레딧이 소모됩니다.',
      },
    ],
  },
  {
    id: 'smart-reply',
    title: '스마트 리플라이',
    icon: ChatBubbleBottomCenterTextIcon,
    content: [
      {
        subtitle: '개요',
        text: '스마트 리플라이는 AI가 댓글, DM, 멘션 등에 대한 최적의 답변을 자동으로 제안하고, 규칙 기반 자동 답변을 설정할 수 있는 기능입니다. 팬과의 소통을 효율적으로 관리할 수 있습니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          'AI 답변 제안: 댓글의 감성(긍정/부정/중립)을 분석하고 상황에 맞는 답변을 여러 톤(친근/전문/캐주얼/감사/유머)으로 제안합니다.',
          '답변 규칙 설정: 특정 키워드가 포함된 댓글에 자동으로 답변하는 규칙을 생성합니다.',
          '자동 발송: 규칙에 따라 답변을 자동으로 발송하거나, 수동 확인 후 발송하도록 설정할 수 있습니다.',
          '통계 대시보드: 총 답변 수, 평균 응답 시간, 감성 분포, 플랫폼별 답변 현황 등 통계를 확인합니다.',
          '설정 관리: 기본 톤, 일일 자동 답변 한도, 제외 키워드, 답변 지연 시간 등을 커스터마이징합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "스마트 리플라이"를 클릭합니다.',
          '제안 탭에서 AI가 생성한 답변 제안을 확인하고 적합한 답변을 선택하여 발송합니다.',
          '규칙 탭에서 "새 규칙" 버튼을 클릭하여 자동 답변 규칙을 생성합니다.',
          '통계 탭에서 답변 현황과 팬 반응을 모니터링합니다.',
          '설정에서 기본 톤과 자동 답변 옵션을 조정합니다.',
        ],
      },
      {
        subtitle: '크레딧 비용',
        text: 'AI 답변 제안 1건당 1크레딧이 소모됩니다. 규칙 기반 답변은 크레딧이 소모되지 않습니다.',
      },
    ],
  },
  {
    id: 'audience-segments',
    title: '오디언스 세분화',
    icon: UsersIcon,
    content: [
      {
        subtitle: '개요',
        text: '오디언스 세분화는 구독자와 시청자를 다양한 기준(연령, 성별, 지역, 관심사, 행동 패턴)으로 분류하여 각 그룹에 최적화된 콘텐츠 전략을 수립할 수 있는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '자동 세그먼트: AI가 시청 데이터를 분석하여 연령, 성별, 지역, 관심사, 행동 패턴 기반 세그먼트를 자동 생성합니다.',
          '커스텀 세그먼트: 원하는 조건을 조합하여 직접 세그먼트를 만들 수 있습니다.',
          '세그먼트 인사이트: 각 세그먼트의 시청 패턴, 참여율, 성장률, 선호 콘텐츠 유형 등 상세 분석을 제공합니다.',
          '세그먼트 비교: 두 개 이상의 세그먼트를 선택하여 주요 지표를 비교 분석할 수 있습니다.',
          '타겟 콘텐츠 추천: 각 세그먼트에 가장 효과적인 콘텐츠 주제, 업로드 시간, 썸네일 스타일 등을 추천합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "오디언스 세분화"를 클릭합니다.',
          '그리드 뷰에서 기존 세그먼트 목록과 각 세그먼트의 규모, 참여율을 확인합니다.',
          '"새 세그먼트" 버튼을 클릭하여 커스텀 세그먼트를 생성합니다.',
          '세그먼트를 클릭하면 상세 인사이트 패널이 열립니다.',
          '비교 탭에서 여러 세그먼트를 선택하여 비교 분석합니다.',
        ],
      },
    ],
  },
  {
    id: 'content-rights',
    title: '콘텐츠 저작권 관리',
    icon: ShieldExclamationIcon,
    content: [
      {
        subtitle: '개요',
        text: '콘텐츠 저작권 관리는 업로드된 콘텐츠에 사용된 음악, 이미지, 영상 소스 등의 저작권을 체계적으로 추적하고 관리하는 기능입니다. 라이선스 만료 알림, 대체 에셋 추천 등을 제공합니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '저작권 등록: 콘텐츠에 사용된 에셋(음악, 이미지, 폰트, 영상 클립)의 라이선스 정보를 등록하고 관리합니다.',
          '만료 알림: 라이선스 만료가 임박하면 자동으로 알림을 발송하여 사전에 대응할 수 있습니다.',
          '위험도 분석: 각 에셋의 저작권 위험도(안전/주의/위험/만료)를 색상으로 구분하여 직관적으로 파악합니다.',
          '대체 에셋 추천: 저작권 문제가 있는 에셋에 대해 무료 또는 라이선스가 유효한 대체 에셋을 추천합니다.',
          '요약 대시보드: 전체 에셋 현황, 상태별 분포, 곧 만료되는 라이선스 등을 한눈에 확인합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "콘텐츠 저작권"을 클릭합니다.',
          '대시보드에서 전체 저작권 현황과 알림을 확인합니다.',
          '"새 저작권 등록" 버튼을 클릭하여 에셋의 라이선스 정보를 입력합니다.',
          '알림 탭에서 만료 예정이거나 위험한 에셋을 확인하고 조치합니다.',
          '대체 에셋 추천을 활용하여 안전한 에셋으로 교체합니다.',
        ],
      },
    ],
  },
  {
    id: 'creator-academy',
    title: '크리에이터 아카데미',
    icon: AcademicCapIcon,
    content: [
      {
        subtitle: '개요',
        text: '크리에이터 아카데미는 콘텐츠 제작, 편집, 마케팅, AI 도구 활용 등 크리에이터에게 필요한 교육 콘텐츠를 제공하는 학습 플랫폼입니다. 과정 수료 시 AI 크레딧을 보상으로 받을 수 있습니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '카테고리별 과정: 촬영, 편집, 마케팅, AI 도구, 수익화, 성장, 브랜딩, 분석 등 8개 카테고리의 강의를 제공합니다.',
          '난이도별 분류: 초급/중급/고급으로 구분되어 자신의 수준에 맞는 과정을 선택할 수 있습니다.',
          '학습 진도 관리: 수강 중인 과정의 진행률, 학습 연속일수, 총 시청 시간 등을 추적합니다.',
          '크레딧 보상: 과정을 수료하면 과정별로 정해진 AI 크레딧을 보상으로 받습니다.',
          '약점 분석: AI가 학습 데이터를 분석하여 부족한 영역과 추천 과정을 안내합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "크리에이터 아카데미"를 클릭합니다.',
          '상단 진도 대시보드에서 학습 현황을 확인합니다.',
          '카테고리 필터로 원하는 분야의 과정을 검색합니다.',
          '과정 카드를 클릭하여 상세 정보를 확인하고 수강 신청합니다.',
          '레슨을 완료하면 자동으로 진도가 업데이트되고, 과정 수료 시 크레딧이 지급됩니다.',
        ],
      },
    ],
  },
  {
    id: 'multi-brand-calendar',
    title: '다중 브랜드 캘린더',
    icon: CalendarDaysIcon,
    content: [
      {
        subtitle: '개요',
        text: '다중 브랜드 캘린더는 에이전시나 MCN에서 여러 브랜드/채널의 콘텐츠 스케줄을 한 곳에서 통합 관리할 수 있는 기능입니다. 브랜드별 색상 구분, 충돌 감지, 담당자 배정 등을 지원합니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '브랜드 관리: 여러 브랜드를 색상별로 구분하여 등록하고, 카테고리와 담당 에디터를 배정합니다.',
          '통합 캘린더: 모든 브랜드의 스케줄을 하나의 캘린더에서 확인하며, 브랜드별 필터링이 가능합니다.',
          '충돌 감지: 같은 시간대에 여러 콘텐츠가 겹치면 자동으로 충돌을 감지하고 경고를 표시합니다.',
          '스케줄 관리: 브랜드별 콘텐츠 업로드 스케줄을 생성, 수정, 삭제할 수 있습니다.',
          '상태 추적: 각 스케줄의 상태(초안/예약됨/게시 중/게시 완료/실패)를 실시간으로 추적합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "다중 브랜드 캘린더"를 클릭합니다.',
          '상단 요약 바에서 전체 브랜드 수, 활성 브랜드, 이번 주 예약 건수를 확인합니다.',
          '"새 브랜드" 버튼을 클릭하여 브랜드를 등록합니다.',
          '브랜드 필터로 특정 브랜드의 스케줄만 표시합니다.',
          '"새 스케줄" 버튼을 클릭하여 콘텐츠 업로드 일정을 생성합니다.',
          '충돌 알림이 표시되면 스케줄을 조정하여 겹치는 시간을 피합니다.',
        ],
      },
    ],
  },
  {
    id: 'script-writer',
    title: 'AI 스크립트 작성기',
    icon: PencilSquareIcon,
    content: [
      {
        subtitle: '개요',
        text: 'AI 스크립트 작성기는 영상 주제와 형식을 입력하면 AI가 자동으로 대본을 생성해주는 도구입니다. 훅, 인트로, 본문, CTA, 아웃트로 등 섹션별로 구조화된 대본을 제공합니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '자동 대본 생성: 주제, 형식(롱폼/숏폼/튜토리얼/리뷰/브이로그/인터뷰), 톤(캐주얼/전문/유머/교육/드라마틱/스토리텔링)을 선택하면 AI가 대본을 생성합니다.',
          '섹션 편집: 생성된 대본은 섹션별로 개별 편집이 가능하며, 특정 섹션만 재생성할 수 있습니다.',
          '예상 시간/단어 수: 목표 영상 길이에 맞춰 자동으로 단어 수가 조절됩니다.',
          '템플릿 저장: 자주 사용하는 대본 구조를 템플릿으로 저장하여 재사용할 수 있습니다.',
          '상태 관리: 초안 → 검토 중 → 최종 → 보관 상태로 대본의 진행 상황을 관리합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "AI 스크립트 작성기"를 클릭합니다.',
          '"새 스크립트 생성" 버튼을 클릭합니다.',
          '주제, 형식, 톤, 목표 길이, 키워드를 입력합니다.',
          'AI가 생성한 대본을 섹션별로 검토하고 필요시 편집합니다.',
          '완성된 대본의 상태를 "최종"으로 변경합니다.',
        ],
      },
      {
        subtitle: '크레딧 비용',
        text: '스크립트 생성 1건당 3크레딧이 소모됩니다. 섹션 재생성은 1크레딧입니다.',
      },
    ],
  },
  {
    id: 'collaboration-board',
    title: '협업 보드',
    icon: ViewColumnsIcon,
    content: [
      {
        subtitle: '개요',
        text: '협업 보드는 칸반 스타일의 프로젝트 관리 도구로, 콘텐츠 제작 파이프라인(아이디어 → 대본 → 촬영 → 편집 → 검토 → 예약 → 발행)을 시각적으로 관리합니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '칸반 보드: 7개 단계별 컬럼으로 콘텐츠 제작 흐름을 시각적으로 관리합니다.',
          '태스크 관리: 각 태스크에 우선순위, 담당자, 마감일, 태그, 첨부파일을 설정합니다.',
          '드래그 앤 드롭: 태스크를 다른 컬럼으로 이동하여 진행 상태를 업데이트합니다.',
          '활동 로그: 누가 어떤 태스크를 이동/생성/완료했는지 실시간으로 추적합니다.',
          '요약 통계: 전체 태스크 수, 완료율, 지연 태스크, 팀원별 현황을 확인합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "협업 보드"를 클릭합니다.',
          '각 컬럼 하단의 "+" 버튼을 클릭하여 태스크를 생성합니다.',
          '태스크 카드를 클릭하여 상세 정보를 확인하고 편집합니다.',
          '태스크를 다음 단계 컬럼으로 이동시켜 진행 상황을 업데이트합니다.',
          '요약 바에서 전체 프로젝트 현황을 모니터링합니다.',
        ],
      },
    ],
  },
  {
    id: 'sponsorship-tracker',
    title: '스폰서십 트래커',
    icon: GiftIcon,
    content: [
      {
        subtitle: '개요',
        text: '스폰서십 트래커는 브랜드 스폰서십 딜을 체계적으로 관리하는 도구입니다. 딜 금액, 진행 상태, 산출물 관리, 결제 추적 등을 한 곳에서 관리합니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '딜 파이프라인: 문의 → 협상 → 계약 → 진행 중 → 납품 → 결제 → 취소 등 딜 상태를 추적합니다.',
          '산출물 관리: 각 스폰서십의 산출물(전용 영상, 통합 영상, 숏폼, 스토리 등)과 마감일을 관리합니다.',
          '결제 추적: 결제 상태(대기/청구서 발행/완료/연체)와 금액을 추적합니다.',
          '수익 분석: 월별 스폰서십 수익 추이, 평균 딜 금액, 완료율 등 통계를 제공합니다.',
          '연락처 관리: 각 브랜드의 담당자 이름과 이메일을 기록합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "스폰서십 트래커"를 클릭합니다.',
          '"새 딜" 버튼을 클릭하여 스폰서십 정보를 등록합니다.',
          '딜 카드를 클릭하여 상세 정보와 산출물 목록을 확인합니다.',
          '산출물 완료 시 체크하여 진행률을 업데이트합니다.',
          '요약 통계에서 전체 수익과 진행 현황을 모니터링합니다.',
        ],
      },
    ],
  },
  {
    id: 'playlist-manager',
    title: '재생목록 관리',
    icon: QueueListIcon,
    content: [
      {
        subtitle: '개요',
        text: '재생목록 관리는 YouTube, TikTok, Instagram, Naver Clip 등 여러 플랫폼의 재생목록을 한 곳에서 통합 관리하는 기능입니다. 재생목록 생성, 영상 추가/제거, 순서 변경, 플랫폼 동기화를 지원합니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '크로스 플랫폼: YouTube, TikTok, Instagram, Naver Clip 4개 플랫폼의 재생목록을 통합 관리합니다.',
          '재생목록 동기화: 플랫폼의 실제 재생목록과 동기화하여 최신 상태를 유지합니다.',
          '영상 관리: 재생목록에 영상을 추가/제거하고 순서를 변경할 수 있습니다.',
          '통계 대시보드: 전체 재생목록 수, 총 영상 수, 총 조회수, 플랫폼별 분포를 확인합니다.',
          '공개 설정: 각 재생목록의 공개/비공개/일부 공개 상태를 관리합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "재생목록 관리"를 클릭합니다.',
          '플랫폼 필터로 특정 플랫폼의 재생목록만 표시합니다.',
          '"새 재생목록" 버튼을 클릭하여 재생목록을 생성합니다.',
          '재생목록 카드의 동기화 버튼을 클릭하여 플랫폼과 싱크합니다.',
          '재생목록을 클릭하여 영상 목록을 확인하고 순서를 변경합니다.',
        ],
      },
    ],
  },
  {
    id: 'hashtag-analytics',
    title: 'AI 해시태그 분석기',
    icon: HashtagIcon,
    content: [
      {
        subtitle: '개요',
        text: 'AI 해시태그 분석기는 해시태그별 성과를 분석하고 트렌드를 파악하여 최적의 해시태그를 추천합니다. 해시태그 그룹을 관리하여 효율적으로 재사용할 수 있습니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '성과 분석: 해시태그별 사용 횟수, 총 조회수, 평균 참여율, 성장률을 분석합니다.',
          '트렌드 방향: 각 해시태그의 트렌드 방향(상승/하락/안정)을 표시합니다.',
          'AI 추천: 주제와 플랫폼을 입력하면 AI가 관련성 높은 해시태그를 추천합니다.',
          '해시태그 그룹: 자주 사용하는 해시태그 조합을 그룹으로 저장하여 재사용합니다.',
          '경쟁도 분석: 추천 해시태그의 경쟁도(낮음/중간/높음)와 예상 도달 범위를 표시합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "AI 해시태그 분석기"를 클릭합니다.',
          '성과 탭에서 기존 해시태그의 성과를 확인합니다.',
          '추천 탭에서 주제와 플랫폼을 입력하고 "분석" 버튼을 클릭합니다.',
          '추천된 해시태그를 검토하고 사용할 해시태그를 선택합니다.',
          '그룹 탭에서 자주 사용하는 해시태그 조합을 저장합니다.',
        ],
      },
      {
        subtitle: '크레딧 비용',
        text: '해시태그 AI 분석 1회당 2크레딧이 소모됩니다.',
      },
    ],
  },
  {
    id: 'revenue-split',
    title: '수익 분배 관리',
    icon: BanknotesIcon,
    content: [
      {
        subtitle: '개요',
        text: '수익 분배 관리는 팀원이나 협업자 간의 수익을 공정하게 분배하는 기능입니다. 분배 비율 설정, 승인, 지급까지 전체 워크플로우를 관리합니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '분배 생성: 제목, 총 금액, 기간을 설정하고 멤버별 비율을 지정합니다.',
          '멤버 관리: 크리에이터, 편집자, 매니저 등 역할별 분배 비율과 금액을 관리합니다.',
          '승인 워크플로우: 초안 → 대기중 → 승인 → 분배완료 단계를 거칩니다.',
          '지급 추적: 각 멤버의 지급 상태(대기/완료/실패)를 실시간 추적합니다.',
          '비율 시각화: 파이 차트로 멤버별 분배 비율을 시각적으로 확인합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "수익 분배 관리"를 클릭합니다.',
          '"새 분배" 버튼을 클릭하여 분배를 생성합니다.',
          '멤버를 추가하고 각 비율을 설정합니다.',
          '승인 버튼을 클릭하여 분배를 확정합니다.',
          '"분배 실행"으로 실제 지급을 처리합니다.',
        ],
      },
    ],
  },
  {
    id: 'ab-test-results',
    title: 'A/B 테스트 결과',
    icon: BeakerIcon,
    content: [
      {
        subtitle: '개요',
        text: 'A/B 테스트 결과는 콘텐츠 A/B 테스트의 상세한 분석 결과를 제공합니다. 변형별 성과 비교, 통계적 신뢰도, 우승자 자동 판정 기능을 포함합니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '변형 비교: 각 변형의 조회수, CTR, 평균 시청시간, 참여율, 전환수를 비교합니다.',
          '신뢰도 게이지: 반원형 게이지로 테스트의 통계적 신뢰도를 시각화합니다.',
          '우승자 판정: 95% 이상의 신뢰도에서 자동으로 우승 변형을 판정합니다.',
          '테스트 제어: 진행중인 테스트를 일시중지, 재개, 중지할 수 있습니다.',
          '상태 필터: 전체/진행중/완료/일시중지/취소별로 테스트를 필터링합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "A/B 테스트 결과"를 클릭합니다.',
          '요약 통계에서 전체 테스트 수와 평균 개선율을 확인합니다.',
          '테스트 카드를 클릭하여 상세 결과를 확인합니다.',
          '변형 비교 테이블에서 각 변형의 성과를 비교합니다.',
          '신뢰도가 충분하면 우승 변형을 적용합니다.',
        ],
      },
    ],
  },
  {
    id: 'fan-community',
    title: '팬 커뮤니티',
    icon: ChatBubbleLeftRightIcon,
    content: [
      {
        subtitle: '개요',
        text: '팬 커뮤니티는 크리에이터와 팬이 소통하는 게시판입니다. 공지사항, 토론, 투표, Q&A, 비하인드, 팬아트 등 다양한 게시글 유형을 지원합니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '게시글 유형: 공지사항, 토론, 투표, Q&A, 비하인드, 팬아트 6가지 유형을 지원합니다.',
          '고정 게시글: 중요한 게시글을 상단에 고정하여 항상 먼저 표시합니다.',
          '좋아요 & 댓글: 팬들이 게시글에 좋아요와 댓글로 반응할 수 있습니다.',
          '커뮤니티 통계: 총 게시글, 총 멤버, 오늘 활성 멤버, 좋아요 수를 확인합니다.',
          '타입 필터: 게시글 유형별로 필터링하여 원하는 콘텐츠만 볼 수 있습니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "팬 커뮤니티"를 클릭합니다.',
          '커뮤니티 통계에서 멤버 활동 현황을 확인합니다.',
          '"새 게시글" 버튼을 클릭하고 게시글 유형을 선택합니다.',
          '제목, 내용, 태그를 입력하고 게시합니다.',
          '팬 게시글에 좋아요와 댓글로 소통합니다.',
        ],
      },
    ],
  },
  {
    id: 'calendar-insights',
    title: '캘린더 인사이트',
    icon: CalendarDaysIcon,
    content: [
      {
        subtitle: '개요',
        text: '캘린더 인사이트는 콘텐츠 업로드 패턴을 분석하고 최적의 업로드 시간을 추천하는 기능입니다. 요일/시간대별 성과 데이터를 기반으로 가장 효과적인 업로드 타이밍을 찾아줍니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '최적 시간 추천: AI가 과거 데이터를 분석하여 요일별, 시간대별 최적 업로드 시간을 추천합니다.',
          '일별 인사이트: 날짜별 업로드 수, 평균 조회수, 참여율, 점수를 확인합니다.',
          '업로드 패턴: 플랫폼별 업로드 빈도, 가장 활발한 요일/시간, 일관성 점수를 분석합니다.',
          '요약 통계: 총 업로드 수, 주간 평균, 최적 요일, 일관성 점수를 한눈에 파악합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "캘린더 인사이트"를 클릭합니다.',
          '요약 통계에서 전체 업로드 현황을 확인합니다.',
          '최적 업로드 시간 추천 카드에서 추천 이유를 확인합니다.',
          '플랫폼별 업로드 패턴을 확인하여 일관성을 개선합니다.',
          '최적 시간에 맞춰 콘텐츠 업로드 일정을 조정합니다.',
        ],
      },
    ],
  },
  {
    id: 'team-performance',
    title: '팀 성과 대시보드',
    icon: UserGroupIcon,
    content: [
      {
        subtitle: '개요',
        text: '팀 성과 대시보드는 팀원들의 작업 완료율, 콘텐츠 생산량, 활동 내역을 추적하고 분석하는 기능입니다. 팀의 생산성을 시각화하고 최고 성과자를 파악할 수 있습니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '멤버 성과 카드: 각 팀원의 완료율, 작업 수, 콘텐츠 수, 평점, 연속 근무일을 표시합니다.',
          '성과 랭킹: 완료율 기준으로 팀원 순위를 매기고 프로그레스바로 시각화합니다.',
          '활동 피드: 팀원들의 최근 활동(편집 완료, 기획 제출 등)을 타임라인으로 표시합니다.',
          '탑 퍼포머: 가장 높은 성과를 보인 팀원을 하이라이트합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "팀 성과 대시보드"를 클릭합니다.',
          '요약 통계에서 팀 전체 성과를 확인합니다.',
          '멤버 카드에서 개별 팀원의 상세 성과를 검토합니다.',
          '성과 랭킹에서 팀원 간 비교를 확인합니다.',
          '활동 피드에서 최근 팀 활동 내역을 모니터링합니다.',
        ],
      },
    ],
  },
  {
    id: 'brand-safety',
    title: '브랜드 안전성 점검',
    icon: ShieldExclamationIcon,
    content: [
      {
        subtitle: '개요',
        text: '브랜드 안전성 점검은 콘텐츠가 광고 정책, 저작권, 언어 기준 등을 준수하는지 자동으로 검사하는 기능입니다. 업로드 전에 잠재적 문제를 사전에 발견하여 수익 손실을 방지합니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '자동 점검: 비속어, 저작권 음악, 광고 표시, 위험 행위 등을 자동으로 감지합니다.',
          '상태 분류: 안전(SAFE), 경고(WARNING), 위반(VIOLATION) 3단계로 분류합니다.',
          '규칙 관리: 점검 규칙의 활성화/비활성화 및 심각도를 커스터마이즈합니다.',
          '권고사항: 문제가 발견되면 구체적인 개선 권고사항을 제공합니다.',
          '요약 통계: 전체 점검 수, 안전/경고/위반 건수, 평균 점수를 확인합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "브랜드 안전성 점검"을 클릭합니다.',
          '요약 통계에서 전체 안전성 현황을 확인합니다.',
          '"점검 실행" 버튼을 클릭하여 새로운 콘텐츠를 검사합니다.',
          '점검 결과에서 경고/위반 항목의 권고사항을 확인합니다.',
          '규칙 관리에서 필요한 규칙을 활성화/비활성화합니다.',
        ],
      },
    ],
  },
  {
    id: 'subtitle-translation',
    title: '자동 자막 번역',
    icon: LanguageIcon,
    content: [
      {
        subtitle: '개요',
        text: '자동 자막 번역은 AI를 활용하여 동영상 자막을 다국어로 번역하는 기능입니다. 영어, 일본어, 중국어, 스페인어 등 10개 이상의 언어를 지원하며, 번역 품질을 수동으로 편집할 수 있습니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '다국어 지원: 영어, 일본어, 중국어, 스페인어, 프랑스어 등 10개 이상 언어를 지원합니다.',
          '실시간 진행: 번역 진행률을 실시간으로 확인할 수 있습니다.',
          '라인 에디터: 각 자막 라인의 번역을 수동으로 편집하고 시간코드를 확인합니다.',
          '품질 점수: AI가 번역 품질을 자동 평가하여 점수를 제공합니다.',
          '크레딧 관리: 번역에 사용된 AI 크레딧을 추적합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "자동 자막 번역"을 클릭합니다.',
          '"새 번역 요청" 버튼을 클릭합니다.',
          '영상을 선택하고 소스 언어와 타겟 언어를 설정합니다.',
          '번역이 완료되면 라인 에디터에서 번역 품질을 검토합니다.',
          '필요 시 개별 라인의 번역을 수동으로 수정합니다.',
        ],
      },
    ],
  },
  {
    id: 'sentiment-analyzer',
    title: 'AI 감정 분석기',
    icon: FaceSmileIcon,
    content: [
      {
        subtitle: '개요',
        text: 'AI 감정 분석기는 콘텐츠 댓글의 감정을 자동으로 분석하여 긍정, 중립, 부정으로 분류하고 인사이트를 제공하는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '감정 분류: 댓글을 긍정, 중립, 부정으로 자동 분류합니다.',
          '키워드 분석: 긍정/부정 키워드를 추출하여 시청자 반응을 파악합니다.',
          '감정 점수: 각 댓글의 감정 점수를 0~100으로 평가합니다.',
          '트렌드 추적: 시간에 따른 감정 변화 추이를 분석합니다.',
          '콘텐츠별 비교: 콘텐츠간 감정 반응을 비교할 수 있습니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "AI 감정 분석기"를 클릭합니다.',
          '분석할 콘텐츠를 선택하고 "분석 시작" 버튼을 클릭합니다.',
          '분석이 완료되면 감정 분포 차트와 키워드를 확인합니다.',
          '개별 댓글을 클릭하면 상세 감정 분석 결과를 볼 수 있습니다.',
        ],
      },
    ],
  },
  {
    id: 'content-versioning',
    title: '콘텐츠 버전 관리',
    icon: DocumentChartBarIcon,
    content: [
      {
        subtitle: '개요',
        text: '콘텐츠 버전 관리는 제목, 썸네일, 설명 등의 변경 이력을 추적하고 각 변경이 성과에 미친 영향을 분석하는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '변경 이력 추적: 콘텐츠의 모든 수정 사항을 타임라인으로 기록합니다.',
          '성과 비교: 변경 전후의 조회수, 좋아요, 참여율, CTR을 비교합니다.',
          '변경 유형 분류: 제목, 썸네일, 설명, 태그, 자막, 영상 등 유형별로 분류합니다.',
          '최적 변경 분석: 가장 성과가 좋았던 변경 유형을 식별합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "콘텐츠 버전 관리"를 클릭합니다.',
          '콘텐츠 그룹 카드를 클릭하여 버전 이력을 펼칩니다.',
          '각 버전의 변경 내용과 성과 비교 데이터를 확인합니다.',
          '가장 효과적인 변경 전략을 파악하여 향후 콘텐츠에 적용합니다.',
        ],
      },
    ],
  },
  {
    id: 'creator-milestone',
    title: '크리에이터 마일스톤',
    icon: TrophyIcon,
    content: [
      {
        subtitle: '개요',
        text: '크리에이터 마일스톤은 구독자 수, 조회수, 수익 등의 성장 목표를 설정하고 달성 과정을 추적하는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '목표 설정: 구독자, 조회수, 영상 수, 시청 시간, 수익 등 다양한 목표를 설정합니다.',
          '진행률 추적: 원형 프로그레스바로 목표 달성률을 실시간 확인합니다.',
          '달성 기록: 마일스톤 달성 시 자동으로 기록하고 축하 뱃지를 제공합니다.',
          '상태 관리: 대기, 진행 중, 달성, 만료 상태로 마일스톤을 관리합니다.',
          '플랫폼별 목표: 각 플랫폼별로 독립적인 목표를 설정할 수 있습니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "크리에이터 마일스톤"을 클릭합니다.',
          '"새 마일스톤" 버튼을 클릭하고 목표 유형, 제목, 목표값을 입력합니다.',
          '대시보드에서 진행 중인 마일스톤의 달성률을 확인합니다.',
          '상태 필터를 사용하여 진행 중/달성/대기 마일스톤을 구분하여 봅니다.',
        ],
      },
    ],
  },
  {
    id: 'algorithm-insights',
    title: '알고리즘 인사이트',
    icon: CpuChipIcon,
    content: [
      {
        subtitle: '개요',
        text: '알고리즘 인사이트는 YouTube, TikTok, Instagram 등 각 플랫폼의 알고리즘 요소를 분석하고 최적화 전략을 제시하는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '플랫폼별 점수: 콘텐츠, 참여, 메타데이터, 일관성, 오디언스 5개 영역의 점수를 제공합니다.',
          '요소 분석: 각 알고리즘 요소의 중요도, 현재 점수, 트렌드를 분석합니다.',
          '변경 추적: 플랫폼 알고리즘 변경 사항을 감지하고 영향도를 평가합니다.',
          '최적화 추천: 각 요소별 구체적인 개선 방안을 AI가 제안합니다.',
          '플랫폼 필터: YouTube, TikTok, Instagram별로 데이터를 필터링합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "알고리즘 인사이트"를 클릭합니다.',
          '플랫폼별 점수 카드에서 전체 점수와 세부 점수를 확인합니다.',
          '알고리즘 요소 목록에서 개선이 필요한 영역과 추천 사항을 확인합니다.',
          '최근 알고리즘 변경 카드에서 영향도와 대응 전략을 파악합니다.',
        ],
      },
    ],
  },
  {
    id: 'content-calendar-ai',
    title: 'AI 콘텐츠 캘린더',
    icon: AiCalIcon,
    content: [
      {
        subtitle: '개요',
        text: 'AI 콘텐츠 캘린더는 머신러닝을 활용하여 최적의 콘텐츠 업로드 일정을 자동으로 생성하고 추천하는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '일정 자동 생성: AI가 최적 업로드 시간과 콘텐츠 주제를 자동으로 제안합니다.',
          '최적 시간 슬롯: 플랫폼별 시청자 활동률 기반 최적 업로드 시간을 추천합니다.',
          '신뢰도 점수: 각 제안의 예상 성과 신뢰도를 제공합니다.',
          '제안 수락: 마음에 드는 제안을 수락하여 일정에 반영할 수 있습니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "AI 콘텐츠 캘린더"를 클릭합니다.',
          '기간과 플랫폼을 선택하고 "생성" 버튼을 클릭합니다.',
          'AI가 생성한 제안 목록에서 콘텐츠 일정을 확인합니다.',
          '마음에 드는 제안을 수락하여 캘린더에 반영합니다.',
        ],
      },
    ],
  },
  {
    id: 'creator-benchmark',
    title: '크리에이터 벤치마크',
    icon: BenchmarkIcon,
    content: [
      {
        subtitle: '개요',
        text: '크리에이터 벤치마크는 동일 분야 크리에이터들과 구독자, 조회수, 참여율 등 핵심 지표를 비교 분석하는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '지표 비교: 내 지표 vs 평균 vs 상위 크리에이터를 한눈에 비교합니다.',
          '백분위 분석: 각 지표별 나의 위치(백분위)를 확인합니다.',
          '피어 비교: 같은 분야 주요 크리에이터들의 성과를 비교합니다.',
          '트렌드 추적: 시간에 따른 내 순위 변화를 추적합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "크리에이터 벤치마크"를 클릭합니다.',
          '플랫폼 필터를 사용해 특정 플랫폼의 벤치마크를 확인합니다.',
          '지표 카드에서 내 값과 평균/상위 값을 비교합니다.',
          '피어 비교 표에서 경쟁 크리에이터의 성과를 파악합니다.',
        ],
      },
    ],
  },
  {
    id: 'content-cluster',
    title: '콘텐츠 클러스터',
    icon: CircleStackIcon,
    content: [
      {
        subtitle: '개요',
        text: '콘텐츠 클러스터는 AI가 유사한 콘텐츠를 자동으로 그룹핑하여 콘텐츠 전략과 성과 패턴을 분석하는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '자동 클러스터링: AI가 콘텐츠를 주제, 형식, 성과 기반으로 자동 분류합니다.',
          '성과 분석: 클러스터별 평균 조회수, 참여율을 비교합니다.',
          '유사도 분석: 클러스터 내 각 콘텐츠의 유사도를 확인합니다.',
          '태그 분석: 가장 많이 사용되는 태그를 파악합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "콘텐츠 클러스터"를 클릭합니다.',
          '클러스터 카드를 클릭하여 포함된 콘텐츠 목록을 확인합니다.',
          '각 콘텐츠의 유사도와 성과 데이터를 분석합니다.',
          '가장 성과가 좋은 클러스터의 패턴을 파악하여 전략에 활용합니다.',
        ],
      },
    ],
  },
  {
    id: 'fan-insights',
    title: '팬 인사이트',
    icon: EyeIcon,
    content: [
      {
        subtitle: '개요',
        text: '팬 인사이트는 시청자의 인구통계, 행동 패턴, 세그먼트를 분석하여 팬층을 깊이 이해할 수 있는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '인구통계: 연령대, 성별, 국가/도시별 팬 분포를 확인합니다.',
          '행동 분석: 활동 시간, 시청 시간, 재방문률, 댓글률, 공유율을 분석합니다.',
          '세그먼트: 열성 팬, 캐주얼 시청자 등 팬 유형별로 분류합니다.',
          '플랫폼 필터: 플랫폼별로 팬 데이터를 필터링합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "팬 인사이트"를 클릭합니다.',
          '인구통계 탭에서 팬의 연령대, 성별 분포를 확인합니다.',
          '행동 탭에서 시청 패턴과 참여 데이터를 분석합니다.',
          '세그먼트 탭에서 팬 유형별 특성을 파악합니다.',
        ],
      },
    ],
  },
  {
    id: 'content-repurposer',
    title: '콘텐츠 리퍼포징',
    icon: ArrowPathRoundedSquareIcon,
    content: [
      {
        subtitle: '개요',
        text: '콘텐츠 리퍼포징은 긴 영상을 틱톡, 릴스, 네이버 클립 등 숏폼 플랫폼에 맞게 자동으로 변환하는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '자동 변환: AI가 하이라이트를 추출하여 타겟 플랫폼에 최적화된 숏폼을 생성합니다.',
          '템플릿: 유튜브→틱톡, 유튜브→릴스 등 미리 정의된 변환 템플릿을 제공합니다.',
          '진행 상태: 각 작업의 진행률과 상태를 실시간으로 확인합니다.',
          '성공률 추적: 전체 작업의 성공률과 시간 절약 효과를 모니터링합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "콘텐츠 리퍼포징"을 클릭합니다.',
          '작업 탭에서 현재 리퍼포징 작업 목록을 확인합니다.',
          '템플릿 탭에서 변환 템플릿을 선택하고 새 작업을 생성합니다.',
          '완료된 작업에서 변환된 숏폼 영상을 다운로드합니다.',
        ],
      },
    ],
  },
  {
    id: 'audience-overlap',
    title: '오디언스 오버랩',
    icon: Square3Stack3DIcon,
    content: [
      {
        subtitle: '개요',
        text: '오디언스 오버랩은 여러 플랫폼 간 시청자의 중복과 고유 오디언스를 분석하는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '오버랩 분석: 두 플랫폼 간 시청자 중복률을 시각적으로 확인합니다.',
          '세그먼트: 공유 오디언스를 관심사 기반으로 세그먼트화합니다.',
          '고유 오디언스: 각 플랫폼에만 존재하는 고유 시청자 수를 파악합니다.',
          '참여율 비교: 세그먼트별 참여율과 관심사를 비교합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "오디언스 오버랩"을 클릭합니다.',
          '분석 결과에서 플랫폼 쌍별 오버랩 수치를 확인합니다.',
          '세그먼트 섹션에서 공유 오디언스 특성을 파악합니다.',
          '"분석 실행" 버튼으로 새로운 오버랩 분석을 시작합니다.',
        ],
      },
    ],
  },
  {
    id: 'video-seo',
    title: '비디오 SEO 최적화',
    icon: MagnifyingGlassIcon,
    content: [
      {
        subtitle: '개요',
        text: '비디오 SEO 최적화는 영상의 제목, 설명, 태그, 썸네일을 분석하여 검색 최적화 점수와 개선 방안을 제시합니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          'SEO 점수: 제목, 설명, 태그, 썸네일 각각의 점수와 종합 점수를 제공합니다.',
          '개선 제안: AI가 SEO 점수를 높이기 위한 구체적인 개선 사항을 추천합니다.',
          '키워드 분석: 관련 키워드의 검색량, 경쟁도, 트렌드를 분석합니다.',
          '플랫폼별 분석: 플랫폼 특성에 맞는 SEO 최적화 방향을 제시합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "비디오 SEO"를 클릭합니다.',
          'SEO 분석 목록에서 각 영상의 점수를 확인합니다.',
          '상세 제안사항을 참고하여 메타데이터를 개선합니다.',
          '키워드 분석에서 타겟 키워드의 검색 트렌드를 확인합니다.',
        ],
      },
    ],
  },
  {
    id: 'mood-board',
    title: '크리에이터 무드보드',
    icon: PaintBrushIcon,
    content: [
      {
        subtitle: '개요',
        text: '크리에이터 무드보드는 콘텐츠 기획을 위한 비주얼 영감 보드를 만들고 관리하는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '보드 관리: 카테고리별 무드보드를 생성하고 태그로 분류합니다.',
          '아이템 타입: 이미지, 컬러 팔레트, 텍스트 메모 등 다양한 타입의 아이템을 추가합니다.',
          '공유 설정: 공개/비공개를 선택하여 팀원과 영감을 공유합니다.',
          '핀보드 레이아웃: Pinterest 스타일의 시각적 레이아웃으로 아이디어를 정리합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "무드보드"를 클릭합니다.',
          '"보드 만들기"를 눌러 새 무드보드를 생성합니다.',
          '보드에 이미지, 색상, 텍스트 등 영감 아이템을 추가합니다.',
          '카테고리별로 보드를 관리하고 콘텐츠 기획에 활용합니다.',
        ],
      },
    ],
  },
  {
    id: 'thumbnail-ab-test',
    title: '썸네일 A/B 테스트',
    icon: ThumbnailAbIcon2,
    content: [
      {
        subtitle: '개요',
        text: '썸네일 A/B 테스트는 두 가지 썸네일 디자인의 클릭률(CTR)을 비교하여 최적의 썸네일을 선택하는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '변형 비교: A/B 두 가지 썸네일 변형의 노출수, 클릭수, CTR을 실시간 비교합니다.',
          '자동 승자 결정: 통계적 유의성에 도달하면 자동으로 승자를 결정합니다.',
          '테스트 관리: 활성/완료/초안 상태별로 테스트를 관리합니다.',
          '성과 분석: CTR 차이, 신뢰구간 등 상세 통계를 제공합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "썸네일 A/B"를 클릭합니다.',
          '"새 테스트"를 눌러 A/B 테스트를 생성합니다.',
          '두 가지 썸네일 이미지를 업로드하고 테스트를 시작합니다.',
          '결과를 확인하고 승자 썸네일을 적용합니다.',
        ],
      },
    ],
  },
  {
    id: 'revenue-goal',
    title: '수익 목표',
    icon: RevenueGoalIcon2,
    content: [
      {
        subtitle: '개요',
        text: '수익 목표는 채널 수익 목표를 설정하고 달성 과정을 추적하는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '목표 설정: 월별/분기별 수익 목표 금액과 기간을 설정합니다.',
          '마일스톤 추적: 목표 달성을 위한 중간 마일스톤 진행률을 확인합니다.',
          '진행률 시각화: 현재 수익 대비 목표 달성률을 차트로 표시합니다.',
          '알림: 마일스톤 도달 시 알림을 받습니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "수익 목표"를 클릭합니다.',
          '"목표 추가"를 눌러 새 수익 목표를 설정합니다.',
          '목표 금액, 기간, 마일스톤을 입력합니다.',
          '대시보드에서 실시간 진행률을 확인합니다.',
        ],
      },
    ],
  },
  {
    id: 'comment-summary',
    title: 'AI 댓글 요약',
    icon: CommentSummaryIcon,
    content: [
      {
        subtitle: '개요',
        text: 'AI 댓글 요약은 영상의 댓글을 AI가 분석하여 주요 토픽, 감정 분석, 인기 댓글을 자동으로 정리해주는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          'AI 요약: 수백~수천 개의 댓글을 AI가 분석하여 핵심 내용을 요약합니다.',
          '토픽 분석: 댓글에서 자주 언급되는 주제를 자동으로 추출합니다.',
          '감정 분석: 댓글의 전체적인 감정(긍정/부정/중립) 비율을 분석합니다.',
          '인기 댓글: 좋아요 수 기준 상위 댓글을 한눈에 보여줍니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "댓글 요약"을 클릭합니다.',
          '분석할 영상을 선택합니다.',
          '"AI 분석"을 눌러 댓글 분석을 시작합니다.',
          '요약 결과, 토픽, 감정 분석, 인기 댓글을 확인합니다.',
        ],
      },
    ],
  },
  {
    id: 'platform-health',
    title: '플랫폼 건강 점수',
    icon: HealthIcon2,
    content: [
      {
        subtitle: '개요',
        text: '플랫폼 건강 점수는 각 연결된 플랫폼별 채널 건강 상태를 종합적으로 분석하는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '건강 점수: 각 플랫폼별 0~100 점수로 채널 건강 상태를 표시합니다.',
          '이슈 감지: 성장 둔화, 참여율 하락 등 문제점을 자동으로 감지합니다.',
          '심각도 분류: 이슈를 높음/중간/낮음 심각도로 분류하여 우선순위를 제공합니다.',
          '전체 요약: 전체 플랫폼의 건강 상태를 한눈에 파악합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "플랫폼 건강"을 클릭합니다.',
          '각 플랫폼별 건강 점수와 상태를 확인합니다.',
          '감지된 이슈를 클릭하여 상세 내용과 해결 방법을 확인합니다.',
          '정기적으로 점검하여 채널 건강을 유지합니다.',
        ],
      },
    ],
  },
  {
    id: 'subtitle-generator',
    title: 'AI 자막 생성기',
    icon: MicrophoneIcon,
    content: [
      {
        subtitle: '개요',
        text: 'AI 자막 생성기는 영상에서 AI가 음성을 인식하여 자동으로 자막을 생성하는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '자동 생성: AI 음성 인식으로 영상의 자막을 자동 생성합니다.',
          '다국어 지원: 한국어, 영어 등 다양한 언어의 자막을 생성합니다.',
          '세그먼트 편집: 생성된 자막의 시간과 텍스트를 세그먼트별로 편집합니다.',
          '진행률 추적: 자막 생성 작업의 진행 상황을 실시간으로 확인합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "AI 자막 생성"을 클릭합니다.',
          '자막을 생성할 영상과 언어를 선택합니다.',
          '"생성하기"를 눌러 AI 자막 생성을 시작합니다.',
          '완료된 자막을 다운로드하거나 편집합니다.',
        ],
      },
    ],
  },
  {
    id: 'content-library',
    title: '콘텐츠 라이브러리',
    icon: FolderOpenIcon,
    content: [
      {
        subtitle: '개요',
        text: '콘텐츠 라이브러리는 업로드한 모든 콘텐츠를 체계적으로 관리하는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '폴더 관리: 카테고리별 폴더를 생성하여 콘텐츠를 정리합니다.',
          '타입 필터: 영상, 이미지, 오디오, 문서별로 필터링합니다.',
          '태그 시스템: 태그를 추가하여 콘텐츠를 쉽게 검색합니다.',
          '용량 관리: 전체 사용량과 파일별 크기를 확인합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "콘텐츠 라이브러리"를 클릭합니다.',
          '폴더를 생성하여 콘텐츠를 분류합니다.',
          '타입별 필터를 사용하여 원하는 콘텐츠를 찾습니다.',
          '필요 없는 콘텐츠는 삭제하여 저장 공간을 관리합니다.',
        ],
      },
    ],
  },
  {
    id: 'fan-poll',
    title: '팬 투표',
    icon: HandRaisedIcon,
    content: [
      {
        subtitle: '개요',
        text: '팬 투표는 팬들의 의견을 수집하고 콘텐츠 방향을 결정하는 데 도움이 되는 투표/설문 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '투표 생성: 단일 선택, 복수 선택, 평점 등 다양한 타입의 투표를 생성합니다.',
          '실시간 결과: 투표 참여 현황과 결과를 실시간으로 확인합니다.',
          '기간 설정: 투표 시작/종료 날짜를 설정하여 자동 마감합니다.',
          '참여율 분석: 투표별 참여율과 트렌드를 분석합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "팬 투표"를 클릭합니다.',
          '"투표 만들기"를 눌러 새 투표를 생성합니다.',
          '제목, 옵션, 마감일을 설정합니다.',
          '팬들의 투표 결과를 확인하고 콘텐츠에 반영합니다.',
        ],
      },
    ],
  },
  {
    id: 'creator-network',
    title: '크리에이터 네트워크',
    icon: UserCircleIcon,
    content: [
      {
        subtitle: '개요',
        text: '크리에이터 네트워크는 다른 크리에이터와 연결하고 협업 기회를 찾는 기능입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '',
        items: [
          '크리에이터 검색: 카테고리, 플랫폼별로 크리에이터를 검색합니다.',
          '매치 점수: AI 기반 매치 점수로 적합한 협업 파트너를 추천합니다.',
          '협업 요청: 콜라보, 크로스 프로모션 등 협업 요청을 보내고 관리합니다.',
          '네트워크 관리: 연결된 크리에이터와 협업 이력을 관리합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "크리에이터 네트워크"를 클릭합니다.',
          '관심 카테고리의 크리에이터를 검색합니다.',
          '매치 점수를 확인하고 협업 요청을 보냅니다.',
          '수신된 협업 요청에 응답합니다.',
        ],
      },
    ],
  },
  {
    id: 'trend-predictor',
    title: 'AI 트렌드 예측기',
    icon: ArrowTrendingUpIcon,
    content: [
      {
        subtitle: '개요',
        text: 'AI가 콘텐츠 트렌드를 예측하여 최적의 콘텐츠 주제를 추천합니다.',
      },
      {
        subtitle: '주요 기능',
        text: '키워드별 트렌드 점수와 예측 점수를 비교하고, 상승/안정/하락 방향을 확인합니다.',
        items: [
          '트렌드 예측: 카테고리별 키워드의 현재/예측 점수를 비교합니다.',
          '방향 분석: 상승, 안정, 하락 트렌드를 한눈에 확인합니다.',
          '토픽 분석: 관련 토픽의 영상 수, 평균 조회수, 성장률을 분석합니다.',
          '피크 예측: 트렌드 정점 예상일을 AI가 예측합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "AI 트렌드 예측기"를 클릭합니다.',
          '카테고리별 트렌드 예측을 확인합니다.',
          '상승 트렌드 키워드를 선택하여 관련 토픽을 분석합니다.',
          '예측 정확도와 피크 예상일을 참고하여 콘텐츠를 기획합니다.',
        ],
      },
    ],
  },
  {
    id: 'performance-report',
    title: '콘텐츠 성과 보고서',
    icon: ReportIcon3,
    content: [
      {
        subtitle: '개요',
        text: '주간/월간/분기별 종합 성과 리포트를 자동 생성합니다.',
      },
      {
        subtitle: '주요 기능',
        text: '기간을 설정하여 조회수, 인게이지먼트, 인기 콘텐츠를 포함한 종합 보고서를 생성합니다.',
        items: [
          '보고서 생성: 기간과 제목을 설정하여 종합 보고서를 자동 생성합니다.',
          '섹션별 분석: 전체 요약, 인게이지먼트, 인기 콘텐츠, AI 추천을 확인합니다.',
          'PDF 다운로드: 완성된 보고서를 PDF로 다운로드합니다.',
          '자동 예약: 주기적 보고서 자동 생성을 예약합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "성과 보고서"를 클릭합니다.',
          '"보고서 생성" 버튼으로 기간과 제목을 설정합니다.',
          '생성된 보고서의 섹션별 상세 내용을 확인합니다.',
          '완성된 보고서를 PDF로 다운로드합니다.',
        ],
      },
    ],
  },
  {
    id: 'live-stream',
    title: '라이브 스트림 관리',
    icon: VideoCameraIcon,
    content: [
      {
        subtitle: '개요',
        text: '멀티플랫폼 라이브 스트리밍을 예약하고 관리합니다.',
      },
      {
        subtitle: '주요 기능',
        text: 'YouTube, Instagram, TikTok 등 여러 플랫폼의 라이브 스트림을 한곳에서 관리합니다.',
        items: [
          '스트림 예약: 플랫폼별 라이브 스트림을 예약합니다.',
          '실시간 모니터링: 시청자 수와 채팅 메시지를 실시간으로 확인합니다.',
          '채팅 관리: 하이라이트 메시지, 모더레이터 표시를 확인합니다.',
          '성과 요약: 총 시청자, 최대 시청자, 채팅 수 통계를 확인합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "라이브 스트림"을 클릭합니다.',
          '"새 스트림" 버튼으로 플랫폼과 예약 시간을 설정합니다.',
          '라이브 중 시청자 수와 채팅을 모니터링합니다.',
          '종료 후 성과 요약을 확인합니다.',
        ],
      },
    ],
  },
  {
    id: 'creator-marketplace',
    title: '크리에이터 마켓플레이스',
    icon: ShoppingBagIcon,
    content: [
      {
        subtitle: '개요',
        text: '크리에이터 간 서비스를 거래할 수 있는 마켓플레이스입니다.',
      },
      {
        subtitle: '주요 기능',
        text: '영상 편집, 썸네일 제작, 대본 작성, 나레이션 등 다양한 서비스를 등록하거나 주문할 수 있습니다.',
        items: [
          '서비스 등록: 내 전문 분야의 서비스를 등록합니다.',
          '서비스 검색: 유형별로 필터링하여 원하는 서비스를 찾습니다.',
          '평점/리뷰: 평점과 리뷰를 참고하여 최적의 서비스를 선택합니다.',
          '주문 관리: 주문 현황과 배송 상태를 추적합니다.',
        ],
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "크리에이터 마켓"을 클릭합니다.',
          '서비스 유형별로 리스팅을 필터링합니다.',
          '원하는 서비스의 상세 내용과 리뷰를 확인합니다.',
          '"주문하기"를 클릭하여 서비스를 주문합니다.',
        ],
      },
    ],
  },
  {
    id: 'music-recommender',
    title: 'AI 음악 추천',
    icon: MusicalNoteIcon,
    content: [
      {
        subtitle: '개요',
        text: 'AI가 영상 분위기에 맞는 배경음악을 추천합니다.',
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "AI 음악 추천"을 클릭합니다.',
          '영상별 추천 음악 목록을 확인합니다.',
          '매치 점수와 미리듣기로 적합한 트랙을 선택합니다.',
          '선택한 트랙을 영상에 적용합니다.',
        ],
      },
    ],
  },
  {
    id: 'content-ab-analyzer',
    title: '콘텐츠 A/B 분석기',
    icon: AdjustmentsHorizontalIcon,
    content: [
      {
        subtitle: '개요',
        text: '동일 주제의 다른 스타일 콘텐츠 성과를 비교 분석합니다.',
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "콘텐츠 A/B 분석"을 클릭합니다.',
          '비교할 두 영상을 선택하여 테스트를 생성합니다.',
          '조회수, 좋아요, CTR 등 메트릭을 비교합니다.',
          '신뢰도와 승자 분석 결과를 확인합니다.',
        ],
      },
    ],
  },
  {
    id: 'fan-reward',
    title: '팬 리워드',
    icon: RewardIcon4,
    content: [
      {
        subtitle: '개요',
        text: '팬 활동에 포인트를 부여하고 리워드를 제공하는 시스템입니다.',
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "팬 리워드"를 클릭합니다.',
          '리워드 항목을 생성하고 포인트 비용을 설정합니다.',
          '팬 활동 로그에서 포인트 적립 현황을 확인합니다.',
          '리워드 클레임 현황을 관리합니다.',
        ],
      },
    ],
  },
  {
    id: 'platform-automation-pro',
    title: '플랫폼 자동화',
    icon: AutoIcon,
    content: [
      {
        subtitle: '개요',
        text: '플랫폼별 자동 게시, 알림, 태그 등 자동화 규칙을 관리합니다.',
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "플랫폼 자동화"를 클릭합니다.',
          '트리거(스케줄, 이벤트, 키워드 등)와 액션을 설정합니다.',
          '규칙을 활성화/비활성화합니다.',
          '실행 로그에서 자동화 결과를 확인합니다.',
        ],
      },
    ],
  },
  {
    id: 'video-script-assistant',
    title: 'AI 스크립트 어시스턴트',
    icon: PencilSquareIcon,
    content: [
      {
        subtitle: '개요',
        text: 'AI를 활용하여 비디오 스크립트를 작성하고 개선합니다. 톤, 길이, 섹션별 제안을 통해 더 나은 스크립트를 만들 수 있습니다.',
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "스크립트 어시스턴트"를 클릭합니다.',
          '제목, 톤(캐주얼/전문/유머/교육/극적), 목표 길이를 설정합니다.',
          'AI가 스크립트를 생성하면 섹션별 제안을 확인합니다.',
          '훅, 인트로, 본문, 전환, CTA 등 섹션별로 제안을 적용합니다.',
        ],
      },
    ],
  },
  {
    id: 'channel-health',
    title: '채널 건강도 대시보드',
    icon: HealthIcon2,
    content: [
      {
        subtitle: '개요',
        text: '채널의 종합 건강도를 성장, 참여도, 일관성, 오디언스, 수익화 5개 영역으로 측정합니다.',
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "채널 건강도"를 클릭합니다.',
          '각 채널의 전체 점수와 세부 지표를 확인합니다.',
          '채널을 선택하면 트렌드 변화를 볼 수 있습니다.',
          '추천 사항을 참고하여 약점 영역을 개선합니다.',
        ],
      },
    ],
  },
  {
    id: 'content-translator',
    title: '콘텐츠 번역기',
    icon: LanguageIcon,
    content: [
      {
        subtitle: '개요',
        text: 'AI 기반으로 제목, 설명, 태그, 자막 등 콘텐츠를 다국어로 번역합니다. 용어집으로 일관된 번역 품질을 유지합니다.',
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "콘텐츠 번역기"를 클릭합니다.',
          '번역할 비디오와 대상 언어, 콘텐츠 유형을 선택합니다.',
          '번역 완료 후 품질 점수를 확인합니다.',
          '용어집에 자주 사용하는 용어를 등록하여 번역 일관성을 높입니다.',
        ],
      },
    ],
  },
  {
    id: 'revenue-analyzer',
    title: '수익 분석기',
    icon: BanknotesIcon,
    content: [
      {
        subtitle: '개요',
        text: '광고, 멤버십, 슈퍼챗, 스폰서십, 굿즈, 제휴 등 수익원별로 상세하게 분석하고 미래 수익을 예측합니다.',
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "수익 분석기"를 클릭합니다.',
          '채널별, 수익원별 금액과 성장률을 확인합니다.',
          '채널을 선택하면 수익 예측과 신뢰도를 볼 수 있습니다.',
          '예측 요인을 분석하여 수익 성장 전략을 수립합니다.',
        ],
      },
    ],
  },
  {
    id: 'schedule-optimizer',
    title: 'AI 일정 최적화',
    icon: OptimizerIcon2,
    content: [
      {
        subtitle: '개요',
        text: 'AI가 과거 성과 데이터를 분석하여 콘텐츠 게시 최적 시간대를 추천하고, 일정을 자동으로 최적화합니다.',
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "AI 일정 최적화"를 클릭합니다.',
          '플랫폼별 최적 시간대 점수를 확인합니다.',
          'AI 추천 일정 목록에서 신뢰도와 콘텐츠를 검토합니다.',
          '"적용" 버튼을 눌러 추천 일정을 캘린더에 반영합니다.',
        ],
      },
    ],
  },
  {
    id: 'portfolio-builder',
    title: '포트폴리오 빌더',
    icon: RectangleGroupIcon,
    content: [
      {
        subtitle: '개요',
        text: '크리에이터 포트폴리오를 자동으로 생성하고, 템플릿을 선택하여 브랜드를 소개할 수 있는 공개 페이지를 만듭니다.',
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "포트폴리오 빌더"를 클릭합니다.',
          '"새 포트폴리오" 버튼으로 포트폴리오를 생성합니다.',
          '템플릿을 선택하고 섹션별 내용을 편집합니다.',
          '"공개" 버튼을 눌러 공개 URL을 생성하여 공유합니다.',
        ],
      },
    ],
  },
  {
    id: 'fan-segment-campaign',
    title: '팬 세그먼트 캠페인',
    icon: CampaignIcon2,
    content: [
      {
        subtitle: '개요',
        text: '팬을 세그먼트(신규, 열성팬, 비활성 등)로 나누고, 각 세그먼트에 맞춤 캠페인(이메일, 푸시, 인앱, SMS)을 발송합니다.',
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "팬 세그먼트 캠페인"을 클릭합니다.',
          '팬 세그먼트 목록에서 대상 그룹을 확인합니다.',
          '"새 캠페인" 버튼으로 발송 유형과 대상을 선택합니다.',
          '오픈률, 클릭률 등 캠페인 성과를 모니터링합니다.',
        ],
      },
    ],
  },
  {
    id: 'content-funnel',
    title: '콘텐츠 퍼널 분석',
    icon: FunnelIcon,
    content: [
      {
        subtitle: '개요',
        text: '콘텐츠 노출에서 구독/전환까지의 퍼널을 단계별로 분석하여 이탈 지점을 파악하고 전환율을 개선합니다.',
      },
      {
        subtitle: '사용 방법',
        text: '',
        steps: [
          '사이드바에서 "콘텐츠 퍼널 분석"을 클릭합니다.',
          '영상별 퍼널 차트에서 단계별 이탈률을 확인합니다.',
          '최대 이탈 구간을 파악하여 개선 포인트를 찾습니다.',
          '두 영상을 비교하여 성과 차이를 분석합니다.',
        ],
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
        text: 'onGo supports 13 platforms including YouTube, TikTok, Instagram Reels, and Naver Clip. You can securely connect channels through each platform\'s official OAuth authentication.',
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
          'X (Twitter): A Twitter/X developer account is required. Uses OAuth 2.0 PKCE.',
          'Facebook: Facebook Page admin permissions are required.',
          'Threads: A Meta/Instagram Business account is required.',
          'Pinterest: A Pinterest Business account is required.',
          'LinkedIn: Post video content to your LinkedIn profile.',
          'WordPress: A WordPress.com blog is required.',
          'Tumblr: Post video content to your Tumblr blog.',
          'Vimeo: Upload high-quality videos to your Vimeo account.',
          'Dailymotion: Upload videos to your Dailymotion account.',
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
      {
        subtitle: 'Media Analysis (FFprobe)',
        text: 'Uploaded videos are automatically analyzed using FFprobe. View the results in the "Media Analysis Info" tab on the video detail page.',
        items: [
          'Video Track: Codec (H.264/H.265 etc.), resolution, FPS, bitrate, profile',
          'Audio Track: Codec (AAC etc.), bitrate, sample rate, channel count',
          'Color Info: Color space, pixel format',
          'Platform Compatibility: Automatically evaluates and displays compatibility badges for all 13 supported platforms.',
        ],
      },
      {
        subtitle: 'Direct Original Upload',
        text: 'Original video files are uploaded directly to each platform without any transcoding. Each platform handles its own optimal processing, allowing for fast uploads with no quality loss.',
      },
      {
        subtitle: 'Real-time Processing Progress',
        text: 'Monitor the video processing pipeline in real-time after upload. Each processing stage is automatically updated via SSE (Server-Sent Events).',
        items: [
          'Processing Stages: Media Analysis → Thumbnail Generation → Caption Generation → Platform Upload',
          'View both overall and per-stage progress simultaneously.',
          'Estimated time remaining is automatically calculated and displayed.',
          'Automatic reconnection is attempted if the connection is lost.',
        ],
      },
      {
        subtitle: 'AI Thumbnail Generation',
        text: 'After upload, thumbnail candidates are automatically generated using FFmpeg.',
        items: [
          'Scene Detection: Visually interesting frames are automatically extracted at scene transition points.',
          'Even Spacing: Frames are extracted at even intervals across the entire video.',
          'Approximately 10 candidates are generated. Click your preferred thumbnail in the grid to set it as the featured image.',
          'Custom Thumbnails: Upload your own images in JPG, PNG, or WebP format (max 5MB).',
        ],
      },
      {
        subtitle: 'AI Auto Captions (Whisper STT)',
        text: 'Uses OpenAI Whisper to automatically transcribe video audio to text.',
        items: [
          'Supports Korean and English caption generation.',
          'Edit generated SRT captions directly in the inline editor.',
          'Save edited captions to the server with the Save button.',
          'Download in SRT or VTT format.',
          'Caption generation costs 5 AI credits (confirmation popup shown before generation).',
        ],
      },
      {
        subtitle: 'Enhanced Upload',
        text: 'Tus protocol-based uploads have been further improved.',
        items: [
          'Adaptive Chunk Sizing: Chunk size automatically adjusts between 2MB and 20MB based on network speed.',
          'Upload speed and estimated time remaining are displayed in real-time.',
          'Interrupted uploads within 24 hours can be automatically resumed.',
        ],
      },
    ],
  },
  {
    id: 'image-upload',
    title: 'Image/Photo Upload',
    icon: PhotoIcon,
    content: [
      {
        subtitle: 'Photo Upload',
        text: 'onGo supports not only videos but also photo/image content. You can upload images to platforms that support photo posts, such as Instagram.',
        steps: [
          'When you select an image file on the upload page, it automatically switches to image mode.',
          'Select multiple images at once to create a multi-image story.',
          'Enter metadata such as title, description, and tags.',
          'Select target platforms and upload.',
        ],
      },
      {
        subtitle: 'Multi-Image Stories',
        text: 'You can bundle multiple images into a single post, similar to Instagram Stories.',
        items: [
          'Add up to 10 images per post.',
          'Reorder images with drag and drop.',
          'Each image supports up to 50MB.',
          'Supported formats: JPG, PNG, WebP, GIF, HEIC',
        ],
      },
      {
        subtitle: 'Differences Between Images and Videos',
        text: '',
        items: [
          'Image content is uploaded directly, same as video originals.',
          'Auto thumbnail generation and caption generation apply to videos only.',
          'Image content is distinguished by an image icon in the content list.',
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
  {
    id: 'ai-content-studio',
    title: 'AI Content Studio',
    icon: FilmIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'AI Content Studio is an integrated workspace that provides AI-powered tools for content creation, including video clip generation, automatic caption generation, and AI thumbnail creation.',
      },
      {
        subtitle: 'Clip Generation',
        text: 'AI automatically detects highlight segments from your original video and generates short-form clips.',
        steps: [
          'Click the "AI Clip Generation" button on the video detail page.',
          'AI analyzes the video and automatically extracts key segments.',
          'Select your preferred clips from the generated clip list.',
          'Fine-tune the start/end points manually if needed.',
          'Upload the selected clips directly to TikTok, Instagram Reels, and more.',
        ],
      },
      {
        subtitle: 'Caption Generation',
        text: 'AI analyzes the video content and automatically generates social media-optimized captions (post text).',
        steps: [
          'Select the target video from the "Caption Generation" tab.',
          'Set the tone (professional, casual, humorous, etc.) and length.',
          'Platform-optimized captions are generated.',
          'Edit the generated captions or apply them directly.',
        ],
      },
      {
        subtitle: 'Thumbnail Generation',
        text: 'AI analyzes key scenes in the video and automatically generates high-CTR thumbnail candidates.',
        items: [
          'Automatically extracts the most visually appealing frames from the video.',
          'Basic editing such as text overlays and filters is available.',
          'Generated thumbnails can be applied directly during upload.',
        ],
      },
      {
        subtitle: 'Credit Consumption',
        text: 'Each feature in AI Content Studio consumes AI credits.',
        items: [
          'Clip Generation: 10 credits',
          'Caption Generation: 3 credits',
          'Thumbnail Generation: 5 credits',
          'A credit deduction confirmation popup is shown before each operation.',
          'Features are disabled when remaining credits are insufficient.',
        ],
      },
    ],
  },
  {
    id: 'ai-performance-prediction',
    title: 'AI Performance Prediction',
    icon: PresentationChartLineIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'AI Performance Prediction helps you plan your content strategy by predicting expected views, engagement rates, and optimal posting times before uploading a video.',
      },
      {
        subtitle: 'Running Predictions',
        text: 'AI predicts performance based on your video metadata and historical channel data.',
        steps: [
          'Click the "Performance Prediction" button on the video detail or upload page.',
          'AI analyzes the title, description, tags, thumbnail, and other metadata.',
          'Compares your channel\'s historical performance data with current trends.',
          'Displays expected views, likes, comments, and optimal posting time as results.',
        ],
      },
      {
        subtitle: 'Reading the Heatmap',
        text: 'Performance prediction results include a time-based heatmap.',
        items: [
          'Darker colors indicate higher expected engagement for that time slot.',
          'The horizontal axis represents days of the week, and the vertical axis represents time slots.',
          'Click on the heatmap to immediately set a scheduled upload for that time.',
          'Different heatmaps are provided per platform, so check optimal times for each platform individually.',
        ],
      },
      {
        subtitle: 'Competitor Comparison',
        text: 'Compare your predicted performance against competitor channel averages.',
        items: [
          'View comparison charts of your predicted metrics against competitor channels.',
          'Provides strength/weakness analysis compared to category averages.',
          'Suggests metadata improvements to help boost performance.',
        ],
      },
    ],
  },
  {
    id: 'creator-portfolio',
    title: 'Creator Portfolio',
    icon: BriefcaseIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Creator Portfolio allows you to create media kits for brand collaborations, manage brand history, and export to PDF.',
      },
      {
        subtitle: 'Media Kit Editor',
        text: 'Create and edit a professional media kit to appeal to brands.',
        steps: [
          'Click "Portfolio" in the sidebar.',
          'Enter editing mode with the "Edit Media Kit" button.',
          'Enter your profile photo, bio, and channel statistics.',
          'Add featured content and collaboration case studies.',
          'Choose a theme and layout to finalize the design.',
        ],
      },
      {
        subtitle: 'Brand History Management',
        text: 'Systematically manage your past brand collaboration history.',
        items: [
          'Record collaboration brand names, dates, and campaign details.',
          'Display collaboration content performance data (views, engagement rate, etc.) linked from your analytics.',
          'Share your brand collaboration portfolio via a public link.',
        ],
      },
      {
        subtitle: 'PDF Export',
        text: 'Export your completed media kit as a PDF file.',
        items: [
          'Click the "Export PDF" button to convert the media kit to PDF.',
          'Choose between A4 or Letter size.',
          'Send the downloaded PDF directly to brands.',
        ],
      },
    ],
  },
  {
    id: 'social-commerce',
    title: 'Social Commerce Dashboard',
    icon: ShoppingCartIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'The Social Commerce Dashboard integrates with each social platform\'s shopping features to provide unified product management, sales tracking, and revenue analytics.',
      },
      {
        subtitle: 'Platform Integration',
        text: 'To use social commerce features, connect your platform shopping accounts.',
        steps: [
          'Click the "Connect Platform" button on the Social Commerce Dashboard.',
          'Select the commerce platform to connect (YouTube Shopping, TikTok Shop, Instagram Shopping, etc.).',
          'Complete the shopping account authentication for the platform.',
          'Product catalogs are automatically synced after connection.',
        ],
      },
      {
        subtitle: 'Product Management',
        text: 'Manage products from all connected platforms in one place.',
        items: [
          'View and manage products from all platforms on a single screen.',
          'Check key metrics like sales volume, click-through rate, and conversion rate per product.',
          'Link product tags to videos to enable in-video shopping features.',
          'Monitor inventory status and price changes in real-time.',
        ],
      },
      {
        subtitle: 'Revenue Tracking',
        text: 'Track your social commerce revenue in detail.',
        items: [
          'View revenue by platform, product, and time period in charts.',
          'Analyze sales contribution per video to identify top-performing content.',
          'Automatically calculate net revenue including fees and refunds.',
          'Auto-generate monthly/weekly revenue reports.',
        ],
      },
    ],
  },
  {
    id: 'agency-dashboard',
    title: 'Agency Dashboard',
    icon: BuildingOfficeIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'The Agency Dashboard is an integrated management tool for MCNs and agencies managing multiple creators. Group creators by workspace, compare performance, and provide reports to clients.',
      },
      {
        subtitle: 'Workspaces',
        text: 'Create and manage workspaces organized by project or team within the agency.',
        steps: [
          'Create a new workspace with the "Create Workspace" button.',
          'Invite creators to the workspace.',
          'Set permissions per workspace (Admin, Manager, Viewer).',
          'View integrated performance of all creators in the workspace dashboard.',
        ],
      },
      {
        subtitle: 'Creator Comparison',
        text: 'Compare and analyze performance across your roster of creators.',
        items: [
          'Compare key metrics like subscribers, views, and engagement rate by creator.',
          'Automatically classify top/bottom performers based on growth rate.',
          'Visualize content publishing frequency and performance trends per creator.',
          'Generate customized comparison reports.',
        ],
      },
      {
        subtitle: 'Client Portal',
        text: 'Provide a dedicated portal for advertiser/brand clients.',
        items: [
          'Generate dedicated login links for each client.',
          'Share campaign performance data in real-time.',
          'Precisely control the scope of data visible to clients.',
          'Send automated periodic reports to clients.',
        ],
      },
    ],
  },
  {
    id: 'ai-content-calendar',
    title: 'AI Content Calendar',
    icon: ClockIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'The AI Content Calendar is a feature where AI analyzes your channel data and trends to automatically suggest an optimal content publishing schedule.',
      },
      {
        subtitle: 'Period & Frequency Settings',
        text: 'Configure the basic settings for content calendar generation.',
        steps: [
          'Click "Create New Calendar" on the AI Content Calendar page.',
          'Set the planning period (1 week, 2 weeks, 1 month, 3 months).',
          'Set the publishing frequency per week (e.g., 3 times per week).',
          'Select target platforms and content types (short-form, long-form, etc.).',
          'AI automatically generates the optimal publishing schedule.',
        ],
      },
      {
        subtitle: 'Seasonal Events',
        text: 'AI optimizes the calendar by incorporating seasonal events and trends.',
        items: [
          'Automatically displays holidays, anniversaries, and seasonal events on the calendar.',
          'AI suggests event-related content topics automatically.',
          'Provides industry/category-specific seasonal events.',
          'Supports both global and local events.',
        ],
      },
      {
        subtitle: 'Schedule Application',
        text: 'Apply the AI-generated calendar to your actual upload schedule.',
        items: [
          'Apply calendar dates individually or in bulk to scheduled uploads.',
          'Freely rearrange schedules with drag and drop.',
          'Displays warnings when conflicts with existing scheduled uploads are detected.',
          'Share the calendar with team members for collaboration.',
        ],
      },
    ],
  },
  {
    id: 'workflow-builder',
    title: 'Workflow Builder',
    icon: CubeTransparentIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'The Workflow Builder is a visual workflow editor that lets you automate repetitive content publishing processes. Build complex automations with a node-based interface, no coding required.',
      },
      {
        subtitle: 'Adding Nodes',
        text: 'Add each step of the workflow as a node.',
        steps: [
          'Create a "New Workflow" in the Workflow Builder.',
          'Drag nodes from the left panel onto the canvas.',
          'Available nodes: Triggers (upload complete, scheduled time, etc.), Actions (AI processing, platform upload, etc.), Conditions (performance-based branching, etc.)',
          'Click each node to configure its detailed settings.',
        ],
      },
      {
        subtitle: 'Connections',
        text: 'Define execution order and data flow between nodes using connections.',
        items: [
          'Drag from a node\'s output port to the next node\'s input port to connect them.',
          'Condition nodes allow "Yes/No" branches connected to different nodes.',
          'Connect one output to multiple nodes for parallel execution.',
          'Click a connection to configure data mapping.',
        ],
      },
      {
        subtitle: 'Templates',
        text: 'Save frequently used workflows as templates for reuse.',
        items: [
          'Built-in templates: Multi-platform auto-upload, AI metadata generation then upload, performance-based auto-repost, and more.',
          'Save your own workflows as templates for reuse.',
          'Share templates with team members.',
        ],
      },
      {
        subtitle: 'Test Run',
        text: 'Test your workflow before applying it live.',
        items: [
          'Simulate the workflow with the "Test Run" button.',
          'Monitor each node\'s execution results and data flow in real-time.',
          'Errors are highlighted with a red indicator on the affected node, with cause details.',
          'After passing the test, click "Activate" to apply the workflow live.',
        ],
      },
    ],
  },
  {
    id: 'ai-video-resizer',
    title: 'AI Video Resizer',
    icon: ScissorsIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'AI Video Resizer automatically converts a single original video to the optimal aspect ratio and resolution for each platform. AI recognizes subjects in the frame to perform smart cropping.',
      },
      {
        subtitle: 'Platform Presets',
        text: 'Presets matching each platform\'s recommended specifications are provided.',
        items: [
          'YouTube: 16:9 (1920x1080), Shorts 9:16 (1080x1920)',
          'TikTok: 9:16 (1080x1920)',
          'Instagram Reels: 9:16 (1080x1920), Feed 1:1 (1080x1080), Feed 4:5 (1080x1350)',
          'Naver Clip: 9:16 (1080x1920)',
          'You can also set custom aspect ratios and resolutions.',
        ],
      },
      {
        subtitle: 'Smart Crop',
        text: 'AI recognizes subjects (faces, objects, etc.) in the video and automatically adjusts the crop area to ensure important elements are not cut off.',
        items: [
          'Face Detection: Ensures faces are always within the frame in videos featuring people.',
          'Motion Tracking: The crop area automatically follows moving subjects.',
          'Manual Adjustment: Preview AI crop results and adjust manually if needed.',
        ],
      },
      {
        subtitle: 'Job Management',
        text: 'Efficiently manage your resize jobs.',
        items: [
          'Select multiple presets to run batch resizing in one go.',
          'Monitor progress in real-time from the job queue.',
          'Upload completed resized videos directly to platforms.',
          'Re-download previous resize results from the job history.',
        ],
      },
    ],
  },
  {
    id: 'subtitle-editor',
    title: 'Subtitle Editor',
    icon: LanguageIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'The Subtitle Editor is a professional tool that supports the entire subtitle workflow, from AI auto-generation to timeline-based editing and export in various formats.',
      },
      {
        subtitle: 'AI Subtitle Generation',
        text: 'Uses OpenAI Whisper STT to automatically convert video audio into subtitles.',
        steps: [
          'Select the target video in the Subtitle Editor.',
          'Choose the subtitle language (Korean, English, etc.).',
          'Click the "AI Subtitle Generation" button (costs 5 credits).',
          'Once generation is complete, subtitles are automatically placed on the timeline.',
        ],
      },
      {
        subtitle: 'Editing',
        text: 'Precisely edit generated subtitles in the timeline-based editor.',
        items: [
          'Drag subtitle blocks on the timeline to adjust start/end times.',
          'Directly edit subtitle text, split, or merge segments.',
          'Sync with the video preview to check subtitle timing in real-time.',
          'Set styles including font, size, color, and position.',
          'Use keyboard shortcuts for faster editing.',
        ],
      },
      {
        subtitle: 'Export (SRT/VTT/ASS)',
        text: 'Export your edited subtitles in various formats.',
        items: [
          'SRT: The most universal subtitle format, supported by most platforms.',
          'VTT (WebVTT): Web standard subtitle format, compatible with HTML5 video.',
          'ASS (Advanced SubStation Alpha): Feature-rich subtitle format, capable of complex styling.',
          'Choose encoding (UTF-8, EUC-KR, etc.) when exporting.',
        ],
      },
    ],
  },
  {
    id: 'competitor-analysis',
    title: 'Competitor Deep Analysis',
    icon: MagnifyingGlassCircleIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Competitor Deep Analysis provides in-depth analysis of competitor channels\' content strategies, performance trends, and undiscovered topics to help you build a differentiated content strategy.',
      },
      {
        subtitle: 'Adding Competitors',
        text: 'Add and manage the competitor channels you want to analyze.',
        steps: [
          'Click the "Add Competitor" button on the Competitor Analysis page.',
          'Search by competitor channel URL or name.',
          'Select and add the target channel.',
          'You can register up to 10 channels as competitors.',
        ],
      },
      {
        subtitle: 'Content Gap Analysis',
        text: 'AI analyzes topics covered by competitor channels that your channel hasn\'t explored yet (content gaps).',
        items: [
          'Compares popular topics from competitor channels with your channel\'s topics.',
          'Provides content suggestions for undiscovered topics.',
          'Displays expected performance and competition intensity together.',
          'Add recommended topics directly to your content calendar.',
        ],
      },
      {
        subtitle: 'Trend Topics',
        text: 'Monitor trending topics and rising subjects within your category in real-time.',
        items: [
          'Displays currently rising topics and keywords in real-time.',
          'AI analyzes the trend start time and expected duration.',
          'View competitor channels\' content publishing status for trending topics.',
          'Early Adopter Opportunities: Prioritizes new trends with low competition.',
        ],
      },
    ],
  },
  {
    id: 'brand-voice',
    title: 'AI Brand Voice Engine',
    icon: ChatBubbleBottomCenterTextIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Train your brand\'s unique tone and manner with AI, and generate content with a consistent brand voice.',
      },
      {
        subtitle: 'Voice Profile Management',
        text: 'AI learns your writing style based on your existing content.',
        items: [
          'Profile Training: Input your existing content text for AI to learn your writing style.',
          'Tone Settings: Choose from 5 tones — Casual, Professional, Humorous, Educational, and Inspirational.',
          'Vocabulary Management: Set frequently used words and words to avoid.',
          'Emoji Settings: Adjust emoji frequency in 4 levels — None, Minimal, Moderate, and Frequent.',
        ],
      },
      {
        subtitle: 'AI Text Generation',
        text: 'Select a trained voice profile and enter a topic to generate text that matches your brand tone.',
        items: [
          'Platform-specific optimization (YouTube, TikTok, Instagram, Naver Clip) is supported.',
          'Automatic hashtag generation option is available.',
          'Verify brand consistency with a confidence score.',
        ],
      },
      {
        subtitle: 'Text Analysis',
        text: 'Paste existing text to analyze tone, sentence length, frequently used words, emoji frequency, and more.',
        items: [
          'Provides readability score and formality score.',
        ],
      },
      {
        subtitle: 'Credit Cost',
        text: 'Text generation costs 3 credits, and analysis costs 2 credits.',
      },
    ],
  },
  {
    id: 'cross-analytics',
    title: 'Cross-Platform Analytics',
    icon: ArrowsPointingOutIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Compare and analyze the performance of the same content uploaded across multiple platforms at a glance.',
      },
      {
        subtitle: 'Overview Dashboard',
        text: 'Get a comprehensive view of performance across platforms.',
        items: [
          'Summary cards showing total views and likes per platform.',
          'Platform Share Chart: Visualizes the proportion of total views each platform accounts for.',
          'AI Recommended Strategy: Receive data-driven optimization suggestions for each platform.',
        ],
      },
      {
        subtitle: 'Content Comparison',
        text: 'Compare the performance of the same content across platforms in a table format.',
        items: [
          'Views, likes, and CTR are displayed side by side for each platform.',
          'Top-performing platforms are automatically highlighted for each metric.',
        ],
      },
      {
        subtitle: 'Audience Overlap Analysis',
        text: 'Visualize audience overlap rates between platform pairs.',
        items: [
          'Platforms with high overlap rates are recommended differentiation strategies.',
          'Platforms with low overlap rates are recommended cross-promotion strategies.',
        ],
      },
      {
        subtitle: 'Period Settings',
        text: 'Choose analysis periods of 7 days, 30 days, or 90 days.',
      },
    ],
  },
  {
    id: 'growth-coach',
    title: 'Creator Growth Coach',
    icon: AcademicCapIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Accelerate your channel growth with AI-powered growth coaching.',
        items: [
          'Overall Score: Assess your channel growth status at a glance with a 0-100 score.',
          'Growth Metrics: Track changes in subscribers, views, and engagement rates in real-time.',
          'Priority Insights: View the top 3 most impactful insights immediately.',
        ],
      },
      {
        subtitle: 'Goal Management',
        text: 'Set goals for subscribers, views, engagement rate, revenue, upload frequency, and more.',
        items: [
          'Track progress with visual progress bars.',
          'Automatically calculates remaining days until the deadline.',
        ],
      },
      {
        subtitle: 'Weekly Reports',
        text: 'AI automatically analyzes channel performance each week and generates reports.',
        items: [
          'Displays highlights (strengths) and concerns (areas for improvement) separately.',
          'Provides actionable items as a checklist.',
          'Compare with past reports to identify trends.',
        ],
      },
      {
        subtitle: 'AI Insights',
        text: 'Filter insights by impact level (High, Medium, Low).',
        items: [
          'Actionable insights include specific recommended actions.',
        ],
      },
      {
        subtitle: 'Credit Cost',
        text: 'Report generation costs 10 credits.',
      },
    ],
  },
  {
    id: 'copyright-check',
    title: 'Copyright Pre-Check',
    icon: ShieldCheckIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Verify copyright issues before uploading videos and predict the impact on monetization.',
      },
      {
        subtitle: 'Video Selection and Scanning',
        text: 'Select videos to scan from the list of videos pending upload.',
        items: [
          'Music Check: Verifies the copyright status of background music.',
          'Content Check: Checks for content policy violations in images and video.',
          'Brand Check: Detects brand logo and trademark exposure.',
        ],
      },
      {
        subtitle: 'Platform-Specific Validation',
        text: 'Validates against the policies of each platform — YouTube, TikTok, Instagram, and Naver Clip.',
        items: [
          'View pass/warning/blocked status for each platform at a glance.',
        ],
      },
      {
        subtitle: 'Scan Results',
        text: 'Scan results are classified into 3 levels.',
        items: [
          'PASSED: No issues found in all scan items (green).',
          'WARNING: Some issues found but upload is possible (yellow).',
          'BLOCKED: Serious copyright issues that may result in sanctions upon upload (red).',
        ],
      },
      {
        subtitle: 'Issue Details',
        text: 'View detailed information for each issue and resolve them.',
        items: [
          'Each issue\'s severity (Info, Warning, Critical) is color-coded.',
          'Time range is displayed for music-related issues.',
          'AI-suggested resolution methods are provided.',
          'Auto-Fix: Some issues can be automatically fixed by AI.',
        ],
      },
      {
        subtitle: 'Monetization Eligibility',
        text: 'Displays monetization eligibility based on scan results.',
      },
      {
        subtitle: 'Credit Cost',
        text: 'Each scan costs 3 credits.',
      },
    ],
  },
  {
    id: 'ab-test-manager',
    title: 'A/B Test Manager',
    icon: BeakerIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'The A/B Test Manager lets you compare different variations of video metadata such as thumbnails, titles, and descriptions to determine the optimal combination through data-driven decisions.',
      },
      {
        subtitle: 'Creating Tests',
        text: 'Create a new A/B test.',
        steps: [
          'Click the "Create New Test" button on the A/B Test page.',
          'Select the target video for testing.',
          'Choose the test type (thumbnail, title, description).',
          'Enter content for each variation (A, B, up to 4).',
          'Set the test duration and success metric (CTR, views, engagement rate, etc.).',
          'Click "Start Test" to begin the test.',
        ],
      },
      {
        subtitle: 'Variation Comparison',
        text: 'Compare the performance of each variation in real-time during an active test.',
        items: [
          'Compare impressions, clicks, CTR, views, and more for each variation in real-time charts.',
          'Statistical significance (Confidence Level) is automatically calculated and displayed.',
          'AI automatically identifies the winning variation when sufficient data is collected.',
          'Monitor performance trends for each variation even mid-test.',
        ],
      },
      {
        subtitle: 'Applying the Winner',
        text: 'Apply the best-performing variation when the test is complete.',
        items: [
          'AI automatically recommends the winning variation based on statistical significance.',
          'Click "Apply Winner" to immediately apply the winning variation to the video.',
          'Save test result reports and review past test history.',
          'Leverage test insights for future content strategy.',
        ],
      },
    ],
  },
  {
    id: 'thumbnail-generator',
    title: 'AI Thumbnail Generator',
    icon: PhotoIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'The AI Thumbnail Generator automatically designs and creates high-quality thumbnails optimized for your video content. It analyzes visual elements that maximize click-through rate (CTR) and produces professional designer-level thumbnails.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'AI Auto Design: Automatically generates optimal thumbnail layouts by analyzing video content and titles.',
          'Text Overlay: Automatically places titles, subtitles, and other text, recommending highly readable fonts and colors.',
          'Background Removal/Compositing: Automatically removes backgrounds from subject photos and composites them with new backgrounds.',
          'Style Templates: Provides popular thumbnail style templates by category (gaming, beauty, food, education, etc.).',
          'CTR Prediction: AI displays the predicted click-through rate of generated thumbnails as a score.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click the "AI Thumbnail Generation" button on the video detail page or upload screen.',
          'Select a thumbnail style (modern, impact, minimal, etc.).',
          'Enter text overlay content or choose AI auto-generation.',
          'AI generates 4-6 thumbnail candidates.',
          'Select your preferred thumbnail, fine-tune if needed, and apply.',
        ],
      },
      {
        subtitle: 'Credit Cost',
        text: 'Each thumbnail generation costs 5 credits.',
      },
    ],
  },
  {
    id: 'performance-heatmap',
    title: 'Performance Heatmap',
    icon: TableCellsIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'The Performance Heatmap provides a 7x24 grid-based heatmap organized by day of the week (7 days) and time of day (24 hours), enabling you to visually identify the most effective time slots for content uploads.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          '7x24 Heatmap: Visualizes performance metrics such as views, engagement rate, and CTR by day and time using color intensity.',
          'Platform Filter: View individual heatmaps for each platform — YouTube, TikTok, Instagram, Naver Clip, and more.',
          'Metric Switching: Switch the heatmap between views, likes, comments, CTR, engagement rate, and other metrics.',
          'Period Settings: Freely set the analysis period — last 7 days, 30 days, 90 days, 1 year, etc.',
          'Optimal Time Recommendations: AI analyzes the data and automatically recommends the top 5 optimal upload times.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Performance Heatmap" from the analytics menu.',
          'Select the platform and metric to analyze.',
          'Identify cells with darker colors (high-performance time slots) on the heatmap.',
          'Click any cell to view detailed performance data for that time slot.',
          'Use AI-recommended times to set up scheduled uploads.',
        ],
      },
    ],
  },
  {
    id: 'live-dashboard',
    title: 'Live Dashboard',
    icon: BoltIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'The Live Dashboard is an integrated monitoring feature that tracks key metrics from all connected platforms in real-time and provides instant alerts when anomalies are detected.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Real-Time Metrics: Key metrics such as views, subscribers, likes, and comment counts are automatically refreshed.',
          'Auto-Refresh: Data refreshes automatically at 30-second, 1-minute, or 5-minute intervals, and the interval is configurable.',
          'Alert System: Instantly sends alerts when anomalies occur, such as subscriber surges, unusual view changes, or spikes in negative comments.',
          'Custom Widgets: Freely add, remove, and arrange widgets displayed on the dashboard.',
          'Multi-Channel View: Monitor metrics from multiple channels simultaneously on a single screen.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Live Dashboard" in the sidebar.',
          'Select the channels and metrics to monitor.',
          'Set alert conditions (thresholds), e.g., alert when subscribers increase by 100+ within 1 hour.',
          'Customize the dashboard layout to your preference.',
          'The real-time data stream starts automatically.',
        ],
      },
    ],
  },
  {
    id: 'revenue-forecaster',
    title: 'Revenue Forecaster',
    icon: CurrencyDollarIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'The Revenue Forecaster uses AI to analyze your channel\'s historical revenue data and growth trends, predict future expected revenue, and suggest revenue optimization strategies.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'AI Revenue Prediction: Comprehensively analyzes past revenue patterns, subscriber growth rate, content publishing frequency, and more to calculate expected revenue for 1 month, 3 months, 6 months, and 1 year ahead.',
          'Scenario Analysis: Compare expected revenue across 3 scenarios — optimistic, moderate, and pessimistic.',
          'Revenue Source Breakdown: Provides predictions broken down by revenue source — ad revenue, donations/Super Chat, memberships, commerce, etc.',
          'Growth Factor Analysis: Identifies the factors that have the greatest impact on revenue (upload frequency, engagement rate, subscribers, etc.).',
          'Optimization Suggestions: AI recommends specific action items to maximize revenue.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Revenue Forecaster" from the analytics menu.',
          'Select the forecast period (1 month to 1 year).',
          'Choose the revenue sources to include in the forecast.',
          'AI generates the revenue forecast report.',
          'Review scenario-based predictions and optimization suggestions.',
        ],
      },
      {
        subtitle: 'Credit Cost',
        text: 'Each revenue forecast report generation costs 8 credits.',
      },
    ],
  },
  {
    id: 'content-rewriter',
    title: 'AI Content Rewriter',
    icon: DocumentTextIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'The AI Content Rewriter automatically transforms a single piece of content for multiple platforms. It generates text optimized for each platform\'s characteristics, including character limits, hashtag styles, and tone.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Platform Optimization: Automatically converts content to the appropriate format for YouTube descriptions, TikTok captions, Instagram captions, Naver blog posts, and more.',
          'Tone Transformation: Converts the same content into various tones — professional, casual, humorous, educational, etc.',
          'Length Control: Adjusts content to desired lengths — short-form (under 30 characters), medium (100 characters), long-form (500+ characters).',
          'Auto Hashtag Generation: Automatically adds optimal hashtags for each platform.',
          'Multilingual Conversion: Automatically translates Korean content to English, Japanese, and other languages.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Content Rewriter" from the AI tools menu.',
          'Enter the original text or import an existing video description.',
          'Select the target platforms for conversion (multiple selection available).',
          'Set tone and length options.',
          'Click "Convert" to generate platform-optimized text.',
          'Review the results, edit if needed, and apply directly.',
        ],
      },
      {
        subtitle: 'Credit Cost',
        text: 'Each content conversion costs 3 credits (same regardless of the number of platforms).',
      },
    ],
  },
  {
    id: 'social-listening',
    title: 'Social Listening',
    icon: MegaphoneIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Social Listening monitors brand mentions across social media and uses sentiment analysis to track public opinion trends in real-time.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Brand Mention Monitoring: Collects posts mentioning your channel name, brand name, and related keywords in real-time.',
          'Sentiment Analysis: AI automatically classifies mentions by sentiment (positive, neutral, negative) and visualizes the ratios.',
          'Trend Tracking: Tracks time-series changes in mention volume to detect spikes and dips.',
          'Competitor Comparison: Compare and analyze mention volume and sentiment against competitor channels.',
          'Alert Settings: Configure conditional alerts for negative mention surges, specific keyword detection, and more.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Social Listening" from the analytics menu.',
          'Register keywords to monitor (channel name, brand name, etc.).',
          'Set the monitoring scope (platforms, time period).',
          'View mention status, sentiment distribution, and trend charts on the dashboard.',
          'Click individual mentions to view the original text and respond directly.',
        ],
      },
    ],
  },
  {
    id: 'influencer-match',
    title: 'Influencer Matching',
    icon: UserPlusIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Influencer Matching uses AI to analyze channel data, content types, and audience characteristics to automatically recommend optimal collaboration partners.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'AI Matching Algorithm: Calculates a compatibility score by comprehensively analyzing category, audience size, engagement rate, content style, and more.',
          'Audience Overlap Analysis: Analyzes viewer overlap between two channels to predict synergy effects.',
          'Category Filter: Search and filter by categories such as beauty, gaming, food, education, tech, and more.',
          'Collaboration History Management: Record and track the performance of past collaboration partners.',
          'Contact Management: Manage contact information and collaboration notes for influencers of interest.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Influencer Matching" in the sidebar.',
          'Set desired collaboration criteria (category, subscriber range, platform, etc.).',
          'AI recommends a list of influencers matching your criteria, sorted by compatibility score.',
          'Review detailed profiles and channel statistics for recommended influencers.',
          'Add influencers of interest to favorites and propose collaborations.',
        ],
      },
      {
        subtitle: 'Credit Cost',
        text: 'Each influencer search costs 5 credits.',
      },
    ],
  },
  {
    id: 'quality-score',
    title: 'Content Quality Score',
    icon: ClipboardDocumentCheckIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Content Quality Score is a feature where AI comprehensively evaluates your content quality before upload, assigns a score from 0 to 100, and suggests improvement points.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Overall Quality Score: Evaluates video, audio, metadata, SEO, and more across multiple dimensions to produce a comprehensive score from 0 to 100.',
          'Category Subscores: Provides individual scores for video quality (resolution, FPS, stability), audio quality (volume, noise), metadata (title, description, tags), SEO optimization, and more.',
          'Improvement Suggestions: AI suggests specific improvement methods for low-scoring items.',
          'Benchmark Comparison: Compare your score against the average scores of top creators in the same category.',
          'History Tracking: Track quality score trends across your past content.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click the "Quality Score" button on the video detail page or upload screen.',
          'AI comprehensively analyzes the video file and metadata.',
          'Review the overall score and category-specific subscores.',
          'Refer to improvement suggestions to enhance your content.',
          'Re-scan after making changes to confirm score improvements.',
        ],
      },
      {
        subtitle: 'Credit Cost',
        text: 'Each quality score scan costs 3 credits.',
      },
    ],
  },
  {
    id: 'content-series',
    title: 'Content Series Manager',
    icon: RectangleStackIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Content Series Manager lets you systematically plan, manage, and analyze series-based content. Track performance by episode and get an at-a-glance view of overall series trends.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Series Creation & Management: Create and manage series by setting title, description, platform, upload frequency (daily/weekly/biweekly/monthly), and more.',
          'Episode Tracking: Track each episode through statuses: Planned, Drafted, Published, and Skipped.',
          'Series Analytics: Analyze key metrics including views trend by episode, subscriber growth rate, average engagement rate, and drop-off rate.',
          'Best Episode: Automatically identifies and highlights your best-performing episode.',
          'Tag Management: Add tags to series for easy categorization and quick searching.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Series Manager" in the sidebar.',
          'Click "Create New Series" and enter series information.',
          'Add episodes to the series and set their schedule.',
          'After publishing episodes, review performance in the Series Analytics panel.',
          'Change series status to Active, Paused, or Completed as needed.',
        ],
      },
    ],
  },
  {
    id: 'fan-funding',
    title: 'Fan Funding Tracker',
    icon: HeartIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Fan Funding Tracker consolidates and analyzes fan support revenue from various platforms including Super Chat, Memberships, Super Thanks, TikTok Gifts, Instagram Badges, and Naver Star Balloons.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Unified Revenue Dashboard: View key metrics at a glance including total revenue, this month/last month revenue, and growth rate.',
          'Transaction History: Filter and view transaction details by source (Super Chat, Membership, etc.) and platform.',
          'Daily Trends: Visualize daily revenue changes with interactive charts.',
          'Top Donors: View rankings of your most supportive fans.',
          'Funding Goals: Set revenue goals and track achievement rate in real-time.',
          'Period Selection: Analyze data across 7-day, 30-day, and 90-day periods.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Fan Funding Tracker" in the sidebar.',
          'Review overall revenue status and trends in the Overview tab.',
          'Filter detailed transactions by source and platform in the Transactions tab.',
          'Click "New Goal" in the Goals tab to set funding targets.',
          'Monitor goal achievement and modify or delete goals as needed.',
        ],
      },
    ],
  },
  {
    id: 'media-kit',
    title: 'Media Kit Generator',
    icon: DocumentArrowUpIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Media Kit Generator uses AI to analyze your channel data and automatically create professional media kits for brand collaborations. Include platform statistics, audience demographics, campaign results, and rate cards.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Template Selection: Choose from 4 template styles — Modern, Classic, Minimal, and Creative.',
          'Auto Platform Stats: Automatically collects follower count, average views, engagement rate, and growth rate from each connected platform.',
          'Rate Cards: Set pricing for sponsored videos, product placement, stories, short-form, live streams, and bundles.',
          'Campaign Results: Include past brand collaboration results (views, engagement, ROI).',
          'Publish & Share: Publish your media kit online and generate a shareable link.',
          'PDF Download: Download your media kit as a PDF to send directly to brands.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Media Kit Generator" in the sidebar.',
          'Select your preferred template style.',
          'Configure whether to include rate cards and campaign results.',
          'Click "Generate Media Kit" and AI will automatically create the kit.',
          'Edit the generated kit, then click "Publish" to create a shareable link.',
        ],
      },
      {
        subtitle: 'Credit Cost',
        text: 'Each media kit generation costs 5 credits.',
      },
    ],
  },
  {
    id: 'smart-reply',
    title: 'Smart Reply',
    icon: ChatBubbleBottomCenterTextIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Smart Reply uses AI to automatically suggest optimal responses to comments, DMs, and mentions, and allows you to set rule-based auto-replies. Efficiently manage fan engagement and communication.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'AI Reply Suggestions: Analyzes comment sentiment (positive/negative/neutral) and suggests contextually appropriate replies in multiple tones (friendly/professional/casual/grateful/humorous).',
          'Reply Rules: Create rules that automatically respond to comments containing specific keywords.',
          'Auto-Send: Configure rules to auto-send replies or require manual review before sending.',
          'Statistics Dashboard: View stats including total replies sent, average response time, sentiment distribution, and replies by platform.',
          'Settings Management: Customize default tone, daily auto-reply limit, excluded keywords, and reply delay time.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Smart Reply" in the sidebar.',
          'Review AI-generated reply suggestions in the Suggestions tab and send the best match.',
          'Click "New Rule" in the Rules tab to create auto-reply rules.',
          'Monitor reply activity and fan reactions in the Stats tab.',
          'Adjust default tone and auto-reply options in Settings.',
        ],
      },
      {
        subtitle: 'Credit Cost',
        text: 'Each AI reply suggestion costs 1 credit. Rule-based replies do not consume credits.',
      },
    ],
  },
  {
    id: 'audience-segments',
    title: 'Audience Segmentation',
    icon: UsersIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Audience Segmentation classifies your subscribers and viewers by various criteria (age, gender, region, interests, behavior patterns) to help you develop optimized content strategies for each group.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Auto Segments: AI analyzes viewing data to automatically generate segments based on age, gender, region, interests, and behavior patterns.',
          'Custom Segments: Create your own segments by combining desired conditions.',
          'Segment Insights: View detailed analysis including viewing patterns, engagement rates, growth rates, and preferred content types for each segment.',
          'Segment Comparison: Select two or more segments to compare key metrics side by side.',
          'Target Content Recommendations: Get recommendations for the most effective content topics, upload times, and thumbnail styles for each segment.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Audience Segments" in the sidebar.',
          'View existing segment list with size and engagement rate in the grid view.',
          'Click "New Segment" to create a custom segment.',
          'Click a segment to open the detailed insights panel.',
          'Use the Compare tab to select and compare multiple segments.',
        ],
      },
    ],
  },
  {
    id: 'content-rights',
    title: 'Content Rights Manager',
    icon: ShieldExclamationIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Content Rights Manager systematically tracks and manages copyrights for music, images, video sources, and other assets used in your uploaded content. It provides license expiration alerts and alternative asset recommendations.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Rights Registration: Register and manage license information for assets (music, images, fonts, video clips) used in your content.',
          'Expiration Alerts: Receive automatic notifications when licenses are about to expire so you can take proactive action.',
          'Risk Analysis: Visually identify each asset\'s copyright risk level (safe/caution/danger/expired) with color-coded indicators.',
          'Alternative Asset Suggestions: Get recommendations for free or properly licensed alternative assets when copyright issues are detected.',
          'Summary Dashboard: View overall asset status, distribution by status, and upcoming license expirations at a glance.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Content Rights" in the sidebar.',
          'Review overall copyright status and alerts on the dashboard.',
          'Click "New Rights Entry" to register license information for an asset.',
          'Check the Alerts tab for expiring or at-risk assets and take action.',
          'Use alternative asset recommendations to replace problematic assets.',
        ],
      },
    ],
  },
  {
    id: 'creator-academy',
    title: 'Creator Academy',
    icon: AcademicCapIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Creator Academy is a learning platform that provides educational content on content creation, editing, marketing, AI tools, and more. Earn AI credits as rewards for completing courses.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Category Courses: Courses across 8 categories including filming, editing, marketing, AI tools, monetization, growth, branding, and analytics.',
          'Difficulty Levels: Courses are categorized as beginner, intermediate, or advanced so you can choose the right level.',
          'Progress Tracking: Track your learning progress, streak days, total watch time, and completion rate.',
          'Credit Rewards: Earn AI credits as a reward for completing each course.',
          'Weakness Analysis: AI analyzes your learning data to identify weak areas and recommend relevant courses.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Creator Academy" in the sidebar.',
          'Review your learning status on the progress dashboard at the top.',
          'Use category filters to find courses in your desired field.',
          'Click a course card to view details and enroll.',
          'Progress updates automatically as you complete lessons, and credits are awarded upon course completion.',
        ],
      },
    ],
  },
  {
    id: 'multi-brand-calendar',
    title: 'Multi-Brand Calendar',
    icon: CalendarDaysIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Multi-Brand Calendar enables agencies and MCNs to manage content schedules for multiple brands and channels from a single unified view. Features include color-coded brand identification, conflict detection, and assignee management.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Brand Management: Register multiple brands with color-coded identification, assign categories and editors.',
          'Unified Calendar: View all brand schedules in one calendar with brand-specific filtering.',
          'Conflict Detection: Automatically detect and alert when multiple content items are scheduled at overlapping times.',
          'Schedule Management: Create, edit, and delete content upload schedules for each brand.',
          'Status Tracking: Track each schedule\'s status (draft/scheduled/publishing/published/failed) in real-time.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Multi-Brand Calendar" in the sidebar.',
          'Check total brands, active brands, and scheduled items this week in the summary bar.',
          'Click "New Brand" to register a brand.',
          'Use brand filters to display schedules for specific brands.',
          'Click "New Schedule" to create a content upload schedule.',
          'When conflict alerts appear, adjust schedules to avoid overlapping times.',
        ],
      },
    ],
  },
  {
    id: 'script-writer',
    title: 'AI Script Writer',
    icon: PencilSquareIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'AI Script Writer automatically generates complete video scripts based on your topic, format, and tone preferences. Create structured scripts with hooks, intros, body sections, CTAs, and outros optimized for your content style.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'AI Generation: Enter a topic, format (long-form/short-form/tutorial/review/vlog/interview), tone (casual/professional/humorous/educational/dramatic/friendly), and target length to generate a complete script.',
          'Section Structure: Scripts are divided into sections (HOOK/INTRO/BODY/CTA/OUTRO/TRANSITION) with estimated duration for each.',
          'Script Editor: Edit generated scripts section by section, reorder sections, and fine-tune content.',
          'Templates: Choose from pre-built script templates for common content formats.',
          'Keyword Integration: Add target keywords to ensure SEO-optimized scripts.',
          'Script Management: Save scripts as drafts, mark for review, finalize, or archive.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Script Writer" in the sidebar.',
          'Click "New Script" to open the generation modal.',
          'Enter your topic, select format and tone, set target length, and add keywords.',
          'Click "Generate" and AI will create a structured script.',
          'Edit the generated script in the editor, rearrange sections as needed.',
          'Save as draft or mark as finalized when complete.',
        ],
      },
      {
        subtitle: 'Credit Cost',
        text: 'Each script generation costs 5 credits. Section regeneration costs 2 credits.',
      },
    ],
  },
  {
    id: 'collaboration-board',
    title: 'Collaboration Board',
    icon: ViewColumnsIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Collaboration Board is a Kanban-style project management tool designed for content creation teams. Track tasks through 7 workflow stages from idea to publication, manage assignees, priorities, and deadlines.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          '7-Stage Kanban Board: Track tasks through IDEA → SCRIPTING → FILMING → EDITING → REVIEW → SCHEDULED → PUBLISHED stages.',
          'Priority Management: Assign LOW/MEDIUM/HIGH/URGENT priority levels with color-coded indicators.',
          'Task Cards: Each task shows title, priority, assignee, due date, tags, attachments, and comment counts.',
          'Drag & Move: Move tasks between columns using left/right navigation buttons.',
          'WIP Limits: Set work-in-progress limits per column to prevent bottlenecks.',
          'Overdue Tracking: Tasks past their due date are highlighted in red for immediate attention.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Collaboration Board" in the sidebar.',
          'Review summary stats showing total tasks, completed, overdue, and in-progress counts.',
          'Click "Add Task" at the bottom of any column to create a new task.',
          'Click a task card to view full details including description and tags.',
          'Use the arrow buttons on task cards to move them between workflow stages.',
          'Monitor overdue tasks and adjust priorities as needed.',
        ],
      },
    ],
  },
  {
    id: 'sponsorship-tracker',
    title: 'Sponsorship Tracker',
    icon: GiftIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Sponsorship Tracker helps you manage brand deals from initial inquiry through payment completion. Track deal pipelines, manage deliverables, monitor revenue, and ensure timely payments.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Deal Pipeline: Track sponsorships through 7 stages — Inquiry, Negotiation, Contracted, In Progress, Delivered, Paid, and Cancelled.',
          'Revenue Dashboard: View monthly revenue charts and total earnings at a glance.',
          'Deliverable Management: Track individual deliverables (dedicated video, PPL, short-form, story, post, live) with platform and deadline info.',
          'Payment Tracking: Monitor payment status (pending/invoiced/paid/overdue) for each deal.',
          'Deal Details: View brand info, contact person, deal amount, dates, and completion progress in a detailed sidebar.',
          'Status Filtering: Filter deals by pipeline stage to focus on specific workflow phases.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Sponsorship Tracker" in the sidebar.',
          'Review summary cards showing total deals, active deals, total revenue, and outstanding payments.',
          'Click "New Deal" to register a new brand sponsorship.',
          'Use status filter tabs to view deals at specific pipeline stages.',
          'Click a deal card to open the detail sidebar with deliverable list.',
          'Check off deliverables as they are completed to track progress.',
        ],
      },
    ],
  },
  {
    id: 'playlist-manager',
    title: 'Playlist Manager',
    icon: QueueListIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Playlist Manager lets you create, organize, and sync playlists across YouTube, TikTok, Instagram, and Naver Clip from a single interface. Manage video collections, track performance, and keep playlists synchronized.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Multi-Platform Support: Manage playlists for YouTube, TikTok, Instagram, and Naver Clip in one place.',
          'Playlist Statistics: View total playlists, videos, views, and platform distribution with a donut chart.',
          'Platform Filtering: Filter playlists by platform to focus on specific channels.',
          'Sync Management: Sync individual playlists or all playlists at once to keep them up to date.',
          'Visibility Settings: Set playlists as public, unlisted, or private.',
          'Tag Organization: Add tags to playlists for easy categorization and search.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Playlist Manager" in the sidebar.',
          'Review playlist statistics and platform distribution at the top.',
          'Use the platform filter to view playlists for a specific platform.',
          'Click "New Playlist" to create a playlist with title, description, platform, visibility, and tags.',
          'Click "Sync All" to synchronize all playlists with their respective platforms.',
          'Click a playlist card to view details, or use the sync button for individual playlist updates.',
        ],
      },
    ],
  },
  {
    id: 'hashtag-analytics',
    title: 'AI Hashtag Analytics',
    icon: HashtagIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'AI Hashtag Analytics analyzes the performance of your hashtags, identifies trends, and recommends optimal hashtags for your content. Save frequently used hashtag combinations as groups for easy reuse.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Performance Analysis: Track usage count, total views, average engagement rate, and growth rate for each hashtag.',
          'Trend Direction: Displays whether each hashtag is trending UP, DOWN, or STABLE.',
          'AI Recommendations: Enter a topic and platform to get AI-powered hashtag recommendations with relevance scores.',
          'Hashtag Groups: Save frequently used hashtag combinations as named groups for quick reuse.',
          'Competition Analysis: View competition level (Low/Medium/High) and expected reach for recommended hashtags.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "AI Hashtag Analytics" in the sidebar.',
          'Review existing hashtag performance in the Performance tab.',
          'In the Recommendations tab, enter a topic and platform, then click "Analyze".',
          'Review recommended hashtags and select the ones to use.',
          'Save frequently used combinations as groups in the Groups tab.',
        ],
      },
      {
        subtitle: 'Credit Cost',
        text: 'Each hashtag AI analysis costs 2 credits.',
      },
    ],
  },
  {
    id: 'revenue-split',
    title: 'Revenue Split Manager',
    icon: BanknotesIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Revenue Split Manager helps you fairly distribute revenue among team members and collaborators. Manage the entire workflow from setting split ratios to approval and distribution.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Split Creation: Set title, total amount, period, and assign percentage ratios per member.',
          'Member Management: Manage split ratios and amounts by role — creator, editor, manager, etc.',
          'Approval Workflow: Progress through Draft → Pending → Approved → Distributed stages.',
          'Payment Tracking: Track each member\'s payment status (Pending/Paid/Failed) in real-time.',
          'Ratio Visualization: View member split ratios visually with a pie chart.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Revenue Split" in the sidebar.',
          'Click "New Split" to create a revenue split.',
          'Add members and set each person\'s percentage.',
          'Click "Approve" to finalize the split.',
          'Click "Distribute" to process actual payments.',
        ],
      },
    ],
  },
  {
    id: 'ab-test-results',
    title: 'A/B Test Results',
    icon: BeakerIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'A/B Test Results provides detailed analysis of your content A/B tests. Compare variant performance, view statistical confidence levels, and get automatic winner determination.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Variant Comparison: Compare views, CTR, average watch time, engagement rate, and conversions across variants.',
          'Confidence Gauge: Visualize statistical confidence with a semi-circular gauge chart.',
          'Winner Determination: Automatically determines the winning variant at 95%+ confidence.',
          'Test Controls: Pause, resume, or stop running tests.',
          'Status Filters: Filter tests by status — All, Running, Completed, Paused, Cancelled.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "AB Test Results" in the sidebar.',
          'Review summary stats showing total tests and average improvement.',
          'Click a test card to view detailed results.',
          'Compare variant metrics in the comparison table.',
          'Apply the winning variant when confidence is sufficient.',
        ],
      },
    ],
  },
  {
    id: 'fan-community',
    title: 'Fan Community',
    icon: ChatBubbleLeftRightIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Fan Community is a message board where creators and fans can interact. Supports various post types including announcements, discussions, polls, Q&A, behind-the-scenes, and fan art.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Post Types: Supports 6 types — Announcement, Discussion, Poll, Q&A, Behind the Scenes, and Fan Art.',
          'Pinned Posts: Pin important posts to always show them at the top.',
          'Likes & Comments: Fans can react to posts with likes and comments.',
          'Community Stats: View total posts, total members, active members today, and total likes.',
          'Type Filter: Filter posts by type to view only the content you want.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Fan Community" in the sidebar.',
          'Check community stats to see member activity.',
          'Click "New Post" and select the post type.',
          'Enter title, content, and tags, then publish.',
          'Engage with fan posts through likes and comments.',
        ],
      },
    ],
  },
  {
    id: 'calendar-insights',
    title: 'Calendar Insights',
    icon: CalendarDaysIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Calendar Insights analyzes your content upload patterns and recommends optimal upload times. It finds the most effective upload timing based on performance data by day and time slot.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Optimal Time Recommendations: AI analyzes past data to recommend the best upload times by day and time slot.',
          'Daily Insights: View upload count, average views, engagement rate, and score for each date.',
          'Upload Patterns: Analyze upload frequency, most active days/hours, and consistency score by platform.',
          'Summary Statistics: Get a quick overview of total uploads, weekly average, best day, and consistency score.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Calendar Insights" in the sidebar.',
          'Check summary statistics for overall upload status.',
          'Review optimal time recommendation cards for reasoning.',
          'Check platform-specific upload patterns to improve consistency.',
          'Adjust your content upload schedule to match optimal times.',
        ],
      },
    ],
  },
  {
    id: 'team-performance',
    title: 'Team Performance Dashboard',
    icon: UserGroupIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Team Performance Dashboard tracks and analyzes team members\' task completion rates, content production, and activity history. Visualize team productivity and identify top performers.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Member Performance Cards: Display each member\'s completion rate, task count, content count, rating, and work streak.',
          'Performance Ranking: Rank team members by completion rate with progress bar visualization.',
          'Activity Feed: Show recent team activities (editing, planning submissions, etc.) in a timeline format.',
          'Top Performer: Highlight the team member with the highest performance.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Team Performance" in the sidebar.',
          'Check summary statistics for overall team performance.',
          'Review individual member performance details in member cards.',
          'Compare team members in the performance ranking.',
          'Monitor recent team activity in the activity feed.',
        ],
      },
    ],
  },
  {
    id: 'brand-safety',
    title: 'Brand Safety Check',
    icon: ShieldExclamationIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Brand Safety Check automatically inspects whether content complies with advertising policies, copyright, language standards, and more. Detect potential issues before uploading to prevent revenue loss.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Automated Checks: Automatically detect profanity, copyrighted music, ad disclosure, dangerous activities, etc.',
          'Status Classification: Classify results into three levels — Safe, Warning, and Violation.',
          'Rule Management: Customize check rules by enabling/disabling them and adjusting severity levels.',
          'Recommendations: Provide specific improvement recommendations when issues are found.',
          'Summary Statistics: View total checks, safe/warning/violation counts, and average score.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Brand Safety" in the sidebar.',
          'Check summary statistics for overall safety status.',
          'Click "Run Check" to inspect new content.',
          'Review recommendations for warning/violation items in check results.',
          'Enable/disable rules as needed in rule management.',
        ],
      },
    ],
  },
  {
    id: 'subtitle-translation',
    title: 'Auto Subtitle Translation',
    icon: LanguageIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Auto Subtitle Translation uses AI to translate video subtitles into multiple languages. Supports 10+ languages including English, Japanese, Chinese, and Spanish, with manual editing capability for translation quality.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Multi-language Support: Supports 10+ languages including English, Japanese, Chinese, Spanish, French, and more.',
          'Real-time Progress: Track translation progress in real-time.',
          'Line Editor: Manually edit translations for each subtitle line and verify timecodes.',
          'Quality Score: AI automatically evaluates translation quality and provides a score.',
          'Credit Tracking: Track AI credits used for translations.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Subtitle Translation" in the sidebar.',
          'Click the "New Translation Request" button.',
          'Select a video and set the source and target languages.',
          'Once translation is complete, review quality in the line editor.',
          'Manually edit individual line translations if needed.',
        ],
      },
    ],
  },
  {
    id: 'sentiment-analyzer',
    title: 'Sentiment Analyzer',
    icon: FaceSmileIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'The AI Sentiment Analyzer automatically analyzes the sentiment of content comments, classifying them as positive, neutral, or negative, and provides actionable insights.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Sentiment Classification: Automatically classifies comments as positive, neutral, or negative.',
          'Keyword Analysis: Extracts positive/negative keywords to understand viewer reactions.',
          'Sentiment Score: Evaluates each comment sentiment on a 0-100 scale.',
          'Trend Tracking: Analyzes sentiment changes over time.',
          'Content Comparison: Compare sentiment reactions across different content.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Sentiment Analyzer" in the sidebar.',
          'Select the content to analyze and click "Start Analysis".',
          'Once analysis is complete, review sentiment distribution charts and keywords.',
          'Click individual comments to view detailed sentiment analysis results.',
        ],
      },
    ],
  },
  {
    id: 'content-versioning',
    title: 'Content Versioning',
    icon: DocumentChartBarIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Content Versioning tracks changes to titles, thumbnails, descriptions, and more, analyzing the impact each change has on performance.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Change History: Records all content modifications in a timeline format.',
          'Performance Comparison: Compares views, likes, engagement rate, and CTR before and after changes.',
          'Change Type Classification: Categorizes changes by type: title, thumbnail, description, tags, subtitles, video.',
          'Best Change Analysis: Identifies the change types that yielded the best results.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Content Versioning" in the sidebar.',
          'Click on a content group card to expand the version history.',
          'Review change details and performance comparison data for each version.',
          'Identify the most effective change strategies to apply to future content.',
        ],
      },
    ],
  },
  {
    id: 'creator-milestone',
    title: 'Creator Milestones',
    icon: TrophyIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Creator Milestones lets you set growth goals for subscribers, views, revenue, and more, tracking your progress toward achievement.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Goal Setting: Set various goals for subscribers, views, video count, watch hours, revenue, and more.',
          'Progress Tracking: Monitor goal achievement with circular progress indicators in real-time.',
          'Achievement Records: Automatically records milestone achievements and provides celebration badges.',
          'Status Management: Manage milestones in pending, in-progress, achieved, or expired states.',
          'Platform Goals: Set independent goals for each platform.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Creator Milestones" in the sidebar.',
          'Click "New Milestone" and enter goal type, title, and target value.',
          'Check progress of active milestones on the dashboard.',
          'Use status filters to view milestones by in-progress/achieved/pending status.',
        ],
      },
    ],
  },
  {
    id: 'algorithm-insights',
    title: 'Algorithm Insights',
    icon: CpuChipIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Algorithm Insights analyzes algorithm factors for each platform (YouTube, TikTok, Instagram) and provides optimization strategies.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Platform Scores: Provides scores across 5 areas: content, engagement, metadata, consistency, and audience.',
          'Factor Analysis: Analyzes importance, current score, and trends for each algorithm factor.',
          'Change Tracking: Detects platform algorithm changes and evaluates their impact.',
          'Optimization Recommendations: AI suggests specific improvement actions for each factor.',
          'Platform Filter: Filter data by YouTube, TikTok, or Instagram.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Algorithm Insights" in the sidebar.',
          'Review overall and detailed scores on platform score cards.',
          'Check the algorithm factors list for areas needing improvement and recommendations.',
          'Review recent algorithm change cards for impact assessment and response strategies.',
        ],
      },
    ],
  },
  {
    id: 'content-calendar-ai',
    title: 'AI Content Calendar',
    icon: AiCalIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'The AI Content Calendar uses machine learning to automatically generate and recommend optimal content upload schedules.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Auto Schedule Generation: AI suggests optimal upload times and content topics.',
          'Optimal Time Slots: Recommends best upload times based on platform audience activity.',
          'Confidence Score: Provides expected performance confidence for each suggestion.',
          'Accept Suggestions: Accept preferred suggestions to add them to your schedule.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "AI Content Calendar" in the sidebar.',
          'Select date range and platforms, then click "Generate".',
          'Review AI-generated content schedule suggestions.',
          'Accept preferred suggestions to add them to your calendar.',
        ],
      },
    ],
  },
  {
    id: 'creator-benchmark',
    title: 'Creator Benchmark',
    icon: BenchmarkIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Creator Benchmark compares your key metrics like subscribers, views, and engagement rates with creators in the same category.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Metric Comparison: Compare your metrics vs average vs top creators at a glance.',
          'Percentile Analysis: See where you rank (percentile) for each metric.',
          'Peer Comparison: Compare performance with key creators in your category.',
          'Trend Tracking: Track your ranking changes over time.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Creator Benchmark" in the sidebar.',
          'Use platform filters to view benchmarks for specific platforms.',
          'Compare your values with average and top values on metric cards.',
          'Review the peer comparison table to understand competitor performance.',
        ],
      },
    ],
  },
  {
    id: 'content-cluster',
    title: 'Content Clusters',
    icon: CircleStackIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Content Clusters uses AI to automatically group similar content, analyzing content strategy and performance patterns.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Auto Clustering: AI classifies content by topic, format, and performance.',
          'Performance Analysis: Compare average views and engagement rates by cluster.',
          'Similarity Analysis: View similarity scores for content within each cluster.',
          'Tag Analysis: Identify the most commonly used tags.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Content Clusters" in the sidebar.',
          'Click on a cluster card to view its content list.',
          'Analyze similarity scores and performance data for each content.',
          'Identify patterns from top-performing clusters to inform your strategy.',
        ],
      },
    ],
  },
  {
    id: 'fan-insights',
    title: 'Fan Insights',
    icon: EyeIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Fan Insights analyzes viewer demographics, behavior patterns, and segments to help you deeply understand your audience.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Demographics: View fan distribution by age group, gender, and country/city.',
          'Behavior Analysis: Analyze active hours, watch time, return rate, comment rate, and share rate.',
          'Segments: Classify fans by type such as super fans, casual viewers, etc.',
          'Platform Filter: Filter fan data by individual platforms.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Fan Insights" in the sidebar.',
          'Check age and gender distribution in the Demographics tab.',
          'Analyze viewing patterns and engagement data in the Behavior tab.',
          'Identify fan type characteristics in the Segments tab.',
        ],
      },
    ],
  },
  {
    id: 'content-repurposer',
    title: 'Content Repurposer',
    icon: ArrowPathRoundedSquareIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Content Repurposer automatically converts long-form videos into short-form content optimized for TikTok, Reels, Naver Clip, and other platforms.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Auto Convert: AI extracts highlights and creates optimized short-form content for the target platform.',
          'Templates: Pre-defined conversion templates like YouTube→TikTok, YouTube→Reels are available.',
          'Progress Tracking: Monitor each job\'s progress and status in real-time.',
          'Success Rate: Track overall job success rate and time savings.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Content Repurposer" in the sidebar.',
          'Check current repurposing jobs in the Jobs tab.',
          'Select a conversion template in the Templates tab and create a new job.',
          'Download converted short-form videos from completed jobs.',
        ],
      },
    ],
  },
  {
    id: 'audience-overlap',
    title: 'Audience Overlap',
    icon: Square3Stack3DIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Audience Overlap analyzes viewer overlap and unique audiences across multiple platforms.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Overlap Analysis: Visually identify viewer overlap rates between two platforms.',
          'Segments: Segment shared audiences based on interests.',
          'Unique Audiences: Determine the number of unique viewers per platform.',
          'Engagement Comparison: Compare engagement rates and interests across segments.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Audience Overlap" in the sidebar.',
          'Review overlap metrics for each platform pair in the analysis results.',
          'Identify shared audience characteristics in the Segments section.',
          'Click "Run Analysis" to start a new overlap analysis.',
        ],
      },
    ],
  },
  {
    id: 'video-seo',
    title: 'Video SEO Optimizer',
    icon: MagnifyingGlassIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Video SEO Optimizer analyzes video titles, descriptions, tags, and thumbnails to provide search optimization scores and improvement suggestions.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'SEO Scores: Get individual scores for title, description, tags, and thumbnail, plus an overall score.',
          'Improvement Suggestions: AI recommends specific actions to boost your SEO score.',
          'Keyword Analysis: Analyze search volume, competition, and trends for related keywords.',
          'Platform-specific: Get SEO optimization recommendations tailored to each platform.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Video SEO" in the sidebar.',
          'Review SEO scores for each video in the analysis list.',
          'Follow detailed suggestions to improve your metadata.',
          'Check keyword search trends in the keyword analysis section.',
        ],
      },
    ],
  },
  {
    id: 'mood-board',
    title: 'Creator Mood Board',
    icon: PaintBrushIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Creator Mood Board lets you create and manage visual inspiration boards for content planning.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Board Management: Create mood boards by category and organize with tags.',
          'Item Types: Add various types of items including images, color palettes, and text notes.',
          'Sharing: Choose public or private visibility to share inspiration with team members.',
          'Pin Board Layout: Organize ideas in a Pinterest-style visual layout.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Mood Board" in the sidebar.',
          'Click "Create Board" to make a new mood board.',
          'Add inspiration items like images, colors, and text to your board.',
          'Manage boards by category and use them for content planning.',
        ],
      },
    ],
  },
  {
    id: 'thumbnail-ab-test',
    title: 'Thumbnail A/B Test',
    icon: ThumbnailAbIcon2,
    content: [
      {
        subtitle: 'Overview',
        text: 'Thumbnail A/B Test compares click-through rates (CTR) of two thumbnail designs to help you choose the best performing one.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Variant Comparison: Compare impressions, clicks, and CTR of two thumbnail variants in real-time.',
          'Auto Winner: Automatically determines the winner when statistical significance is reached.',
          'Test Management: Manage tests by status — active, completed, or draft.',
          'Performance Stats: View detailed statistics including CTR difference and confidence intervals.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Thumbnail A/B" in the sidebar.',
          'Click "New Test" to create an A/B test.',
          'Upload two thumbnail images and start the test.',
          'Review results and apply the winning thumbnail.',
        ],
      },
    ],
  },
  {
    id: 'revenue-goal',
    title: 'Revenue Goal',
    icon: RevenueGoalIcon2,
    content: [
      {
        subtitle: 'Overview',
        text: 'Revenue Goal lets you set channel revenue targets and track your progress toward achieving them.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Goal Setting: Set monthly or quarterly revenue targets with amounts and periods.',
          'Milestone Tracking: Monitor progress through intermediate milestones.',
          'Progress Visualization: View current revenue vs. target achievement rates in charts.',
          'Notifications: Get notified when milestones are reached.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Revenue Goal" in the sidebar.',
          'Click "Add Goal" to set a new revenue target.',
          'Enter target amount, period, and milestones.',
          'Track real-time progress on the dashboard.',
        ],
      },
    ],
  },
  {
    id: 'comment-summary',
    title: 'AI Comment Summary',
    icon: CommentSummaryIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'AI Comment Summary uses AI to analyze video comments, automatically extracting key topics, sentiment analysis, and popular comments.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'AI Summary: Analyze hundreds to thousands of comments and summarize key content.',
          'Topic Analysis: Automatically extract frequently mentioned topics from comments.',
          'Sentiment Analysis: Analyze overall comment sentiment ratios (positive/negative/neutral).',
          'Top Comments: Display top comments sorted by likes at a glance.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Comment Summary" in the sidebar.',
          'Select the video to analyze.',
          'Click "AI Analyze" to start comment analysis.',
          'Review summary results, topics, sentiment, and top comments.',
        ],
      },
    ],
  },
  {
    id: 'platform-health',
    title: 'Platform Health Score',
    icon: HealthIcon2,
    content: [
      {
        subtitle: 'Overview',
        text: 'Platform Health Score provides a comprehensive analysis of channel health status for each connected platform.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Health Score: Display channel health status as a 0-100 score per platform.',
          'Issue Detection: Automatically detect issues like growth slowdown and engagement decline.',
          'Severity Classification: Classify issues by high/medium/low severity for prioritization.',
          'Overall Summary: Get a comprehensive view of all platform health at a glance.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Platform Health" in the sidebar.',
          'Check health scores and status for each platform.',
          'Click detected issues for details and resolution steps.',
          'Perform regular check-ups to maintain channel health.',
        ],
      },
    ],
  },
  {
    id: 'subtitle-generator',
    title: 'AI Subtitle Generator',
    icon: MicrophoneIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'AI Subtitle Generator automatically creates subtitles from video audio using AI speech recognition.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Auto Generation: Generate subtitles from video audio using AI speech recognition.',
          'Multi-language: Generate subtitles in Korean, English, and other languages.',
          'Segment Editing: Edit generated subtitle timing and text per segment.',
          'Progress Tracking: Monitor subtitle generation progress in real-time.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Subtitle Generator" in the sidebar.',
          'Select the video and target language for subtitles.',
          'Click "Generate" to start AI subtitle generation.',
          'Download or edit the completed subtitles.',
        ],
      },
    ],
  },
  {
    id: 'content-library',
    title: 'Content Library',
    icon: FolderOpenIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Content Library lets you systematically manage all your uploaded content in one place.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Folder Management: Create category folders to organize your content.',
          'Type Filter: Filter by video, image, audio, or document types.',
          'Tag System: Add tags to easily search and find content.',
          'Storage Management: Monitor total usage and file sizes.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Content Library" in the sidebar.',
          'Create folders to categorize your content.',
          'Use type filters to find specific content.',
          'Delete unnecessary content to manage storage space.',
        ],
      },
    ],
  },
  {
    id: 'fan-poll',
    title: 'Fan Poll',
    icon: HandRaisedIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Fan Poll helps you collect fan opinions and make content decisions through polls and surveys.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Poll Creation: Create various poll types — single choice, multiple choice, and rating.',
          'Real-time Results: View participation and results in real-time.',
          'Date Settings: Set start and end dates for automatic poll closure.',
          'Participation Analysis: Analyze participation rates and trends per poll.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Fan Poll" in the sidebar.',
          'Click "Create Poll" to start a new poll.',
          'Set the title, options, and deadline.',
          'Review fan voting results and apply to content decisions.',
        ],
      },
    ],
  },
  {
    id: 'creator-network',
    title: 'Creator Network',
    icon: UserCircleIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Creator Network lets you connect with other creators and discover collaboration opportunities.',
      },
      {
        subtitle: 'Key Features',
        text: '',
        items: [
          'Creator Search: Search creators by category and platform.',
          'Match Score: AI-based match scores recommend suitable collaboration partners.',
          'Collaboration Requests: Send and manage collaboration requests for collabs, cross-promotions, and more.',
          'Network Management: Manage connected creators and collaboration history.',
        ],
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Creator Network" in the sidebar.',
          'Search for creators in your category of interest.',
          'Check match scores and send collaboration requests.',
          'Respond to received collaboration requests.',
        ],
      },
    ],
  },
  {
    id: 'trend-predictor',
    title: 'AI Trend Predictor',
    icon: ArrowTrendingUpIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'AI predicts content trends and recommends optimal topics for your channel.',
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "AI Trend Predictor" in the sidebar.',
          'Review trend predictions by category.',
          'Select rising trend keywords to analyze related topics.',
          'Plan content based on prediction accuracy and peak dates.',
        ],
      },
    ],
  },
  {
    id: 'performance-report',
    title: 'Performance Report',
    icon: ReportIcon3,
    content: [
      {
        subtitle: 'Overview',
        text: 'Auto-generate comprehensive weekly, monthly, or quarterly performance reports.',
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Performance Report" in the sidebar.',
          'Click "Generate Report" to set period and title.',
          'Review detailed sections of the generated report.',
          'Download the completed report as PDF.',
        ],
      },
    ],
  },
  {
    id: 'live-stream',
    title: 'Live Stream Manager',
    icon: VideoCameraIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Schedule and manage multi-platform live streaming from one place.',
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Live Stream" in the sidebar.',
          'Click "New Stream" to set platform and schedule.',
          'Monitor viewer count and chat during live sessions.',
          'Review performance summary after ending.',
        ],
      },
    ],
  },
  {
    id: 'creator-marketplace',
    title: 'Creator Marketplace',
    icon: ShoppingBagIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'A marketplace for trading services between creators.',
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Creator Marketplace" in the sidebar.',
          'Filter listings by service type.',
          'Review service details and ratings.',
          'Click "Order" to purchase a service.',
        ],
      },
    ],
  },
  {
    id: 'music-recommender',
    title: 'AI Music Recommender',
    icon: MusicalNoteIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'AI recommends background music that matches your video mood.',
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "AI Music" in the sidebar.',
          'Browse recommended tracks for each video.',
          'Preview and select the best matching track.',
          'Apply the selected track to your video.',
        ],
      },
    ],
  },
  {
    id: 'content-ab-analyzer',
    title: 'Content A/B Analyzer',
    icon: AdjustmentsHorizontalIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Compare performance of different content styles on the same topic.',
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Content A/B" in the sidebar.',
          'Create a test by selecting two videos to compare.',
          'Compare metrics like views, likes, and CTR.',
          'Review confidence scores and winner analysis.',
        ],
      },
    ],
  },
  {
    id: 'fan-reward',
    title: 'Fan Reward',
    icon: RewardIcon4,
    content: [
      {
        subtitle: 'Overview',
        text: 'Reward fans with points based on their engagement activities.',
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Fan Reward" in the sidebar.',
          'Create reward items and set point costs.',
          'Monitor fan activity logs and point accumulation.',
          'Manage reward claims and availability.',
        ],
      },
    ],
  },
  {
    id: 'platform-automation-pro',
    title: 'Platform Automation',
    icon: AutoIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Manage automated publishing, notifications, and tagging rules per platform.',
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Platform Auto" in the sidebar.',
          'Set triggers (schedule, event, keyword) and actions.',
          'Enable or disable automation rules.',
          'Review execution logs for automation results.',
        ],
      },
    ],
  },
  {
    id: 'video-script-assistant',
    title: 'AI Script Assistant',
    icon: PencilSquareIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Use AI to write and improve video scripts. Get section-by-section suggestions for hooks, transitions, CTAs, and more.',
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Script Assistant" in the sidebar.',
          'Set the title, tone (casual/professional/humorous/educational/dramatic), and target length.',
          'Review AI-generated suggestions for each script section.',
          'Apply suggestions for hooks, intros, body, transitions, and CTAs.',
        ],
      },
    ],
  },
  {
    id: 'channel-health',
    title: 'Channel Health Dashboard',
    icon: HealthIcon2,
    content: [
      {
        subtitle: 'Overview',
        text: 'Measure your channel\'s overall health across 5 areas: growth, engagement, consistency, audience, and monetization.',
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Channel Health" in the sidebar.',
          'View overall scores and detailed metrics for each channel.',
          'Select a channel to see trend changes over time.',
          'Follow recommendations to improve weak areas.',
        ],
      },
    ],
  },
  {
    id: 'content-translator',
    title: 'Content Translator',
    icon: LanguageIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'AI-powered translation for titles, descriptions, tags, and subtitles into multiple languages. Use glossary for consistent quality.',
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Translator" in the sidebar.',
          'Select the video, target language, and content type to translate.',
          'Review the quality score after translation completes.',
          'Add frequently used terms to the glossary for translation consistency.',
        ],
      },
    ],
  },
  {
    id: 'revenue-analyzer',
    title: 'Revenue Analyzer',
    icon: BanknotesIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Analyze revenue by source (ads, memberships, super chat, sponsorship, merchandise, affiliate) and project future earnings.',
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Revenue Analyzer" in the sidebar.',
          'View amounts and growth rates by channel and revenue source.',
          'Select a channel to see revenue projections and confidence levels.',
          'Analyze projection factors to build revenue growth strategies.',
        ],
      },
    ],
  },
  {
    id: 'schedule-optimizer',
    title: 'AI Schedule Optimizer',
    icon: OptimizerIcon2,
    content: [
      {
        subtitle: 'Overview',
        text: 'AI analyzes past performance data to recommend optimal posting times and automatically optimizes your schedule.',
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Schedule Optimizer" in the sidebar.',
          'View optimal time slot scores by platform.',
          'Review AI schedule recommendations with confidence levels.',
          'Click "Apply" to add recommended schedules to your calendar.',
        ],
      },
    ],
  },
  {
    id: 'portfolio-builder',
    title: 'Portfolio Builder',
    icon: RectangleGroupIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Automatically generate creator portfolios, choose templates, and create public pages to showcase your brand.',
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Portfolio Builder" in the sidebar.',
          'Click "New Portfolio" to create a portfolio.',
          'Select a template and edit section content.',
          'Click "Publish" to generate a public URL for sharing.',
        ],
      },
    ],
  },
  {
    id: 'fan-segment-campaign',
    title: 'Fan Segment Campaigns',
    icon: CampaignIcon2,
    content: [
      {
        subtitle: 'Overview',
        text: 'Segment fans (new, superfans, inactive, etc.) and send targeted campaigns (email, push, in-app, SMS) to each segment.',
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Fan Campaigns" in the sidebar.',
          'Review fan segments and target groups.',
          'Click "New Campaign" to select delivery type and target segment.',
          'Monitor campaign performance including open rates and click rates.',
        ],
      },
    ],
  },
  {
    id: 'content-funnel',
    title: 'Content Funnel Analysis',
    icon: FunnelIcon,
    content: [
      {
        subtitle: 'Overview',
        text: 'Analyze the funnel from content impressions to subscriptions/conversions step by step, identify drop-off points, and improve conversion rates.',
      },
      {
        subtitle: 'How to Use',
        text: '',
        steps: [
          'Click "Content Funnel" in the sidebar.',
          'View step-by-step drop-off rates in funnel charts for each video.',
          'Identify the biggest drop-off stages to find improvement points.',
          'Compare two videos to analyze performance differences.',
        ],
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

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

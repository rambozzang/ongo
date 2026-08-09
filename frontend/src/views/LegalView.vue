<template>
  <main class="min-h-screen bg-surface-base px-4 py-8 text-content tablet:px-8 tablet:py-12">
    <div class="mx-auto max-w-3xl">
      <header class="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <RouterLink to="/login" class="inline-flex items-center gap-2 text-sm font-semibold text-accent hover:text-accent-hover">
            <span aria-hidden="true">←</span>
            {{ copy.backToLogin }}
          </RouterLink>
          <p class="mt-7 font-mono text-[10px] uppercase tracking-[0.18em] text-content-tertiary">ONGO / {{ documentKind }}</p>
          <h1 class="mt-2 text-3xl font-bold tracking-tight text-content tablet:text-4xl">{{ copy.title }}</h1>
          <p class="mt-3 max-w-2xl text-sm leading-6 text-content-secondary">{{ copy.intro }}</p>
        </div>
        <a
          v-if="contactEmail"
          :href="`mailto:${contactEmail}`"
          class="rounded-lg border border-line-control px-3 py-2 text-xs font-semibold text-content-secondary hover:bg-surface-raised hover:text-content"
        >
          {{ copy.contact }}
        </a>
      </header>

      <div class="mb-6 flex flex-wrap items-center gap-2 border-y border-line py-3 text-xs text-content-tertiary">
        <span>{{ copy.lastUpdated }}: {{ lastUpdated }}</span>
        <span aria-hidden="true">·</span>
        <span>{{ operatorName }}</span>
      </div>

      <nav aria-label="Legal documents" class="mb-8 flex flex-wrap gap-2">
        <RouterLink
          v-for="link in documentLinks"
          :key="link.path"
          :to="link.path"
          class="rounded-full border px-3 py-1.5 text-xs font-semibold transition-colors"
          :class="link.kind === documentKind ? 'border-accent bg-accent-dim text-accent' : 'border-line-control text-content-secondary hover:bg-surface-raised hover:text-content'"
        >
          {{ link.label }}
        </RouterLink>
      </nav>

      <article class="space-y-8 rounded-xl border border-line bg-surface-card p-5 shadow-sm tablet:p-8">
        <section v-for="section in copy.sections" :key="section.heading">
          <h2 class="text-lg font-bold text-content">{{ section.heading }}</h2>
          <p v-for="paragraph in section.paragraphs" :key="paragraph" class="mt-3 whitespace-pre-line text-sm leading-7 text-content-secondary">
            {{ paragraph }}
          </p>
          <ul v-if="section.items?.length" class="mt-3 list-disc space-y-2 pl-5 text-sm leading-7 text-content-secondary">
            <li v-for="item in section.items" :key="item">{{ item }}</li>
          </ul>
        </section>

        <section class="rounded-lg border border-accent/30 bg-accent-dim p-4">
          <h2 class="text-sm font-bold text-content">{{ copy.contactHeading }}</h2>
          <p class="mt-2 text-sm leading-6 text-content-secondary">{{ copy.contactBody }}</p>
          <a v-if="contactEmail" :href="`mailto:${contactEmail}`" class="mt-3 inline-block text-sm font-semibold text-accent hover:text-accent-hover">
            {{ contactEmail }}
          </a>
        </section>
      </article>

      <footer class="mt-8 flex flex-wrap items-center justify-between gap-3 text-xs text-content-tertiary">
        <span>© {{ currentYear }} {{ operatorName }}</span>
        <RouterLink to="/login" class="font-semibold text-accent hover:text-accent-hover">{{ copy.backToLogin }}</RouterLink>
      </footer>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

type DocumentKind = 'terms' | 'privacy' | 'refund' | 'data-deletion' | 'support'
type LegalSection = { heading: string; paragraphs: string[]; items?: string[] }
type LegalCopy = { title: string; intro: string; contact: string; lastUpdated: string; backToLogin: string; contactHeading: string; contactBody: string; sections: LegalSection[] }

const { locale } = useI18n()
const route = useRoute()
const documentKind = computed<DocumentKind>(() => {
  const value = String(route.meta.document ?? 'terms')
  return ['terms', 'privacy', 'refund', 'data-deletion', 'support'].includes(value) ? value as DocumentKind : 'terms'
})

const operatorName = import.meta.env.VITE_LEGAL_OPERATOR_NAME?.trim() || '온고 운영팀'
const contactEmail = import.meta.env.VITE_LEGAL_CONTACT_EMAIL?.trim() || 'support@codelabtiger.com'
const lastUpdated = import.meta.env.VITE_LEGAL_LAST_UPDATED?.trim() || '2026-08-09'
const currentYear = new Date().getFullYear()

const ko: Record<DocumentKind, LegalCopy> = {
  terms: {
    title: '이용약관',
    intro: '온고(ongo)의 콘텐츠 제작·예약 게시·성과 관리 서비스를 이용하기 전에 확인해 주세요.',
    contact: '문의하기',
    lastUpdated: '최종 업데이트',
    backToLogin: '로그인으로 돌아가기',
    contactHeading: '문의 및 약관 관련 요청',
    contactBody: '서비스 이용, 약관 해석 또는 권리 행사에 관한 문의는 아래 지원 이메일로 보내 주세요. 요청 접수와 처리 결과를 확인할 수 있도록 회신 가능한 주소를 사용해 주세요.',
    sections: [
      { heading: '1. 서비스의 범위', paragraphs: ['온고는 사용자가 업로드한 미디어를 여러 소셜 플랫폼에 게시하고, 게시 일정을 관리하며, 자막·메타데이터·성과 정보를 관리할 수 있도록 돕는 SaaS입니다. 플랫폼별 게시 가능 여부와 기능은 각 플랫폼의 정책, 권한, API 상태에 따라 달라질 수 있습니다.'] },
      { heading: '2. 계정과 외부 채널', paragraphs: ['사용자는 본인의 계정 정보를 안전하게 관리하고, 연결하는 외부 채널에 대해 게시 권한을 보유해야 합니다. 외부 채널의 권한이 만료되거나 취소되면 게시가 지연되거나 실패할 수 있으며, 온고는 결과 상태와 재시도 방법을 서비스 화면에 표시합니다.'] },
      { heading: '3. 게시와 자동화', paragraphs: ['사용자가 예약 게시, 반복 예약, 자동 숏츠 생성 또는 UGC 기능을 실행하면 입력한 조건에 따라 작업이 처리됩니다. 최종 게시 여부는 각 플랫폼의 검수·정책·장애·콘텐츠 제한의 영향을 받으므로 중요한 게시물은 게시 결과와 플랫폼 링크를 확인해야 합니다.'] },
      { heading: '4. 금지된 이용', paragraphs: ['다음 행위는 허용되지 않습니다.'], items: ['타인의 권리 또는 개인정보를 침해하는 콘텐츠 게시', '스팸, 사기, 악성코드, 불법 콘텐츠 또는 플랫폼 정책 위반 콘텐츠의 자동화', '서비스 또는 외부 플랫폼의 인증·요금·제한을 우회하는 행위', '다른 사용자·워크스페이스의 데이터에 접근하려는 행위'] },
      { heading: '5. 요금과 변경', paragraphs: ['유료 플랜의 가격, 사용량, 결제 주기와 제공 범위는 결제 화면에 표시됩니다. 서비스 기능과 요금은 사전에 고지하고 변경할 수 있으며, 이미 처리된 결제와 환불은 환불정책 및 관련 법령에 따라 처리합니다.'] },
      { heading: '6. 이용 제한과 종료', paragraphs: ['보안, 법령, 결제 또는 외부 플랫폼 정책 위반이 확인되면 서비스 이용을 제한할 수 있습니다. 계정 삭제를 요청하면 삭제 가능 여부를 확인한 뒤 연결 채널·미디어·게시 이력 등 관련 데이터를 정책에 따라 삭제하거나 법령상 필요한 범위에서 보존합니다.'] },
    ],
  },
  privacy: {
    title: '개인정보처리방침',
    intro: '온고가 로그인, 채널 연결, 게시와 고객 지원을 위해 처리하는 정보와 사용자의 권리를 안내합니다.',
    contact: '개인정보 문의',
    lastUpdated: '시행일',
    backToLogin: '로그인으로 돌아가기',
    contactHeading: '개인정보 권리 행사',
    contactBody: '열람, 정정, 삭제, 처리정지 또는 개인정보 관련 문의는 아래 이메일로 요청해 주세요. 본인 확인과 법정 보존 의무 확인 후 처리 결과를 안내합니다.',
    sections: [
      { heading: '1. 처리하는 정보', paragraphs: ['로그인 과정에서 이메일, 이름, 프로필 식별자와 인증 상태를 처리합니다. 사용자가 기능을 사용할 때 미디어·자막·제목·설명·해시태그·예약 정보, 연결한 채널 식별자와 게시 결과를 처리합니다. 결제 기능을 사용하면 결제 식별자와 구독 상태를 처리하며, 카드번호 등 결제수단 원문은 결제대행사가 처리합니다.'] },
      { heading: '2. 처리 목적', paragraphs: ['계정 생성·인증, 외부 채널 연결, 게시·예약·재시도, 자동 숏츠와 UGC 작업, 성과 분석, 결제·환불·고객지원, 보안 및 장애 대응을 위해 정보를 처리합니다. 광고 목적의 판매나 사용자가 요청하지 않은 외부 게시에는 사용하지 않습니다.'] },
      { heading: '3. 외부 서비스와 국외 이전', paragraphs: ['게시를 요청한 플랫폼, 인증 제공자, 저장소·AI·결제·모니터링 등 기능 제공에 필요한 처리자에게 필요한 범위의 정보를 전송할 수 있습니다. 외부 서비스의 국가, 보유 기간과 처리 방식은 해당 서비스의 정책을 함께 확인해야 하며, 온고는 채널 권한과 전송 목적을 최소화합니다.'] },
      { heading: '4. 보관과 삭제', paragraphs: ['계정 삭제가 완료되면 서비스 운영에 불필요한 계정·채널 토큰·미디어와 작업 데이터를 삭제합니다. 결제·세무·분쟁 대응에 필요한 정보는 관련 법령에서 정한 기간 동안 별도로 보존할 수 있습니다. 외부 플랫폼에 이미 게시된 콘텐츠의 삭제는 각 플랫폼의 정책과 API 지원 범위에 따릅니다.'] },
      { heading: '5. 쿠키와 로컬 저장소', paragraphs: ['로그인 토큰, 언어·테마·화면 설정 등 서비스 운영에 필요한 브라우저 저장소를 사용합니다. 브라우저 설정으로 저장소를 차단하면 로그인이나 일부 설정이 작동하지 않을 수 있습니다.'] },
      { heading: '6. 사용자 권리', paragraphs: ['사용자는 자신의 개인정보에 대해 열람·정정·삭제·처리정지를 요청할 수 있습니다. 요청은 본인 확인과 다른 사용자의 권리, 법정 보존 의무를 확인한 후 처리합니다.'] },
    ],
  },
  refund: {
    title: '환불정책',
    intro: '구독과 크레딧 결제의 취소·환불 요청이 어떻게 처리되는지 안내합니다.',
    contact: '환불 문의',
    lastUpdated: '최종 업데이트',
    backToLogin: '로그인으로 돌아가기',
    contactHeading: '환불 요청 방법',
    contactBody: '결제 이메일, 결제일, 결제 식별자와 요청 사유를 함께 보내 주세요. 민감한 카드번호나 비밀번호는 보내지 마세요. 결제대행사 정책과 관련 법령을 확인한 뒤 처리 결과와 예상 일정을 회신합니다.',
    sections: [
      { heading: '1. 취소 요청', paragraphs: ['다음 결제 주기를 원하지 않으면 서비스의 구독 관리 화면에서 갱신을 취소할 수 있습니다. 취소해도 이미 결제된 이용 기간의 종료일까지는 플랜이 유지될 수 있으며, 다음 결제부터 청구되지 않도록 요청 시점을 확인해 주세요.'] },
      { heading: '2. 환불 심사', paragraphs: ['환불 가능 여부는 상품 유형, 사용량·크레딧 소비 여부, 결제일, 서비스 제공 상태, 결제대행사 규정과 관련 법령을 기준으로 판단합니다. 게시 예약·AI 작업·스토리지 등 이미 제공되었거나 사용된 서비스는 환불 금액에서 제외될 수 있습니다.'] },
      { heading: '3. 서비스 장애', paragraphs: ['온고의 중대한 장애로 유료 기능을 이용하지 못한 경우 장애 시간과 영향 범위를 확인해 합리적인 보상 또는 기간 연장을 검토합니다. 외부 플랫폼의 정책 변경·권한 만료·플랫폼 장애로 게시가 실패한 경우에는 외부 플랫폼 상태와 작업 이력을 함께 확인합니다.'] },
      { heading: '4. 처리 결과', paragraphs: ['승인된 환불은 원 결제수단으로 요청하며, 실제 입금 시점은 결제대행사와 카드사·은행의 처리 일정에 따라 달라질 수 있습니다. 동일 결제에 대한 중복 요청은 하나의 요청으로 묶어 처리합니다.'] },
    ],
  },
  'data-deletion': {
    title: '계정·데이터 삭제 안내',
    intro: '온고 계정과 연결된 외부 채널·미디어·게시 작업 데이터를 삭제하는 방법을 안내합니다.',
    contact: '삭제 문의',
    lastUpdated: '최종 업데이트',
    backToLogin: '로그인으로 돌아가기',
    contactHeading: '삭제 요청이 막힌 경우',
    contactBody: '삭제 전 결제·환불·법정 보존 또는 진행 중인 외부 게시 작업을 확인해야 할 수 있습니다. 화면에 표시된 지원 참조값과 함께 아래 이메일로 문의하면 필요한 확인 절차를 안내합니다.',
    sections: [
      { heading: '1. 앱에서 확인하기', paragraphs: ['로그인 후 설정 → 계정에서 계정 삭제 가능 여부를 확인합니다. 현재는 연결된 데이터의 안전한 삭제 정책이 완성되기 전까지 앱의 삭제 요청이 차단될 수 있습니다. 차단되면 계정 데이터는 변경되지 않으며, 화면의 안내에 따라 고객지원에 문의해 주세요.'] },
      { heading: '2. 외부 플랫폼 데이터', paragraphs: ['온고가 보관하는 외부 채널 토큰은 삭제 대상에 포함됩니다. 이미 YouTube, Instagram, TikTok 등 외부 플랫폼에 게시된 콘텐츠와 해당 플랫폼이 보유한 데이터의 삭제는 각 플랫폼의 계정 설정 또는 삭제 API를 통해 별도로 요청해야 합니다.'] },
      { heading: '3. 보존 예외', paragraphs: ['결제·세무·분쟁·보안 감사와 같이 법령 또는 정당한 권리 보호를 위해 필요한 정보는 해당 목적과 기간 동안 제한적으로 보존할 수 있습니다. 보존 기간이 지나면 안전하게 삭제합니다.'] },
      { heading: '4. 처리 확인', paragraphs: ['삭제 요청이 접수되면 요청 상태를 확인하고, 완료 또는 차단 사유를 안내합니다. 브라우저에서 로그아웃만 하는 것은 서버 데이터 삭제가 아니므로 삭제 가능 상태와 처리 결과를 확인해 주세요.'] },
    ],
  },
  support: {
    title: '고객지원',
    intro: '로그인, 채널 연결, 게시 실패, 결제와 계정 삭제 문제를 빠르게 확인할 수 있는 지원 안내입니다.',
    contact: '지원 이메일 보내기',
    lastUpdated: '운영 정보',
    backToLogin: '로그인으로 돌아가기',
    contactHeading: '문의할 때 함께 보내 주세요',
    contactBody: '문제 화면의 작업 ID·지원 참조값·발생 시각·플랫폼 이름을 보내 주세요. 액세스 토큰, 비밀번호, 카드번호와 원본 개인정보는 보내지 마세요.',
    sections: [
      { heading: '로그인과 OAuth', paragraphs: ['Google·카카오 로그인 또는 외부 채널 연결이 실패하면 계정 이메일, 브라우저, 발생 시각과 화면에 표시된 오류를 알려 주세요. OAuth 동의 화면에서 요구하는 권한을 거부하면 연결할 수 없습니다.'] },
      { heading: '게시와 자동 숏츠', paragraphs: ['영상 ID, 대상 플랫폼·채널, 예약 시각, 현재 상태를 함께 보내 주세요. 게시 결과에 플랫폼 링크가 없는 상태는 완료로 간주하지 않으며, 화면의 재확인 또는 재시도 기능을 먼저 사용할 수 있습니다.'] },
      { heading: '결제와 환불', paragraphs: ['결제 이메일, 결제일과 결제 식별자를 보내 주세요. 카드번호 전체와 인증정보는 절대 보내지 마세요. 환불 조건은 환불정책과 결제대행사 처리 결과를 함께 기준으로 확인합니다.'] },
      { heading: '운영 범위', paragraphs: ['지원 가능 시간과 답변 시간은 운영 환경과 문의량에 따라 달라질 수 있습니다. 긴급한 보안 문제는 제목에 “보안”을 표시해 보내 주세요.'] },
    ],
  },
}

const en: Record<DocumentKind, LegalCopy> = {
  terms: {
    title: 'Terms of Service', intro: 'Please review these terms before using ongo for content creation, scheduling, publishing, and performance management.', contact: 'Contact us', lastUpdated: 'Last updated', backToLogin: 'Back to login', contactHeading: 'Questions about these terms', contactBody: 'For questions about the service, these terms, or exercising your rights, contact support using the email below. Please use a reply-capable address.', sections: [
      { heading: '1. Service scope', paragraphs: ['ongo is a SaaS product that helps you publish user-provided media to social platforms, manage schedules, and manage subtitles, metadata, and performance data. Availability depends on each platform’s policies, permissions, and API status.'] },
      { heading: '2. Accounts and connected channels', paragraphs: ['You must control the account and publishing permissions for every external channel you connect. Expired or revoked permissions may delay or prevent publishing; ongo shows the resulting status and recovery options in the product.'] },
      { heading: '3. Publishing and automation', paragraphs: ['When you schedule, repeat, generate an automated short, or run a UGC workflow, ongo processes the conditions you provide. Platform review, policy, outages, and content limits can affect the final result, so verify the status and platform link for important posts.'] },
      { heading: '4. Prohibited use', paragraphs: ['You may not use ongo to infringe rights or privacy, distribute spam, fraud, malware, or unlawful content, bypass platform controls, or access another user’s workspace or data.'] },
      { heading: '5. Fees and changes', paragraphs: ['Prices, usage, billing cycles, and plan scope are shown at checkout. We may change features or pricing with notice. Payments and refunds are handled under the refund policy and applicable law.'] },
      { heading: '6. Restriction and termination', paragraphs: ['We may restrict use for security, legal, payment, or platform-policy reasons. After a deletion request, we delete related data where permitted and retain only information required by law or legitimate dispute, accounting, or security obligations.'] },
    ]
  },
  privacy: {
    title: 'Privacy Policy', intro: 'This notice explains the information ongo processes for sign-in, connected channels, publishing, and support.', contact: 'Privacy contact', lastUpdated: 'Effective date', backToLogin: 'Back to login', contactHeading: 'Exercise your privacy rights', contactBody: 'Contact the email below for access, correction, deletion, restriction, or privacy questions. We verify identity and legal retention requirements before responding.', sections: [
      { heading: '1. Information we process', paragraphs: ['We process email, name, profile identifiers, and authentication state. When you use features, we process media, subtitles, titles, descriptions, hashtags, schedules, channel identifiers, and publishing results. For payments, we process payment identifiers and subscription state; payment providers process the underlying payment details.'] },
      { heading: '2. Purposes', paragraphs: ['We use information for account authentication, channel connections, publishing and retries, automated shorts and UGC workflows, analytics, billing and refunds, support, security, and incident response. We do not sell it or publish externally without your request.'] },
      { heading: '3. Providers and international transfers', paragraphs: ['We may share the minimum information needed with requested platforms, identity providers, storage, AI, payment, and monitoring providers. Review those providers’ notices for their locations and retention practices; ongo minimizes permissions and transfer purposes.'] },
      { heading: '4. Retention and deletion', paragraphs: ['After account deletion, we delete account, channel-token, media, and job data that is no longer needed. Payment, tax, dispute, and security records may be retained for the period required by law. Content already published to an external platform must be removed under that platform’s policies and API support.'] },
      { heading: '5. Cookies and local storage', paragraphs: ['We use browser storage needed for tokens, language, theme, and screen preferences. Blocking storage may prevent sign-in or some settings from working.'] },
      { heading: '6. Your rights', paragraphs: ['You may request access, correction, deletion, or restriction of your personal information, subject to identity checks, other people’s rights, and legal retention duties.'] },
    ]
  },
  refund: {
    title: 'Refund Policy', intro: 'This policy explains cancellation and refund requests for subscriptions and credits.', contact: 'Refund contact', lastUpdated: 'Last updated', backToLogin: 'Back to login', contactHeading: 'How to request a refund', contactBody: 'Include the billing email, payment date, payment identifier, and reason. Never send your full card number or password. We review the payment provider’s rules and applicable law and reply with the outcome and expected timing.', sections: [
      { heading: '1. Cancellation', paragraphs: ['Cancel renewal from subscription settings before the next billing cycle. A cancelled subscription may remain active through the already-paid period; check the cancellation timestamp to avoid the next charge.'] },
      { heading: '2. Review', paragraphs: ['Refund eligibility depends on product type, usage or consumed credits, payment date, service availability, payment-provider rules, and applicable law. Services already delivered or used may be excluded.'] },
      { heading: '3. Service outages', paragraphs: ['For a material ongo outage, we review the affected period and may provide a reasonable credit or extension. External platform policy changes, revoked permissions, or platform outages are assessed with the external status and job history.'] },
      { heading: '4. Processing', paragraphs: ['Approved refunds are requested to the original payment method. The payment provider, card issuer, or bank controls the final settlement time. Duplicate requests for one payment are consolidated.'] },
    ]
  },
  'data-deletion': {
    title: 'Account and Data Deletion', intro: 'Follow these steps to delete your ongo account and associated channels, media, and publishing jobs.', contact: 'Deletion contact', lastUpdated: 'Last updated', backToLogin: 'Back to login', contactHeading: 'If deletion is blocked', contactBody: 'Pending payment, refund, legal retention, or external publishing work may require review. Send the support reference shown in the product to the email below and we will explain the required steps.', sections: [
      { heading: '1. Check it in the app', paragraphs: ['After signing in, open Settings → Account to check whether deletion is available. Until the safe deletion policy for all connected data is complete, the app may block the request. When blocked, no account data is changed; follow the on-screen instructions to contact support.'] },
      { heading: '2. External platform data', paragraphs: ['Tokens held by ongo are included in deletion. Content already published to YouTube, Instagram, TikTok, or another platform must be removed through that platform’s settings or supported deletion API.'] },
      { heading: '3. Retention exceptions', paragraphs: ['Payment, tax, dispute, and security records may be retained for the limited period required by law or legitimate rights protection, then securely deleted.'] },
      { heading: '4. Confirmation', paragraphs: ['We show the request status and completion or blocking reason. Signing out of the browser is not server-side deletion; check the deletion status and result.'] },
    ]
  },
  support: {
    title: 'Support', intro: 'Get help with sign-in, channel connections, publishing failures, billing, and account deletion.', contact: 'Email support', lastUpdated: 'Operating information', backToLogin: 'Back to login', contactHeading: 'Include this information', contactBody: 'Share the job ID, support reference, time, and platform shown on the problem screen. Never send access tokens, passwords, card numbers, or unnecessary personal data.', sections: [
      { heading: 'Sign-in and OAuth', paragraphs: ['For Google, Kakao, or channel connection failures, include the account email, browser, time, and displayed error. Refusing required OAuth permissions prevents the connection.'] },
      { heading: 'Publishing and automated shorts', paragraphs: ['Include the video ID, target platform or channel, scheduled time, and current status. A result without a platform URL is not treated as complete; use the product’s recheck or retry action first.'] },
      { heading: 'Billing and refunds', paragraphs: ['Include billing email, payment date, and payment identifier. Never send a full card number or authentication data. We review the refund policy and payment-provider result together.'] },
      { heading: 'Operating scope', paragraphs: ['Response times depend on operating conditions and volume. For urgent security reports, include “Security” in the subject.'] },
    ]
  },
}

const copy = computed(() => (locale.value === 'en' ? en : ko)[documentKind.value])
const documentLinks = computed(() => locale.value === 'en'
  ? [{ kind: 'terms' as const, path: '/terms', label: 'Terms' }, { kind: 'privacy' as const, path: '/privacy', label: 'Privacy' }, { kind: 'refund' as const, path: '/refund', label: 'Refunds' }, { kind: 'data-deletion' as const, path: '/data-deletion', label: 'Data deletion' }, { kind: 'support' as const, path: '/support', label: 'Support' }]
  : [{ kind: 'terms' as const, path: '/terms', label: '이용약관' }, { kind: 'privacy' as const, path: '/privacy', label: '개인정보처리방침' }, { kind: 'refund' as const, path: '/refund', label: '환불정책' }, { kind: 'data-deletion' as const, path: '/data-deletion', label: '계정·데이터 삭제' }, { kind: 'support' as const, path: '/support', label: '고객지원' }])
</script>

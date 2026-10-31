# 완료

# 01. 경쟁 조사 (1단계 A)

작성 2026-10-31 · Claude(term_ee09746a, 조사 담당). 웹 조사만 했고 코드는 건드리지 않았다.

## 조사 방법과 한계
- 각 서비스의 공식 가격·기능·도움말 페이지와 Trustpilot을 직접 열어 확인했다. 확인 기준일은 2026-10-31이다.
- **G2·Capterra는 직접 열면 접근이 막혔다(403).** 평점과 리뷰 요지는 검색 결과 요약과 비교 사이트 인용으로 대신했다. 이렇게 옮긴 내용은 "(검색 요약)"으로 표시했다.
- **Reddit은 검색 도구로 글타래를 직접 열지 못했다.** 다른 글이 인용한 Reddit 내용만 옮겼다.
- 리뷰 블로그 상당수가 경쟁 회사 글이다(FrameOS, quso.ai, 피카클립, Overlap 등). 한쪽으로 치우쳤을 수 있다.
- 확인하지 못한 것은 "확인 못 함", 추정은 "(추정)"이라고 적었다. 원화 환산은 1달러=1,400원으로 가정한 추정이다.
- Trustpilot 평점은 주로 청구·환불에 화가 난 사용자가 남긴다. 그래서 G2·Capterra보다 크게 낮게 나온다. 기능 품질보다는 **청구·해지 경험의 지표**로 읽는 게 맞다.

---

## 0. 한눈에 비교

| 서비스 | 성격 | 롱폼→쇼츠 자동 생성 | 직접 게시·예약 | 통합 댓글함 | 분석 깊이 | 한국어 화면 | 네이버 클립 | 월 요금(대표) |
|---|---|---|---|---|---|---|---|---|
| Buffer | 예약 게시 | 없음 | 있음(11개+) | 있음(전 요금제) | 얕음 | 없음 | 없음 | 채널당 $5~10 |
| Hootsuite | 기업용 통합 관리 | 없음 | 있음 | 있음(Standard~) | 깊음(경쟁사·리스닝) | 없음 | 없음 | 사용자당 $99~399 |
| Later | 인스타 중심 예약 | 없음 | 있음 | Growth부터(IG·FB·TikTok만) | 중간 | 없음 | 없음 | $18.75~82.50(연간) |
| Sprout Social | 기업용 통합 관리 | 없음 | 있음 | 있음(Standard~), 최고 수준 | 가장 깊음 | 없음 | 없음 | 사용자당 $79~399 |
| Metricool | 예약 + 분석 | 없음 | 있음 | 있음(일부) | 깊음(경쟁 100개) | 없음 | 없음 | 브랜드 기준 $20~210 |
| Publer | 예약 게시 | 없음(수동 편집기) | 있음 | 없음(Kibo 준비 중) | 중간 | 없음 | 없음 | $12~21 + 계정 추가 |
| Repurpose.io | 자동 재게시 | 없음 | 있음(재게시 방식) | 없음 | 없음 | 없음(추정) | 없음 | $35~179 |
| Opus Clip | 쇼츠 생성 | **있음(분야 대표)** | 있음(프로: 9개) | 없음 | 비즈니스만 | 확인 못 함 | 없음 | $15~29 |
| Vizard | 쇼츠 생성 | **있음** | 있음(6개) | 없음 | 확인 못 함 | **있음** | 없음 | $29~39(월간) |
| Klap | 쇼츠 생성 | **있음** | 있음(4~5개) | 없음 | 확인 못 함 | 확인 못 함 | 없음 | $29~189 |
| Descript | 대본 기반 편집 | 반자동(후보 제안) | YouTube 일반 영상만 | 없음 | 없음 | 없음(추정) | 없음 | $24~65 |
| Vrew(브루) | 국산 자막 편집 | **있음** | 없음(내보내기만) | 없음 | 없음(추정) | **있음(최상)** | 없음 | 0~39,600원 |
| CapCut(캡컷) | 모바일 편집 | **있음** | 틱톡(앱만), 예약은 Pippit | 없음 | Pippit에만 | **있음** | 없음 | 25,000원(Pro) |
| 한큐 | 국산 동시 게시 | 없음 | 있음(5개) | 확인 못 함 | 있음 | 있음 | 언급 없음 | 0~39,900원 |
| 포스트림 | 국산 자동 재게시 | 없음 | 있음(3개) | 없음 | 보고서 | 있음 | 언급 없음 | 4,500~18,000원 |
| 알파컷 | 국산 쇼츠 생성 | **있음** | 있음(5개, 회사 설명) | 없음 | 채널 분석 | 있음 | 언급 없음 | 확인 못 함 |
| **onGo(참고)** | 통합 | 있음 | 있음 | 있음 | 있음 | 있음 | **계획상 있음 — 아래 4장 주의** | 0 / 9,900 / 19,900 / 49,900원 |

> onGo 행은 이 조사의 대상이 아니다. 우리 기능의 실제 동작은 `02-our-capability.md`(조정 세션)에서 점검한다.

---

## 1. 예약 게시·통합 관리 도구

### 1-1. Buffer
- **주력 기능**: 채널별 예약 게시와 캘린더, 통합 댓글함(Community), 성과 분석(Insights)과 AI 요약(Takeaways), 승인 흐름(Team), 공개 API·MCP 연결 — [가격](https://buffer.com/pricing), [변경 기록](https://buffer.com/changelog)
- **쇼츠 자동 생성**: 없음. 공식 안내는 "긴 영상에서 직접 잘라 쓰라"는 조언뿐이다 — [쇼츠 안내](https://buffer.com/resources/how-to-schedule-youtube-shorts/)
  - YouTube는 쇼츠만 올릴 수 있다(3분 이하, 10GB 이하, 1:1 또는 9:16) — [도움말](https://support.buffer.com/article/562-using-youtube-shorts-with-buffer)
- **지원 플랫폼**: Bluesky, Facebook, Google Business Profile, Instagram, LinkedIn, Mastodon, Pinterest, Substack, Threads, TikTok, X, YouTube(쇼츠만)
- **요금(채널 단위)**
  - Free: 0달러, 채널 3개
  - Essentials: 채널당 월 5달러(연간 결제)
  - Team: 채널당 월 10달러(연간 결제)
  - 월간 결제는 Essentials 6달러, Team 12달러로 보인다. 2026년 중 채널당 1달러쯤 오른 것으로 보이나 (추정)이다 — [use-apify](https://use-apify.com/blog/buffer-review)
- **한국어 지원**: 웹 화면은 영어만 지원한다 — [도움말](https://support.buffer.com/article/929-can-i-use-buffer-in-another-language)
  - iOS 앱도 한국어가 없다 — [앱스토어](https://apps.apple.com/us/app/buffer-plan-schedule-posts/id490474324)
  - 결제는 달러 카드만 받는다 — [결제 도움말](https://support.buffer.com/article/537-accepted-payment-methods)
- **평점**: Trustpilot 4.2점(148건), Capterra 4.5점(1,492건), G2 4.3점(1,041건)
- **강점**: 쉽고 직관적이다. 여러 채널 동시 게시로 시간이 준다. 소규모 사용자에게 가성비가 좋다.
- **불만**: 분석이 얕고 경쟁사 추적이 없다. 채널 연결 끊김, 초안 유실, 게시 실패가 있다. 영상 업로드 오류가 있다.
  - 출처: [Trustpilot](https://www.trustpilot.com/review/buffer.com), [Capterra](https://www.capterra.com/p/143492/Buffer/reviews/), [G2](https://www.g2.com/products/buffer/reviews)(검색 요약)
- **AI 기능**
  - AI 도우미: 전 요금제 무제한
  - 여러 채널을 묶은 성과 요약: 2026-09 추가
  - AI 댓글 답변 제안: 무료는 주 5회
  - MCP 연결(Claude, ChatGPT 등): 2026-06 추가
- **2026년 변화**: AI 도우미와 통합 댓글함을 무료 요금제에도 열었다. 2026-04에 댓글함에 TikTok·YouTube를 붙였다 — [변경 기록](https://buffer.com/changelog)

### 1-2. Hootsuite
- **주력 기능**: 2026-06-24에 "Social OS"로 개편했다 — [보도자료](https://www.hootsuite.com/newsroom/press-releases/hootsuite-rebuilds-for-ai-era-introducing-wisdom)
  - Perch: 제작·게시
  - Nest: 인박스
  - Lumen: 소셜 리스닝, 인수한 Talkwalker 기반
  - Wisdom: AI 에이전트
  - Parliament: 임직원 홍보
- **쇼츠 자동 생성**: 없음. 공식 블로그(2026-08-14)에 "Video clipping happens outside Perch, so pair it with a repurposing tool"이라고 적혀 있다 — [블로그](https://blog.hootsuite.com/ai-social-media-content-creation-tools/)
- **지원 플랫폼**: Facebook, Instagram, X, LinkedIn, TikTok(비즈니스 계정만), YouTube, Threads, WhatsApp, Pinterest — [연동 목록](https://www.hootsuite.com/platform/integrations)
  - Bluesky는 공식 목록에 없다.
- **요금(사용자 단위)**
  - Standard 99달러, Professional 199달러, Advanced 399달러. 연간 결제 기준 사용자당 월 요금이다.
  - 무료 요금제는 없고 14일 체험만 있다.
  - 출처: [요금제](https://www.hootsuite.com/plans), [lilachbullock](https://www.lilachbullock.com/hootsuite-pricing/)
- **한국어 지원**: 화면 언어 7개(영·프·독·이·포·스·일) 중 한국어는 없다 — [도움말](https://help.hootsuite.com/s/article/change-language)
  - 인박스의 언어 감지만 한국어를 지원한다.
- **평점**: Trustpilot 1.3점(556건, 1점이 69%), Capterra 4.4점(3,804건), G2 4.3점(약 7,476건)
- **강점**: 여러 플랫폼 예약과 승인·분석을 한 화면에서 한다. 지원 플랫폼이 넓다.
- **불만**: 해지 후 과금과 환불 거부가 2026년에도 반복된다. 지원이 느리다. 비싼데 하위 요금제는 기능이 빠져 있다.
  - 출처: [Trustpilot](https://www.trustpilot.com/review/hootsuite.com), [Capterra](https://www.capterra.com/p/121701/HootSuite/reviews/)
- **AI 기능**
  - Wisdom(초안, 추천, 분석)
  - 스마트 답변과 90일 예측: Professional부터
  - 최적 게시 시간
  - MCP 연결
- **댓글함·분석**: 댓글함은 Standard부터, 배정·분배는 Advanced부터 있다. 경쟁사 5~20곳 비교와 리스닝까지 지원해 분석이 깊다.

### 1-3. Later
- **주력 기능**: 시각형 캘린더, 인스타그램 중심 예약, 링크인바이오, 최적 시간 추천, 인플루언서 관리 — [가격](https://later.com/pricing/)
  - 2025-01에 Mavely를 인수했고, 2026년에 통합했다 — [Later 블로그](https://later.com/blog/mavely-is-now-later/)
- **쇼츠 자동 생성**: 없음. 영상 길이를 다듬는 기본 편집만 있다 — [AI 도구](https://later.com/social-media-ai-tools/)
- **지원 플랫폼**: Instagram, Facebook, Threads, Pinterest, TikTok, LinkedIn, YouTube(쇼츠), Snapchat
  - X는 2025-08-28에 지원을 끊었고, Bluesky는 지원하지 않는다 — [Blotato](https://www.blotato.com/blog/later-pricing)
- **요금(연간 결제)**
  - Starter 18.75달러, Growth 37.50달러, Scale 82.50달러
  - 월간 결제 시 Growth 50달러, Scale 110달러다 — 같은 출처
- **한국어 지원**: 웹 화면은 영·스·프·독만, 앱은 영어만 지원한다 — [도움말](https://help.later.com/hc/en-us/articles/360042869154-What-Languages-Does-Later-Support)
- **평점**: Trustpilot 1.4점(352건), Capterra 4.4점(399건), G2 4.5점(384건)
- **강점**: 쉽다. 시각형 캘린더가 좋다. 가성비가 좋다.
- **불만**: 예고 없는 연간 자동 갱신 청구와 환불 거부(2026-05~08 리뷰 다수). 하위 요금제 분석이 빈약하다. 프로필 연결이 끊긴다.
  - 출처: [Trustpilot](https://www.trustpilot.com/review/later.com)
- **AI 기능**: 말투를 학습한 캡션 작성(1크레딧에 1개, Starter는 월 5크레딧)과 최적 시간 추천이 있다.
  - 캡션 품질이 "ChatGPT만큼 날카롭지 않다"는 평이 있다(검색 요약).
- **댓글함·분석**: 댓글함은 Growth부터 있고 Instagram·Facebook·TikTok만 다룬다. 경쟁사 비교는 Scale에만 있다.

### 1-4. Sprout Social
- **주력 기능**: Smart Inbox(통합 인박스), 예약 게시, 심화 분석·보고서, 리스닝(유료 추가), AI 에이전트 Trellis — [가격](https://sproutsocial.com/pricing/), [Smart Inbox](https://sproutsocial.com/features/smart-inbox/)
- **쇼츠 자동 생성**: 없음 — [Sprout 글](https://sproutsocial.com/insights/short-form-video/)
- **지원 플랫폼**: Bluesky, Facebook, Instagram, LinkedIn, Pinterest, Reddit, Snapchat, Threads, TikTok, X, YouTube, 그리고 인박스용 WhatsApp·Messenger — [FAQ](https://sproutsocial.com/faq/), [startupowl](https://startupowl.com/reviews/sprout-social)
- **요금(사용자 단위)**
  - Essentials: 79달러(연간 결제) / 99달러(월간 결제). 인박스는 없다.
  - Standard 199달러, Professional 299달러, Advanced 399달러
- **한국어 지원**: 본 제품 화면은 영·스·프·이·포만 지원한다 — [도움말](https://support.sproutsocial.com/hc/en-us/articles/202508636-Localization)
- **평점**: Trustpilot 1.8점(89건), G2 4.4점(7,430건), Capterra 4.4점(608건)
- **강점**: 깔끔한 화면, Smart Inbox(긍정 평가 97%), 팀 협업, 보고서 자동화
- **불만**: 비싸다(가격 관련 리뷰의 65%가 부정). 게시 실패와 데이터 부정확. 연간 계약 자동 갱신 분쟁.
  - 출처: [G2](https://www.g2.com/products/sprout-social/reviews)(검색 요약), [Trustpilot](https://www.trustpilot.com/review/sproutsocial.com)
- **AI 기능**: Trellis(인박스 요약, 보고서 요약, 답변 초안)는 2026-07에 전 요금제로 확대됐다. 최적 발송 시간 추천도 있다 — [투자자 공시](https://investors.sproutsocial.com/news/news-details/2026/Now-Generally-Available-Sprout-Socials-AI-Agent-Trellis-Is-Helping-Reshape-How-Social-Teams-Prove-Their-Value/default.aspx)
- **댓글함·분석**: 조사 대상 중 가장 깊다. 경쟁사, 태그, 광고, 팀 생산성 보고서까지 있다. 감정 분석은 Advanced부터다.

### 1-5. Metricool
- **주력 기능**: 브랜드 단위 플래너, 통합 분석과 경쟁 계정 분석, 통합 인박스, AI 보고서(Studio), 광고 분석, 자동화(Flows: 댓글이 달리면 자동 DM) — [가격](https://metricool.com/pricing/)
- **쇼츠 자동 생성**: 없음. 2026년 업데이트 기록에도 클립 기능이 없다 — [6월 업데이트](https://metricool.com/product-updates-june-2026/), [8월 업데이트](https://metricool.com/product-updates-august-2026/)
- **지원 플랫폼**: Facebook, Instagram, Threads, X(유료 추가), Bluesky, Pinterest, TikTok, YouTube, Twitch, LinkedIn, Google 비즈니스, 웹·블로그
- **요금(브랜드 단위, 월간 결제)**
  - Free(브랜드 1개)
  - Starter: 25달러(브랜드 5개)부터
  - Advanced: 67~210달러
  - 연간 결제는 최대 24% 할인된다.
- **2026년 변화**
  - 4월 이전에 가격을 올렸다 — [socialpilot](https://www.socialpilot.co/insights/metricool-pricing)
  - 7월에 X 추가 기능을 계정당 5달러에서 10달러로 두 배 올렸다.
  - 8월에 Flows를 출시했다.
- **한국어 지원**: 화면 지원 언어 8개 중 한국어가 없다 — [getapp](https://www.getapp.co.uk/software/2060489/metricool)
  - AI 보고서는 한국어로 지시하면 한국어로 나온다고 하나, 품질 평가는 확인 못 함 — [튜토리얼](https://metricool.com/metricool-mega-tutorial/)
- **평점**: Trustpilot 4.1점(692건), G2 4.5점, Capterra 4.4점
- **강점**: 사람 상담원에게 쉽게 닿는다(2026-10 리뷰에서 반복 칭찬). 브랜드 단위 과금이라 가성비가 좋다. 팔로워·해시태그 분석이 강하다.
- **불만**: AI 상담 봇이 같은 답만 반복한다. "게시 완료로 표시됐는데 실제로는 안 올라감"(2026-10). 자동 갱신과 환불 불가.
  - 출처: [Trustpilot](https://www.trustpilot.com/review/metricool.com)
- **AI 기능**: 캡션·해시태그 생성(브랜드당 월 5~35크레딧), 최적 게시 시간(무료), AI 인사이트, MCP 연결 — [최적 시간 도움말](https://help.metricool.com/best-time-to-post-on-social-media-in-metricool-w7ll9)
- **댓글함·분석**
  - 인박스 범위: Facebook·Instagram은 댓글과 DM, TikTok·YouTube·LinkedIn은 댓글만 다룬다.
  - 인박스 한계: 삭제가 안 되고 새 메시지 알림이 없다 — [인박스 도움말](https://help.metricool.com/inbox-manager-how-to-manage-messages-and-comments-from-metricool-s9zze)
  - 경쟁 계정은 최대 100개까지 비교한다 — [bloggingwizard](https://bloggingwizard.com/metricool-review/)

### 1-6. Publer
- **주력 기능**: 대량 예약(CSV), 자동·반복 게시, 스레드 작성기, 분석·경쟁 계정 분석(Business 이상), AI Assist, 사진·영상 편집기, API·MCP — [지원 플랫폼](https://publer.com/help/en/article/what-social-networks-are-supported-npoun1/)
- **쇼츠 자동 생성**: 없음. 수동 영상 편집기만 있다(2025년 3분기 출시) — [편집기 소개](https://publer.com/blog/all-in-one-editing-upgraded-photo-editor-new-video-editor-in-publer/)
- **지원 플랫폼(13개)**: Facebook, Instagram, X, LinkedIn, Pinterest, Google 비즈니스, YouTube, WordPress, TikTok, Telegram, Mastodon, Threads, Bluesky
- **요금(계정·팀원 단위)**
  - Free: 계정 3개
  - Professional: 월 12달러(계정 3개)
  - Business: 월 21달러(계정 3개)
  - 계정과 팀원을 추가할 때마다 요금이 붙는다.
  - 공식 가격 페이지는 열리지 않아 제3자 자료로 적었다 — [postplanify](https://postplanify.com/publer-pricing)
- **한국어 지원**: 화면 한국어가 없다 — [2025 회고](https://publer.com/blog/year-in-review-2025/)
- **평점**: Trustpilot 4.9점(1,055건), G2 4.7~4.8점, Capterra 4.8점(896건). **조사 대상 중 사용자 만족이 가장 높다.**
- **강점**: 깔끔하고 쉽다. 대량·자동 예약이 유연하다. Sprout·Hootsuite보다 싸다. API가 믿을 만하다.
- **불만**: 계정 단위 과금이라 플랫폼이 많으면 비싸진다. 웹과 앱의 구독 관리가 따로 논다. 분석 수치가 플랫폼 수치와 다를 때가 있다.
  - 출처: [Trustpilot](https://www.trustpilot.com/review/publer.io), [socialk.it](https://socialk.it/en/compare/metricool-vs-publer)
- **댓글함·분석**
  - 통합 인박스가 없다. 자기 게시물 댓글에 답하는 것만 된다.
  - DM·멘션을 통합하는 별도 제품 Kibo가 2026년 조기 체험 중이다 — [Kibo 소개](https://publer.com/blog/welcome-kibo/)
  - 경쟁 계정 분석은 Facebook·Instagram·X만 지원한다 — [경쟁 분석 문서](https://publer.com/docs/analytics/competitor-analysis)

### 1-7. Repurpose.io
- **주력 기능**: 원본 플랫폼에 새 영상이 올라오면 다른 플랫폼에 자동으로 다시 올린다(워크플로). TikTok 영상을 워터마크 없는 원본으로 재게시하고, 기존 영상을 일정에 따라 하나씩 내보낸다 — [자동 게시 안내](https://support.repurpose.io/en/article/how-auto-publishing-works-in-repurpose-aktmnm/)
- **동작 방식**
  - 워크플로 하나에 목적지는 하나다. 세 곳에 보내려면 워크플로 3개가 필요하다 — [skitrate](https://skitrate.com/blog/repurpose-io-workflow-setup-guide-2026)
  - 워터마크 제거는 로고를 덮는 게 아니다. TikTok이 워터마크를 붙이기 전의 원본을 가져온다 — [TikTok FAQ](https://support.repurpose.io/en/article/tiktok-connection-faq-troubleshooting-guide-cxguap/)
  - 2026-06-22에 같은 플랫폼끼리의 워크플로를 없앴다. 자동 게시는 시간당 3건으로 제한했다 — [릴리스 노트](https://support.repurpose.io/en/article/release-notes-whats-new-at-repurpose-1y1nxx5/)
- **쇼츠 자동 생성**: 없음. 리뷰들은 OpusClip과 함께 쓰라고 권한다 — [socialrails](https://socialrails.com/blog/repurpose-io-review)
- **지원 플랫폼** — [지원 플랫폼](https://support.repurpose.io/en/article/supported-platforms-roles-and-media-types-j22278/)
  - 원본과 목적지 모두 가능: TikTok, Instagram, Facebook, Snapchat, YouTube
  - 목적지로만 가능: Pinterest, LinkedIn, Threads, X, Bluesky 등
- **요금**: Starter 35달러, Pro 79달러, Agency 179달러(월간 결제) — [가격](https://repurpose.io/pricing/)
  - Agency는 2026년 1월에 149달러였던 것으로 보여 그 사이 오른 것으로 보인다 (추정).
- **한국어 지원**: 화면은 영어만 지원하는 것으로 보인다 (추정).
- **평점**: Trustpilot 4.0점(93건). 5점 62%, 1점 33%로 양극단이다.
- **강점**: 한 번 설정하면 끝나는 완전 자동화. 지원팀이 친절하다.
- **불만**: **알림 없이 조용히 실패한다** — "Things will just break… you won't even know"(2026-06). 예고 없이 워크플로를 꺼버렸다. 자체 분석이 없다.
  - 출처: [Trustpilot](https://www.trustpilot.com/review/repurpose.io)
  - 사용자의 27~31%가 실패나 재인증 문제를 겪는다는 분석도 있다 — [allaboutai](https://www.allaboutai.com/ai-reviews/repurpose-io/)

---

## 2. 롱폼→쇼츠 자동 생성 도구

### 2-1. Opus Clip (분야 대표)
- **주력 기능**: ClipAnything(아무 영상이나 쇼츠로 자르기), ReframeAnything(물체를 추적해 화면 비율 변경), 자동 자막, 바이럴 점수, AI 보조 영상(B-roll), Agent Opus(대본으로 영상 생성), 예약 게시, API·MCP(비즈니스) — [홈](https://www.opus.pro/), [요금·크레딧](https://help.opus.pro/docs/article/plans-and-credits)
- **생성 방식**
  - 바이럴 점수는 0~99점이다. 첫머리·흐름·가치·유행 네 가지를 보고, 유료 요금제에서만 보인다 — [점수 안내](https://help.opus.pro/docs/article/virality-score)
  - 클립 길이는 0~15분 범위에서 고른다 — [길이 안내](https://help.opus.pro/docs/article/select-clip-length)
- **품질 평가**
  - 자막 정확도: 회사는 97~99%라고 주장한다. 독립 시험에서는 영어 약 98%, 그 밖의 언어 약 95%였다 — [reap 보고서](https://reap.video/reports/state-of-top-ai-video-clipping-tools-2026)
  - 클립 선정: 결론 대사만 뽑고 앞 맥락을 놓친다. 클립의 20~40%는 손보거나 버려야 한다 — [thecmo](https://thecmo.com/tools/opusclip-review/), [scored](https://scored.tools/blog/opus-clip-review-2026/)
  - 화자 추적: 엉뚱한 사람에게 화면이 넘어가거나 문장 중간에 정지 화면에 고정된다. 여러 명 토론에서 약하다 — [FrameOS(2026-10-20)](https://frameos.studio/blog/opusclip-review)
- **직접 게시**
  - 스타터: YouTube Shorts·TikTok·Instagram 자동 게시만 된다.
  - 프로: 게시와 예약 모두 된다. YouTube, Instagram, TikTok, LinkedIn, Facebook, X, Pinterest, Threads, Bluesky를 지원한다 — [가격](https://www.opus.pro/pricing)
  - 예약 기능에서 TikTok 연결 끊김과 게시 실패가 조용히 일어난다는 지적이 있다(검색 요약).
- **요금(1크레딧 = 원본 1분)**
  - 무료: 0달러, 60크레딧, 워터마크, 3일 뒤 만료
  - 스타터: 15달러, 150크레딧
  - 프로: 29달러, 300크레딧
  - 한국에서 접속하면 더 싸다. 스타터 12달러, 프로 월 23달러(2026-10-28 확인) — [스쿨오더](https://schoolorder.kr/blog/opusclip-starter-pro-shorts-school)
  - 2026년 중 프로 연간 요금이 바뀐 것으로 보인다 (추정, 시행일 확인 못 함) — [usagepricing](https://www.usagepricing.com/blueprint/opus-clip)
- **한국어 지원**
  - 한국어는 **베타**다. 이모지·강조 단어가 부정확할 수 있다고 공식 안내한다 — [지원 언어](https://help.opus.pro/docs/article/video-languages-supported)
  - 국내 실제 시험: "자막 애니메이션 타이밍이 실제 발화와 맞지 않는 경우가 많았다", "하이라이트 강조 방식도 한국 정서와는 다소 어긋난 부분" — [사이버타임즈(2026-02-19)](https://cybertimes.co.kr/m/view.php?idx=29737)
  - 한국어 글꼴이 1개뿐이고, 자동 이모지가 한국어를 지원하지 않는다 — [콘텐츠랩코리아](https://contentslabkorea.com/opus-clip-%EB%A6%AC%EB%B7%B0-%EC%88%8F%ED%8F%BC-%EC%A0%9C%EC%9E%91%EC%9D%98-%EA%B2%8C%EC%9E%84%EC%B2%B4%EC%9D%B8%EC%A0%80%EA%B0%80-%EB%90%A0-%EC%88%98-%EC%9E%88%EC%9D%84%EA%B9%8C/)
  - 화면 한국어는 확인 못 함(`/ko` 경로는 404).
- **평점**: Trustpilot 4.0점(512건, 1점 20%), Product Hunt 4.5점(78건)
- **강점**: 팟캐스트·인터뷰 클립 선정은 분야 최상위다. 빠르다. 깨끗한 음성에서 자막 품질이 좋다.
- **불만**
  - 크레딧: "nearly 3,000 unused credits had expired"(2026-10-14)
  - 해지: "16 emails to cancel and they're still trying to charge me"(2026-09-26)
  - 기본 편집 기능이 유료 장벽에 막혀 있다.
  - 출처: [Trustpilot](https://www.trustpilot.com/review/opus.pro), [Product Hunt](https://www.producthunt.com/products/opus-clip/reviews)
- **분석·댓글**: 클립 분석은 비즈니스 요금제만 제공한다(프로는 "준비 중"). 댓글 관리는 없다.

### 2-2. Vizard
- **주력 기능**: AI 클립 추출과 바이럴 점수, 여러 화자에 대응하는 화면 조정(AI Reframe 2), 35개 이상 언어 자막, 보조 영상 자동 삽입, 예약 게시와 플랫폼별 제목·설명·해시태그 생성, Vizard Agent(2026년 신기능) — [클립 생성기](https://vizard.ai/tools/ai-viral-clip-maker)
- **생성 방식**: 감정 변화, 의미 구조, 속도, 이야기 흐름으로 고른다. 1시간 영상에서 최대 40개, 클립당 보통 30~90초다.
- **품질 평가**
  - "클립 선정·확대·자막 위치를 수동으로 고쳐야 할 때가 많다"(Capterra 인용, 검색 요약) — [roborhythms](https://www.roborhythms.com/vizard-ai-review/)
  - G2 단점 요약: 비용, 요금제 제한, 렌더링이 느림(검색 요약)
  - 14일 TikTok 시험에서 고득점 클립 조회수가 저득점의 1.6배였다(같은 시험에서 Opus 2.3배, Klap 2.1배). 신뢰도는 낮다 — [buildfastwithai](https://www.buildfastwithai.com/ai-tools/vizard)
- **직접 게시**: YouTube·Shorts, TikTok, Instagram, Facebook, LinkedIn, X. 크리에이터 요금제부터 예약할 수 있다 — [가격](https://vizard.ai/pricing)
  - 예약 기능은 전문 예약 도구보다 단순하다는 평이다(검색 요약).
- **요금**: 크리에이터 월 29달러(연간 14.50달러), 비즈니스 월 39달러(연간 19.50달러). 무료는 720p와 워터마크다.
  - 공식 페이지는 금액 칸이 읽히지 않아 제3자 자료로 적었다 — [techpilot](https://techpilot.ai/tools/vizard-ai/), [usefastlane](https://www.usefastlane.ai/pricing/vizard)
  - 크레딧은 남긴 클립이 아니라 올린 영상 길이만큼 깎인다.
- **한국어 지원**: **화면 대부분이 한국어다**(vizard.ai/ko). 한국어 전사 정확도는 97% 이상이라고 공식 주장하지만 독립 검증은 없다 — [한국어 자막](https://vizard.ai/ko/tools/auto-subtitle-generator-online/korean-subtitles)
  - 한국어 하이라이트 품질에 대한 실사용 후기는 확인 못 함.
- **평점**: G2 4.7점(425건, 검색 요약). Trustpilot 4.7점(2,521건)이지만 최근 12개월 리뷰는 9건뿐이다.
- **강점**: 쉽다. 클립을 뽑은 뒤 예약하고 팀으로 협업하는 흐름이 좋다.
- **불만**: 해지하면 남은 크레딧이 사라지고 환불이 없다 — "blackmail you into maintaining the subscription"(2026-03-18). 지원이 느리다. 긴 영상에서 크레딧 사용량을 예측하기 어렵다.
  - 출처: [Trustpilot](https://www.trustpilot.com/review/vizard.ai)
- **분석·댓글**: 분석은 확인 못 함. 댓글 관리는 없다.

### 2-3. Klap
- **주력 기능**: 말 위주 영상에서 주제를 나눠 클립을 만들고, 바이럴 점수를 매긴다. 화면 분할·게임 화면 배치(Reframe 2), 52개 언어 자막, 29개 언어 AI 더빙, 예약 게시, REST API·MCP·Zapier 연동이 있다 — [홈](https://klap.app/), [MCP](https://klap.app/mcp)
- **품질 평가**
  - 15분 강의 영상으로 19개를 만들었다. 7개는 조금 손보면 쓸 수 있었고, 5개는 크게 고쳐야 했고, 6개는 쓸 수 없었다. 40분 영상은 처리에 실패했다 — [Cybernews(2026-02-03)](https://cybernews.com/ai-tools/klap-ai-review/)
  - 문장이 일찍 잘리고 전문 용어 자막이 틀린다 — [aitoolgems](https://www.aitoolgems.com/review/klap-app)
  - 화면 조정은 호평이다 — "the only one that does the reframing sooooo well"(2026-10-20) — [Trustpilot](https://www.trustpilot.com/review/klap.app)
- **직접 게시**: TikTok, YouTube, Instagram, LinkedIn, Facebook. 연결 계정 수는 무제한이다 — [가격](https://klap.app/pricing)
- **요금**: 베이직 29달러(클립 100개), 프로 79달러(300개), 프로+ 189달러(1,000개). 연간 결제하면 약 절반이다. 무료 요금제는 없다.
  - **2026년에 과금 기준을 여러 번 바꿨다**(영상 수 → 분 → 클립 수) — [valmera](https://valmera.io/blog/klap-review)
- **한국어 지원**: 52개 언어에 한국어가 들어 있다.
  - **국내 시험에서 Opus Clip·Klap·QUSO 중 1위였다**: "자막 인식 정확도는 Opus Clip보다 훨씬 안정적", "발화와 자막 타이밍이 비교적 잘 맞았다" — [사이버타임즈(2026-02-19)](https://cybertimes.co.kr/m/view.php?idx=29737)
  - 다만 같은 시험에서 점수는 "한국 정서에 완벽히 맞진 않아 참고 지표 수준"이라고 했다.
  - 화면 한국어는 확인 못 함.
- **평점**: Trustpilot 3.7점(59건, 1점 30%)
- **강점**: 말하는 사람 위주 영상의 화면 조정이 좋다. 지원 채팅이 빠르다. 원본 1분당 비용이 싸다.
- **불만**: 무료 체험으로는 결과물을 받을 수 없다. 이중 결제 분쟁이 있다. 요금 기준이 자주 바뀐다. 강의·화면 녹화 영상에서 품질이 떨어진다.

### 2-4. Descript
- **주력 기능**: 대본을 고치듯 영상을 편집한다. AI 공동 편집자 Underlord, 음질 개선(Studio Sound), 군말 제거, 클립 만들기, 번역·더빙이 있다 — [가격](https://www.descript.com/pricing), [클립](https://www.descript.com/clips)
- **쇼츠 생성**: 반자동이다. Underlord가 후보를 제안하면 사용자가 고르고 화면 비율은 직접 바꾼다. 화자 자동 추적과 바이럴 점수는 확인 못 함.
  - 품질 불만: "AI feature shockingly poor"(2026-08-15), 강의 편집에서 중요한 단어를 지워 고치는 데 2시간 넘게 걸렸다(2026-08-21) — [Trustpilot](https://www.trustpilot.com/review/descript.com)
- **직접 게시**: YouTube 일반 영상만, 웹 버전에서만 된다. 쇼츠와 브랜드 채널은 안 되고 예약도 없다 — [도움말](https://help.descript.com/hc/en-us/articles/10255756672909-Export-to-YouTube)
- **요금**: 호비스트 24달러, 크리에이터 35달러, 비즈니스 65달러(월간 결제). 남은 분량은 이월되지 않는다.
  - 2026-09에 크레딧 묶음 가격을 공개하고 덤 크레딧을 없앴다 — [hackceleration](https://hackceleration.com/labs/review/descript)
- **한국어 지원**: **한국어 음성 인식이 안 된다.** 공식 전사 언어 27개에 한국어가 없다. 그래서 한국어 영상은 핵심인 대본 편집 자체를 쓸 수 없다 — [지원 언어](https://help.descript.com/script-editing/supported-transcription-languages)
- **평점**: Trustpilot 2.6점(292건, 1점 43%)
- **강점**: 대본 기반 편집이라는 발상. 지원 직원 대응. 장기 사용자 충성도.
- **불만**: 크레딧 소모 — "Burned through 800 credits just trying to clean up filler pauses on a short reel"(2026-10-03). 해지가 어렵다. 오류와 앱 멈춤.

---

## 3. 한국 시장

### 3-1. Vrew(브루, 보이저엑스)
- **주력 기능**: 음성 인식 자동 자막(구글·아마존·위스퍼 엔진 중 선택), 자막을 지우면 영상도 같이 잘리는 글 기반 편집, 무음 삭제, AI 목소리 200종 이상, 번역 자막 — [모켓](https://www.moket.kr/tools/vrew)
- **쇼츠 자동 생성**: 있다.
  - "비디오 리믹스"에서 1분 요약이나 하이라이트(쇼츠 여러 개)를 만든다 — [공식](https://vrew.ai/ko/feature/video-summarizer/)
  - "AI 숏폼 메이커"는 장면 배열, 대본, 자막까지 자동으로 만든다 — [공식](https://vrew.ai/ko/feature/ai-shorts-maker/)
  - 쇼츠 품질을 따로 평가한 후기는 확인 못 함.
- **직접 게시**: 없다. mp4로 내보낸 뒤 직접 올린다 — [공식 FAQ](https://vrew.imweb.me/faq/?bmode=view&idx=12337950)
  - 오래된 FAQ라 최신판에 게시 기능이 생겼는지는 확인 못 함.
- **요금**: 2026-04-22부터 모든 AI 기능을 하나의 크레딧으로 쓴다 — [공식 공지](https://vrew.imweb.me/notice/?bmode=view&idx=170656156)
  - 무료: 200크레딧
  - 라이트: 14,900원, 2,000크레딧
  - 스탠다드: 23,900원, 10,000크레딧
  - 비즈니스: 39,600원, 12,000크레딧
  - 원화 금액은 블로그 자료라 공식 확인이 필요하다.
- **한국어 지원**: 국산이라 최상급이다.
- **강점**: 한국어 자막 정확도가 높다(블로그 자체 시험에서 92~95%, 캡컷·프리미어보다 높음). 문서 고치듯 편집하는 쉬움. 무료로 시작할 수 있다 — [정확도 실험](https://jbeanstalk.com/entry/%EB%B8%8C%EB%A3%A8-%EC%9E%90%EB%A7%89-%EC%A0%95%ED%99%95%EB%8F%84-%EC%8B%A4%ED%97%98-%ED%94%84%EB%A6%AC%EB%AF%B8%EC%96%B4-vs-%EB%B8%8C%EB%A3%A8-vs-%EC%BA%A1%EC%BB%B7-AI-%EC%9D%B8%EC%8B%9D%EB%A5%A0-%EB%B9%84%EA%B5%90)
- **불만**: 편집 중 멈춤(오래된 글이 많다), 고유명사 오인식, 정밀 타임라인 편집이 약함, 데스크톱 중심이라 협업이 어려움 — [질문 게시판](https://vrew.imweb.me/questions/?bmode=view&idx=15009288), [CEO매거진](https://www.ceomagazine.co.kr/ko-kr/articles/33467)
- **분석·댓글**: 없음 (추정)

### 3-2. CapCut(캡컷, 바이트댄스)
- **주력 기능**: 템플릿 편집, 꾸미기에 강한 자동 자막, 자동 화면 맞춤, 긴 영상을 쇼츠로 바꾸기, AI 생성
- **쇼츠 자동 생성**: 있다. 하이라이트를 찾아 세로로 자르고 자막을 단다. 길이는 60초 이하 / 60~90초 / 90초~3분 중에 고른다.
  - 2026-04부터 자동 화면 맞춤과 일부 자동 자막이 Pro 전용이 됐다 — [cut.pro](https://cut.pro/en/blog/capcut-clips-or-clipping-tool-2026), [BIGVU](https://bigvu.tv/blog/capcut-free-vs-pro-what-2026s-restructure-gives-you/)
  - 자세한 품질 평가는 확인 못 함.
- **직접 게시**
  - 틱톡: 휴대폰 앱에서만 된다 — [공식 도움말](https://www.capcut.com/help/how-to-link-tiktok-with-capcut)
  - 예약 게시: 별도 서비스 Pippit이 맡는다(틱톡·페이스북·인스타그램) — [Pippit](https://pippit.capcut.com/resource/help-center/publisher-n-analytics)
- **요금(한국)**: Pro 월 25,000원(연 198,000원). 2025년에 크게 올랐다는 후기가 있다 — [공식 요금](https://www.capcut.com/ko-kr/pricing), [Beompick](https://beompick.com/capcut-price-increase/)
- **한국어 지원**: 화면이 한국어다. 한국어 자막 정확도는 브루보다 낮다는 블로그 시험 결과가 있다.
- **강점**: 앱 평점 약 4.6점으로 편집 경험은 호평이다. 자막 꾸미기가 좋다.
- **불만**: 유료 전환이 핵심 불만이다. 부정 후기 2,881건 중 31%가 유료 장벽 이야기였다. Trustpilot은 1.2점이다. 1080p 내보내기 같은 기본 기능도 유료가 됐다 — [unstar 분석](https://unstar.app/blog/capcut-everything-is-pro-now-cant-export-reviews-2026)

### 3-3. 국내 서비스 (운영이 확인된 것)

| 서비스 | 주력 | 쇼츠 생성 | 직접 게시 | 월 요금 | 분석 / 댓글 | 출처 |
|---|---|---|---|---|---|---|
| **한큐** | 영상 하나를 5개 채널에 동시 게시, 예약 대기열, 채널별 성과 비교 | 없음 | 인스타·유튜브·페이스북·틱톡·스레드 | 무료(월 5회) / 12,900원 / 21,900원 / 39,900원 | 분석 있음 / 댓글 언급 없음 | [hanqupload.com](https://hanqupload.com/) (운영사 표기란이 비어 있음) |
| **포스트림**(보이스루) | 유튜브에 올리면 인스타·틱톡에 자동 재게시, AI 설명문 | 없음 | 유튜브·인스타·틱톡 | 4,500원 / 9,000원 / 18,000원 | 보고서 / 없음 | [postream.io](https://postream.io/) |
| **알파컷**(2026-03 설립) | 유튜브 링크로 쇼츠 여러 편, 첫 3초 후킹, 화자 따라가기 | 있음 | 유튜브·인스타·틱톡·페이스북·스레드(회사 설명) | 원본 길이만큼 차감, 무료 30분 | 채널 분석 / 없음 | [alphacut.video](https://alphacut.video/) |
| **캡투스** | 치지직·SOOP 다시보기의 채팅 반응으로 하이라이트 숏폼 생성 | 있음 | 확인 못 함 | 확인 못 함 | 채팅 반응 분석 | [captus.kr](https://captus.kr/) |
| **블링**(Vling) | 유튜버 검색, 광고 단가, 채널 비교 | 없음 | 없음 | 49,000원(스탠다드) | 경쟁 채널 분석(광고주 쪽) | [노마랩 비교](https://nomalab.kr/blog/youtube-channel-analysis-tools-compared) |
| **뷰트랩** | 영상·채널 찾기, 노출 확률 지표 | 없음 | 없음 | 59,800원~(30일) | 분석만 | 같은 출처 |
| **녹스인플루언서** | 인플루언서 데이터, 캠페인 관리(브랜드용) | 없음 | 없음 | 무료 / $239 / $399 | 분석만 | [G2](https://www.g2.com/products/noxinfluencer/pricing) |

- 국내 서비스의 사용자 후기는 대부분 확인 못 함.
- 알파컷 후기 1건: 정보형 롱폼에서 하이라이트 추출이 안정적이라는 평이다. 다만 고유명사 자막은 점검해야 하고, 결과물을 내 말투로 손봐야 한다고 했다 — [JinBlog](https://jinblog.kr/%EC%95%8C%ED%8C%8C%EC%BB%B7-%EC%82%AC%EC%9A%A9-%ED%9B%84%EA%B8%B0-%EB%A1%B1%ED%8F%BC-1%EA%B0%9C%EB%A1%9C-%EC%87%BC%EC%B8%A0-10%EA%B0%9C-%EB%A7%8C%EB%93%A0-%EB%92%A4-%EC%A1%B0%ED%9A%8C%EC%88%98/)
- **플랫폼이 직접 AI 하이라이트를 시작했다.**
  - 네이버 클립은 2026-08-19에 "AI 하이라이트"를 냈다. 3분 넘는 가로 영상에서 후보를 만들고, 크리에이터가 골라서 직접 등록한다 — [클립팀 공지](https://blog.naver.com/clipteam/224383458810)
  - SOOP은 다시보기 하이라이트 AI "샤크"와 채팅 관리 AI를 내놨다 — [뉴시스](https://www.newsis.com/view/NISX20260316_0003550160)
  - → 무료 기본 기능이 생겼으니, **우리 쇼츠 생성은 "플랫폼 무료 기능보다 나은 이유"를 증명해야 한다.**

---

## 4. 네이버 클립 외부 업로드 — 반드시 확인할 사항

- **공개 API도, 파트너 전용 API도 확인 못 함.** 조사한 경쟁 서비스 16곳 중 네이버 클립 직접 게시를 지원하는 곳은 하나도 없다.
- 공식 업로드 경로는 두 가지뿐이다.
  - 네이버 앱 / 클립 크리에이터 앱: 최대 1분 30초 — [정리 글](https://www.dioramarketing.co.kr/blog/naver-clip-long-video-ai-highlight-2026)
  - 클립 크리에이터 PC 웹: 최대 7시간·8GB, 예약 최대 30일 — [클립팀 공지](https://blog.naver.com/clipteam/224381987952), [PC 업로드 안내](https://www.dioramarketing.co.kr/blog/naver-clip-pc-upload-guide)
- 네이버 클라우드 공개 API 목록에도 클립은 없다 — [ncloud API 안내](https://api.ncloud-docs.com/docs/ko/common-naverapi-naverapi)
- 외부 자동 업로드 사례는 앱 화면을 자동으로 조작하는 우회 방식뿐이다 — [크몽 사례](https://kmong.com/portfolio/view/166251). 약관 위반과 계정 제재 위험이 있다 (추정).
- **조정 세션 확인 요청**: 프로젝트 문서는 핵심 기능으로 "YouTube, TikTok, Instagram Reels, Naver Clip에 한 번에 게시"를 적고 있다. 우리 네이버 클립 연동이 실제로 무엇을 호출하는지 `02-our-capability.md`에서 꼭 확인해 달라. 공개 API가 없는 상태에서 "네이버 클립 동시 게시"를 광고하면 허위 표시 위험이 있다 (추정).
- 현실적인 선택지 (추정)
  - 반자동 방식: 클립 규격에 맞춘 파일, 제목·설명·해시태그를 미리 준비해 두고, PC 웹 업로드 화면으로 바로 이동시킨다.
  - 네이버 제휴 문의
  - 업로드 후 성과를 수동이나 별도 방법으로 가져오는 방안은 확인 못 함.

---

## 5. 한국 크리에이터가 여러 플랫폼을 함께 운영할 때 겪는 불편
근거가 홍보성 글이거나 소수 사례라 강도는 약하다.

1. **같은 영상을 플랫폼마다 따로 올리고 고치는 수고.** 글자 수, 해시태그, 말투를 일일이 맞추다 보니 "만드는 시간보다 뿌리는 시간이 길다"고 한다 — [스레드](https://www.threads.com/@newgugbulon/post/DZpV-1DgFNF)(홍보성)
2. **시간이 모자라 채널이 하나씩 빠진다.** 다섯 채널을 굴리다 두 달 만에 하나만 남았다는 경험담이 있다 — [미라 블로그](https://www.mirra.my/ko/blog/multi-platform-content-strategy-one-idea-5-channels-2026)(업체 블로그)
3. **도용과 중복 판정 위험.** 남이 먼저 틱톡에 퍼 가 올린 탓에 원작자 영상이 중복 경고를 받았고, 그래서 "동시에 올려야 안 뺏긴다"고 했다 — [스레드](https://www.threads.com/@mooyoung_tube/post/DFaMJvTptwE)
4. **플랫폼마다 규격이 다르다.** 클립 모바일은 1분 30초, 쇼츠는 3분이다. 워터마크 있는 영상을 다시 올리면 노출이 제한된다는 경고도 있다 — [필모라](https://filmora.wondershare.kr/video-editing-tips/how-to-create-naver-clip-video.html)
5. **같은 영상인데 성과와 수익 조건이 제각각이다.** 유튜브 300회, 틱톡 5만 회처럼 차이가 난다 — [타입캐스트](https://typecast.ai/kr/learn/%EC%9C%A0%ED%8A%9C%EB%B8%8C%EC%9D%B8%EC%8A%A4%ED%83%80%EA%B7%B8%EB%9E%A8%ED%8B%B1%ED%86%A1-%EA%B0%99%EC%9D%80-%EC%98%81%EC%83%81-%EB%8B%A4%EB%A5%B8-%EC%A1%B0%ED%9A%8C%EC%88%98/)
   - 클립 광고 수익 조건은 팔로워 1,000명과 최근 90일 조회 300만 회다 — [quasa](https://quasa.io/ko/media/neibeo-keulribinya-youtube-syoceunya-gateun-45rado-munteogeun-dareuda)

---

## 6. 이 분야 상위 3위가 공통으로 갖춘 필수 기능

onGo는 "게시·관리"와 "쇼츠 생성" 두 분야에 걸쳐 있어서 분야별로 상위 3곳을 따로 골랐다. **선정 기준은 리뷰 수와 시장 언급량이고, 공식 시장 점유율 자료는 아니다 (추정).**
- 게시·관리 상위 3: **Hootsuite, Sprout Social, Buffer** — G2 리뷰 각 7,476 / 7,430 / 1,041건
- 쇼츠 생성 상위 3: **Opus Clip, Vizard, Klap** — 해외 비교 글에서 거의 항상 이 셋이 함께 비교된다

### 6-1. 게시·관리 상위 3이 모두 갖춘 것
1. **여러 플랫폼 예약 게시 + 캘린더**: 최소 9~12개 플랫폼. 2026년 기본값은 Threads·Bluesky 포함이다.
2. **통합 댓글함**: 여러 플랫폼 댓글을 한곳에서 보고 답한다. Buffer는 2026년에 무료 요금제까지 열었다.
3. **AI 답변 초안·글 작성**: Buffer AI 도우미, Hootsuite Wisdom, Sprout Trellis
4. **AI 성과 요약**: 숫자가 아니라 "그래서 무엇을 하라"를 문장으로 준다. Buffer Takeaways, Trellis 보고서 요약.
5. **최적 게시 시간 추천**
6. **승인 흐름과 팀 권한**
7. **외부 AI 연결(MCP)·공개 API**: 2026년에 Buffer·Hootsuite가 붙였다. Metricool·Publer·Opus·Klap도 있다. **사실상 새 기본값이다.**
8. **연결 끊김·게시 실패 알림**: 갖춘 곳이 많지만 불만이 가장 많은 영역이기도 하다(아래 7장).

### 6-2. 쇼츠 생성 상위 3이 모두 갖춘 것
1. **롱폼 하나에서 클립 여러 개 자동 추출**(1시간에 수십 개)
2. **바이럴 점수와 점수순 정렬**
3. **화자 추적 화면 조정**(9:16 자동 리프레임, 여러 화자 배치)
4. **자동 자막 + 자막 꾸미기**(강조 단어, 이모지)
5. **클립 길이 지정**
6. **결과물에서 바로 예약 게시**(최소 YouTube Shorts·TikTok·Instagram)
7. **플랫폼별 제목·설명·해시태그 생성**(Vizard)
8. **원본 분량(분) 기준 크레딧 과금**: 1크레딧 = 원본 1분이 업계 표준이다.

> 정리하면, 2026년 "통합 도구"로 인정받으려면 **예약 게시 + 통합 댓글함 + AI 답변 + AI 성과 요약 + 최적 시간**이 기본이고, 쇼츠 쪽은 **자동 추출 + 점수 + 리프레임 + 자막 + 바로 게시**가 기본이다. 이 둘을 **한 제품에 다 갖춘 곳은 조사 대상 중 없다**(Opus는 게시까지만, Hootsuite·Sprout은 클립이 없음).

---

## 7. 아무도 잘 못하는 빈틈 (한국 크리에이터 관점)

| # | 빈틈 | 근거 | onGo가 노릴 점 |
|---|---|---|---|
| 1 | **롱폼→쇼츠 생성과 통합 관리가 한 제품에 없다** | 게시 도구 7곳은 모두 쇼츠 생성이 없다. Hootsuite는 "클리핑은 다른 도구로"라고 공식적으로 밝혔다. 쇼츠 도구 4곳은 모두 댓글 관리가 없고 분석도 약하다. | 생성 → 게시 → 성과 → 댓글을 한 흐름으로. 지금은 사용자가 Opus($29)와 Buffer·Hootsuite를 따로 결제한다. |
| 2 | **한국어 쇼츠 품질** | Opus 한국어는 베타다. 자막 타이밍이 어긋나고 "한국 정서와 어긋난" 강조, 글꼴 1개. Descript는 한국어 인식이 아예 안 된다. Klap이 그나마 낫지만 점수는 "참고 수준"이다. Vrew는 자막은 최상인데 게시가 없다. | 한국어 고유명사·말끝 처리, 한국형 강조 자막과 글꼴, 한국 시청 습관에 맞춘 하이라이트 기준. **"한국어로는 우리가 1등"을 시험으로 증명할 수 있어야 한다.** |
| 3 | **네이버 클립** | 16곳 모두 지원하지 않는다. 공개 API도 없다. | 공식 API가 없으니 "완전 자동"은 불가하다 (추정). **클립 규격 맞춤 + 메타데이터 준비 + PC 웹 업로드로 바로 이동**하는 반자동이 현실적인 차별점이다. 정직하게 표시해야 한다(4장). |
| 4 | **한국어 화면·원화 결제·국내 요금** | 해외 게시 도구 7곳 모두 화면에 한국어가 없고 달러 결제만 받는다. 기업용은 사용자당 $79~399다. | 원화 9,900~49,900원은 Hootsuite·Sprout 최저가($79~99)의 1/10 수준이다 (추정 환산). 다만 국내 한큐(12,900~39,900원)·포스트림(4,500~18,000원)과는 가격이 겹치니 **기능 묶음으로 이겨야 한다.** |
| 5 | **청구·해지·크레딧 신뢰** | Hootsuite·Later·Sprout·CapCut의 Trustpilot 1~2점대는 대부분 자동 갱신, 해지 후 과금, 환불 거부다. Opus·Vizard·Descript는 크레딧 만료와 소멸 불만이 반복된다. | 갱신 전 알림, 한 번에 해지, 남은 크레딧 처리 규칙과 환불 기준의 명확한 공개. 비용이 거의 안 드는데 경쟁사 대부분이 실패한 영역이다. |
| 6 | **"조용한 실패"** | Repurpose.io "you won't even know", Metricool "게시 완료로 표시됐는데 안 올라감", Opus 예약의 TikTok 조용한 실패, Buffer 연결 끊김. | 플랫폼별 실제 게시 확인, 실패 즉시 알림, 재시도, 토큰 만료 사전 경고. **"게시됐다고 표시했으면 정말 게시된 것"이라는 신뢰.** 가짜 성공 표시를 금지한 우리 원칙과 맞는다. |
| 7 | **국내 플랫폼 성과의 통합 비교** | 해외 도구는 네이버 클립·치지직·SOOP을 모른다. 국내 분석 도구(블링·뷰트랩·플레이보드)는 유튜브만 본다. 크리에이터는 "같은 영상인데 플랫폼별 성과가 제각각"이라고 한다. | 같은 영상의 플랫폼별 성과 나란히 비교, 수익 조건 진행률(예: 클립 90일 300만 회) 표시. 단, 데이터를 가져올 수 없는 플랫폼은 "측정 불가"로 정직하게 표시한다. |
| 8 | **AI 성과 해석이 한국어로 안 된다** | 2026년 상위 도구는 AI 요약(Takeaways, Trellis, Wisdom)으로 경쟁하는데, 모두 한국어 화면이 없다. | 한국어 "이번 주 무엇을 할까" 요약. 데이터가 부족하면 결론을 내지 않는다. |
| 9 | **도용·중복 대응** | "남이 먼저 퍼 가 올려 내 영상이 중복 판정"(5장 3번) | 동시 게시 기록을 원작 증빙 시각으로 남기는 기능 (추정, 수요 근거 약함). |

### 우선순위 제안 (조정 세션 판단용, 추정)
- **1순위: 빈틈 1(생성→게시→성과→댓글 한 흐름) + 빈틈 2(한국어 쇼츠 품질).** 상위 3곳 어디에도 없고, onGo 기능 구성이 이미 이 방향이다.
- **2순위: 빈틈 6(조용한 실패 없음) + 빈틈 5(청구 신뢰).** 비용이 적고, 경쟁사 낮은 평점의 주원인이라 효과가 크다.
- **3순위: 빈틈 3(네이버 클립 반자동).** 그 전에 우리 연동의 실제 동작부터 확인해야 한다(4장).
- **따라잡아야 할 기본기**: 6장 필수 기능 중 우리에게 없거나 약한 것. 특히 AI 성과 요약, 바이럴 점수, 화자 추적 리프레임, 실패 알림, MCP·API. 해당 여부는 `02-our-capability.md`와 대조해 정한다.

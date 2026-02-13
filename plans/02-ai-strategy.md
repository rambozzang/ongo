# onGo AI 기능 차별화 전략 기획서

> 기획자 2 - AI 기능 차별화 전략가
> 작성일: 2026-02-09
> 버전: v1.0

---

## 1. 전략 개요

### 1.1 현황 분석

**Publer AI (경쟁사)**
- AI 캡션 작성 (품질 양호)
- AI 이미지 생성
- 해시태그 추천
- 콘텐츠 자동 재활용
- 가격: $12/month, AI Rating 4/5
- **약점**: 영상 분석 없음, 플랫폼별 차별화 없음, 분석 기능 빈약, 한국 시장 무관심

**onGo 현재 구현 완료된 AI 기능**
| 기능 | 크레딧 | 상태 |
|------|--------|------|
| AI 메타데이터 생성 (플랫폼별 제목/설명/해시태그) | 5 | 구현 완료 |
| AI 해시태그 생성 (플랫폼별 분리) | 3 | 구현 완료 |
| STT 음성 변환 (Whisper) | 10 | 구현 완료 |
| AI 대본 분석 (키워드/타겟/카테고리) | 5 | 구현 완료 |
| AI 댓글 답변 생성 (톤별 3종) | 2 | 구현 완료 |
| AI 업로드 시간 추천 | 3 | 구현 완료 |
| AI 콘텐츠 아이디어 제안 | 5 | 구현 완료 |
| AI 성과 리포트 (마크다운) | 8 | 구현 완료 |

### 1.2 차별화 핵심 방향

Publer가 **텍스트 기반 AI**에 머무는 반면, onGo는 **영상 자체를 이해하는 AI**로 포지셔닝한다.

핵심 슬로건: **"영상을 올리면 AI가 알아서 다 해준다"**

차별점 3축:
1. **영상 이해 AI** -- 프레임/음성/자막을 직접 분석 (Publer 불가)
2. **플랫폼 알고리즘 전문가** -- YouTube/TikTok/Instagram/Naver Clip 각각 최적화 (Publer 미지원)
3. **데이터 기반 예측** -- 업로드 전 성과 예측 + 사후 분석 통합 (Publer 약점)

---

## 2. AI 기능 상세 기획

---

### 2.1 AI 영상 분석 엔진

**우선순위: P0 (핵심 차별화)**
**예상 개발 기간: 6~8주**
**크레딧 소모: 15크레딧**

#### 2.1.1 기능 정의

업로드된 영상을 자동 분석하여 다음 결과물을 생성한다:
- 영상 전체 요약 (1~3줄)
- 핵심 키워드 추출 (영상 내용 기반)
- 최적 썸네일 후보 타임스탬프 (시각적 임팩트가 높은 프레임 3~5개)
- 하이라이트 구간 추출 (TikTok/Reels용 쇼츠 편집 포인트)
- 분위기/톤 분석 (밝은/진지한/유머/감동 등)
- 자동 챕터 분할 포인트 (YouTube 챕터용)

#### 2.1.2 구현 방안

```
[영상 업로드]
    |
    v
[FFmpeg 전처리] -----> 오디오 추출 (.wav)
    |                      |
    v                      v
[프레임 샘플링]       [Whisper STT]
 (2fps 추출)          (타임스탬프 포함)
    |                      |
    v                      v
[Claude Vision API]   [텍스트 분석]
 (키 프레임 분석)      (대본 구조화)
    |                      |
    +----------+-----------+
               |
               v
    [통합 분석 파이프라인]
        - 씬 전환 감지
        - 감정/톤 분석
        - 하이라이트 점수 산출
        - 썸네일 후보 랭킹
        - 챕터 포인트 결정
               |
               v
    [VideoAnalysisResult 반환]
```

**파이프라인 상세:**

1. **프레임 샘플링 (FFmpeg)**
   - 2fps로 전체 영상 프레임 추출 (10분 영상 = 1,200장)
   - 씬 변화 감지 알고리즘으로 대표 프레임 50~100장 선별
   - 선별 기준: 색상 히스토그램 변화량 > 임계치 (씬 전환점)
   - 추가 필터: 블러/흔들림 프레임 제거 (Laplacian variance 활용)

2. **멀티모달 분석 (Claude Vision)**
   - 대표 프레임을 Claude sonnet-4 Vision API에 일괄 전송
   - 프레임별 분석 항목: 객체 인식, 텍스트 OCR, 표정/감정, 시각적 임팩트 점수
   - 배치 처리: 프레임 20장 단위로 묶어 API 호출 최소화

3. **음성-비전 통합**
   - STT 타임스탬프와 프레임 타임스탬프를 매핑
   - 음성의 감정 변화 + 시각적 변화가 동시에 일어나는 지점 = 하이라이트
   - 음성 에너지(dB 변화)와 프레임 변화율의 합산 점수로 랭킹

4. **챕터 자동 분할**
   - STT 텍스트를 Claude에 전달하여 주제 전환점 감지
   - 각 챕터에 자동 제목 부여
   - YouTube Description용 챕터 마커 형식 출력 (00:00 인트로, 01:23 본론 ...)

#### 2.1.3 기술 스택

| 구성요소 | 기술 | 역할 |
|---------|------|------|
| 프레임 추출 | FFmpeg (ProcessBuilder로 호출) | 영상에서 프레임 이미지 추출 |
| 씬 감지 | FFmpeg scene filter + OpenCV (optional) | 대표 프레임 선별 |
| 영상 분석 | Claude claude-sonnet-4-20250514 Vision API | 프레임 시각 분석 |
| 음성 분석 | OpenAI Whisper API (기존 STT 확장) | 타임스탬프별 음성 텍스트 |
| 텍스트 분석 | Claude claude-sonnet-4-20250514 (Spring AI) | 대본 구조/키워드/요약 |
| 비동기 처리 | Spring Events + Virtual Threads | 영상 분석은 비동기 실행 |
| 캐싱 | Caffeine Cache (분석 결과 30일 보관) | 재분석 방지 |
| 저장 | PostgreSQL JSONB | 분석 결과 구조화 저장 |

#### 2.1.4 DB 스키마 확장

```sql
CREATE TABLE video_analysis (
    id              BIGSERIAL PRIMARY KEY,
    video_id        BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE UNIQUE,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- 'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'
    summary         TEXT,
    keywords        JSONB,          -- ["키워드1", "키워드2", ...]
    mood            VARCHAR(50),    -- 'bright', 'serious', 'humor', 'emotional'
    thumbnail_candidates JSONB,     -- [{timestamp, score, description}, ...]
    highlights      JSONB,          -- [{startTime, endTime, score, reason}, ...]
    chapters        JSONB,          -- [{timestamp, title}, ...]
    scene_count     INTEGER,
    raw_analysis    JSONB,          -- 전체 분석 원본 데이터
    credits_used    INTEGER NOT NULL DEFAULT 15,
    processing_time_ms BIGINT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_video_analysis_video ON video_analysis(video_id);
CREATE INDEX idx_video_analysis_status ON video_analysis(status);
```

#### 2.1.5 API 설계

```
POST /api/v1/ai/analyze-video
Request:  { videoId: Long }
Response: { analysisId: Long, status: "PROCESSING" }  -- 비동기 시작

GET /api/v1/ai/analyze-video/{analysisId}
Response: {
    status: "COMPLETED",
    summary: "...",
    keywords: [...],
    mood: "bright",
    thumbnailCandidates: [
        { timestamp: 45.2, score: 0.95, description: "호스트가 제품을 들고 밝게 웃는 장면" },
        ...
    ],
    highlights: [
        { startTime: 120.0, endTime: 135.5, score: 0.92, reason: "핵심 메시지 전달 + 시각적 임팩트" },
        ...
    ],
    chapters: [
        { timestamp: 0, title: "인트로" },
        { timestamp: 83, title: "제품 소개" },
        ...
    ]
}
```

#### 2.1.6 예상 효과

- **Publer 대비 완전한 차별화**: Publer는 텍스트만 다루지만, onGo는 영상 자체를 이해
- 영상 업로드 시 메타데이터 작성 시간 **80% 단축** (수동 15분 -> AI 자동 3분)
- 썸네일 선정 고민 해소, 하이라이트 추출로 쇼츠 제작 편의성 증가
- 챕터 자동 생성으로 YouTube SEO 향상 (챕터 있는 영상의 CTR +15~20%)

---

### 2.2 플랫폼별 AI 최적화

**우선순위: P0 (핵심 차별화 -- 이미 부분 구현)**
**예상 개발 기간: 3~4주 (기존 기능 고도화)**
**크레딧 소모: 기존 메타데이터 생성 5크레딧 내 통합**

#### 2.2.1 기능 정의

현재 `GenerateMetaUseCase`에서 플랫폼별 제목/설명/해시태그를 생성하고 있으나, 각 플랫폼 알고리즘의 구체적 특성을 깊이 반영하도록 고도화한다.

**플랫폼별 최적화 매트릭스:**

| 항목 | YouTube | TikTok | Instagram Reels | Naver Clip |
|------|---------|--------|-----------------|------------|
| 제목 전략 | SEO 키워드 + 호기심 유발 (CTR 최적화) | 트렌디 + 이모지 + 짧은 임팩트 | 스토리텔링 + 감성 | 네이버 검색 키워드 중심 |
| 설명 전략 | 키워드 밀도 2~3%, 링크/CTA 포함, 3단 구조 | 해시태그 위주, 1~2줄 | 캡션 + 이모지 + CTA | 한국어 롱테일 키워드 |
| 해시태그 | 5~15개 (검색 최적화) | 3~5개 (트렌드 + FYP 태그) | 20~30개 (커뮤니티 태그 포함) | 5~10개 (네이버 검색어) |
| 썸네일 | 고대비, 텍스트 오버레이, 표정 클로즈업 | N/A (자동 선택) | 영상 첫 프레임 중요 | 대표 이미지 |
| 영상 길이 힌트 | 8~12분 (광고 수익 최적) | 15~60초 (완시청률) | 15~30초 (알고리즘 선호) | 1~3분 |

#### 2.2.2 구현 방안 -- 프롬프트 엔지니어링 고도화

현재 `PromptTemplates.META_GENERATION_SYSTEM`을 플랫폼별 전문 프롬프트로 분리한다.

```kotlin
object PlatformPromptStrategies {

    val YOUTUBE_STRATEGY = """
        YouTube 알고리즘 최적화 전략:
        1. 제목: 첫 40자 안에 핵심 키워드 배치 (모바일 미리보기 대응)
        2. 제목 공식: [숫자/결과] + [방법/비밀] + [감정 트리거]
           예시: "구독자 1만 달성한 진짜 비법 3가지 (아무도 안 알려줌)"
        3. 설명문 구조:
           - 1줄: 영상 핵심 내용 (SEO 키워드 포함)
           - 2~3줄: 시청자 행동 유도 (구독/좋아요/알림)
           - 4줄~: 타임스탬프(챕터), 관련 링크, SNS 링크
        4. 태그: 핵심 키워드 3개 + 롱테일 5개 + 채널 브랜드 2개
        5. 카테고리 자동 매핑 (YouTube IAB 카테고리)
    """.trimIndent()

    val TIKTOK_STRATEGY = """
        TikTok 알고리즘 최적화 전략:
        1. 캡션: 150자 이내, 첫 문장에 후킹 포인트
        2. 해시태그: #fyp #foryou 필수 + 트렌드 태그 1~2개 + 니치 태그 1~2개
        3. 사운드: 트렌딩 사운드 연계 가능한 키워드 제안
        4. 스티치/듀엣 유도 문구 포함
        5. 완시청률 극대화를 위한 "루프 엔딩" 제안
        6. 한국 TikTok 트렌드 반영 (챌린지, 밈 등)
    """.trimIndent()

    val INSTAGRAM_STRATEGY = """
        Instagram Reels 알고리즘 최적화 전략:
        1. 캡션 구조: 훅(1줄) + 본문(가치 전달) + CTA(팔로우/저장 유도)
        2. 해시태그 전략: 인기(30%)+중간(40%)+니치(30%) 혼합, 최대 30개
        3. 위치 태그 활용 제안 (로컬 노출 부스트)
        4. 카루셀 vs Reels 추천 (콘텐츠 유형별)
        5. 스토리 연계 문구 제안
        6. 한국 인스타그램 인기 해시태그 반영
    """.trimIndent()

    val NAVER_CLIP_STRATEGY = """
        네이버 클립 최적화 전략:
        1. 제목: 네이버 검색 키워드 중심 (네이버 자동완성 활용)
        2. 설명: 블로그 포스팅 연계 키워드 삽입
        3. 태그: 네이버 인기 검색어 + 카테고리 키워드
        4. 한국어 검색 패턴 반영 (조사 포함 키워드)
        5. 네이버 쇼핑/지식iN 연계 키워드 포함
        6. 시즌/이벤트 키워드 반영 (명절, 계절, 기념일)
    """.trimIndent()
}
```

#### 2.2.3 A/B 테스트 메타데이터

```kotlin
data class PlatformMetaVariant(
    val platform: String,
    val variant: String,        // "A" 또는 "B"
    val title: String,
    val description: String,
    val hashtags: List<String>,
    val predictedCtr: Double,   // AI 예측 CTR
    val reasoning: String,      // 왜 이 변형이 효과적인지
)
```

각 플랫폼당 **A/B 2세트**의 메타데이터를 생성하여, 크리에이터가 선택하거나 A/B 테스트할 수 있도록 한다.

#### 2.2.4 예상 효과

- **Publer와 가장 직접적인 차별점**: Publer는 모든 플랫폼에 동일 캡션을 생성하지만, onGo는 플랫폼별 알고리즘 특성을 반영
- 플랫폼별 CTR 예상 향상: YouTube +15%, TikTok +20%, Instagram +12%, Naver Clip +25%
- 크리에이터 작업 시간: 4개 플랫폼 메타데이터 작성 60분 -> 5분

---

### 2.3 AI 성과 예측

**우선순위: P1**
**예상 개발 기간: 5~6주**
**크레딧 소모: 8크레딧**

#### 2.3.1 기능 정의

영상을 업로드하기 **전에** 예상 성과를 예측하여, 크리에이터가 메타데이터를 수정하거나 업로드 타이밍을 조정할 수 있게 한다.

예측 항목:
- 예상 조회수 범위 (하한 ~ 상한, 신뢰구간 포함)
- 예상 좋아요/댓글 비율
- 예상 구독 전환율
- 플랫폼별 성과 예측 비교 차트
- "이렇게 수정하면 성과가 N% 올라갑니다" 개선 제안

#### 2.3.2 구현 방안

```
[입력 데이터]
    |
    +-- 영상 분석 결과 (2.1에서 추출)
    +-- 메타데이터 (제목, 설명, 태그)
    +-- 채널 히스토리 (최근 30개 영상 성과)
    +-- 업로드 예정 시간
    +-- 카테고리
    |
    v
[예측 모델 파이프라인]
    |
    +-- Step 1: 채널 기본 성과 벤치마크 계산
    |   - 최근 30개 영상 평균/중앙값/표준편차
    |   - 성장 추세 (상승/정체/하락)
    |
    +-- Step 2: 콘텐츠 품질 점수 산출
    |   - 제목 CTR 예측 (Claude 분석)
    |   - 썸네일 임팩트 점수 (Vision API)
    |   - 대본 품질 점수 (키워드 밀도, 구조, 훅)
    |   - 트렌드 부합도 (현재 검색 트렌드 매칭)
    |
    +-- Step 3: 외부 요인 보정
    |   - 업로드 시간대 보정 계수
    |   - 경쟁 콘텐츠 밀도 보정
    |   - 시즌/이벤트 보정 (명절, 시험기간 등)
    |
    +-- Step 4: 최종 예측 산출
        - 가중 평균 모델
        - 신뢰구간 (68%, 95%)
        - 개선 제안 생성
```

**Phase 1 (Claude 기반 휴리스틱):**

초기에는 ML 모델 없이 Claude의 추론 능력과 채널 히스토리 데이터를 활용한다.

```kotlin
@Service
class PredictPerformanceUseCase(
    @Qualifier("anthropicChatClient") private val chatClient: ChatClient,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val analyticsRepository: AnalyticsRepository,
    private val channelRepository: ChannelRepository,
) {
    fun execute(userId: Long, videoId: Long, metadata: MetadataInput): PerformancePredictionResult {
        // 1. 채널 히스토리 수집
        val recentPerformance = analyticsRepository.getRecentVideoPerformance(userId, limit = 30)
        val channelAvg = calculateBenchmarks(recentPerformance)

        // 2. Claude에 예측 요청
        val prompt = buildPredictionPrompt(metadata, channelAvg, recentPerformance)

        return chatClient.prompt()
            .system(PromptTemplates.PERFORMANCE_PREDICTION_SYSTEM)
            .user(prompt)
            .call()
            .entity(PerformancePredictionResult::class.java)!!
    }
}
```

**Phase 2 (ML 모델 도입, 데이터 축적 후):**

6개월 데이터 축적 후 예측-실제 성과 비교 데이터로 경량 ML 모델 학습.
- Feature: 제목 길이, 키워드 매치율, 업로드 시간, 채널 평균 성과, 트렌드 점수
- Model: Gradient Boosting (LightGBM) 또는 간단한 회귀 모델
- 서빙: ONNX Runtime (JVM 내 추론) 또는 Python 마이크로서비스

#### 2.3.3 응답 구조

```kotlin
data class PerformancePredictionResult(
    val overallScore: Int,          // 0~100 성과 예측 점수
    val viewsPrediction: RangePrediction,
    val likesPrediction: RangePrediction,
    val commentsPrediction: RangePrediction,
    val platformComparison: List<PlatformPrediction>,
    val improvements: List<ImprovementSuggestion>,
    val confidenceLevel: String,    // "HIGH", "MEDIUM", "LOW"
) {
    data class RangePrediction(
        val low: Long,
        val expected: Long,
        val high: Long,
        val confidence: Double,     // 0.0 ~ 1.0
    )

    data class PlatformPrediction(
        val platform: String,
        val expectedViews: Long,
        val relativeStrength: String,  // "이 영상은 TikTok에서 가장 높은 성과가 예상됩니다"
    )

    data class ImprovementSuggestion(
        val area: String,           // "제목", "썸네일", "설명", "업로드시간"
        val currentScore: Int,
        val suggestion: String,
        val expectedImprovement: String,  // "+15% 조회수 예상"
    )
}
```

#### 2.3.4 기술 스택

| 구성요소 | 기술 | 비고 |
|---------|------|------|
| Phase 1 예측 | Claude API (Spring AI) | 채널 데이터 + 프롬프트 기반 |
| 히스토리 집계 | jOOQ + analytics_daily 테이블 | 기존 인프라 활용 |
| Phase 2 ML | LightGBM + ONNX Runtime | 6개월 후 도입 |
| 피드백 루프 | Spring Scheduler | 예측 vs 실제 비교 자동 기록 |

#### 2.3.5 예상 효과

- **Publer 완전 미지원 기능**: 경쟁사에 전혀 없는 기능으로 강력한 차별화
- 크리에이터의 "감"에 의존하던 업로드 의사결정을 데이터 기반으로 전환
- 메타데이터 수정 -> 재예측 반복으로 최적 메타데이터 도출 가능
- 예상 전환율: 무료 사용자 -> 유료 전환 시 킬러 기능으로 작용

---

### 2.4 AI 최적 게시 시간 추천

**우선순위: P0 (기존 구현 고도화)**
**예상 개발 기간: 2~3주 (고도화)**
**크레딧 소모: 기존 3크레딧 유지**

#### 2.4.1 현황 및 고도화 방향

현재 `SuggestScheduleUseCase`가 구현되어 있으나, heatmap 데이터와 Claude 프롬프트 기반의 간단한 추천에 그친다. 이를 고도화한다.

#### 2.4.2 고도화 항목

**A. 실제 팔로워 활동 패턴 분석**
```kotlin
data class AudienceActivityPattern(
    val hourlyEngagement: Map<Int, Double>,    // 0~23시별 참여율
    val dayOfWeekPattern: Map<String, Double>, // 요일별 참여율
    val peakWindows: List<TimeWindow>,          // 최적 시간대 슬롯
    val avoidWindows: List<TimeWindow>,         // 회피 시간대 (경쟁 과다)
    val seasonalAdjustment: Double,             // 계절 보정 계수
)
```

**B. 경쟁 콘텐츠 밀도 분석**
- 같은 카테고리 인기 채널의 업로드 패턴 수집
- 경쟁이 적은 "블루오션 타임슬롯" 발견
- 예: "뷰티 카테고리는 화요일 오후 7시에 경쟁이 40% 적습니다"

**C. 카테고리 x 플랫폼 매트릭스**

각 플랫폼의 알고리즘 특성을 반영한 시간대 추천:
- YouTube: 검색 기반이므로 시간대 영향 상대적으로 적음. 그래도 첫 1시간 성과가 중요
- TikTok: FYP 알고리즘이므로 시간보다 완시청률이 중요하나, 초기 부스트 시간은 존재
- Instagram: 팔로워 활동 시간과 직결. 저녁 7~10시가 골든타임
- Naver Clip: 네이버 메인 노출 주기와 연동. 오전 9시, 오후 2시, 저녁 8시 체크포인트

**D. 개인화 학습**
- 해당 크리에이터의 과거 업로드 시간 vs 성과 상관관계 분석
- 최소 10개 영상 데이터 축적 시 개인화 추천 활성화
- "당신의 영상은 수요일 오후 8시에 평균 대비 37% 높은 조회수를 기록합니다"

#### 2.4.3 UI 개선

```
+-----------------------------------------------+
|  최적 업로드 시간 추천                           |
+-----------------------------------------------+
|                                                 |
|  [히트맵 시각화: 요일 x 시간대]                  |
|                                                 |
|  월  ██░░░░████░░░░░████████░░░░                |
|  화  ░░░░░░████░░░░░██████████░░                |
|  수  ██░░░░████░░░░░████████████                |
|  목  ░░░░░░████░░░░░████████░░░░                |
|  금  ██░░░░████░░░░░██████████░░                |
|  토  ██████████░░░░░████████████                |
|  일  ██████████░░░░░████████████                |
|      06  09  12  15  18  21  24                  |
|                                                 |
|  TOP 3 추천 시간:                                |
|  1. 수요일 20:00 (+37% 예상) ← 개인 최적       |
|  2. 토요일 14:00 (+28% 예상) ← 카테고리 트렌드  |
|  3. 금요일 19:00 (+22% 예상) ← 경쟁 블루오션    |
|                                                 |
|  [이 시간에 예약하기]  [다른 시간대 보기]         |
+-----------------------------------------------+
```

#### 2.4.4 예상 효과

- 기존 단순 추천에서 **데이터 기반 개인화 추천**으로 진화
- 적절한 게시 시간 선택으로 초기 24시간 성과 **20~35% 향상** 기대
- "최적 시간에 예약" 원클릭으로 게시 예약 연동 (UX 개선)

---

### 2.5 AI 썸네일 생성/추천

**우선순위: P1**
**예상 개발 기간: 5~6주**
**크레딧 소모: 추천 5크레딧 / 생성 10크레딧**

#### 2.5.1 기능 정의

두 가지 모드를 제공한다:

**모드 A: 최적 프레임 추출 (5크레딧)**
- 2.1 영상 분석 엔진의 결과를 활용
- 시각적 임팩트가 높은 프레임 5개 추천
- 각 프레임에 점수와 선정 이유 표시
- 프레임 선택 시 자동 크롭/리사이즈 (각 플랫폼 비율에 맞게)

**모드 B: AI 썸네일 생성 (10크레딧)**
- 추출된 프레임 + 영상 제목을 기반으로 완성형 썸네일 생성
- 텍스트 오버레이, 강조 효과, 배경 처리 포함
- 각 플랫폼 규격에 맞는 다중 사이즈 출력

#### 2.5.2 구현 방안

**모드 A 파이프라인:**
```
[영상 분석 결과에서 thumbnail_candidates 활용]
    |
    v
[Claude Vision으로 각 프레임 재평가]
    - 얼굴 표정 분석 (밝은 표정 우선)
    - 구도 평가 (1/3 법칙, 시선 방향)
    - 색상 대비도 (썸네일에서의 가독성)
    - 텍스트 배치 가능 영역 식별
    |
    v
[랭킹 결과 + 크롭 가이드 반환]
```

**모드 B 파이프라인:**
```
[선택된 프레임 + 제목 + 브랜딩 가이드]
    |
    v
[이미지 생성 AI]
    - 프레임 배경 확장/블러 (outpainting)
    - 텍스트 오버레이 자동 배치
    - 브랜드 컬러/로고 삽입
    - 3가지 스타일 변형 생성
    |
    v
[플랫폼별 리사이즈]
    - YouTube: 1280x720
    - Instagram: 1080x1080 / 1080x1920
    - Naver Clip: 1080x1920
```

#### 2.5.3 기술 스택

| 구성요소 | 기술 | 비고 |
|---------|------|------|
| 프레임 분석 | Claude Vision API | 이미 2.1에서 활용 |
| 이미지 생성 | Stability AI (SDXL) 또는 DALL-E 3 | 썸네일 생성 전용 |
| 텍스트 렌더링 | Java 2D Graphics / Thumbnailator | 텍스트 오버레이 |
| 이미지 처리 | imgscalr + Thumbnailator | 리사이즈/크롭 |
| 저장 | MinIO/S3 | 생성된 썸네일 저장 |

#### 2.5.4 썸네일 스타일 프리셋

```kotlin
enum class ThumbnailStyle(val description: String) {
    BOLD_TEXT("큰 텍스트 + 강렬한 색상 대비"),
    CLEAN_MINIMAL("깔끔한 미니멀 디자인"),
    EMOTIONAL("감정 표현 중심 + 부드러운 톤"),
    CLICKBAIT("호기심 유발 + 화살표/동그라미 강조"),
    PROFESSIONAL("전문가 느낌 + 깔끔한 레이아웃"),
}
```

크리에이터가 자신의 채널 톤에 맞는 스타일을 선택하면, 이후 해당 스타일이 기본값으로 적용된다.

#### 2.5.5 예상 효과

- 썸네일 제작 시간 **90% 단축** (외부 디자인 도구 불필요)
- 전문 디자이너 없이도 고품질 썸네일 생성 가능
- CTR 최적화된 썸네일로 조회수 **10~25% 향상** 기대
- Publer는 일반 이미지 생성만 가능, 영상 기반 썸네일은 onGo만의 기능

---

### 2.6 AI 크로스플랫폼 리포트

**우선순위: P0 (Publer 최대 약점 공략)**
**예상 개발 기간: 4~5주 (기존 리포트 확장)**
**크레딧 소모: 12크레딧**

#### 2.6.1 기능 정의

현재 `GenerateReportUseCase`가 단일 채널 성과 리포트를 생성하지만, 이를 **전체 플랫폼 통합 인사이트**로 확장한다.

Publer 리뷰에서 가장 많이 지적되는 약점: *"분석 기능이 빈약하다. 게시만 하고 결과를 모른다."*
이 약점을 정확히 공략한다.

#### 2.6.2 리포트 구성

```
+================================================================+
|                    onGo AI 주간 리포트                            |
|                    2026.02.03 ~ 2026.02.09                       |
+================================================================+

[1] 종합 대시보드
+----------------------------------------------------------+
| 전체 도달     | 125,340 (+23%)  |  ████████████████████   |
| 전체 좋아요   | 8,920 (+15%)    |  ██████████████         |
| 전체 댓글     | 1,230 (+8%)     |  ████████               |
| 구독자 변화   | +340            |  ██████████████████     |
+----------------------------------------------------------+

[2] 플랫폼별 성과 비교
+----------------------------------------------------------+
|          | YouTube | TikTok | Instagram | Naver Clip     |
|----------|---------|--------|-----------|----------------|
| 조회수   | 45,230  | 52,100 | 18,340    | 9,670          |
| 참여율   | 4.2%    | 8.7%   | 5.1%      | 3.8%           |
| 최고영상 | "제목A" | "제목B"| "제목C"   | "제목D"        |
| 추세     |   상승  |  급상승 |  정체     |  상승          |
+----------------------------------------------------------+

[3] AI 인사이트
- "TikTok에서의 성과가 YouTube를 추월하고 있습니다.
   숏폼 콘텐츠 비중을 현재 30%에서 45%로 높이는 것을 추천합니다."
- "수요일 오후 8시 업로드 영상이 일관되게 높은 성과를 보입니다.
   이 시간대를 고정 업로드 슬롯으로 활용하세요."
- "뷰티 카테고리에서 '겨울 스킨케어' 키워드의 검색량이 급증 중입니다.
   관련 콘텐츠 제작을 추천합니다."

[4] 콘텐츠 성과 랭킹
1. "제목B" (TikTok) - 조회수 32,100, 참여율 12.3%
   성공 요인: 트렌드 사운드 활용, 15초 완시청률 87%
2. ...

[5] 다음 주 액션 플랜
- [ ] 월요일: TikTok 쇼츠 2개 업로드 (겨울 스킨케어 키워드)
- [ ] 수요일 20:00: YouTube 메인 영상 업로드
- [ ] 금요일: Instagram Reels 3개 (기존 YouTube 영상 리패키징)
- [ ] 토요일: Naver Clip 2개 (네이버 검색 트렌드 반영)
```

#### 2.6.3 구현 방안

```kotlin
@Service
class CrossPlatformReportUseCase(
    @Qualifier("anthropicChatClient") private val chatClient: ChatClient,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val analyticsRepository: AnalyticsRepository,
    private val channelRepository: ChannelRepository,
) {
    fun execute(userId: Long, days: Int): CrossPlatformReportResult {
        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.CROSS_PLATFORM_REPORT)

        // 1. 모든 연결된 채널의 분석 데이터 수집
        val channels = channelRepository.findByUserId(userId)
        val platformData = channels.map { channel ->
            val kpi = analyticsRepository.getDashboardKpi(userId, days, channel.id)
            val topVideos = analyticsRepository.getTopVideos(userId, days, 5, channel.id)
            PlatformAnalyticsData(channel.platform, kpi, topVideos)
        }

        // 2. 크로스플랫폼 비교 데이터 구성
        val comparisonData = buildComparisonData(platformData)

        // 3. Claude에 통합 인사이트 생성 요청
        val prompt = buildCrossPlatformPrompt(comparisonData, platformData)

        return chatClient.prompt()
            .system(PromptTemplates.CROSS_PLATFORM_REPORT_SYSTEM)
            .user(prompt)
            .call()
            .entity(CrossPlatformReportResult::class.java)!!
    }
}
```

#### 2.6.4 리포트 유형

| 리포트 유형 | 주기 | 크레딧 | 내용 |
|------------|------|--------|------|
| 주간 요약 | 매주 | 12 | 종합 성과 + 인사이트 + 다음 주 액션 |
| 월간 심화 | 매월 | 20 | 트렌드 분석 + 성장 곡선 + 경쟁 비교 + 전략 제안 |
| 영상별 포스트모템 | 업로드 후 72시간 | 5 | 개별 영상 성과 분석 + 학습 포인트 |
| 자동 알림 | 실시간 | 0 | 이상 성과 감지 시 자동 알림 (바이럴 감지 등) |

#### 2.6.5 예상 효과

- **Publer 최대 약점 직접 공략**: "게시만 하고 결과를 모른다"는 불만 해소
- 크리에이터의 데이터 리터러시 향상 -> 전략적 콘텐츠 기획 가능
- 자동 액션 플랜으로 "다음에 뭘 만들지" 고민 해소
- Pro/Business 플랜의 핵심 부가가치 기능

---

### 2.7 AI 콘텐츠 캘린더 자동 생성

**우선순위: P1**
**예상 개발 기간: 4~5주**
**크레딧 소모: 10크레딧**

#### 2.7.1 기능 정의

크리에이터의 채널 데이터, 트렌드, 최적 게시 시간을 종합하여 **향후 2주~1개월 콘텐츠 캘린더**를 자동 생성한다.

```
+================================================================+
|          2026년 2월 AI 추천 콘텐츠 캘린더                        |
+================================================================+
|
|  2/10 (월)                        2/11 (화)
|  ┌──────────────────────┐         ┌──────────────────────┐
|  │ TikTok 14:00         │         │                      │
|  │ "겨울 피부관리 루틴"  │         │  (휴식 추천)          │
|  │ 예상 조회: 15K~25K   │         │                      │
|  │ [트렌드 키워드 매칭]  │         │                      │
|  └──────────────────────┘         └──────────────────────┘
|
|  2/12 (수)                        2/13 (목)
|  ┌──────────────────────┐         ┌──────────────────────┐
|  │ YouTube 20:00        │         │ Instagram 19:00      │
|  │ "2026 스킨케어 BEST" │         │ (수요일 영상 쇼츠화)  │
|  │ 예상 조회: 8K~15K    │         │ Naver Clip 14:00     │
|  │ [시즌 키워드]        │         │ (수요일 영상 재편집)   │
|  └──────────────────────┘         └──────────────────────┘
|
|  2/14 (금) 발렌타인데이
|  ┌──────────────────────┐
|  │ 전 플랫폼 동시 업로드 │
|  │ "발렌타인 메이크업"   │
|  │ 예상 조회: 25K~50K   │
|  │ [이벤트 + 트렌드]    │
|  └──────────────────────┘
```

#### 2.7.2 구현 방안

```kotlin
data class ContentCalendarRequest(
    val channelIds: List<Long>,
    val periodDays: Int = 14,       // 2주 기본
    val uploadsPerWeek: Int = 3,    // 주당 업로드 목표
    val contentCategories: List<String>,
    val excludeDates: List<LocalDate> = emptyList(),  // 제외 날짜 (휴가 등)
)

data class ContentCalendarResult(
    val calendarItems: List<CalendarItem>,
    val weeklyStrategy: String,
    val platformMix: Map<String, Int>,  // 플랫폼별 업로드 비율
) {
    data class CalendarItem(
        val date: LocalDate,
        val time: LocalTime,
        val platform: String,
        val suggestedTitle: String,
        val contentType: String,        // "original", "repurpose", "shorts"
        val keywords: List<String>,
        val predictedViews: String,     // "15K~25K"
        val reason: String,             // "겨울 스킨케어 검색량 급증"
        val priority: String,           // "HIGH", "MEDIUM", "LOW"
        val sourceVideoId: Long?,       // 리패키징 시 원본 영상 ID
    )
}
```

**캘린더 생성 로직:**

1. **업로드 빈도 최적화**: 주당 목표 업로드 수 기반으로 날짜 분배
2. **플랫폼 믹스 최적화**: 채널별 성과 데이터 기반 플랫폼 비중 결정
3. **콘텐츠 유형 배분**: 오리지널 60% + 리패키징 25% + 쇼츠 15% (카테고리별 조정)
4. **이벤트/시즌 매핑**: 한국 공휴일, 기념일, 시즌 키워드 자동 반영
5. **콘텐츠 리패키징 제안**: 기존 성과 좋은 영상을 다른 플랫폼용으로 재편집 제안

#### 2.7.3 한국 이벤트/시즌 DB

```kotlin
object KoreanSeasonalEvents {
    val events = mapOf(
        MonthDay.of(1, 1) to EventInfo("신정", listOf("새해", "신년", "목표", "다짐")),
        MonthDay.of(2, 14) to EventInfo("발렌타인데이", listOf("발렌타인", "초콜릿", "선물", "데이트")),
        // ... 설날(음력), 추석(음력), 수능, 블프, 크리스마스 등
    )

    val seasons = mapOf(
        Month.MARCH to listOf("봄", "개강", "벚꽃", "환절기"),
        Month.JUNE to listOf("여름", "휴가", "다이어트", "자외선"),
        Month.SEPTEMBER to listOf("가을", "추석", "단풍", "가을패션"),
        Month.DECEMBER to listOf("겨울", "연말", "크리스마스", "송년"),
    )
}
```

#### 2.7.4 캘린더 - 스케줄러 연동

생성된 캘린더 아이템을 직접 `schedules` 테이블에 등록할 수 있도록 한다.

```
POST /api/v1/ai/generate-calendar
Response: { calendarItems: [...] }

POST /api/v1/ai/apply-calendar
Request: { calendarItemIds: [1, 3, 5] }  -- 선택한 항목만 예약 등록
Response: { scheduledCount: 3, scheduleIds: [101, 102, 103] }
```

#### 2.7.5 예상 효과

- "다음에 뭘 올리지?" 고민을 AI가 해결
- 트렌드 + 이벤트 + 개인 데이터 기반의 전략적 콘텐츠 기획
- 업로드 일관성 향상 (크리에이터 번아웃 방지)
- Publer의 "콘텐츠 재활용" 기능보다 한 단계 위의 전략적 접근

---

### 2.8 AI 경쟁자 분석

**우선순위: P2**
**예상 개발 기간: 6~8주**
**크레딧 소모: 15크레딧**

#### 2.8.1 기능 정의

같은 카테고리의 경쟁 크리에이터를 벤치마킹하여 전략적 인사이트를 제공한다.

#### 2.8.2 분석 항목

```kotlin
data class CompetitorAnalysisResult(
    val competitors: List<CompetitorProfile>,
    val categoryBenchmark: CategoryBenchmark,
    val gaps: List<GapAnalysis>,
    val opportunities: List<Opportunity>,
    val actionItems: List<String>,
) {
    data class CompetitorProfile(
        val channelName: String,
        val platform: String,
        val subscriberCount: Long,
        val avgViews: Long,
        val uploadFrequency: String,        // "주 3회"
        val topKeywords: List<String>,
        val contentStyle: String,           // "튜토리얼 중심"
        val growthRate: String,             // "+5.2%/월"
        val strengths: List<String>,
        val weaknesses: List<String>,
    )

    data class CategoryBenchmark(
        val avgSubscribers: Long,
        val avgViewsPerVideo: Long,
        val avgEngagementRate: Double,
        val topKeywords: List<String>,
        val trendingTopics: List<String>,
        val userPosition: String,           // "상위 25%"
        val userRanking: String,            // "카테고리 내 약 150위"
    )

    data class GapAnalysis(
        val area: String,                   // "업로드 빈도", "쇼츠 비율"
        val userValue: String,
        val categoryAvg: String,
        val recommendation: String,
    )

    data class Opportunity(
        val topic: String,
        val reason: String,                 // "경쟁자들이 다루지 않는 주제"
        val estimatedDemand: String,        // "월 검색량 12,000"
        val difficulty: String,             // "낮음"
    )
}
```

#### 2.8.3 데이터 수집 방안

**Phase 1: 공개 데이터 기반 (API 활용)**
- YouTube Data API v3: 채널 통계, 인기 영상 목록, 키워드
- Naver Search API: 네이버 클립 채널 검색, 인기 키워드
- 크리에이터가 직접 경쟁자 채널 URL을 등록

**Phase 2: 크롤링 + 분석 고도화**
- 카테고리별 상위 채널 자동 수집 (YouTube API `search` endpoint)
- 키워드 검색량 추적 (네이버 데이터랩 API, Google Trends API)
- 경쟁자 업로드 패턴 추적 (주기적 데이터 수집)

#### 2.8.4 구현 아키텍처

```
[경쟁자 분석 요청]
    |
    v
[데이터 수집 레이어]
    +-- YouTube Data API -> 경쟁자 채널 데이터
    +-- Naver API -> 검색 트렌드
    +-- 내부 DB -> 사용자 채널 데이터
    |
    v
[데이터 정규화]
    - 플랫폼별 메트릭 표준화
    - 시계열 데이터 정리
    |
    v
[Claude 분석]
    - 경쟁 포지션 분석
    - 갭 분석
    - 기회 영역 도출
    - 액션 플랜 생성
    |
    v
[CompetitorAnalysisResult 반환]
```

#### 2.8.5 기술 스택

| 구성요소 | 기술 | 비고 |
|---------|------|------|
| YouTube 데이터 | YouTube Data API v3 | @HttpExchange 인터페이스 |
| 네이버 데이터 | Naver Search API / DataLab API | 네이버 개발자센터 |
| Google 트렌드 | Google Trends (비공식 API) | Pytrends 또는 직접 파싱 |
| 데이터 저장 | PostgreSQL JSONB | 경쟁자 데이터 캐싱 (24시간) |
| 분석 엔진 | Claude API (Spring AI) | 통합 인사이트 생성 |
| 스케줄링 | Spring Scheduler | 주기적 경쟁자 데이터 갱신 |

#### 2.8.6 경쟁자 추적 대시보드

```
+================================================================+
|  경쟁자 벤치마킹 대시보드                                        |
+================================================================+
|                                                                  |
|  [카테고리: 뷰티/스킨케어]  [내 위치: 상위 25%]                  |
|                                                                  |
|  구독자 성장률 비교 (최근 3개월)                                  |
|  ┌──────────────────────────────────────┐                        |
|  │  채널A ████████████████ +12.3%       │                        |
|  │  내 채널 ██████████████ +10.1%       │                        |
|  │  채널B ████████████ +8.5%            │                        |
|  │  카테고리 평균 ████████ +6.2%        │                        |
|  └──────────────────────────────────────┘                        |
|                                                                  |
|  블루오션 키워드 (경쟁자 미진출)                                  |
|  1. "민감성 피부 겨울 루틴" - 월 검색 12K, 경쟁도 낮음           |
|  2. "비건 스킨케어 추천" - 월 검색 8K, 급상승 중                  |
|  3. "40대 안티에이징" - 월 검색 15K, 경쟁자 3명 미만              |
|                                                                  |
+================================================================+
```

#### 2.8.7 예상 효과

- 크리에이터가 자신의 카테고리 내 위치를 객관적으로 파악
- 블루오션 키워드/주제 발견으로 차별화된 콘텐츠 기획
- 경쟁자의 성공 패턴에서 학습 (벤치마킹)
- Pro/Business 전용 프리미엄 기능으로 업셀링 효과

---

## 3. 우선순위 및 로드맵

### 3.1 우선순위 매트릭스

| 기능 | 우선순위 | 차별화 임팩트 | 구현 난이도 | 개발 기간 | Phase |
|------|---------|-------------|-----------|----------|-------|
| 2.2 플랫폼별 AI 최적화 고도화 | **P0** | 매우 높음 | 낮음 (프롬프트 개선) | 3~4주 | Phase 1 |
| 2.4 최적 게시 시간 고도화 | **P0** | 높음 | 낮음 (기존 확장) | 2~3주 | Phase 1 |
| 2.6 크로스플랫폼 리포트 | **P0** | 매우 높음 | 중간 | 4~5주 | Phase 1 |
| 2.1 영상 분석 엔진 | **P0** | 매우 높음 | 높음 | 6~8주 | Phase 1~2 |
| 2.3 성과 예측 | **P1** | 높음 | 중간 | 5~6주 | Phase 2 |
| 2.5 썸네일 생성/추천 | **P1** | 높음 | 중간 | 5~6주 | Phase 2 |
| 2.7 콘텐츠 캘린더 자동 생성 | **P1** | 중간~높음 | 중간 | 4~5주 | Phase 2 |
| 2.8 경쟁자 분석 | **P2** | 중간 | 높음 | 6~8주 | Phase 3 |

### 3.2 개발 로드맵

```
Phase 1 (2026 Q1): 기반 다지기 + 즉시 차별화
├── Week 1~4:   플랫폼별 AI 최적화 고도화 (P0)
├── Week 2~5:   최적 게시 시간 고도화 (P0) [병렬]
├── Week 3~8:   크로스플랫폼 리포트 (P0) [병렬]
└── Week 5~12:  영상 분석 엔진 v1 (P0) [프레임 추출 + STT 통합]

Phase 2 (2026 Q2): AI 킬러 기능 완성
├── Week 1~6:   성과 예측 v1 (P1)
├── Week 3~8:   썸네일 추천/생성 (P1) [병렬]
├── Week 5~10:  콘텐츠 캘린더 (P1) [병렬]
└── Week 8~12:  영상 분석 엔진 v2 (하이라이트 + 챕터 고도화)

Phase 3 (2026 Q3): 프리미엄 기능 + 데이터 축적 활용
├── Week 1~8:   경쟁자 분석 (P2)
├── Week 4~8:   성과 예측 ML 모델 도입 (Phase 2 데이터 활용)
└── Week 6~12:  AI 기능 전체 고도화 + 자동화 파이프라인
```

---

## 4. 크레딧 과금 체계 확장

### 4.1 신규 기능 크레딧 단가

| 기능 | 기존/신규 | 크레딧 | 산정 근거 |
|------|----------|--------|----------|
| 메타데이터 생성 | 기존 | 5 | Claude API 1회 호출 |
| 해시태그 생성 | 기존 | 3 | Claude API 1회 호출 (간단) |
| STT 변환 | 기존 | 10 | Whisper API 비용 높음 |
| 대본 분석 | 기존 | 5 | Claude API 1회 호출 |
| 댓글 답변 | 기존 | 2 | Claude API 1회 호출 (짧은 응답) |
| 업로드 시간 추천 | 기존 | 3 | Claude API 1회 + DB 조회 |
| 콘텐츠 아이디어 | 기존 | 5 | Claude API 1회 호출 |
| 성과 리포트 | 기존 | 8 | Claude API 1회 + 데이터 집계 |
| **영상 분석** | **신규** | **15** | Vision API 다회 + FFmpeg + STT |
| **성과 예측** | **신규** | **8** | Claude API 1회 + 데이터 분석 |
| **썸네일 추천** | **신규** | **5** | Vision API 분석 (영상 분석 결과 재활용) |
| **썸네일 생성** | **신규** | **10** | 이미지 생성 AI + 후처리 |
| **크로스플랫폼 리포트** | **신규** | **12** | Claude API + 멀티 채널 데이터 집계 |
| **콘텐츠 캘린더** | **신규** | **10** | Claude API + 트렌드 분석 + 스케줄링 |
| **경쟁자 분석** | **신규** | **15** | 외부 API 다회 + Claude 분석 |

### 4.2 `AiFeature` enum 확장

```kotlin
enum class AiFeature(
    val displayName: String,
    val creditCost: Int,
) {
    // 기존
    META_GENERATION("제목/설명 생성", 5),
    HASHTAG_RECOMMENDATION("해시태그 추천", 3),
    STT("영상 STT 변환", 10),
    SCRIPT_ANALYSIS("스크립트 분석", 5),
    COMMENT_REPLY("댓글 답변 생성", 2),
    SCHEDULE_SUGGESTION("업로드 시간 추천", 3),
    CONTENT_IDEA("콘텐츠 아이디어", 5),
    PERFORMANCE_REPORT("성과 리포트", 8),

    // 신규
    VIDEO_ANALYSIS("영상 분석", 15),
    PERFORMANCE_PREDICTION("성과 예측", 8),
    THUMBNAIL_RECOMMENDATION("썸네일 추천", 5),
    THUMBNAIL_GENERATION("썸네일 생성", 10),
    CROSS_PLATFORM_REPORT("크로스플랫폼 리포트", 12),
    CONTENT_CALENDAR("콘텐츠 캘린더", 10),
    COMPETITOR_ANALYSIS("경쟁자 분석", 15),
}
```

### 4.3 플랜별 무료 크레딧 조정

현재 플랜별 무료 월간 크레딧을 신규 기능 추가에 맞게 조정한다.

| 플랜 | 현재 무료 크레딧 | 조정 후 | 근거 |
|------|----------------|---------|------|
| Free | 30 | 30 (유지) | 기본 기능 체험용 (메타데이터 6회 또는 해시태그 10회) |
| Starter (9,900원) | 100 | 150 | 영상 분석 10회 가능하도록 여유 확보 |
| Pro (19,900원) | 300 | 500 | 크로스플랫폼 리포트 주간 사용 + 영상 분석 충분 |
| Business (49,900원) | 1,000 | 1,500 | 경쟁자 분석 + 전체 기능 충분 사용 |

---

## 5. Publer 대비 경쟁력 비교 종합

| 기능 영역 | Publer | onGo (현재) | onGo (전략 완료 후) |
|----------|--------|-------------|-------------------|
| AI 캡션/메타데이터 | O (단일 플랫폼) | **O (플랫폼별 분리)** | **O+ (알고리즘 최적화)** |
| AI 이미지 생성 | O | X | **O (영상 기반 썸네일)** |
| 해시태그 추천 | O (기본) | **O (플랫폼별)** | **O (플랫폼별 + 트렌드)** |
| 콘텐츠 재활용 | O | X | **O (캘린더 내 리패키징)** |
| **영상 분석** | **X** | X | **O (프레임/음성/챕터)** |
| **플랫폼별 알고리즘 최적화** | **X** | 부분 | **O (4개 플랫폼 전문)** |
| **성과 예측** | **X** | X | **O (업로드 전 예측)** |
| **크로스플랫폼 리포트** | **약함** | 기본 | **O (통합 인사이트)** |
| **콘텐츠 캘린더 AI** | **X** | X | **O (트렌드 기반)** |
| **경쟁자 분석** | **X** | X | **O (벤치마킹)** |
| **댓글 AI 답변** | **X** | **O** | **O** |
| **STT (음성-텍스트)** | **X** | **O** | **O** |
| 한국 시장 최적화 | **X** | **O** | **O+** |
| 가격 | $12/월 | 9,900원~/월 | 9,900원~/월 |

**결론: Publer가 AI 4/5 평가를 받는 수준을 넘어, onGo는 "영상을 이해하는 AI"라는 카테고리 자체를 새로 정의한다.**

---

## 6. 리스크 및 완화 방안

| 리스크 | 영향도 | 완화 방안 |
|--------|--------|----------|
| Claude Vision API 비용 급증 | 높음 | 프레임 샘플링 최적화, 배치 처리, 캐싱 30일, 크레딧 단가에 비용 반영 |
| 영상 분석 처리 시간 지연 | 중간 | 비동기 처리 (Virtual Threads), 진행률 표시, 분석 완료 알림 |
| 성과 예측 정확도 부족 | 중간 | 신뢰구간 범위 제공, "참고용" 명시, Phase 2 ML 모델로 정확도 향상 |
| YouTube/플랫폼 API 제한 | 높음 | 할당량 관리, 캐싱 24시간, 점진적 데이터 수집, 유료 API 티어 검토 |
| 경쟁사 빠른 추격 | 중간 | 한국 시장 특화 + 영상 분석 선점 + 데이터 축적으로 진입 장벽 구축 |
| AI 환각/부정확 응답 | 중간 | Structured Output 강제, 검증 레이어 추가, 사용자 피드백 루프 |

---

## 7. 성공 지표 (KPI)

| 지표 | 목표 (6개월 후) | 측정 방법 |
|------|----------------|----------|
| AI 기능 사용률 | 유료 사용자의 70% 이상 | AI 크레딧 소비 사용자 비율 |
| AI 기능 만족도 | 4.5/5 이상 | 기능별 피드백 수집 |
| Free -> 유료 전환율 | 15% 이상 | AI 기능 사용 후 전환 추적 |
| AI 크레딧 매출 비중 | 전체 매출의 20% | 크레딧 구매 매출 |
| 메타데이터 작성 시간 단축 | 80% 감소 | 사용 전후 비교 서베이 |
| Publer 대비 AI 기능 우위 항목 수 | 8개 이상 | 기능 비교표 기준 |

---

## 8. 결론 및 핵심 메시지

### onGo AI 차별화의 3가지 핵심 축

1. **영상 이해 AI (Video Intelligence)**
   - Publer는 텍스트만 다루지만, onGo는 영상 프레임, 음성, 구조를 직접 분석한다.
   - 이것은 단순한 기능 추가가 아니라 **카테고리 재정의**다.

2. **플랫폼 알고리즘 전문가 (Platform-Native AI)**
   - 4개 플랫폼 각각의 알고리즘 특성을 깊이 반영한 최적화를 제공한다.
   - "한 번 올리면 4개 플랫폼에 맞게 알아서 최적화" = onGo의 핵심 가치.

3. **데이터 기반 의사결정 (Data-Driven Creator)**
   - 업로드 전 성과 예측, 사후 크로스플랫폼 분석, 경쟁자 벤치마킹.
   - 크리에이터를 "감"에서 "데이터"로 이동시키는 파트너.

### 마케팅 포지셔닝

> **"Publer는 게시 도구다. onGo는 AI 매니저다."**
>
> Publer에 $12를 내고 게시만 하시겠습니까?
> onGo에 9,900원을 내고 AI가 분석, 최적화, 예측, 리포팅까지 다 해드립니다.

---

*본 기획서의 모든 기술 구현은 기존 onGo 아키텍처(Spring Boot 4.0 + Spring AI + jOOQ + Modular Monolith)와 완전히 호환되도록 설계되었습니다.*

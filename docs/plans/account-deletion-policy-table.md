# 계정 삭제 — FK 별 정책 및 구현 현황

작성: 2026-08-07 · 구현 반영: 2026-08-15
전제 문서: `schema-drift-audit.md` §5.1

이 문서는 정책 근거와 현재 구현 범위를 함께 기록한다. `DELETE` 로 확정하지 않은 FK는
여전히 `REVIEW_BLOCK`으로 남기며, 외부 스토리지·플랫폼·결제 데이터가 있는 계정은 삭제하지 않는다.

## 0. 왜 표부터 만드는가

처음에 "`comments` 와 `competitors` 만 정리하면 탈퇴가 된다"고 보고했다. **틀렸다.**
그 둘만 찔러봤기 때문에 그 둘만 보였을 뿐이다. 전수 측정하니 탈퇴를 막을 수 있는 테이블이
**33개**였다.

33개를 코드에 나열하는 방식도 기각됐다. 다음 기능이 34번째를 추가하는 순간 조용히 깨지는데,
그건 방금 잡은 스키마 드리프트와 같은 실패 방식이다.

FK 메타데이터만 보고 자동 삭제하는 방식도 기각됐다(codex).
**`pg_constraint` 는 참조 관계를 알려주지만 "이 행을 지워도 되는가"는 알려주지 않는다.**
새 테이블이 자동으로 삭제 대상이 되면 다음 기능 추가가 정책 검토를 우회한다 — fail-open 이다.

**결론: 메타데이터는 순서만 제공하고, 삭제 권한은 정책 레지스트리가 제공한다.**

## 1. 정책 3상태

| 상태 | 의미 |
|---|---|
| `DELETE` | 사용자 단독 소유이고 DB 안에서 완결된다. 삭제 대상 |
| `PRESERVE_ANONYMIZE` | 보존하되 개인 식별자를 끊는다. **보존 정책이 이미 결정된 것만** |
| `REVIEW_BLOCK` | 판단 미완. 하나라도 있으면 삭제 job 은 `BLOCKED_POLICY` 로 끝난다 |

**중요**: 정책이 미정인 것을 `PRESERVE_ANONYMIZE` 로 가장하지 않는다. 그건 결정된 척하는
것이다. 미정이면 `REVIEW_BLOCK` 이다.
따라서 **현재 `PRESERVE_ANONYMIZE` 는 0건이다.**

### 1.1 차단 범위 — 전역과 사용자별을 구분한다 (codex 보완)

"`REVIEW_BLOCK` 이 레지스트리에 있으니 모든 탈퇴를 막는다"로 두면, 정책이 확정된
`DELETE` 대상만 가진 사용자까지 **영구히 탈퇴 불가**가 된다. 둘을 나눈다.

| 상황 | 차단 범위 | 이유 |
|---|---|---|
| **미분류 FK 가 존재** | **전역** `BLOCKED_POLICY`. 모든 job 이 시작 전에 멈춘다 | 새 기능이 정책 검토를 우회하지 못하게 한다. 사람이 분류할 때까지 멈추는 게 맞다 |
| **분류된 `REVIEW_BLOCK` FK** | **해당 사용자에게 그 FK 로 엮인 행이 있을 때만** `BLOCKED_POLICY` | 정책 판단이 필요한 데이터를 실제로 가진 사용자만 막으면 된다 |

즉 검사 순서는 (1) 레지스트리 완전성 → 전역 판정, (2) 대상 사용자 행 존재 여부 →
사용자별 판정이다. 둘 다 **삭제를 한 건이라도 실행하기 전**에 끝낸다.

이렇게 하면 `DELETE` 대상만 가진 사용자는 정책이 다 정해지기 전에도 탈퇴할 수 있다.

### 1.2 행 단위 연산 정책 — 다중 `users` 참조 행 (codex 보완)

FK 정책이 constraint 단위여도, **한 행이 여러 사용자를 참조하면 "이 행을 어떻게 할지"를
따로 정해야 한다.** 정책 3상태는 "이 FK 로 엮인 것"을 말할 뿐 행 전체의 운명을 말하지 않는다.

**실측으로 확인한 문제 — 이미 일어나고 있다.**

`approvals` 에 `user_id`=탈퇴자, `requester_id`=다른 사용자, `reviewer_id`=제3자인
행을 만들고 탈퇴자를 삭제했다.

```
삽입 후 approvals 행 수: 1
탈퇴자(user_id, CASCADE) 삭제 → DELETE 성공, 남은 행: 0
→ 다른 사용자(requester)와 리뷰어의 승인 데이터가 함께 사라졌다
```

`approvals.user_id` 가 `ON DELETE CASCADE` 라서 **우리 코드가 무엇을 하든 DB 가 행을 지운다.**
이건 이번 설계가 만드는 문제가 아니라 **현재 스키마에 이미 있는 데이터 유실 경로**다.

따라서 행 단위 연산을 별도로 정의한다.

| 연산 | 의미 |
|---|---|
| `ROW_DELETE` | 행 전체 삭제. 그 행이 탈퇴자 단독 소유일 때만 |
| `ROW_DETACH` | 해당 FK 컬럼만 끊고(`SET NULL`/익명 주체) 행은 남긴다. 다른 사용자가 걸려 있을 때 |
| `ROW_BLOCK` | 판단 미완. 사용자별 `BLOCKED_POLICY` |

`approvals` 는 현재 `ROW_BLOCK` 이다. 그리고 `user_id` 의 `CASCADE` 자체를 재검토해야 한다 —
`ROW_DETACH` 로 가려면 `CASCADE` 를 풀고 `reviewer_id` 처럼 nullable 로 바꾸는 마이그레이션이
필요하다. **정책 승인 전까지는 손대지 않는다.**

## 2. 측정 방법과 결과

실제 PostgreSQL 16 에 마이그레이션 전량 적용 후 `pg_constraint` 직접 조회.
(`information_schema` 는 다중 컬럼 FK 에서 행이 중복돼 쓰지 않았다)

| 항목 | 값 |
|---|---:|
| `users` 참조 FK 제약 | 128 |
| `CASCADE` | 17 |
| `NO ACTION` | 105 |
| 살아 있는 쓰기 경로가 있는 테이블 | 33 |

### 2.1 도달성 표기 — 상한과 실제를 분리한다

전체 표의 "도달성" 열은 **상한선**이다. 유스케이스를 참조하는 컨트롤러가 열려 있는지만 봤고,
그 컨트롤러에 실제 **쓰기** 엔드포인트가 있는지는 확인하지 않았다.

반례가 실제로 있다. `activity_logs` 는 컨트롤러가 열려 있어 상한상 `API` 지만,
`ActivityLogController` 는 `@GetMapping` 조회 하나뿐이고 `ActivityLogUseCase` 의 저장
메서드 호출자가 0건이라 **실제로는 행이 쌓이지 않는다.**

| 표기 | 의미 |
|---|---|
| `API` | 유스케이스를 참조하는 컨트롤러가 열려 있음 (**상한**) |
| `스케줄러` | 컨트롤러 없이 스케줄러·서비스가 부름 |
| `차단` | 컨트롤러가 전부 `@Profile("wip")` |
| `—` | insert 하는 저장소 자체가 없음 |

실제 쓰기 여부는 정책 확정 시 FK 단위로 개별 확인한다. 이 표만 보고 `DELETE` 로 올리지 않는다.

### 2.2 제일 중요한 발견 — FK 컬럼이 `user_id` 가 아닌 것들

소유가 아니라 **관계**를 가리키는 참조다. 자동 삭제하면 **남의 데이터가 사라진다.**

| 테이블 | 컬럼 | 규칙 | 쓰기 경로 |
|---|---|---|---|
| `approvals` | `requester_id` | NO ACTION | LIVE |
| `approvals` | `reviewer_id` | NO ACTION | LIVE |
| `approvals` | `user_id` | **CASCADE** | LIVE |
| `approval_chains` | `approver_id` | NO ACTION | LIVE |
| `workspaces` | `owner_id` | NO ACTION | LIVE |
| `marketplace_orders` | `buyer_id` / `seller_id` | NO ACTION | 쓰기 없음 |
| `board_tasks` | `assignee_id` | NO ACTION | 쓰기 없음 |
| `agency_workspaces` | `owner_user_id` | NO ACTION | 쓰기 없음 |

`approvals` 는 한 테이블에 `users` FK 가 **3개**고 규칙도 서로 다르다.
탈퇴자가 `user_id` 면 행이 CASCADE 로 지워지고, `reviewer_id` 면 막힌다.
**같은 테이블인데 어느 컬럼으로 엮였느냐에 따라 결과가 갈린다.**
→ 정책은 테이블이 아니라 **FK(테이블 + 컬럼) 단위**여야 한다.

## 3. 전체 `users` 참조 관계 (128건)

| 테이블 | FK 컬럼 | 삭제규칙 | 소유 | 쓰기경로 | 도달성(상한) | 외부 리소스 컬럼 |
|---|---|---|---|---|---|---|
| `ab_tests` | `user_id` | NO ACTION | 단독 | LIVE | 스케줄러 | — |
| `academy_enrollments` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `activity_logs` | `user_id` | NO ACTION | 단독 | LIVE | API | — |
| `agency_creators` | `user_id` | NO ACTION | **공유/관계** | 쓰기없음 | — | — |
| `agency_workspaces` | `owner_user_id` | NO ACTION | **공유/관계** | 쓰기없음 | — | logo_url |

### 2.3 최근 마이그레이션에서 추가된 참조

V74·V77·V83·V88·V90에서 추가된 아래 6개 외래키도 레지스트리에 등록했다. 모두
`REVIEW_BLOCK`으로 두었다. CASCADE 여부만으로 개인 소유라고 결론내리면 API 키·OAuth
토큰·게시 이력이 외부 클라이언트나 감사 데이터와 함께 사라질 수 있기 때문이다. 실제 삭제
워커가 외부 접근 폐기와 보존 기간을 처리하는 절차를 갖춘 뒤 개별 정책을 다시 승인한다.

| 테이블 | FK 컬럼 | 현재 정책 | 보류 이유 |
|---|---|---|---|
| `api_keys` | `user_id` | `REVIEW_BLOCK` | 외부 자동화 클라이언트 접근 폐기 절차 필요 |
| `public_api_posts` | `user_id` | `REVIEW_BLOCK` | 외부 게시 이력·예약 상태 보존/취소 정책 필요 |
| `public_oauth_apps` | `owner_id` | `REVIEW_BLOCK` | 개발자 앱과 외부 클라이언트 접근 폐기 필요 |
| `public_oauth_authorization_codes` | `user_id` | `REVIEW_BLOCK` | 승인 코드 만료·감사 보존 정책 필요 |
| `public_oauth_tokens` | `user_id` | `REVIEW_BLOCK` | 외부 OAuth 토큰 전량 폐기 검증 필요 |
| `video_favorites` | `user_id` | `REVIEW_BLOCK` | 원본 영상과의 삭제 순서·보존 정책 필요 |

V94의 `ai_pipeline_jobs.user_id`는 AI 실행 상태와 결과를 담는 사용자 단독 작업 데이터다.
크레딧 사용 이력 자체는 별도 `REVIEW_BLOCK` 정책을 유지하지만, 파이프라인 job의 상태 행은
계정 삭제 시 함께 제거하도록 `DELETE`로 등록했다.

V95의 `ai_batch_jobs.user_id`도 배치 실행 상태와 결과만 담는 사용자 단독 job이므로 같은
기준으로 `DELETE` 처리한다.
| `ai_content_calendars` | `user_id` | NO ACTION | 단독 | LIVE | 차단 | — |
| `ai_credit_transactions` | `user_id` | CASCADE | 단독 | - | — | — |
| `ai_credits` | `user_id` | CASCADE | 단독 | - | — | — |
| `ai_purchased_credits` | `user_id` | CASCADE | 단독 | - | — | — |
| `approval_chains` | `approver_id` | NO ACTION | **공유/관계** | LIVE | API | — |
| `approval_comments` | `user_id` | NO ACTION | **공유/관계** | LIVE | API | — |
| `approvals` | `requester_id` | NO ACTION | **공유/관계** | LIVE | API | — |
| `approvals` | `reviewer_id` | NO ACTION | **공유/관계** | LIVE | API | — |
| `approvals` | `user_id` | CASCADE | **공유/관계** | LIVE | API | — |
| `assets` | `user_id` | NO ACTION | 단독 | LIVE | API | original_filename, filename, file_size_bytes… |
| `audience_profiles` | `user_id` | NO ACTION | 단독 | LIVE | API | avatar_url |
| `audience_segments` | `user_id` | NO ACTION | 단독 | LIVE | API | — |
| `automation_rules` | `user_id` | NO ACTION | 단독 | LIVE | API | — |
| `automation_workflows` | `user_id` | NO ACTION | 단독 | LIVE | API | — |
| `board_activities` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `board_tasks` | `assignee_id` | NO ACTION | **공유/관계** | 쓰기없음 | — | — |
| `brand_deals` | `user_id` | CASCADE | 단독 | - | — | — |
| `brand_kits` | `user_id` | NO ACTION | 단독 | LIVE | API | logo_url, outro_template_url, intro_template_url… |
| `brand_mentions` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | source_url, author_url |
| `brand_schedule_items` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `brand_voice_profiles` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `brands` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | logo_url |
| `campaign_segments` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `channel_audit_reports` | `user_id` | NO ACTION | 단독 | LIVE | API | — |
| `channel_health_metrics` | `user_id` | NO ACTION | 단독 | LIVE | 차단 | — |
| `channels` | `user_id` | CASCADE | 단독 | - | — | platform_channel_id, profile_image_url, channel_url |
| `collab_requests` | `user_id` | NO ACTION | **공유/관계** | 쓰기없음 | — | influencer_profile_id |
| `comment_faq_templates` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `comments` | `user_id` | NO ACTION | 단독 | LIVE | API | platform_video_id, author_channel_url, author_avatar_url… |
| `commerce_platforms` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | platform_type, platform_name |
| `commerce_products` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | image_url, product_url, commerce_platform_id |
| `competitor_profiles` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | avatar_url |
| `competitor_reports` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `competitors` | `user_id` | NO ACTION | 단독 | LIVE | API | profile_image_url, channel_url, platform_channel_id |
| `content_ab_tests` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `content_clips` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | output_url |
| `content_rewrite_results` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `content_rights` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | license_url |
| `content_series` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | cover_image_url |
| `copyright_check_results` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | platform_checks |
| `coupon_usages` | `user_id` | NO ACTION | 단독 | LIVE | API | — |
| `creator_portfolios` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | profile_image_url, public_slug |
| `creator_profiles` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | avatar_url |
| `cross_platform_reports` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | platform_summaries |
| `drive_import_jobs` | `user_id` | CASCADE | 단독 | - | — | drive_file_id, drive_file_name, file_size_bytes |
| `fan_activities` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `fan_campaigns` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `fan_polls` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `fan_rewards` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | image_url |
| `funding_goals` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `funding_transactions` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `funnel_comparisons` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `funnel_stages` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `goals` | `user_id` | NO ACTION | 단독 | LIVE | API | — |
| `growth_goals` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `hashtag_sets` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `ideas` | `user_id` | NO ACTION | 단독 | LIVE | API | reference_url |
| `inbox_messages` | `user_id` | NO ACTION | 단독 | LIVE | API | platform_message_id, sender_avatar_url |
| `influencer_profiles` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | profile_url |
| `keyword_alerts` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `keyword_research_history` | `user_id` | NO ACTION | 단독 | LIVE | API | — |
| `library_folders` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `library_items` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | thumbnail_url, file_size |
| `link_bio_pages` | `user_id` | NO ACTION | 단독 | LIVE | API | avatar_url, slug |
| `live_alert_configs` | `user_id` | NO ACTION | 단독 | LIVE | API | — |
| `live_dashboard_alerts` | `user_id` | NO ACTION | 단독 | LIVE | API | — |
| `live_streams` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | thumbnail_url, stream_url |
| `marketplace_listings` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `marketplace_orders` | `buyer_id` | NO ACTION | **공유/관계** | 쓰기없음 | — | — |
| `marketplace_orders` | `seller_id` | NO ACTION | **공유/관계** | 쓰기없음 | — | — |
| `media_kits` | `user_id` | NO ACTION | 단독 | LIVE | API | profile_image_url, slug, published_url |
| `music_recommendations` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `notifications` | `user_id` | CASCADE | 단독 | - | — | — |
| `optimal_slots` | `user_id` | NO ACTION | 단독 | WIP차단 | — | — |
| `payments` | `user_id` | CASCADE | 단독 | - | — | receipt_url, paddle_invoice_url |
| `performance_predictions` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `performance_reports` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | report_url |
| `playlists` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | platform_playlist_id, thumbnail_url |
| `portfolios` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | public_url |
| `quality_reports` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `recurring_schedules` | `user_id` | NO ACTION | 단독 | LIVE | API | — |
| `recycling_suggestions` | `user_id` | NO ACTION | 단독 | LIVE | API | — |
| `refresh_tokens` | `user_id` | CASCADE | 단독 | - | — | — |
| `repurpose_jobs` | `user_id` | NO ACTION | 단독 | LIVE | API | output_url |
| `resize_jobs` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | thumbnail_url, output_url |
| `revenue_projections` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `revenue_streams` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `rights_alerts` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `schedule_recommendations` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `schedules` | `user_id` | CASCADE | 단독 | - | — | — |
| `script_templates` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `scripts` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `smart_reply_configs` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `smart_reply_rules` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `smart_reply_suggestions` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `sponsorships` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | contract_url |
| `subscriptions` | `user_id` | CASCADE | 단독 | - | — | storage_quota_limit_bytes |
| `subtitle_jobs` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | subtitle_url |
| `subtitle_tracks` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `team_members` | `user_id` | NO ACTION | **공유/관계** | LIVE | API | — |
| `templates` | `user_id` | NO ACTION | 단독 | LIVE | API | — |
| `translation_glossary` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `translation_jobs` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `trend_alerts` | `user_id` | CASCADE | 단독 | - | — | — |
| `trend_predictions` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `usage_alert_configs` | `user_id` | NO ACTION | 단독 | LIVE | API | — |
| `user_content_sources` | `user_id` | CASCADE | 단독 | - | — | external_account_id |
| `user_settings` | `user_id` | CASCADE | 단독 | - | — | — |
| `video_scripts` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `videos` | `user_id` | CASCADE | 단독 | - | — | file_url, file_size_bytes, thumbnail_urls… |
| `visual_workflows` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `watermarks` | `user_id` | NO ACTION | 단독 | LIVE | API | image_url |
| `webhooks` | `user_id` | NO ACTION | 단독 | LIVE | API | url |
| `weekly_digests` | `user_id` | CASCADE | 단독 | - | — | — |
| `weekly_reports` | `user_id` | NO ACTION | 단독 | 쓰기없음 | — | — |
| `workflow_executions` | `user_id` | NO ACTION | 단독 | LIVE | API | — |
| `workspaces` | `owner_id` | NO ACTION | **공유/관계** | LIVE | API | logo_url, slug |

## 4. 정책 현황

### 4.1 `DELETE` 구현 기준 (23건)

DB 안에서 완결되고, 사용자 단독 소유이며, 외부 식별자·스토리지 참조 컬럼이 없다.
컬럼명에 `url/path/file/image/storage/slug/external/platform_` 가 있는지 실제 스키마로 확인했다.

`goals`, `templates`, `automation_rules`, `automation_workflows`, `workflow_executions`,
`recurring_schedules`, `recycling_suggestions`, `audience_segments`, `live_alert_configs`,
`live_dashboard_alerts`, `usage_alert_configs`, `keyword_research_history`,
`ai_content_calendars`, `channel_audit_reports`, `channel_health_metrics`, `ab_tests`,
`ai_credits`, `ai_pipeline_jobs`, `ai_batch_jobs`, `notifications`, `refresh_tokens`, `subscriptions`, `user_settings`
— 전부 `user_id` 컬럼이다.

`subscriptions` 는 무료 상태이고 외부 결제 식별자가 없는 계정만 워커가 통과시킨다. 유료·시험·결제
식별자가 남은 계정은 별도 구독 가드에서 `REVIEW_BLOCK`으로 종료한다. `ai_credits`는 현재 잔액
상태만 삭제하고 구매·사용 이력은 계속 `REVIEW_BLOCK`이다.

`keyword_research_history` 는 자동 검사에서 `keyword` 가 걸렸으나 외부 식별자가 아니다.
수동 확인 후 `DELETE` 로 뒀다.

#### 하위 FK 폐쇄 그래프 실측 (codex 보완 2)

`users` 직접 FK 만으로는 삭제 가능성을 보장할 수 없다. 부모를 지워도 **자식 행이
`NO ACTION` 으로 남아 있으면 실패**한다. 23개 후보를 뿌리로 깊이 4까지 재귀 조회했다.

```
ab_tests             -> ab_test_variants            [CASCADE]
audience_segments    -> audience_profile_segments   [CASCADE]
automation_rules     -> automation_logs             [CASCADE]
automation_workflows -> workflow_actions            [CASCADE]
automation_workflows -> workflow_conditions         [CASCADE]
automation_workflows -> workflow_executions         [CASCADE]
channel_health_metrics -> health_trends             [CASCADE]
goals                -> goal_milestones             [CASCADE]
```

**자식이 있는 8건은 전부 `CASCADE` 다. `NO ACTION` 자식은 0건이다.**
codex 가 든 예(`ab_tests` → variants/results)도 `CASCADE` 라 막지 않는다.
나머지 8건(`templates`, `recurring_schedules`, `recycling_suggestions`,
`live_alert_configs`, `live_dashboard_alerts`, `usage_alert_configs`,
`keyword_research_history`, `channel_audit_reports`)은 자식 FK 자체가 없다.

`workflow_executions` 는 후보이면서 동시에 `automation_workflows` 의 자식이다.
부모를 먼저 지우면 `CASCADE` 로 함께 사라진다. 엔진이 순서를 그래프에서 계산하면 되고
목록에 중복으로 있어도 멱등하게 0행 삭제가 된다.

실제 `DELETE` 재현은 §8의 `AccountDeletionDataAdapterIT` Testcontainers fixture로 고정한다.
정책 레지스트리 전체를 실제 PostgreSQL에서 실행한 뒤 사용자·자식 row 삭제와 `COMPLETED` 기록을
확인한다.

#### wip 상태와 정책을 분리해 기록한다 (codex 보완 3)

`ai_content_calendars` 와 `channel_health_metrics` 는 컨트롤러가 `@Profile("wip")` 이라
**현재 행이 생기지 않는다.** 이건 "지금 안전하다"는 사실이지 "`DELETE` 정책이 옳다"는
근거가 아니다. 두 가지를 분리해 적는다.

| 테이블 | 현재 사실 | 정책 제안 |
|---|---|---|
| `ai_content_calendars` | wip 차단, 행 없음 | `DELETE` (개인 캘린더) |
| `channel_health_metrics` | wip 차단, 행 없음. 자식 `health_trends` CASCADE | `DELETE` (개인 지표) |

기능이 wip 을 벗을 때 정책을 재확인한다.

### 4.2 `PRESERVE_ANONYMIZE` — **0건**

§1 의 원칙대로 결정된 보존 정책이 아직 없다. 후보는 §4.3(c) 로 내렸다.

### 4.3 `REVIEW_BLOCK` — 판단 필요

#### (a) 관계·공유 경계 — 남의 데이터가 걸린다

| FK | 쟁점 |
|---|---|
| `approvals.requester_id` / `.reviewer_id` / `.user_id` | 3개 FK, 규칙 불일치. 리뷰어 탈퇴로 남의 승인 요청을 지울 수 없다 |
| `approval_chains.approver_id` | 승인자 탈퇴로 조직의 승인 체인이 끊긴다 |
| `approval_comments.user_id` | 남의 승인 스레드에 달린 코멘트 |
| `team_members.user_id` | 팀 소속. 지우면 팀 구성이 바뀐다 |
| `workspaces.owner_id` | **소유자 탈퇴 시 워크스페이스와 다른 멤버 데이터 처리 미정.** 소유권 이전이 필요할 수 있다 |

#### (b) 외부 식별자·스토리지 잔존 — DB 삭제만으로 안 끝난다

| 테이블 | 남는 것 |
|---|---|
| `assets` | `file_url`, `filename`, `file_size_bytes` — 실제 파일이 S3/MinIO 에 남는다 |
| `brand_kits` | `logo_url`, `intro_template_url`, `outro_template_url`, `watermark_url` |
| `watermarks` | `image_url` |
| `repurpose_jobs` | `output_url` |
| `media_kits` | `published_url`, `slug` — 공개 URL 이 살아 있을 수 있다. 공유 경계이기도 하다 |
| `link_bio_pages` | `slug`, `avatar_url` — 공개 페이지 |
| `webhooks` | `url` — **외부 엔드포인트로 계속 호출될 수 있다.** 삭제 순서가 중요하다 |
| `audience_profiles` | `avatar_url` |
| `inbox_messages` | `platform_message_id`, `sender_avatar_url` — 원본은 외부 플랫폼 |
| `comments` | `platform_comment_id`, `author_channel_url` 등 — 원본은 외부 플랫폼. self-reference 는 `SET NULL` |
| `competitors` | `platform_channel_id`, `channel_url` — **제3자 채널 정보**. 자식 `competitor_analytics_daily` 는 CASCADE |
| `ideas` | `reference_url` — 사용자가 입력한 외부 링크지 우리 스토리지가 아니다. `DELETE` 로 내릴 여지 있음 (§6 Q4) |

#### (c) 보존 정책 미결정 — 결정 전까지 탈퇴 전체를 막는다

| FK | 쟁점 |
|---|---|
| `activity_logs.user_id` | 감사 성격. 지금은 행이 안 쌓여 문제가 없지만 **기록이 켜지는 순간 탈퇴가 깨진다.** 미리 정해야 한다 |
| `coupon_usages.user_id` | 지우면 **재가입으로 같은 쿠폰을 재사용할 수 있다.** 보존이 맞다고 보지만 결정 전이라 여기 둔다 |

둘 다 `PRESERVE_ANONYMIZE` 로 가려면 `user_id` NOT NULL 해제 마이그레이션이 필요하다.
별도 승인 대상이다.

### 4.4 쓰기 경로가 없는 나머지 (약 70건)

`insert` 하는 저장소가 없거나 소비자가 wip 이라 **현재 행이 생기지 않는다.**
그래도 정책은 매겨야 한다. 기능이 켜지는 순간 미분류인 채로 탈퇴를 막기 때문이다.

**제안: 일괄 `REVIEW_BLOCK`. 각 기능이 wip 을 벗을 때 정책을 함께 정한다.**
§5 가드가 "정책 없는 살아 있는 FK"를 자동으로 잡는다.

## 5. 정책 키와 가드 설계

### 5.1 정책 키 — 제약 identity 로 한다 (codex 보완 1)

처음에 "테이블 + 컬럼"을 키로 제안했는데 **부족하다.** 같은 컬럼에 FK 가 여러 개 걸리거나
복합 FK 가 생기면 누락되거나 충돌한다.

**키 = `(schema, constraint_name, ordered local columns, ordered referenced columns)`**

- `constraint_name` 은 사람이 읽을 수 있고 마이그레이션에서 지정 가능하다
- `oid` 는 DB 재생성마다 바뀌므로 저장 키로 쓰지 않고 조회 시에만 쓴다
- 컬럼은 **순서 있는 목록**으로 둔다. 복합 FK 에서 순서가 의미를 가진다
- 제약 이름이 바뀌면 레지스트리에 없는 키가 되어 가드가 실패한다. 의도된 동작이다

### 5.2 가드

`SchemaDriftGuardIT` 와 같은 방식이되, allowlist 하나가 아니라 **3상태 분류를 강제**한다.

1. `pg_constraint` 로 `users` 를 참조하는 FK 전체를 읽는다
2. 각 FK 의 §5.1 키가 정책 레지스트리에 있는지 확인한다
3. 하나라도 없으면 **실패**한다

FK 단위인 이유는 §2.2 다. `approvals` 처럼 같은 테이블에서 컬럼마다 답이 다르다.

런타임 job 도 같은 검사를 한다. 중요한 건 **검사 시점**이다 — 미분류 발견은
**삭제를 한 건이라도 실행하기 전**이어야 한다. 일부 지운 뒤 발견하면 되돌릴 수 없다
(codex 보완 8). 따라서 job 의 첫 단계가 레지스트리 검증이고, 실패 시 `BLOCKED_POLICY` 로 끝난다.

## 6. 삭제 엔진 설계 및 현재 구현 (C + A')

외부 계약은 C(비동기), 내부 엔진은 A'(정책 레지스트리)로 구현했다. 현재는 정책 승인된
개인 row만 DB 트랜잭션으로 삭제하며, 외부 리소스·공유·결제 데이터가 있는 계정은 차단한다.

### 6.1 외부 계약

- 탈퇴 API 는 **즉시 삭제 완료를 주장하지 않는다.** `DELETION_REQUESTED` 와 `202` 를 반환한다
- 상태 조회 수단을 제공한다: `REQUESTED → IN_PROGRESS → COMPLETED / BLOCKED_POLICY / FAILED`

### 6.2 쓰기 동결

요청 접수 시점에 계정 쓰기를 동결한다. 동결 없이 삭제하면 job 진행 중 새 행이 생겨
FK 위반이나 잔존 데이터가 남는다.

### 6.3 멱등성·재시도

- job 은 재실행 가능해야 한다. 이미 지운 대상은 0행 삭제로 통과한다
- DB 핵심 삭제는 **한 트랜잭션**으로 묶어 부분 반영을 만들지 않는다
- 실패 시 전체 롤백 후 재시도한다. 재시도 횟수와 종료 조건을 명시한다

### 6.4 외부 리소스 — 아직 구현하지 않은 차단 범위 (codex 보완 7)

현재 워커는 외부 리소스를 수집하거나 삭제하지 않는다. 그래서 `assets`, `channels`, `videos`,
외부 OAuth·공개 API·웹훅 등 관련 FK는 `REVIEW_BLOCK`으로 남아 있다. 아래 설계는 후속 구현
기준이며, 완료 전까지 제품이 외부 데이터까지 삭제한다고 약속하지 않는다.

순서는 **"수집 → DB 트랜잭션 → 외부 정리"** 다. `assets` 행을 먼저 지우면 `file_url` 을
잃어 파일을 못 지우기 때문이다.

**URL·플랫폼 ID 가 있다고 항상 외부 삭제 대상인 것은 아니다.** 필드별로 나눈다.

| 분류 | 예 | 처리 |
|---|---|---|
| 우리 소유 스토리지 | `assets.file_url`, `brand_kits.*_url`, `watermarks.image_url`, `repurpose_jobs.output_url` | 실제 삭제 대상 |
| 우리가 발행한 공개 URL | `media_kits.published_url`/`slug`, `link_bio_pages.slug` | 비공개 전환 또는 회수 |
| 외부 연동 엔드포인트 | `webhooks.url` | **삭제 전에 먼저 비활성화**한다. 안 그러면 정리 중에도 호출된다 |
| 제3자 정보 | `comments.author_channel_url`, `competitors.channel_url`, `inbox_messages.platform_message_id` | **외부 삭제 대상이 아니다.** 남의 데이터다 |
| 사용자가 입력한 외부 링크 | `ideas.reference_url` | 외부 삭제 대상이 아니다 |

#### durable 상태 — 중단돼도 재개 가능해야 한다 (codex 보완)

DB 삭제까지 마쳤는데 외부 정리 중 프로세스가 죽으면, 상태가 없으면 어디까지 됐는지 모른다.
상태를 **영속화**한다.

```
REQUESTED → IN_PROGRESS → DB_COMMITTED → EXTERNAL_CLEANUP_PENDING → COMPLETED
                ↘ BLOCKED_POLICY        ↘ FAILED
```

- `DB_COMMITTED` 는 DB 트랜잭션이 커밋된 시점에 기록한다. 이 이후로는 **DB 단계를 다시 하지 않는다**
- `EXTERNAL_CLEANUP_PENDING` 은 재시도 대상이다. 재시도는 수집 목록을 근거로 하므로
  목록이 살아 있어야 한다
- 재시작 시 상태를 읽어 해당 단계부터 재개한다

#### 수집 목록의 민감값 처리 (codex 보완)

목록에는 리소스 식별자만 담고 개인정보 본문은 담지 않는다. 더해서:

- **URL 의 query string·서명(presigned signature)·토큰을 제거하거나 암호화해 보관**한다.
  presigned URL 을 그대로 저장하면 그 자체가 접근 권한이 된다
- **로그에 남기지 않는다.** 실패 로그에도 리소스 식별자와 결과 코드만 남긴다

### 6.5 부분 실패 감사 기록

job 단위로 사용자 식별자, job 식별자, 각 단계 결과를 남긴다.
**개인정보 본문은 남기지 않는다.** 무엇을 지웠는지가 아니라 어디까지 진행됐는지를 남긴다.

감사 로그에 남는 **내부 `user_id` 도 접근통제된 참조값으로 취급한다**(codex 보완).
탈퇴한 사용자의 식별자가 평문으로 조회 가능하면 감사 목적을 넘어선다.

## 7. 판단 결과 (codex 검토 완료)

| # | 쟁점 | 결정 |
|---|---|---|
| 1 | `workspaces.owner_id` | **`REVIEW_BLOCK` 유지.** 관계 행 삭제/익명화 정책과 공유 데이터 생명주기를 먼저 정해야 한다 |
| 2 | `approvals` 3중 FK, `team_members`, `approval_comments`, `approval_chains` | **`REVIEW_BLOCK` 유지.** 같은 이유 |
| 3 | `activity_logs`, `coupon_usages` | **`REVIEW_BLOCK` 유지.** NOT NULL 해제로 `PRESERVE` 전환은 제품·감사·쿠폰 재사용 정책 승인 뒤 **별도 마이그레이션**으로 |
| 4 | `ideas.reference_url` | **`DELETE` 확정.** 아래 근거로 단독 소유를 확인했다 |
| 5 | 외부 리소스 수집 순서 | **이번 설계에 포함.** §6.4 |

### Q4 근거 — `ideas` 단독 소유 확인

codex 조건("공개 공유·다른 사용자 참조가 없는지 확인하고 근거를 명시")에 따라 실측했다.

- 컬럼: `id, user_id, title, description, category, status, priority, source, reference_url, tags, due_date, created_at, updated_at` — **공개/공유 컬럼 없음**(`is_public`, `slug`, `shared_*` 부재)
- `REFERENCES ideas` 인 FK **0건** — 다른 테이블이 참조하지 않는다
- `IdeaController` 엔드포인트: `GET`, `POST`, `PUT /{id}`, `DELETE /{id}`, `POST /ai-generate`,
  `PUT /{id}/status` — **공개·공유 엔드포인트 없음.** 전부 본인 CRUD
- `reference_url` 은 사용자가 입력한 외부 링크지 우리 스토리지가 아니다 → 외부 삭제 대상 아님(§6.4)

## 8. 검증 fixture (codex 요구, 구현과 함께 작성)

Testcontainers 로 실제 PostgreSQL 에 고정한다.

| # | fixture | 고정할 것 |
|---|---|---|
| (a) | 상태별 | **`DELETE` 대상만 있는 사용자 → 성공**(§1.1 대로 다른 사용자의 `REVIEW_BLOCK` 이 이 사용자를 막지 않는다). 해당 사용자에게 `REVIEW_BLOCK` FK 행이 있으면 → `BLOCKED_POLICY`, **DB 무변화**. 미분류 FK 가 있으면 → **전역** `BLOCKED_POLICY` |
| (b) | 다중 users-FK | 컬럼별 개별 사례에 더해 **혼합 행**을 반드시 넣는다 — 같은 `approvals` 행에 `user_id`=탈퇴자, `requester_id`/`reviewer_id`=다른 사용자. 한 FK 가 `CASCADE` 라고 행 전체를 지우면 남의 승인 데이터가 사라진다(§1.2 실측). `ROW_BLOCK` 으로 막히는지 고정 |
| (c) | 하위 FK 폐쇄 그래프 | `automation_workflows` + `workflow_actions`/`conditions`/`executions`, `goals` + `goal_milestones`, `ab_tests` + `ab_test_variants` 를 채운 뒤 삭제해 자식까지 정리되는지 |
| (d) | 재시도·동시 요청·쓰기 동결 | 같은 job 재실행이 멱등(0행 삭제)인지. 동결 후 쓰기 시도가 거부되는지. 동시 요청이 중복 job 을 만들지 않는지 |
| (e) | 외부 목록 수집 실패 | 수집 단계에서 실패하면 **DB 삭제를 시작하지 않는지**. DB 삭제 후 외부 정리 실패 시 `EXTERNAL_CLEANUP_PENDING` 과 목록 보존 |

추가로 FK 실패 시 **전체 롤백**을 단언한다. 부분 반영이 남으면 안 된다.

## 9. 이번 범위 밖 (P1 후속으로 추적)

- 외부 스토리지(S3/MinIO) 파일 삭제
- 플랫폼 OAuth 토큰·연동 해제
- 결제 데이터 보존/익명화
- 법적 보존 의무 — 여기서 단정하지 않는다. 제품·법무 확인 항목이다
- 삭제 job 상태 조회 API와 지원자용 재처리 화면

### 9.1 별건이지만 지금도 일어나는 데이터 유실 — `approvals.user_id` CASCADE

§1.2 에서 실측한 건이다. **이번 설계와 무관하게 현재 스키마에서 이미 발생한다.**
탈퇴자가 `approvals.user_id` 인 행은 `ON DELETE CASCADE` 로 지워지고, 그 행의
`requester_id`·`reviewer_id` 로 걸린 **다른 사용자의 승인 데이터가 함께 사라진다.**

지금은 탈퇴가 다른 FK 에 막혀 실행되지 않아 드러나지 않았을 뿐이다.
탈퇴 경로가 열리는 순간 표면화된다.

`ROW_DETACH` 로 가려면 `user_id` 의 `CASCADE` 를 풀고 nullable 로 바꾸는 마이그레이션이
필요하다. 정책 승인 전까지 손대지 않지만, **탈퇴 기능을 열기 전에 반드시 결론이 나야 한다.**

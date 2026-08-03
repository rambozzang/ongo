# 쇼츠 렌더 워커 설계

작성일: 2026-08-03
상태: **설계만. 지금 구현하지 않는다.** 수요가 확인되면 이 문서대로 붙인다.
선행: Phase 1~3 완료 (`2d3a519`)

## 1. 왜 필요한가

참고 영상이 짚은 핵심은 실력이 아니라 **귀찮음**이다. "롱폼 하나 넣으면 끝"이 상품성의
전부인데, 사용자가 `render.sh` 를 받아 직접 ffmpeg 를 돌려야 한다면 없애려던 귀찮음이
그대로 남는다. 그래서 인코딩은 결국 우리가 해야 한다.

## 2. 무엇을 우려했고, 무엇이 실제 위험인가

원래 이 프로젝트는 영상 편집을 피하려 했다. 서버 부하 때문이다. 그 우려는 맞지만
**대상이 다르다.** 실제 위험은 "영상 편집 기능" 자체가 아니라 **API 서버와 인코딩의 동거**다.

인코딩은 CPU·디스크를 크게 먹고 지연이 길다. 같은 프로세스나 같은 박스에 두면 웹 요청이
굶는다. 반대로 분리하면 위험의 대부분이 사라진다. 이 작업은 **지연 허용**이기도 하다.
쇼츠는 어차피 예약 게시라 몇 분에서 몇십 분 걸려도 사용자 경험이 달라지지 않는다.

### 부하 추정 (거친 값)

| 항목 | 값 |
|---|---|
| 클립 1개 (60초, 1080×1920, h264 crf20) | CPU 약 30~90초 |
| 롱폼 1편 = 클립 10개 | CPU 약 5~15분 |
| 전용 4코어 워커 1대 | 시간당 롱폼 15~25편 |

하루 수백 편까지 워커 1대로 감당된다. 실측 전까지는 추정치로만 다룬다.

## 3. 원칙

**인코딩은 API 서버와 프로세스도 박스도 공유하지 않는다.**

워커가 죽거나 큐가 밀려도 웹은 멀쩡해야 한다. 부하가 늘면 워커만 늘리고, 없으면 0대로
줄인다.

## 4. 구조

```
API 서버                        렌더 워커 (별도 노드, N대)
  │                                  │
  ├─ RENDER_SPEC 단계에서 스펙 생성   │
  ├─ 렌더 잡 INSERT ────────────→ 잡 테이블 (PostgreSQL)
  │                                  ├─ SKIP LOCKED 로 잡 선점
  │                                  ├─ S3에서 원본 다운로드
  │                                  ├─ ffmpeg 실행 (render.sh 그대로)
  │                                  ├─ S3에 결과 업로드
  │                                  └─ 콜백 ─────────┐
  │                                                   │
  └─ 완성 영상을 Video 로 등록 → attachRenderedVideo ─┘
        → 클립 RENDERED → SCHEDULE 단계가 게시 위임
```

### 큐는 PostgreSQL 로 한다

프로젝트 방침이 "Phase 1 무 외부 MQ" 다. Redis/RabbitMQ 를 새로 들이지 않고
**잡 테이블 + `FOR UPDATE SKIP LOCKED`** 로 처리한다. 여러 워커가 안전하게 경합하고,
기존 PostgreSQL·jOOQ 스택 안에서 끝난다. 처리량이 이 방식의 한계를 넘을 일은 당분간 없다.

```sql
CREATE TABLE ugc_shorts_render_jobs (
    id            BIGSERIAL PRIMARY KEY,
    clip_id       BIGINT NOT NULL REFERENCES ugc_shorts_clips(id) ON DELETE CASCADE,
    run_id        BIGINT NOT NULL,
    workspace_id  BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    render_spec   JSONB NOT NULL,        -- 잡 페이로드. 이미 Phase 2에서 만들고 있다
    status        VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
                                          -- QUEUED / LEASED / RUNNING / DONE / FAILED / CANCELLED
    attempt       INT NOT NULL DEFAULT 0,
    max_attempts  INT NOT NULL DEFAULT 3,
    leased_by     VARCHAR(100),           -- 워커 식별자
    lease_expires_at TIMESTAMP,           -- 워커가 죽으면 이 시각 이후 회수
    output_url    VARCHAR(500),
    error_message TEXT,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ugc_shorts_render_jobs_clip UNIQUE (clip_id)
);
CREATE INDEX idx_ugc_shorts_render_jobs_pick
    ON ugc_shorts_render_jobs(status, created_at) WHERE status IN ('QUEUED', 'LEASED');
```

잡 선점:

```sql
UPDATE ugc_shorts_render_jobs SET
    status = 'LEASED', leased_by = :worker, attempt = attempt + 1,
    lease_expires_at = now() + interval '30 minutes', updated_at = now()
WHERE id = (
    SELECT id FROM ugc_shorts_render_jobs
    WHERE status = 'QUEUED'
       OR (status IN ('LEASED','RUNNING') AND lease_expires_at < now())
    ORDER BY created_at
    FOR UPDATE SKIP LOCKED
    LIMIT 1
)
RETURNING *;
```

리스 만료로 회수하므로 워커가 갑자기 죽어도 잡이 유실되지 않는다.
`attempt >= max_attempts` 면 `FAILED` 로 확정하고 재시도하지 않는다.

## 5. 이미 만든 것이 그대로 쓰인다

Phase 2의 `render-spec.json` 이 **그대로 잡 페이로드**다. 컷 구간·크롭 박스·자막 타이밍·
후킹·템플릿이 다 들어 있고, `ShortsRenderSpecBuilder.buildRenderScript()` 가 내는 ffmpeg
명령이 워커가 실행할 명령 그 자체다. 스펙 우선 설계 덕분에 워커 도입 비용이 작다.

API 서버에서 새로 필요한 것은 사실상 **잡 INSERT 와 콜백 수신** 둘뿐이다.
완성 영상을 클립에 붙이는 경로(`attachRenderedVideo`)는 이미 있다.

## 6. 매니지드 트랜스코딩을 쓰지 않는 이유

AWS MediaConvert 같은 선택지가 있지만 이번 경우엔 잘 맞지 않는다.

- 우리 자막은 한 줄 5~9자 규칙에 폰트·외곽선·위치까지 지정한 **ASS** 다. 매니지드 서비스는
  자체 DSL 로 다시 표현해야 하고 스타일 충실도가 떨어진다.
- 우리 스펙이 이미 ffmpeg 명령이라 직접 돌리는 쪽이 호환성과 결과 품질에서 유리하다.
- 비용도 월 500편 기준 전용 워커 1대와 비슷하고, 그 이상부터는 워커가 싸진다.

## 7. 반드시 걸 안전장치

| 항목 | 내용 |
|---|---|
| 동시 실행 상한 | 워커당 ffmpeg 프로세스 수를 코어 수에 맞춰 고정. 무제한이면 워커가 죽는다 |
| 스토리지 수명 | 원본·결과를 영구 보관하지 않는다. 게시 후 N일 뒤 삭제. onGo 는 호스팅 서비스가 아니다 |
| 플랜별 렌더 쿼터 | 원가가 직접 발생하므로 요금제에 반영한다 |
| 잡 타임아웃 | ffmpeg 에 `-timeout` 과 워커 측 하드 킬. 무한 대기 방지 |
| 입력 검증 | render_spec 의 경로·파라미터를 셸에 그대로 넣지 않는다. 인자 배열로 실행 |

마지막 항목은 특히 중요하다. `render.sh` 를 문자열로 조립해 `sh -c` 로 돌리면 명령 주입
위험이 있다. 워커는 스펙을 파싱해 **인자 배열로 ffmpeg 를 직접 실행**한다.

## 8. 순서

1. **파일럿 (지금)** — 현재 상태 그대로. 사용자가 산출물을 받아 직접 렌더하고
   `POST .../clips/{clipId}/rendered-video` 로 연결한다. 서버 비용 0원. 수요를 먼저 본다.
2. **워커 도입** — 수요가 확인되면 이 문서대로 잡 테이블과 워커를 붙인다.
   스펙 계약이 이미 있어 API 서버는 거의 손대지 않는다.
3. **확장** — 워커 수를 늘리거나, 우선순위 큐(유료 플랜 우선)를 얹는다.

## 9. 열어둔 결정

- 워커를 어디에 둘 것인가 (같은 오라클 인스턴스의 별도 컨테이너 vs 별도 노드)
- 렌더 결과를 기존 Video 엔티티로 등록할 때 소유·쿼터를 어떻게 계산할 것인가
- 실패한 잡을 사용자에게 어떻게 노출할 것인가 (현재 클립 상태에 FAILED 가 있으나 사유 전달 경로 미정)

# 게시 큐 장애 복구

게시 작업은 `video_uploads`에 상태와 lease를 저장한다. 애플리케이션이 재시작되어도 외부 플랫폼에 이미 전송했을 가능성이 있는 작업을 무조건 다시 보내지 않는다.

## 상태별 처리

- `UPLOADING` + `next_retry_at`: 429 등 재시도 가능한 일시 오류다. 예약 디스패처가 시간이 되면 새 lease를 획득하고 다시 처리한다. 현재 시도 횟수가 한도를 넘으면 `FAILED`로 끝난다.
- `PROCESSING`: 플랫폼이 접수한 비동기 작업이다. `poll_token`으로 상태만 조회한다.
- `UNCONFIRMED`: 외부 전송 결과를 확인하지 못한 상태다. 중복 게시 방지를 위해 자동 재전송하지 않으며, 사용자 재확인 API를 사용한다.
- `FAILED`: 외부 요청이 확정적으로 실패한 상태다. 영상 상세 화면의 재시도로 새 게시 시도를 시작한다.
- `PARTIALLY_PUBLISHED`: 여러 채널 중 일부만 성공한 상태다. 성공한 채널은 다시 보내지 않고 실패한 대상만 재시도한다.

## 운영 확인

관리자 게시 큐에서 lease, 재시도 예정 시각, `UNCONFIRMED` 건수를 확인한다.

```text
GET /api/v1/admin/publish-queue
POST /api/v1/videos/{videoId}/uploads/{uploadId}/recheck
POST /api/v1/videos/{videoId}/uploads/{uploadId}/retry
```

429 응답은 플랫폼의 `Retry-After`(초)를 우선 사용하고, 없으면 지수 백오프를 적용한다. 네트워크 timeout과 5xx는 외부 수락 여부를 확정할 수 없으므로 자동 재전송하지 않는다.

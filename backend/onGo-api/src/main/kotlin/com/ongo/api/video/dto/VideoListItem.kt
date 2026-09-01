package com.ongo.api.video.dto

import com.ongo.common.enums.MediaType
import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import java.time.LocalDateTime

data class VideoListItem(
    val id: Long,
    val title: String,
    val thumbnailUrl: String?,
    val mediaType: MediaType = MediaType.VIDEO,
    val status: UploadStatus,
    val uploads: List<PlatformStatusItem>,
    /**
     * 총 조회수. **잰 적이 없으면 `null`** 이다.
     *
     * `null` 인 이유는 두 가지이고 [pendingViewUploads] 로 갈린다.
     *
     * - 대기 `0` 건: 조회수를 수집하는 업로드가 없다. Tumblr 의 `views` 자리에는 노트
     *   총합이 들어 있고 Naver Clip 은 분석 API 가 없다 → **측정 불가**.
     * - 대기 `N` 건: 수집하는 업로드는 있는데 아직 집계 전이다 → **수집 대기**.
     *
     * 화면은 `null` 을 `0` 으로 채우지 말 것. 집계 행이 있는 상태의 `0` 은 실측이다.
     */
    val totalViews: Long?,
    /**
     * 조회수를 수집하는 업로드 중 **아직 집계 행이 없는** 개수.
     *
     * `totalViews` 가 이 업로드들을 포함하지 않는다는 뜻이다. 기존 필드를 바꾸지 않고
     * 추가한 값이라, 이 필드를 모르는 클라이언트는 예전과 똑같이 동작한다.
     */
    val pendingViewUploads: Int = 0,
    val createdAt: LocalDateTime?,
)

data class PlatformStatusItem(
    val platform: Platform,
    val status: UploadStatus,
    val platformUrl: String? = null,
)

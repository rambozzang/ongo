package com.ongo.application.analytics.dto

/**
 * 태그 하나의 성과. **그 지표를 수집하는 업로드가 없으면 `null`** 이다.
 *
 * 태그는 여러 영상·여러 플랫폼에 걸쳐 있어 raw 합계가 서로 다른 뜻의 숫자를 섞는다.
 * Tumblr 의 `views` 는 노트 총합, Pinterest 의 `likes` 는 저장(Save) 수라
 * 0 이 아니라 **큰 숫자로 조용히 틀린다.**
 */
data class TagPerformanceItem(
    val tag: String,
    /** 이 태그가 붙은 영상 수. 실제 개수라 항상 값이 있다. */
    val videoCount: Int,
    /**
     * 기간 내 조회수 합계. **잰 적이 없으면 `null`.**
     *
     * 지표가 [unavailableMetrics] 에 있으면 그 지표를 주는 업로드가 없다는 뜻이고,
     * 없으면 수집하지만 그 기간에 행이 없다는 뜻이다. 행이 있고 합이 `0` 이면 실측이다.
     */
    val totalViews: Long?,
    /** 기간 내 좋아요 합계. 계약은 [totalViews] 와 같다. */
    val totalLikes: Long?,
    /** 영상당 평균 조회수. 조회수가 미측정이면 `null`. */
    val avgViews: Long?,
    /**
     * 참여율(%). **좋아요와 조회수를 모두 수집하는 업로드의 행에서만** 계산한다.
     *
     * 분자만 걸러 내고 조회수를 분모에 남기면 참여율이 실제보다 낮아진다.
     */
    val avgEngagement: Double?,
    /** `"up"` | `"down"` | `"stable"`. 비교할 이전 관측이 없으면 `null`. */
    val trend: String?,
    /**
     * 그 지표를 **주는 업로드가 하나도 없는** 지표 이름. 플랫폼 계약의 문제라 기간과
     * 무관하다. 기간에 행이 없어 `null` 인 경우는 여기에 들어가지 않는다.
     */
    val unavailableMetrics: Set<String> = emptySet(),
)

data class TagPerformanceResponse(
    val tags: List<TagPerformanceItem>,
)

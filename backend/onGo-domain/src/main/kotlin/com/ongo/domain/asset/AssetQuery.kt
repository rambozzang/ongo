package com.ongo.domain.asset

/**
 * 목록·집계가 **함께 쓰는** 조회 조건.
 *
 * 조건을 두 곳에 따로 적으면 목록과 총계가 어긋난다 — 화면은 3건을 보여 주면서
 * "총 240건"이라고 말하게 되고, 그 240은 아무것도 세지 않은 숫자다. 그래서 하나의
 * 값으로 묶어 두 질의에 같이 넘긴다.
 *
 * 여기 있는 것이 **서버가 거르는 조건의 전부**다. 화면이 더 거르면 그 결과는 총계와
 * 맞지 않으므로, 새 필터를 UI 에 붙일 때는 여기에도 함께 추가해야 한다.
 */
data class AssetQuery(
    val fileType: String? = null,
    val folder: String? = null,
    /** 파일명(원본/저장명)과 태그에 대한 부분 일치. 대소문자를 구분하지 않는다. */
    val search: String? = null,
    val tag: String? = null,
)

package com.ongo.application.ugc.shorts.dto

/**
 * 예약표 엑셀 가져오기 시 바뀌는 값 하나.
 * 프론트엔드 `SheetDiffRow` 인터페이스와 필드명이 일치해야 한다.
 *
 * @property field 바뀐 필드 키. title / hookText / caption / scheduledAt 중 하나.
 */
data class SheetDiffRow(
    val clipId: Long,
    val seq: Int,
    val field: String,
    val before: String?,
    val after: String?,
)

/**
 * 예약표 엑셀 가져오기 결과.
 * preview는 DB를 건드리지 않고 이 diff만 돌려주고, apply는 실제 반영 후 반영분을 돌려준다.
 * 프론트엔드 `SheetPreviewResponse` 인터페이스와 필드명이 일치해야 한다.
 */
data class SheetPreviewResponse(
    val rows: List<SheetDiffRow>,
    val unknownClipIds: List<Long>,
    val invalidRows: List<String>,
)

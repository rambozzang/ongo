package com.ongo.common.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object DateTimeUtil {

    val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun nowKST(): LocalDateTime = LocalDateTime.now(SEOUL_ZONE)

    fun todayKST(): LocalDate = LocalDate.now(SEOUL_ZONE)

    fun toKST(instant: Instant): LocalDateTime =
        LocalDateTime.ofInstant(instant, SEOUL_ZONE)

    fun formatDate(dateTime: LocalDateTime): String =
        dateTime.format(DATE_FORMAT)

    fun formatDateTime(dateTime: LocalDateTime): String =
        dateTime.format(DATETIME_FORMAT)

    /**
     * 상대 시간 포맷 (예: "3분 전", "2시간 전", "어제").
     */
    fun relativeTime(dateTime: LocalDateTime): String {
        val now = nowKST()
        val minutes = ChronoUnit.MINUTES.between(dateTime, now)
        val hours = ChronoUnit.HOURS.between(dateTime, now)
        val days = ChronoUnit.DAYS.between(dateTime, now)

        return when {
            minutes < 1 -> "방금 전"
            minutes < 60 -> "${minutes}분 전"
            hours < 24 -> "${hours}시간 전"
            days == 1L -> "어제"
            days < 7 -> "${days}일 전"
            days < 30 -> "${days / 7}주 전"
            days < 365 -> "${days / 30}개월 전"
            else -> "${days / 365}년 전"
        }
    }

    /**
     * 해당 월의 첫째 날 자정 (크레딧 리셋 기준).
     */
    fun firstDayOfMonth(): LocalDateTime =
        todayKST().withDayOfMonth(1).atStartOfDay()
}

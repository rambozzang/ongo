package com.ongo.api.common

import com.ongo.common.PeriodLimitMetadata
import com.ongo.common.ResData
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 분석 전용 필드 `periodLimit` 가 **모든 API 응답에 새어 나오지 않는지** 고정한다.
 *
 * 공용 래퍼에 필드를 더하면 Jackson 은 기본으로 null 도 내보낸다. 그러면 로그인·결제·업로드
 * 응답까지 전부 `"periodLimit": null` 을 달게 된다. 앱이 실제로 쓰는 매퍼(Jackson 3)로 확인한다 —
 * 애노테이션 패키지가 Jackson 버전마다 달라, 컴파일만으로는 존중되는지 알 수 없다.
 */
class ResDataSerializationTest {

    /** 앱과 같은 Jackson 3 매퍼. 스프링 컨텍스트 없이 직렬화 규칙만 본다. */
    private val mapper: JsonMapper = JsonMapper.builder().build()

    @Test
    fun `기간 제한이 없으면 필드를 내보내지 않는다`() {
        val json = mapper.writeValueAsString(ResData(success = true, data = mapOf("a" to 1)))
        assertFalse("periodLimit" in json, json)
    }

    @Test
    fun `기간 제한이 있으면 내보낸다`() {
        val limit = PeriodLimitMetadata(requestedDays = 30, appliedDays = 7, maxDays = 7, wasTruncated = true)
        val json = mapper.writeValueAsString(ResData(success = true, data = 1, periodLimit = limit))
        assertTrue("periodLimit" in json, json)
    }
}

package com.ongo.application.video

import com.ongo.common.enums.Platform
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class PlatformCaptionRulesTest {
    @Test
    fun `caption based providers use the same separator and hashtag normalization as writers`() {
        val expected = "제목\n\n설명\n\n#하나 #둘"

        assertEquals(expected, PlatformCaptionRules.compose(
            Platform.TIKTOK, "제목", "설명", listOf("#하나", "둘"),
        ))
        assertEquals(expected, PlatformCaptionRules.compose(
            Platform.INSTAGRAM, "제목", "설명", listOf("#하나", "둘"),
        ))
        assertEquals(expected, PlatformCaptionRules.compose(
            Platform.THREADS, "제목", "설명", listOf("#하나", "둘"),
        ))
    }

    @Test
    fun `Twitter and long form providers use their actual provider text field`() {
        assertEquals(
            "제목\n\n설명\n\n#하나",
            PlatformCaptionRules.compose(Platform.TWITTER, "제목", "설명", listOf("하나")),
        )
        assertEquals(
            "설명\n\n#하나",
            PlatformCaptionRules.compose(Platform.FACEBOOK, "제목은 별도 필드", "설명", listOf("하나")),
        )
        assertNull(PlatformCaptionRules.compose(Platform.YOUTUBE, "제목", "설명", listOf("하나")))
    }
}

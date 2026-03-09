package com.ongo.application.video

import com.ongo.common.enums.Platform

/**
 * 플랫폼별 StreamWriter 팩토리 인터페이스.
 * 각 플랫폼 인프라 구현체가 이 인터페이스를 구현하여 Spring Bean으로 등록됨.
 */
interface PlatformStreamWriterFactory {
    val platform: Platform
    fun createWriter(): PlatformStreamWriter
}

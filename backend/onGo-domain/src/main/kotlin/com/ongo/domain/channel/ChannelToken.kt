package com.ongo.domain.channel

/** 토큰의 저장 상태와 외부 호출 상태를 타입으로 분리한다. */
sealed interface ChannelToken {
    val value: String
}

data class EncryptedToken(override val value: String) : ChannelToken {
    init { require(value.isNotBlank()) { "암호화된 토큰은 비어 있을 수 없습니다." } }
    override fun toString(): String = "EncryptedToken(****)"
}

data class PlainToken(override val value: String) : ChannelToken {
    init { require(value.isNotBlank()) { "평문 토큰은 비어 있을 수 없습니다." } }
    override fun toString(): String = "PlainToken(****)"
}

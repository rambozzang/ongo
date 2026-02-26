package com.ongo.domain.livestream

interface StreamChatRepository {
    fun findByStreamId(streamId: Long): List<StreamChat>
    fun save(chat: StreamChat): StreamChat
}

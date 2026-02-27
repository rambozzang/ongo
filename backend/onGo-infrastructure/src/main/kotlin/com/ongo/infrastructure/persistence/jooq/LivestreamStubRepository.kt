package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.livestream.*
import org.springframework.stereotype.Repository

@Repository
class LiveStreamStubRepository : LiveStreamRepository {
    override fun findById(id: Long): LiveStream? = null
    override fun findByUserId(userId: Long): List<LiveStream> = emptyList()
    override fun findByStatus(userId: Long, status: String): List<LiveStream> = emptyList()
    override fun save(stream: LiveStream): LiveStream = stream.copy(id = 1)
    override fun updateStatus(id: Long, status: String) {}
}

@Repository
class StreamChatStubRepository : StreamChatRepository {
    override fun findByStreamId(streamId: Long): List<StreamChat> = emptyList()
    override fun save(chat: StreamChat): StreamChat = chat.copy(id = 1)
}

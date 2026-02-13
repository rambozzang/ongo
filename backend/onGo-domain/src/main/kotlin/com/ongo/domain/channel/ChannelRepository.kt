package com.ongo.domain.channel

import com.ongo.common.enums.Platform

interface ChannelRepository {
    fun findById(id: Long): Channel?
    fun findAllActive(): List<Channel>
    fun findByUserId(userId: Long): List<Channel>
    fun findByUserIdAndPlatform(userId: Long, platform: Platform): Channel?
    fun save(channel: Channel): Channel
    fun update(channel: Channel): Channel
    fun delete(id: Long)
    fun countByUserId(userId: Long): Int
}

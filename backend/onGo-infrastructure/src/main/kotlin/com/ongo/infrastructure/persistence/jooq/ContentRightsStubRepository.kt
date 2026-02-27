package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentrights.*
import org.springframework.stereotype.Repository

@Repository
class ContentRightStubRepository : ContentRightRepository {
    override fun findById(id: Long): ContentRight? = null
    override fun findByUserId(userId: Long): List<ContentRight> = emptyList()
    override fun save(right: ContentRight): ContentRight = right.copy(id = 1)
    override fun update(right: ContentRight): ContentRight = right
    override fun delete(id: Long) {}
}

@Repository
class RightsAlertStubRepository : RightsAlertRepository {
    override fun findById(id: Long): RightsAlert? = null
    override fun findByUserId(userId: Long): List<RightsAlert> = emptyList()
    override fun save(alert: RightsAlert): RightsAlert = alert.copy(id = 1)
    override fun markRead(id: Long) {}
    override fun delete(id: Long) {}
}

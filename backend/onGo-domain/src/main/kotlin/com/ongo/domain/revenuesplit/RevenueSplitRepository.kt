package com.ongo.domain.revenuesplit

interface RevenueSplitRepository {
    fun findByWorkspaceId(workspaceId: Long): List<RevenueSplit>
    fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<RevenueSplit>
    fun findById(id: Long): RevenueSplit?
    fun save(split: RevenueSplit): RevenueSplit
    fun update(split: RevenueSplit): RevenueSplit
    fun deleteById(id: Long)
}

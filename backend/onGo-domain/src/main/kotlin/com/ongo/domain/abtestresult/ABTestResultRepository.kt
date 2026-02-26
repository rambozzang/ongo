package com.ongo.domain.abtestresult

interface ABTestResultRepository {
    fun findByWorkspaceId(workspaceId: Long): List<ABTestResult>
    fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<ABTestResult>
    fun findById(id: Long): ABTestResult?
    fun save(result: ABTestResult): ABTestResult
    fun update(result: ABTestResult): ABTestResult
    fun deleteById(id: Long)
}

package com.ongo.domain.scriptwriter

interface ScriptRepository {
    fun findByUserId(userId: Long): List<Script>
    fun findByUserIdAndStatus(userId: Long, status: String): List<Script>
    fun findById(id: Long): Script?
    fun save(script: Script): Script
    fun update(script: Script): Script
    fun deleteById(id: Long)
}

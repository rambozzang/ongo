package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.scriptwriter.*
import org.springframework.stereotype.Repository

@Repository
class ScriptStubRepository : ScriptRepository {
    override fun findByUserId(userId: Long): List<Script> = emptyList()
    override fun findByUserIdAndStatus(userId: Long, status: String): List<Script> = emptyList()
    override fun findById(id: Long): Script? = null
    override fun save(script: Script): Script = script.copy(id = 1)
    override fun update(script: Script): Script = script
    override fun deleteById(id: Long) {}
}

@Repository
class ScriptSectionStubRepository : ScriptSectionRepository {
    override fun findByScriptId(scriptId: Long): List<ScriptSection> = emptyList()
    override fun findById(id: Long): ScriptSection? = null
    override fun save(section: ScriptSection): ScriptSection = section.copy(id = 1)
    override fun update(section: ScriptSection): ScriptSection = section
    override fun deleteById(id: Long) {}
    override fun deleteByScriptId(scriptId: Long) {}
}

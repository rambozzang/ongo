package com.ongo.domain.scriptwriter

interface ScriptSectionRepository {
    fun findByScriptId(scriptId: Long): List<ScriptSection>
    fun findById(id: Long): ScriptSection?
    fun save(section: ScriptSection): ScriptSection
    fun update(section: ScriptSection): ScriptSection
    fun deleteById(id: Long)
    fun deleteByScriptId(scriptId: Long)
}

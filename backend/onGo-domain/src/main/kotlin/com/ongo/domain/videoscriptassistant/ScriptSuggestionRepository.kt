package com.ongo.domain.videoscriptassistant

interface ScriptSuggestionRepository {
    fun findByScriptId(scriptId: Long): List<ScriptSuggestion>
    fun save(suggestion: ScriptSuggestion): ScriptSuggestion
    fun updateApplied(id: Long, isApplied: Boolean)
}

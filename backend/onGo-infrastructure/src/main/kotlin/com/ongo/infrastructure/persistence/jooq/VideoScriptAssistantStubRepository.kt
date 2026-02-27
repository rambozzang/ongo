package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.videoscriptassistant.*
import org.springframework.stereotype.Repository

@Repository
class VideoScriptStubRepository : VideoScriptRepository {
    override fun findById(id: Long): VideoScript? = null
    override fun findByUserId(userId: Long): List<VideoScript> = emptyList()
    override fun save(script: VideoScript): VideoScript = script.copy(id = 1)
    override fun updateStatus(id: Long, status: String) {}
    override fun deleteById(id: Long) {}
}

@Repository
class ScriptSuggestionStubRepository : ScriptSuggestionRepository {
    override fun findByScriptId(scriptId: Long): List<ScriptSuggestion> = emptyList()
    override fun save(suggestion: ScriptSuggestion): ScriptSuggestion = suggestion.copy(id = 1)
    override fun updateApplied(id: Long, isApplied: Boolean) {}
}

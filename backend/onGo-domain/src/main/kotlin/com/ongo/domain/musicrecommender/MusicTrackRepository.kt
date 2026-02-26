package com.ongo.domain.musicrecommender

interface MusicTrackRepository {
    fun findById(id: Long): MusicTrack?
    fun findByGenre(genre: String): List<MusicTrack>
    fun findByMood(mood: String): List<MusicTrack>
    fun save(track: MusicTrack): MusicTrack
}

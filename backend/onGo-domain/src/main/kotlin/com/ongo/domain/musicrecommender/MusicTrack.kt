package com.ongo.domain.musicrecommender

import java.time.LocalDateTime

data class MusicTrack(
    val id: Long? = null,
    val title: String,
    val artist: String,
    val genre: String,
    val mood: String,
    val bpm: Int = 0,
    val duration: Int = 0,
    val previewUrl: String? = null,
    val licenseType: String = "FREE",
    val createdAt: LocalDateTime? = null,
)

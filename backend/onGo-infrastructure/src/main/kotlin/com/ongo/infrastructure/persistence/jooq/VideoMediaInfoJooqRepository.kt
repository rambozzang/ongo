package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.video.VideoMediaInfoRepository
import com.ongo.domain.video.entity.VideoMediaInfo
import com.ongo.infrastructure.persistence.jooq.Fields.AUDIO_BITRATE_KBPS
import com.ongo.infrastructure.persistence.jooq.Fields.AUDIO_CHANNELS
import com.ongo.infrastructure.persistence.jooq.Fields.AUDIO_CODEC
import com.ongo.infrastructure.persistence.jooq.Fields.BITRATE_KBPS
import com.ongo.infrastructure.persistence.jooq.Fields.COLOR_SPACE
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DURATION_MS
import com.ongo.infrastructure.persistence.jooq.Fields.FILE_SIZE_BYTES
import com.ongo.infrastructure.persistence.jooq.Fields.FORMAT_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.FPS
import com.ongo.infrastructure.persistence.jooq.Fields.HEIGHT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PIXEL_FORMAT
import com.ongo.infrastructure.persistence.jooq.Fields.PROFILE
import com.ongo.infrastructure.persistence.jooq.Fields.RAW_JSON
import com.ongo.infrastructure.persistence.jooq.Fields.SAMPLE_RATE
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_CODEC
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Fields.WIDTH
import com.ongo.infrastructure.persistence.jooq.Tables.VIDEO_MEDIA_INFO
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class VideoMediaInfoJooqRepository(
    private val dsl: DSLContext,
) : VideoMediaInfoRepository {

    override fun save(mediaInfo: VideoMediaInfo): VideoMediaInfo {
        val id = dsl.insertInto(VIDEO_MEDIA_INFO)
            .set(VIDEO_ID, mediaInfo.videoId)
            .set(VIDEO_CODEC, mediaInfo.videoCodec)
            .set(WIDTH, mediaInfo.width)
            .set(HEIGHT, mediaInfo.height)
            .set(FPS, mediaInfo.fps)
            .set(BITRATE_KBPS, mediaInfo.bitrateKbps)
            .set(DURATION_MS, mediaInfo.durationMs)
            .set(COLOR_SPACE, mediaInfo.colorSpace)
            .set(PIXEL_FORMAT, mediaInfo.pixelFormat)
            .set(PROFILE, mediaInfo.profile)
            .set(AUDIO_CODEC, mediaInfo.audioCodec)
            .set(AUDIO_BITRATE_KBPS, mediaInfo.audioBitrateKbps)
            .set(SAMPLE_RATE, mediaInfo.sampleRate)
            .set(AUDIO_CHANNELS, mediaInfo.audioChannels)
            .set(FORMAT_NAME, mediaInfo.formatName)
            .set(FILE_SIZE_BYTES, mediaInfo.fileSizeBytes)
            .set(DSL.field("raw_json", String::class.java), mediaInfo.rawJson)
            .onConflict(VIDEO_ID)
            .doUpdate()
            .set(VIDEO_CODEC, mediaInfo.videoCodec)
            .set(WIDTH, mediaInfo.width)
            .set(HEIGHT, mediaInfo.height)
            .set(FPS, mediaInfo.fps)
            .set(BITRATE_KBPS, mediaInfo.bitrateKbps)
            .set(DURATION_MS, mediaInfo.durationMs)
            .set(COLOR_SPACE, mediaInfo.colorSpace)
            .set(PIXEL_FORMAT, mediaInfo.pixelFormat)
            .set(PROFILE, mediaInfo.profile)
            .set(AUDIO_CODEC, mediaInfo.audioCodec)
            .set(AUDIO_BITRATE_KBPS, mediaInfo.audioBitrateKbps)
            .set(SAMPLE_RATE, mediaInfo.sampleRate)
            .set(AUDIO_CHANNELS, mediaInfo.audioChannels)
            .set(FORMAT_NAME, mediaInfo.formatName)
            .set(FILE_SIZE_BYTES, mediaInfo.fileSizeBytes)
            .set(DSL.field("raw_json", String::class.java), mediaInfo.rawJson)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun findByVideoId(videoId: Long): VideoMediaInfo? =
        dsl.select()
            .from(VIDEO_MEDIA_INFO)
            .where(VIDEO_ID.eq(videoId))
            .fetchOne()
            ?.toVideoMediaInfo()

    private fun findById(id: Long): VideoMediaInfo? =
        dsl.select()
            .from(VIDEO_MEDIA_INFO)
            .where(ID.eq(id))
            .fetchOne()
            ?.toVideoMediaInfo()

    private fun Record.toVideoMediaInfo(): VideoMediaInfo = VideoMediaInfo(
        id = get(ID),
        videoId = get(VIDEO_ID),
        videoCodec = get(VIDEO_CODEC),
        width = get(WIDTH),
        height = get(HEIGHT),
        fps = get(FPS),
        bitrateKbps = get(BITRATE_KBPS),
        durationMs = get(DURATION_MS),
        colorSpace = get(COLOR_SPACE),
        pixelFormat = get(PIXEL_FORMAT),
        profile = get(PROFILE),
        audioCodec = get(AUDIO_CODEC),
        audioBitrateKbps = get(AUDIO_BITRATE_KBPS),
        sampleRate = get(SAMPLE_RATE),
        audioChannels = get(AUDIO_CHANNELS),
        formatName = get(FORMAT_NAME),
        fileSizeBytes = get(FILE_SIZE_BYTES),
        rawJson = get(RAW_JSON)?.toString(),
        createdAt = localDateTime(CREATED_AT),
    )
}

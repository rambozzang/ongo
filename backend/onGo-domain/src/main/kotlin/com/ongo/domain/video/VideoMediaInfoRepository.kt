package com.ongo.domain.video

import com.ongo.domain.video.entity.VideoMediaInfo

interface VideoMediaInfoRepository {
    fun save(mediaInfo: VideoMediaInfo): VideoMediaInfo
    fun findByVideoId(videoId: Long): VideoMediaInfo?
}

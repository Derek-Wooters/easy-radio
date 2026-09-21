package com.easyradio.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "episodes")
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val podcastId: String,
    val title: String,
    val audioUrl: String,
    val publishedAtEpochMillis: Long?,
    val durationSeconds: Int?,
    val description: String,
    val positionMs: Long = 0,
    val localFilePath: String? = null,
    val chaptersUrl: String? = null,
    val transcriptUrl: String? = null,
    val transcriptType: String? = null,
)

/**
 * A partial view of [EpisodeEntity] for [EpisodeDao.updateMetadata]. Room's
 * `@Update(entity = EpisodeEntity::class)` matches rows by [id] and writes only the columns
 * present here -- [EpisodeEntity.positionMs] and [EpisodeEntity.localFilePath] are deliberately
 * absent so re-syncing an episode's feed-sourced fields (e.g. when paginating to load more
 * episodes) can never reset a listener's saved playback progress or downloaded file, the way a
 * full-row REPLACE previously did.
 */
data class EpisodeMetadata(
    val id: String,
    val podcastId: String,
    val title: String,
    val audioUrl: String,
    val publishedAtEpochMillis: Long?,
    val durationSeconds: Int?,
    val description: String,
    val chaptersUrl: String?,
    val transcriptUrl: String?,
    val transcriptType: String?,
)

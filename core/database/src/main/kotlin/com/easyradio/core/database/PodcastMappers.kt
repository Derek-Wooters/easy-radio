package com.easyradio.core.database

import com.easyradio.core.model.Bookmark
import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import com.easyradio.core.model.SmartPlaylist
import com.easyradio.core.model.SmartPlaylistStatus

fun Podcast.toEntity(subscribedAtEpochMillis: Long): PodcastEntity = PodcastEntity(
    id = id,
    title = title,
    author = author,
    artworkUrl = artworkUrl,
    feedUrl = feedUrl,
    subscribedAtEpochMillis = subscribedAtEpochMillis,
    isPreset = isPreset,
    lastPlayedAtEpochMillis = lastPlayedAtEpochMillis,
    notifyNewEpisodes = notifyNewEpisodes,
    autoDownloadNewEpisodes = autoDownloadNewEpisodes,
    skipFirstSeconds = skipFirstSeconds,
    skipLastSeconds = skipLastSeconds,
    playbackSpeed = playbackSpeed,
)

fun PodcastEntity.toPodcast(): Podcast = Podcast(
    id = id,
    title = title,
    author = author,
    artworkUrl = artworkUrl,
    feedUrl = feedUrl,
    isPreset = isPreset,
    lastPlayedAtEpochMillis = lastPlayedAtEpochMillis,
    subscribedAtEpochMillis = subscribedAtEpochMillis,
    notifyNewEpisodes = notifyNewEpisodes,
    autoDownloadNewEpisodes = autoDownloadNewEpisodes,
    skipFirstSeconds = skipFirstSeconds,
    skipLastSeconds = skipLastSeconds,
    playbackSpeed = playbackSpeed,
)

fun Episode.toEntity(): EpisodeEntity = EpisodeEntity(
    id = id,
    podcastId = podcastId,
    title = title,
    audioUrl = audioUrl,
    publishedAtEpochMillis = publishedAtEpochMillis,
    durationSeconds = durationSeconds,
    description = description,
    positionMs = positionMs,
    localFilePath = localFilePath,
    chaptersUrl = chaptersUrl,
    transcriptUrl = transcriptUrl,
    transcriptType = transcriptType,
    isStarred = isStarred,
    season = season,
)

fun EpisodeEntity.toEpisode(): Episode = Episode(
    id = id,
    podcastId = podcastId,
    title = title,
    audioUrl = audioUrl,
    publishedAtEpochMillis = publishedAtEpochMillis,
    durationSeconds = durationSeconds,
    description = description,
    positionMs = positionMs,
    localFilePath = localFilePath,
    chaptersUrl = chaptersUrl,
    transcriptUrl = transcriptUrl,
    transcriptType = transcriptType,
    isStarred = isStarred,
    season = season,
)

fun BookmarkEntity.toBookmark(): Bookmark = Bookmark(
    id = id,
    episodeId = episodeId,
    positionMs = positionMs,
    note = note,
    createdAtEpochMillis = createdAtEpochMillis,
)

fun SmartPlaylist.toEntity(): SmartPlaylistEntity = SmartPlaylistEntity(
    id = id,
    name = name,
    podcastIds = podcastIds.joinToString(","),
    status = status.name,
    downloadedOnly = downloadedOnly,
    starredOnly = starredOnly,
    createdAtEpochMillis = createdAtEpochMillis,
)

fun SmartPlaylistEntity.toSmartPlaylist(): SmartPlaylist = SmartPlaylist(
    id = id,
    name = name,
    podcastIds = podcastIds.split(",").filter { it.isNotBlank() }.toSet(),
    status = runCatching { SmartPlaylistStatus.valueOf(status) }.getOrDefault(SmartPlaylistStatus.ANY),
    downloadedOnly = downloadedOnly,
    starredOnly = starredOnly,
    createdAtEpochMillis = createdAtEpochMillis,
)

/**
 * Feed-sourced fields only -- see [EpisodeMetadata] for why positionMs/localFilePath are
 * deliberately excluded.
 */
fun Episode.toMetadata(): EpisodeMetadata = EpisodeMetadata(
    id = id,
    podcastId = podcastId,
    title = title,
    audioUrl = audioUrl,
    publishedAtEpochMillis = publishedAtEpochMillis,
    durationSeconds = durationSeconds,
    description = description,
    chaptersUrl = chaptersUrl,
    transcriptUrl = transcriptUrl,
    transcriptType = transcriptType,
    season = season,
)

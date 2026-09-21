package com.easyradio.core.database

import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast

fun Podcast.toEntity(subscribedAtEpochMillis: Long): PodcastEntity = PodcastEntity(
    id = id,
    title = title,
    author = author,
    artworkUrl = artworkUrl,
    feedUrl = feedUrl,
    subscribedAtEpochMillis = subscribedAtEpochMillis,
    isPreset = isPreset,
    lastPlayedAtEpochMillis = lastPlayedAtEpochMillis,
)

fun PodcastEntity.toPodcast(): Podcast = Podcast(
    id = id,
    title = title,
    author = author,
    artworkUrl = artworkUrl,
    feedUrl = feedUrl,
    isPreset = isPreset,
    lastPlayedAtEpochMillis = lastPlayedAtEpochMillis,
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
)

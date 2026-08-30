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
)

fun PodcastEntity.toPodcast(): Podcast = Podcast(
    id = id,
    title = title,
    author = author,
    artworkUrl = artworkUrl,
    feedUrl = feedUrl,
    isPreset = isPreset,
)

fun Episode.toEntity(): EpisodeEntity = EpisodeEntity(
    id = id,
    podcastId = podcastId,
    title = title,
    audioUrl = audioUrl,
    publishedAtEpochMillis = publishedAtEpochMillis,
    durationSeconds = durationSeconds,
    description = description,
    localFilePath = localFilePath,
)

fun EpisodeEntity.toEpisode(): Episode = Episode(
    id = id,
    podcastId = podcastId,
    title = title,
    audioUrl = audioUrl,
    publishedAtEpochMillis = publishedAtEpochMillis,
    durationSeconds = durationSeconds,
    description = description,
    localFilePath = localFilePath,
)

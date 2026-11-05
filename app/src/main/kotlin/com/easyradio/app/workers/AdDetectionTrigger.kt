package com.easyradio.app.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import java.util.concurrent.TimeUnit

/**
 * Enqueues [AdDetectionWorker] right after a successful episode download, but only when it
 * could possibly do anything useful: the podcast has opted in (Options tab), and the episode's
 * feed actually provides an official transcript to diff against.
 */
object AdDetectionTrigger {

    fun scheduleIfNeeded(context: Context, podcast: Podcast, episode: Episode) {
        if (!podcast.adSkipEnabled || episode.transcriptUrl == null) return

        val request = OneTimeWorkRequestBuilder<AdDetectionWorker>()
            .setInputData(Data.Builder().putString(AdDetectionWorker.KEY_EPISODE_ID, episode.id).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            // Transcribing a full episode takes well over WorkManager's default execution
            // budget, so a Result.retry() (see AdDetectionBusyException) is expected and
            // routine here, not exceptional -- WorkManager's 30s default initial backoff would
            // otherwise hammer retries throughout that whole hour-plus run.
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "ad_detection_${episode.id}",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}

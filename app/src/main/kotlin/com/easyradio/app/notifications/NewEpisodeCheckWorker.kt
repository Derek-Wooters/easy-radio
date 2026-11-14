package com.easyradio.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.easyradio.app.EasyRadioGraph
import com.easyradio.app.NetworkUtils
import kotlinx.coroutines.flow.first

/**
 * Periodically checks every subscribed podcast for episodes published since the last
 * check, notifying and/or auto-downloading per that podcast's own options (both default
 * off -- see [com.easyradio.core.model.Podcast]). Registered as unique periodic work from
 * [com.easyradio.app.MainActivity] so it runs even when the app isn't open.
 */
class NewEpisodeCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = EasyRadioGraph.repository(applicationContext)
        val settings = EasyRadioGraph.settings(applicationContext).settings.first()
        val subscribed = repository.subscribedPodcasts().first()

        for (podcast in subscribed) {
            val newEpisodes = repository.checkForNewEpisodes(podcast)
            if (newEpisodes.isEmpty()) continue

            if (podcast.autoDownloadNewEpisodes && (!settings.downloadOverWifiOnly || NetworkUtils.isOnWifi(applicationContext))) {
                newEpisodes.forEach { repository.downloadEpisode(it) }
            }

            if (podcast.notifyNewEpisodes) {
                NewEpisodeNotifier.notify(applicationContext, podcast, newEpisodes)
            }
        }

        return Result.success()
    }
}

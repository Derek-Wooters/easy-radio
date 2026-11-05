package com.easyradio.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.easyradio.app.EasyRadioGraph
import com.easyradio.core.transcription.AdDetectionBusyException

/**
 * Runs transcript look-ahead ad detection for one downloaded episode: transcribes the real
 * audio on-device with whisper.cpp and diffs it against the show's official transcript (see
 * com.easyradio.core.transcription.AdDetectionPipeline). Enqueued by [AdDetectionTrigger] right
 * after a successful download, one-time (not periodic) -- this repo's first OneTimeWorkRequest.
 */
class AdDetectionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_EPISODE_ID = "episode_id"
    }

    override suspend fun doWork(): Result {
        val episodeId = inputData.getString(KEY_EPISODE_ID) ?: return Result.failure()
        val repository = EasyRadioGraph.repository(applicationContext)

        val episode = repository.episodeById(episodeId) ?: return Result.failure()
        val localFilePath = episode.localFilePath ?: return Result.failure()
        if (episode.transcriptUrl == null) return Result.failure()

        // A prior attempt may have already finished while this one was waiting on the
        // single-flight guard below (see AdDetectionBusyException) -- don't redo an hour-long
        // transcription that already landed its results.
        if (repository.adDetectionState(episodeId) == "DONE") return Result.success()

        val officialTranscript = repository.loadTranscript(episode) ?: return Result.success()

        val pipeline = EasyRadioGraph.adDetectionPipeline(applicationContext)
        return try {
            val segments = pipeline.detect(localFilePath, officialTranscript)
            repository.recordAdSegments(episode.id, segments)
            Result.success()
        } catch (e: AdDetectionBusyException) {
            // Another transcription (e.g. the attempt WorkManager is retrying this one over)
            // is still running the one-at-a-time native pipeline -- see AdDetectionBusyException's
            // doc for why starting a second one concurrently isn't safe. Back off and let
            // WorkManager retry once the in-flight one has finished.
            Result.retry()
        }
    }
}

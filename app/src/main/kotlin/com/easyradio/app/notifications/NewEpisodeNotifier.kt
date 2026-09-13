package com.easyradio.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.easyradio.app.MainActivity
import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast

private const val CHANNEL_ID = "new_episodes"

/** Posts a notification when [NewEpisodeCheckWorker] finds new episodes for a subscribed podcast. */
object NewEpisodeNotifier {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "New episodes",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "New episodes from podcasts you follow" }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun notify(context: Context, podcast: Podcast, newEpisodes: List<Episode>) {
        if (newEpisodes.isEmpty()) return
        ensureChannel(context)

        val text = if (newEpisodes.size == 1) {
            newEpisodes.first().title
        } else {
            "${newEpisodes.size} new episodes"
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            podcast.id.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(podcast.title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(podcast.id.hashCode(), notification)
        }
    }
}

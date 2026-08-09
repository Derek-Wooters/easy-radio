package com.easyradio.app.playback

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Serves station/podcast artwork to Android Auto and Android Automotive OS.
 *
 * Auto/AAOS only render browse-item artwork from a local content:// or
 * android.resource:// uri -- remote http(s) uris resolve to a null bitmap and
 * fall back to a placeholder. This provider bridges the gap: the browse tree
 * hands Auto a content:// uri (see [uriFor]) wrapping the real web url, and on
 * first request the image is downloaded, cached, and streamed back as a local
 * file. Mirrors the UAMP AlbumArtContentProvider approach.
 */
class ArtworkContentProvider : ContentProvider() {

    private val client = OkHttpClient()

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val webUrl = uri.getQueryParameter(PARAM_URL) ?: return null
        val context = context ?: return null

        val cacheDir = File(context.cacheDir, "auto_artwork").apply { mkdirs() }
        val cached = File(cacheDir, "${webUrl.hashCode()}.img")

        if (!cached.exists() && !download(webUrl, cached)) return null

        return ParcelFileDescriptor.open(cached, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    // openFile is invoked on a binder thread, so a blocking fetch is fine here.
    private fun download(webUrl: String, destination: File): Boolean = try {
        client.newCall(Request.Builder().url(webUrl).build()).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful) {
                false
            } else {
                body.byteStream().use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
                true
            }
        }
    } catch (e: Exception) {
        destination.delete()
        false
    }

    override fun getType(uri: Uri): String = "image/*"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private const val AUTHORITY = "com.easyradio.app.artwork"
        private const val PARAM_URL = "url"

        /** Wraps a remote artwork [webUrl] in a local content:// uri Auto can render. */
        fun uriFor(webUrl: String): Uri = Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .appendPath("art")
            .appendQueryParameter(PARAM_URL, webUrl)
            .build()
    }
}

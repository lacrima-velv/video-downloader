package com.example.videodownloader.videoutil

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.net.toUri
import com.example.videodownloader.Util.getFileNameFromUri
import com.example.videodownloader.Util.prettyBytes
import com.example.videodownloader.data.Video
import com.example.videodownloader.service.VideoDownloadService
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.database.DatabaseProvider
import com.google.android.exoplayer2.offline.*
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * This class contains methods to control video downloading and playing
 */
class VideoUtil(val context: Context): KoinComponent {

    private val videoDatabaseProvider: DatabaseProvider by inject()

    private val dataSourceFactory = DefaultHttpDataSource.Factory()

    /*
    As my app works with media files that provide value to the user only within my app,
    it's best to store them in app-specific directories within external storage
     */
    private fun getDownloadContentDirectory(): File {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "exo_videos")
        Timber.d("Directory created")
        if (!file.mkdirs()) {
            Timber.d("Directory not created")
        }
        return file
    }

    private val downloadCache: Cache =
        SimpleCache(getDownloadContentDirectory(), NoOpCacheEvictor(), videoDatabaseProvider)

    private val downloadExecutor = Runnable::run

    val downloadManager: DownloadManager =
        DownloadManager(
            context, videoDatabaseProvider, downloadCache, dataSourceFactory, downloadExecutor
        )

    /*
    To play downloaded content we need to create CacheDataSource.Factory using the same cache
    instance that was used for downloading. It will be injected into
    DefaultMediaSourceFactory, when building the player.
    */
    private val cacheDataSourceFactory = CacheDataSource.Factory()
        .setCache(downloadCache)
        .setUpstreamDataSourceFactory(dataSourceFactory)
        .setCacheWriteDataSinkFactory(null) // Disable writing

    private fun getMediaSource(videoUri: Uri): ProgressiveMediaSource {
        return ProgressiveMediaSource.Factory(cacheDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(videoUri))
    }

    // An instance of exoPlayer is created in ViewModel's init block and got released in onCleared()
    fun createExoPlayer(): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()
    }

    // Player is prepared in Activity's onCreate()
    fun preparePlayer(exoPlayer: ExoPlayer) {
        exoPlayer.apply {
            prepare()
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    fun checkIsAnythingDownloaded(): Boolean {
        var downloadedBytes = 0L
        val downloadCursor = downloadManager.downloadIndex.getDownloads()
        while (downloadCursor.moveToNext()) {
            downloadedBytes = downloadCursor.download.bytesDownloaded
        }
        return downloadedBytes > 0L
    }

    fun checkHaveCompletelyDownloadedVideo(): Boolean {
        val downloadCursor = downloadManager.downloadIndex.getDownloads(Download.STATE_COMPLETED)
        Timber.d("checkHaveCompletelyDownloadedVideo() returned ${downloadCursor.moveToFirst()}")
        return downloadCursor.moveToFirst()
    }

    fun checkHaveFailedDownloadedVideo(): Boolean {
        var failedFiledSize = 0L
        val downloadCursor = downloadManager.downloadIndex.getDownloads(Download.STATE_FAILED)
        while (downloadCursor.moveToNext()) {
            failedFiledSize = downloadCursor.download.bytesDownloaded
        }
        return (downloadCursor.moveToFirst() && failedFiledSize != 0L)
    }

    // Used to get Download state of the video in Video's db to display the correct UI
    fun getDownloadState(): Int? {
        var state: Int? = null

        downloadManager.downloadIndex.apply {
            if (getDownloads(Download.STATE_QUEUED).moveToFirst()) { state = Download.STATE_QUEUED }
            if (getDownloads(Download.STATE_STOPPED).moveToFirst()) { state = Download.STATE_STOPPED }
            if (getDownloads(Download.STATE_DOWNLOADING).moveToFirst()) { state = Download.STATE_DOWNLOADING }
            if (getDownloads(Download.STATE_COMPLETED).moveToFirst()) { state = Download.STATE_COMPLETED }
            if (getDownloads(Download.STATE_FAILED).moveToFirst()) { state = Download.STATE_FAILED }
            if (getDownloads(Download.STATE_REMOVING).moveToFirst()) { state = Download.STATE_REMOVING }
            if (getDownloads(Download.STATE_RESTARTING).moveToFirst()) { state = Download.STATE_RESTARTING }
        }

        return state
    }

    // Video is played just after downloading
    fun playDownloadedVideo(
        exoPlayer: ExoPlayer, isPlayerPlaying: Boolean = false, playbackPosition: Long = 0
    ) {
        Timber.d("playDownloadedVideo() is called. " +
                "Downloaded video uri is ${getCompletelyDownloadedItems().last().uri}")
        if (exoPlayer.playbackState == Player.STATE_IDLE) {
            exoPlayer.prepare()
            Timber.d("exoPlayer.playbackState is ${exoPlayer.playbackState}")
        }
        val downloadedVideo = getCompletelyDownloadedItems().last().uri
        exoPlayer.apply {
            setMediaSource(getMediaSource(downloadedVideo))
            playWhenReady = isPlayerPlaying
            seekTo(playbackPosition)
            play()
        }
    }

    fun downloadFailedVideo(context: Context) {
        // Search for videos with state Failed
        val failedDownloadCursor = downloadManager.downloadIndex.getDownloads(Download.STATE_FAILED)
        var failedUri = "".toUri()
        while (failedDownloadCursor.moveToNext()) {
            failedUri = failedDownloadCursor.download.request.uri
        }
        Timber.d("failedUri is $failedUri")

        val helper = DownloadHelper.forMediaItem(context, getMediaSource(failedUri).mediaItem)

        helper.prepare(object : DownloadHelper.Callback {
            override fun onPrepared(helper: DownloadHelper) {
                Timber.d("helper onPrepared is called")
                val json = JSONObject()
                // Set the last part of the uri as a title
                json.put("title", getFileNameFromUri(failedUri))
                val download = helper.getDownloadRequest(
                    failedUri.toString(), Util.getUtf8Bytes(json.toString())
                )
                DownloadService.sendAddDownload(
                    context, VideoDownloadService::class.java, download, false
                )
            }
            // In most cases it won't be displayed
            override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                e.printStackTrace()
                Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }

        })

        if (failedDownloadCursor.moveToFirst()) {
            while (failedDownloadCursor.moveToNext()) {
                val download = failedDownloadCursor.download
                val request = download.request
                DownloadService.sendAddDownload(
                    context, VideoDownloadService::class.java, request, false
                )
            }
        }
    }

    fun downloadVideo(context: Context, videoUri: Uri) {
        Timber.d("Started non-failed download")
        val helper = DownloadHelper.forMediaItem(context, getMediaSource(videoUri).mediaItem)
        helper.prepare(object : DownloadHelper.Callback {
            override fun onPrepared(helper: DownloadHelper) {
                Timber.d("helper onPrepared is called")
                val json = JSONObject()
                // Set last part of the uri as a title
                json.put("title", getFileNameFromUri(videoUri))
                val download = helper.getDownloadRequest(videoUri.toString(), Util.getUtf8Bytes(json.toString()))
                /*
                Send the request to the download service.
                As my app is already in the foreground then the foreground parameter should
                normally be set to false
                 */
                DownloadService.sendAddDownload(
                    context, VideoDownloadService::class.java, download, false
                )
            }
            // In most cases it won't be displayed
            override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                e.printStackTrace()
                Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        })
    }


    fun isPlayerPlaying(exoPlayer: ExoPlayer): Boolean {
        return exoPlayer.isPlaying
    }

    fun getPlaybackPosition(exoPlayer: ExoPlayer): Long {
        return exoPlayer.currentPosition
    }

    // Player is released in ViewModel's onCleared()
    fun releasePlayer(exoPlayer: ExoPlayer) {
        exoPlayer.release()
    }

    fun pauseVideo(exoPlayer: ExoPlayer) {
        exoPlayer.playWhenReady = false
    }

    // Used to play only completely downloaded video
    fun getCompletelyDownloadedItems(): ArrayList<Video> {
        val downloadedVideos = ArrayList<Video>()
        val downloadCursor = downloadManager.downloadIndex.getDownloads(Download.STATE_COMPLETED)
        if (downloadCursor.moveToFirst()) {
            do {
                val jsonString = Util.fromUtf8Bytes(downloadCursor.download.request.data)
                val jsonObject = JSONObject(jsonString)
                val uri = downloadCursor.download.request.uri

                downloadedVideos.add(
                    Video(
                        uri = uri,
                        title = jsonObject.getString("title")
                    )
                )
            } while (downloadCursor.moveToNext())
        }
        Timber.d("getDownloadedItems() returned $downloadedVideos")
        return downloadedVideos
    }

    // Used to display an error if the File's size is too big
    fun getFileSizeDuringDownload(): Long? {
        return downloadManager.currentDownloads.lastOrNull()?.contentLength
    }

    // Used to display downloading progress
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getCurrentProgressDownload(videoUri: Uri): Flow<String> {
        var bytesDownloaded = 0L
        var fileSize = 0L

        Timber.d("videoUri is $videoUri. File size is $fileSize bytes. Bytes downloaded $bytesDownloaded")
        return flow {
            do {
                bytesDownloaded = downloadManager.currentDownloads.lastOrNull()?.bytesDownloaded ?: 0L
                fileSize = downloadManager.currentDownloads.lastOrNull()?.contentLength ?: 0L
                // If file size is -1, it's value is still not computed
                if (fileSize == -1) {
                    emit("Downloaded: ${prettyBytes(bytesDownloaded)}")
                } else {
                    emit("Downloaded: ${prettyBytes(bytesDownloaded)} of ${prettyBytes(fileSize)}")
                }
                delay(500)
            } while (bytesDownloaded != fileSize)
        }
    }

}
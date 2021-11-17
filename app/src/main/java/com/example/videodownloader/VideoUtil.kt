package com.example.videodownloader

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.offline.Download
import com.google.android.exoplayer2.offline.DownloadCursor
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.offline.DownloadService
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.DownloadNotificationHelper
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import timber.log.Timber
import java.io.File

const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"
const val DOWNLOAD_CONTENT_DIRECTORY = "downloads"

class VideoUtil(val context: Context) {

    private val dataSourceFactory = DefaultHttpDataSource.Factory()
    private val videoDatabaseProvider = VideoDatabaseProvider.getInstance(context).videoDatabaseProvider

    /*
    As my app works with media files that provide value to the user only within my app,
    it's best to store them in app-specific directories within external storage
     */
    fun getDownloadContentDirectory(): File {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "exo_videos")
        Timber.d("Directory created")
        if (!file.mkdirs()) {
            Timber.d("Directory not created")
        }
        return file
    }

    fun getDownloadNotificationHelper(): DownloadNotificationHelper {
        return DownloadNotificationHelper(context, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
    }

//    private val downloadContentDirectory: File =
//        File(context.getExternalFilesDir(null), "Video Downloader")
        //File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "videos")
    private val downloadCache: Cache =
        SimpleCache(getDownloadContentDirectory(), NoOpCacheEvictor(), videoDatabaseProvider)
    // TODO: Probably you should modify Executor
    private val downloadExecutor = Runnable::run

    //fun getVideoDownloadManager(context: Context) = DownloadManager(context, videoDatabaseProvider, downloadCache, dataSourceFactory, downloadExecutor)

    val downloadManager: DownloadManager =
        DownloadManager(context, videoDatabaseProvider, downloadCache, dataSourceFactory, downloadExecutor)

    /*
    To play downloaded content we need to create CacheDataSource.Factory using the same cache
    instance that was used for downloading. It will be injected into
    DefaultMediaSourceFactory, when building the player.
    */
    private val cacheDataSourceFactory = CacheDataSource.Factory()
        .setCache(downloadCache)
        .setUpstreamDataSourceFactory(dataSourceFactory)
        .setCacheWriteDataSinkFactory(null) // Disable writing



    fun getMediaSource(videoUri: Uri): ProgressiveMediaSource {
        return ProgressiveMediaSource.Factory(cacheDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(videoUri))
    }

    fun createExoPlayer(): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()
    }

    fun preparePlayer(exoPlayer: ExoPlayer) {
       // val downloadedVideo = getDownloadedItems().last().uri
        exoPlayer.apply {
            //setMediaSource(getMediaSource(downloadedVideo))
            prepare()
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    fun isPlayerPrepared(exoPlayer: ExoPlayer): Boolean {
        return exoPlayer.playbackState == Player.STATE_IDLE
    }

    fun playDownloadedVideo(exoPlayer: ExoPlayer, isPlayerPlaying: Boolean = false, playbackPosition: Long = 0) {
        Timber.d("playDownloadedVideo() is called. Downloaded video uri is ${getDownloadedItems().last().uri}")
        val downloadedVideo = getDownloadedItems().last().uri
        exoPlayer.apply {
            setMediaSource(getMediaSource(downloadedVideo))
            playWhenReady = isPlayerPlaying
            seekTo(playbackPosition)
            play()
        }
    }

    fun continueDownloadFailedVideo(context: Context, videoUri: Uri) {
        DownloadService.sendSetStopReason(context, VideoDownloadService::class.java, videoUri.toString(), Download.STOP_REASON_NONE, false)
    }

    fun isPlayerPlaying(exoPlayer: ExoPlayer): Boolean {
        return exoPlayer.isPlaying
    }

    fun getPlaybackPosition(exoPlayer: ExoPlayer): Long {
       return exoPlayer.currentPosition
    }

    // Add logging to Player
    fun addAnalyticsListenerToPlayer(exoPlayer: ExoPlayer) {
        exoPlayer.addAnalyticsListener(EventLogger(null))
    }

    fun releasePlayer(exoPlayer: ExoPlayer) {
        exoPlayer.release()
    }

    fun pauseVideo(exoPlayer: ExoPlayer) {
        exoPlayer.pause()
    }

    fun getDownloadedItems(): ArrayList<Video> {
        val downloadedVideos = ArrayList<Video>()
        val downloadCursor: DownloadCursor = downloadManager.downloadIndex.getDownloads()
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


    fun checkIfSomethingIsDownloaded(): Boolean {
        return downloadManager.downloadIndex.getDownloads().count > 0 && downloadManager.currentDownloads.isEmpty()
    }

    fun checkIfSomethingIsDownloading(): Boolean {
        return downloadManager.currentDownloads.isNotEmpty()
    }

    fun checkIfVideoDownloaded(videoUri: Uri): Boolean {
        return downloadManager.downloadIndex.getDownload(videoUri.toString()) != null
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getCurrentProgressDownload(videoUri: Uri): Flow<Long?> {
        var bytesDownloaded: Long? = downloadManager.currentDownloads.find { it.request.uri == videoUri}?.bytesDownloaded
        val fileSize: Long? = downloadManager.currentDownloads.find { it.request.uri == videoUri}?.contentLength
        Timber.d("File size is $fileSize bytes. Bytes downloaded $bytesDownloaded")
        return flow {
            do {
                bytesDownloaded = downloadManager.currentDownloads.find { it.request.uri == videoUri}?.bytesDownloaded
                emit(bytesDownloaded)
                delay(500)
            } while (bytesDownloaded != fileSize)
        }
    }

}
package com.example.videodownloader

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.net.toUri
import com.example.videodownloader.Util.bytesToKilobytes
import com.example.videodownloader.Util.bytesToMegabytes
import com.example.videodownloader.Util.prettyBytes
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.offline.*
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
import java.io.IOException
import kotlin.math.pow

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

    fun checkHaveCompletelyDownloadedVideo() : Boolean {
        val downloadCursor = downloadManager.downloadIndex.getDownloads(Download.STATE_COMPLETED)
        Timber.d("checkHaveCompletelyDownloadedVideo() returned ${downloadCursor.moveToFirst()}")
        return downloadCursor.moveToFirst()
    }

    fun checkHaveFailedDownloadedVideo() : Boolean {
        var failedFiledSize = 0L
        val downloadCursor = downloadManager.downloadIndex.getDownloads(Download.STATE_FAILED)
        while (downloadCursor.moveToNext()) {
            failedFiledSize = downloadCursor.download.bytesDownloaded
        }
        return (downloadCursor.moveToFirst() && failedFiledSize != 0L)
    }

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

    fun playDownloadedVideo(exoPlayer: ExoPlayer, isPlayerPlaying: Boolean = false, playbackPosition: Long = 0) {
        Timber.d("playDownloadedVideo() is called. Downloaded video uri is ${getCompletelyDownloadedItems().last().uri}")
        val downloadedVideo = getCompletelyDownloadedItems().last().uri
        exoPlayer.apply {
            setMediaSource(getMediaSource(downloadedVideo))
            playWhenReady = isPlayerPlaying
            seekTo(playbackPosition)
            play()
        }
    }


    fun downloadFailedVideo2(context: Context) {
        // TODO: Check if it really continue download after failing! https://github.com/google/ExoPlayer/issues/6755
        val failedDownloadCursor = downloadManager.downloadIndex.getDownloads(Download.STATE_FAILED)
        val failedUri = failedDownloadCursor.download.request.uri

            if (failedDownloadCursor.moveToFirst()) {
                while (failedDownloadCursor.moveToNext()) {
                    val download = failedDownloadCursor.download
                    val request = download.request
                    DownloadService.sendAddDownload(context, VideoDownloadService::class.java, request, false)
                }
            }
    }

    fun downloadFailedVideo(context: Context) {
        Timber.d("failedDownloadCursor is ${downloadManager.downloadIndex.getDownloads(Download.STATE_FAILED)}")
        val failedDownloadCursor = downloadManager.downloadIndex.getDownloads(Download.STATE_FAILED)
        var failedUri = "".toUri()
        while (failedDownloadCursor.moveToNext()) {
            failedUri = failedDownloadCursor.download.request.uri
        }

        //val failedUri = failedDownloadCursor.download.request.uri
        Timber.d("failedUri is $failedUri")
        val helper = DownloadHelper.forMediaItem(context, getMediaSource(failedUri).mediaItem)

        helper.prepare(object : DownloadHelper.Callback {
            override fun onPrepared(helper: DownloadHelper) {
                Timber.d("helper onPrepared is called")
                val json = JSONObject()
                // TODO: Set the last part of the uri as a title
                json.put("title", "some title")
                val download = helper.getDownloadRequest(failedUri.toString(), Util.getUtf8Bytes(json.toString()))
                //sending the request to the download service
                // If your app is already in the foreground then the foreground parameter should normally be set to false
                DownloadService.sendAddDownload(context, VideoDownloadService::class.java, download, false)
                //DownloadService.sendSetStopReason(context, VideoDownloadService::class.java, videoUri.toString(), 1, false)
            }
            // TODO: Maybe delete this?
            override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                e.printStackTrace()
                Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }

        })

        if (failedDownloadCursor.moveToFirst()) {
            while (failedDownloadCursor.moveToNext()) {
                val download = failedDownloadCursor.download
                val request = download.request
                DownloadService.sendAddDownload(context, VideoDownloadService::class.java, request, false)
            }
        }
    }

    fun downloadVideo(context: Context, videoUri: Uri) {
        // TODO: Check if it really continue download after failing! https://github.com/google/ExoPlayer/issues/6755
//        Timber.d("Check if have failed download")
//        val failedDownloadCursor = downloadManager.downloadIndex.getDownloads(Download.STATE_FAILED)
//        if (failedDownloadCursor.moveToFirst()) {
//            while (failedDownloadCursor.moveToNext()) {
//                val download = failedDownloadCursor.download
//                val request = download.request
//                DownloadService.sendAddDownload(context, VideoDownloadService::class.java, request, false)
//            }
       // } else {
            Timber.d("Started non-failed download")
            val helper = DownloadHelper.forMediaItem(context, getMediaSource(videoUri).mediaItem)
            helper.prepare(object : DownloadHelper.Callback {
                override fun onPrepared(helper: DownloadHelper) {
                    Timber.d("helper onPrepared is called")
                    val json = JSONObject()
                    // TODO: Set the last part of the uri as a title
                    json.put("title", "some title")
                    val download = helper.getDownloadRequest(videoUri.toString(), Util.getUtf8Bytes(json.toString()))
                    //sending the request to the download service
                    // If your app is already in the foreground then the foreground parameter should normally be set to false
                    DownloadService.sendAddDownload(context, VideoDownloadService::class.java, download, false)
                    //DownloadService.sendSetStopReason(context, VideoDownloadService::class.java, videoUri.toString(), 1, false)
                }
                // TODO: Maybe delete this?
                override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
                }

            })
        //}
    }

//    fun tryContinueDownloadFailedVideo(context: Context, videoUri: Uri) {
//        Timber.d("continueDownloadFailedVideo() is called")
//        val failedDownloadCursor = downloadManager.downloadIndex.getDownloads(Download.STATE_FAILED)
//        if (failedDownloadCursor.moveToFirst()) {
//            while (failedDownloadCursor.moveToNext()) {
//                val download = failedDownloadCursor.download
//                val request = download.request
//                DownloadService.sendAddDownload(context, VideoDownloadService::class.java, request, false)
//            }
//        }
////        else {
////            downloadVideo(context, videoUri)
////        }
//        //DownloadService.sendSetStopReason(context, VideoDownloadService::class.java, videoUri.toString(), Download.STOP_REASON_NONE, false)
//    }

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

    fun getCompletelyDownloadedItems(): ArrayList<Video> {
        val downloadedVideos = ArrayList<Video>()
        val downloadCursor: DownloadCursor = downloadManager.downloadIndex.getDownloads(Download.STATE_COMPLETED)
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


//    fun checkIfSomethingIsDownloaded(): Boolean {
//        return downloadManager.downloadIndex.getDownloads().count > 0 && downloadManager.currentDownloads.isEmpty()
//    }
//
//    fun checkIfSomethingIsDownloading(): Boolean {
//        return downloadManager.currentDownloads.isNotEmpty()
//    }

    fun checkIfVideoDownloaded(videoUri: Uri): Boolean {
        return downloadManager.downloadIndex.getDownload(videoUri.toString()) != null
    }



    @OptIn(ExperimentalCoroutinesApi::class)
    fun getCurrentProgressDownload2(videoUri: Uri): Flow<Long?> {
        //var bytesDownloaded: Long? = downloadManager.currentDownloads.find { it.request.uri == videoUri}?.bytesDownloaded
        var bytesDownloaded: Long? = downloadManager.currentDownloads.lastOrNull()?.bytesDownloaded
       // val fileSize: Long? = downloadManager.currentDownloads.find { it.request.uri == videoUri}?.contentLength
        val fileSize: Long? = downloadManager.currentDownloads.lastOrNull()?.contentLength
        Timber.d("videoUri is $videoUri. File size is $fileSize bytes. Bytes downloaded $bytesDownloaded")
        return flow {
            do {
                //bytesDownloaded = downloadManager.currentDownloads.find { it.request.uri == videoUri}?.bytesDownloaded
                    // TODO: Show "Statring download..." instead of 0
                bytesDownloaded = downloadManager.currentDownloads.lastOrNull()?.bytesDownloaded
                emit(bytesDownloaded)
                delay(500)
            } while (bytesDownloaded != fileSize)
        }
    }

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
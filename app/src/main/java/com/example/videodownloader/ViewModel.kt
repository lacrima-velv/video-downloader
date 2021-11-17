package com.example.videodownloader

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.offline.*
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.IOException
import java.lang.Exception

class ViewModel(application: Application): AndroidViewModel(application), KoinComponent {

    private val videoUtil: VideoUtil by inject()
    private var initialDownloadState: DownloadState = DownloadState.NotStarted
    lateinit var exoPlayer: ExoPlayer



//    private val _bytesDownloaded = MutableStateFlow<Float?>(0f)
//    val bytesDownloaded: StateFlow<Float?>
//        get() = _bytesDownloaded

    fun getDownloadedBytes(videoUri: Uri): Flow<Long?> {
        return videoUtil.getCurrentProgressDownload(videoUri)
    }



    private val _downloadState = MutableStateFlow(initialDownloadState)
    val downloadState: StateFlow<DownloadState>
        get() = _downloadState

    sealed class DownloadState {
        object NotStarted : DownloadState()
        object SomethingIsDownloaded : DownloadState()
        object SomethingIsDownloading : DownloadState()
        object Queued : DownloadState()
        object Downloading : DownloadState()
        object Restarting : DownloadState()
        object Stopped : DownloadState()
        object Failed : DownloadState()
        object Completed : DownloadState()
        object Removing : DownloadState()
    }

    private val _isFullscreenOn = MutableStateFlow(false)
    val isFullscreenOn: StateFlow<Boolean>
        get() = _isFullscreenOn

    fun setIsFullscreenOn(isFullscreen: Boolean) {
        _isFullscreenOn.value = isFullscreen
    }

    fun isPlayerPlaying(): Boolean {
        Timber.d("isPlayerPlaying() returned ${videoUtil.isPlayerPlaying(exoPlayer)}")
        return videoUtil.isPlayerPlaying(exoPlayer)
    }

    fun getPlaybackPosition(): Long {
        Timber.d("getPlaybackPosition() returned ${videoUtil.getPlaybackPosition(exoPlayer)}")
        return videoUtil.getPlaybackPosition(exoPlayer)
    }

    fun pausePlayer() {
        videoUtil.pauseVideo(exoPlayer)
    }

    fun preparePlayer() {
        videoUtil.preparePlayer(exoPlayer)
    }

    fun playDownloadedVideo(isPlayerPlaying: Boolean = false, playbackPosition: Long = 0) {
        Timber.d("playDownloadedVideo() is called. isPlayerPlaying is $isPlayerPlaying. exoPlayer error ${exoPlayer.playerError}")
        videoUtil.playDownloadedVideo(exoPlayer, isPlayerPlaying, playbackPosition)
    }

    private val downloadManagerListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            super.onDownloadChanged(downloadManager, download, finalException)
            when (download.state) {
                Download.STATE_QUEUED -> { _downloadState.value = DownloadState.Queued }
                Download.STATE_DOWNLOADING -> {
                    _downloadState.value = DownloadState.Downloading
                }
                Download.STATE_RESTARTING -> { _downloadState.value = DownloadState.Restarting }
                Download.STATE_STOPPED -> { _downloadState.value = DownloadState.Stopped }
                Download.STATE_FAILED -> { _downloadState.value = DownloadState.Failed }
                Download.STATE_COMPLETED -> {
                    _downloadState.value = DownloadState.Completed
                    playDownloadedVideo()
                }
                Download.STATE_REMOVING -> { _downloadState.value = DownloadState.Removing }
            }
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            super.onDownloadRemoved(downloadManager, download)
            Timber.d("onDownloadRemoved")
        }

        override fun onInitialized(downloadManager: DownloadManager) {
            super.onInitialized(downloadManager)
            Timber.d("onInitialized")
        }

        override fun onDownloadsPausedChanged(
            downloadManager: DownloadManager,
            downloadsPaused: Boolean
        ) {
            super.onDownloadsPausedChanged(downloadManager, downloadsPaused)
            if (downloadsPaused) {
                Timber.d("DownloadsPaused")
            } else  {
                Timber.d("DownloadsResumed")
            }
        }
    }

    fun downloadVideo(context: Context, videoUri: Uri) {

                Timber.d("Started download")
                val helper = DownloadHelper.forMediaItem(context, videoUtil.getMediaSource(videoUri).mediaItem)
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


    }

    fun setStopReasonDuringDownloading(context: Context, videoUri: Uri) {
        Timber.d("setStopReasonDuringDownloading() is called. videoUri is $videoUri")
        DownloadService.sendSetStopReason(context, VideoDownloadService::class.java, videoUri.toString(), 1, false)
    }

    fun continueDownloadFailedVideo(context: Context, videoUri: Uri) {
        Timber.d("continueDownloadFailedVideo() is called. videoUri is $videoUri Last downloaded item is ${videoUtil.getDownloadedItems()}")
        videoUtil.continueDownloadFailedVideo(context, videoUri)
    }


//    fun getDownloadedItems(): ArrayList<Video> {
//        val downloadedVideos = ArrayList<Video>()
//        val downloadCursor: DownloadCursor = videoUtil.downloadManager.downloadIndex.getDownloads()
//        if (downloadCursor.moveToFirst()) {
//            do {
//                val jsonString = Util.fromUtf8Bytes(downloadCursor.download.request.data)
//                val jsonObject = JSONObject(jsonString)
//                val uri = downloadCursor.download.request.uri
//
//                downloadedVideos.add(
//                    Video(
//                        url = uri.toString(),
//                        title = jsonObject.getString("title")
//                    )
//                )
//            } while (downloadCursor.moveToNext())
//        }
//        Timber.d("getDownloadedItems() returned $downloadedVideos")
//        return downloadedVideos
//    }

//    fun playVideo(videoUri: Uri) {
//        val mediaSource = videoUtil.getMediaSource(videoUri)
//        videoUtil.exoPlayer.setMediaSource(mediaSource)
//        videoUtil.exoPlayer.prepare()
//    }

    fun clearAllDownloadedVideos(context: Context) {
        DownloadService.sendRemoveAllDownloads(context, VideoDownloadService::class.java, false)
    }

    fun pauseDownloading(context: Context) {
        DownloadService.sendPauseDownloads(context, VideoDownloadService::class.java, false)
    }

    fun resumeDownloading(context: Context) {
        DownloadService.sendResumeDownloads(context, VideoDownloadService::class.java, false)
    }

    fun isPlayerPrepared(): Boolean {
        return videoUtil.isPlayerPrepared(exoPlayer)
    }


            init {
        Timber.d("Init is called")
        exoPlayer = videoUtil.createExoPlayer()

        if (videoUtil.checkIfSomethingIsDownloaded()) {
            initialDownloadState = DownloadState.SomethingIsDownloaded
        } else if (videoUtil.checkIfSomethingIsDownloading()) {
            initialDownloadState = DownloadState.SomethingIsDownloading
        }

        _downloadState.value = initialDownloadState

        videoUtil.downloadManager.addListener(downloadManagerListener)

    }

    override fun onCleared() {
        Timber.d("Release player is called")
        super.onCleared()
        videoUtil.releasePlayer(exoPlayer)
    }
}
package com.example.videodownloader

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.offline.*
import kotlinx.coroutines.flow.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.lang.Exception

private const val DEFAULT_URI = ""
private const val CURRENT_URI = ""

class MainViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
): AndroidViewModel(application), KoinComponent {

    private val videoUtil: VideoUtil by inject()
    private var initialDownloadState: DownloadState = DownloadState.NotStarted
    lateinit var exoPlayer: ExoPlayer

    private val _currentUri = MutableStateFlow(DEFAULT_URI.toUri())
    val currentUri: StateFlow<Uri>
        get() = _currentUri

    fun updateCurrentUri(videoUri: Uri) {
        _currentUri.value = videoUri
    }


//    private val _bytesDownloaded = MutableStateFlow<Float?>(0f)
//    val bytesDownloaded: StateFlow<Float?>
//        get() = _bytesDownloaded

    fun getDownloadedBytes2(videoUri: Uri): Flow<Long?> {
        return videoUtil.getCurrentProgressDownload2(videoUri)
    }

    fun getDownloadedBytes(videoUri: Uri): Flow<String> {
        return videoUtil.getCurrentProgressDownload(videoUri)
    }



    private val _downloadState = MutableStateFlow(initialDownloadState)
    val downloadState: StateFlow<DownloadState>
        get() = _downloadState

    sealed class DownloadState {
        object NotStarted : DownloadState()
//        object SomethingIsDownloaded : DownloadState()
//        object SomethingIsDownloading : DownloadState()
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
                Download.STATE_QUEUED -> {
                    Timber.d("Download state is STATE_QUEUED")
                    _downloadState.value = DownloadState.Queued }
                Download.STATE_DOWNLOADING -> {
                    Timber.d("Download state is STATE_DOWNLOADING")
                    _downloadState.value = DownloadState.Downloading
                    downloadManager.currentDownloads.size
                }
                Download.STATE_RESTARTING -> {
                    Timber.d("Download state is STATE_RESTARTING")
                    _downloadState.value = DownloadState.Restarting }
                Download.STATE_STOPPED -> {
                    Timber.d("Download state is STATE_STOPPED")
                    _downloadState.value = DownloadState.Stopped }
                Download.STATE_FAILED -> {
                    Timber.d("Download state is STATE_FAILED")
                    _downloadState.value = DownloadState.Failed }
                Download.STATE_COMPLETED -> {
                    Timber.d("Download state is STATE_COMPLETED")
                    _downloadState.value = DownloadState.Completed
                    playDownloadedVideo()
                }
                Download.STATE_REMOVING -> {
                    Timber.d("Download state is STATE_REMOVING")
                    _downloadState.value = DownloadState.Removing }
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

    fun checkHaveCompletelyDownloadedVideo(): Boolean {
        return videoUtil.checkHaveCompletelyDownloadedVideo()
    }

    fun checkHaveFailedDownloadedVideo(): Boolean {
        Timber.d("checkHaveFailedDownloadedVideo returned ${videoUtil.checkHaveFailedDownloadedVideo()}")
        return videoUtil.checkHaveFailedDownloadedVideo()
    }

    fun downloadVideo(context: Context, videoUri: Uri) {
        videoUtil.downloadVideo(context, videoUri)
    }

    fun setStopReasonDuringDownloading(context: Context, videoUri: Uri) {
        Timber.d("setStopReasonDuringDownloading() is called. videoUri is $videoUri")
        DownloadService.sendSetStopReason(context, VideoDownloadService::class.java, videoUri.toString(), 1, false)
    }

    // Try to send failed download to DownloadService if there are any. Else download normally.
//    fun tryContinueDownloadFailedVideo(context: Context, videoUri: Uri) {
//        Timber.d("continueDownloadFailedVideo() is called")
//        videoUtil.tryContinueDownloadFailedVideo(context, videoUri)
//    }


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

    fun pauseDownloading(context: Context, videoUri: Uri) {
        //videoUri.toString()
        DownloadService.sendSetStopReason(context, VideoDownloadService::class.java, null, 1, false)
        //DownloadService.sendPauseDownloads(context, VideoDownloadService::class.java, false)
    }

    fun resumeDownloading(context: Context, videoUri: Uri) {
        DownloadService.sendSetStopReason(context, VideoDownloadService::class.java, null, 0, false)
        //DownloadService.sendResumeDownloads(context, VideoDownloadService::class.java, false)
    }

    fun downloadFailedVideo(context: Context) {
        videoUtil.downloadFailedVideo(context)
    }

    fun isPlayerPrepared(): Boolean {
        return videoUtil.isPlayerPrepared(exoPlayer)
    }


    init {
        Timber.d("Init is called")
        Timber.d("In ViewModel init _currentUri.value is ${_currentUri.value}")

        //_currentUri.value = savedStateHandle.get<Uri>(CURRENT_URI)?: DEFAULT_URI.toUri()
        _currentUri.value = savedStateHandle.get<Uri>(CURRENT_URI) ?: DEFAULT_URI.toUri()

        exoPlayer = videoUtil.createExoPlayer()

//        if (videoUtil.checkIfSomethingIsDownloaded()) {
//            initialDownloadState = DownloadState.SomethingIsDownloaded
//        } else if (videoUtil.checkIfSomethingIsDownloading()) {
//            initialDownloadState = DownloadState.SomethingIsDownloading
//        }

        if (videoUtil.getDownloadState() != null) {
            initialDownloadState = when (videoUtil.getDownloadState()) {
                Download.STATE_QUEUED -> DownloadState.Queued
                Download.STATE_STOPPED -> DownloadState.Stopped
                Download.STATE_DOWNLOADING -> DownloadState.Downloading
                Download.STATE_COMPLETED -> DownloadState.Completed
                Download.STATE_FAILED -> DownloadState.Failed
                Download.STATE_REMOVING -> DownloadState.Removing
                Download.STATE_RESTARTING -> DownloadState.Restarting
                else -> DownloadState.NotStarted
            }
        }

        _downloadState.value = initialDownloadState

        videoUtil.downloadManager.addListener(downloadManagerListener)

    }

    override fun onCleared() {
        Timber.d("Release player is called")
        super.onCleared()
        savedStateHandle[CURRENT_URI] = _currentUri.value.toString()
        Timber.d("onCleared() is called. savedStateHandle[CURRENT_URI] is ${savedStateHandle.get<Uri>(CURRENT_URI)}")
        videoUtil.releasePlayer(exoPlayer)
    }
}
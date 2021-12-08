@file:Suppress("JoinDeclarationAndAssignment")

package com.example.videodownloader.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import com.example.videodownloader.service.VideoDownloadService
import com.example.videodownloader.videoutil.VideoUtil
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.offline.*
import kotlinx.coroutines.flow.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.lang.Exception

class MainViewModel(
    application: Application
): AndroidViewModel(application), KoinComponent {

    private val videoUtil: VideoUtil by inject()
    private var initialDownloadState: DownloadState = DownloadState.NotStarted
    lateinit var exoPlayer: ExoPlayer

    private val _currentUri = MutableStateFlow("".toUri())
    val currentUri: StateFlow<Uri>
        get() = _currentUri

    fun updateCurrentUri(videoUri: Uri) {
        _currentUri.value = videoUri
    }

    // Used to show download progress
    fun getDownloadedBytes(videoUri: Uri): Flow<String> {
        return videoUtil.getCurrentProgressDownload(videoUri)
    }

    private val _downloadState = MutableStateFlow(initialDownloadState)
    val downloadState: StateFlow<DownloadState>
        get() = _downloadState

    sealed class DownloadState {
        object NotStarted : DownloadState()
        object Queued : DownloadState()
        object Downloading : DownloadState()
        object Restarting : DownloadState()
        object Stopped : DownloadState()
        object Failed : DownloadState()
        object Completed : DownloadState()
        object Removing : DownloadState()
    }

    /**
     * Download state is actually failed, but the user opened the screen with input text view
     * to use another link so we have to retain this screen, if configuration changes
     */
    private val _retriedAnotherLinkAfterFailed = MutableStateFlow(false)
    val retriedAnotherLinkAfterFailed: StateFlow<Boolean>
        get() = _retriedAnotherLinkAfterFailed

    fun setRetryAnotherLinkAfterFailed(retried: Boolean) {
        _retriedAnotherLinkAfterFailed.value = retried
    }

    // Retain video title to use it in fullscreen mode
    private val _videoTitle = MutableStateFlow("")
    val videoTitle: StateFlow<String>
        get() = _videoTitle

    // This value is used to show an error placeholder in player frame, if it couldn't play the file
    private val _hasPlayerGotError = MutableStateFlow(false)
    val hasPlayerGotError: StateFlow<Boolean>
        get() = _hasPlayerGotError

    // This value is used to show an error, if too big file is started downloading
    private val _sizeOfDownloadingFile = MutableStateFlow(0L)
    val sizeOfDownloadingFile: StateFlow<Long>
        get() = _sizeOfDownloadingFile

    /*
    Used to display the corresponding UI if the video is opened in fullscreen mode
    or returns to non-fullscreen
    */
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

    fun unpausePlayer() {
        videoUtil.unpauseVideo(exoPlayer)
    }

    fun preparePlayer() {
        videoUtil.preparePlayer(exoPlayer)
    }

    fun playDownloadedVideo(isPlayerPlaying: Boolean = false, playbackPosition: Long = 0) {
        Timber.d("playDownloadedVideo() is called. isPlayerPlaying is $isPlayerPlaying. " +
                "playbackPosition is $playbackPosition")
        _videoTitle.value = videoUtil.getCompletelyDownloadedItems().last().title

        videoUtil.playDownloadedVideo(exoPlayer, isPlayerPlaying, playbackPosition)
    }

    private val exoPlayerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            Timber.d("Player Listener: " +
                    "Player has got an error! ${exoPlayer.playbackState}")
            _hasPlayerGotError.value = true
        }

        override fun onPlayerErrorChanged(error: PlaybackException?) {
            super.onPlayerErrorChanged(error)
            if (error != null) {
                Timber.d("Player Listener: " +
                        "Player has got an error! ${exoPlayer.playbackState}")
                _hasPlayerGotError.value = true

            } else {
                Timber.d("Player Listener: " +
                        "Player returned from error state to normal ${exoPlayer.playbackState}")
                _hasPlayerGotError.value = false
            }
        }
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
                    // File size there is used to display an error, if it is too big
                    _sizeOfDownloadingFile.value = videoUtil.getFileSizeDuringDownload() ?: 0
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
                }
                Download.STATE_REMOVING -> {
                    Timber.d("Download state is STATE_REMOVING")
                    _downloadState.value = DownloadState.Removing }
            }
        }
    }

    // Used to play only completely downloaded video
    fun checkHaveCompletelyDownloadedVideo(): Boolean {
        return videoUtil.checkHaveCompletelyDownloadedVideo()
    }

    // Used to continue downloading after fail
    fun checkHaveFailedDownloadedVideo(): Boolean {
        Timber.d("checkHaveFailedDownloadedVideo returned " +
                "${videoUtil.checkHaveFailedDownloadedVideo()}")
        return videoUtil.checkHaveFailedDownloadedVideo()
    }

    fun downloadVideo(context: Context, videoUri: Uri) {
        videoUtil.downloadVideo(context, videoUri)
    }

    // Used to check if there is something downloaded before downloading a new video
    fun checkIsAnythingDownloaded(): Boolean {
        return videoUtil.checkIsAnythingDownloaded()
    }
    // All previous downloads must be cleared before adding a new one
    fun clearAllDownloads(context: Context) {
        DownloadService.sendRemoveAllDownloads(context, VideoDownloadService::class.java, false)
    }

    fun pauseDownloading(context: Context) {
        DownloadService.sendSetStopReason(
            context, VideoDownloadService::class.java, null, 1, false
        )
    }

    fun resumeDownloading(context: Context) {
        DownloadService.sendSetStopReason(
            context, VideoDownloadService::class.java, null, 0, false
        )
    }

    fun downloadFailedVideo(context: Context) {
        videoUtil.downloadFailedVideo(context)
    }

    init {
        exoPlayer = videoUtil.createExoPlayer()
        // Get initial download state to display the correct UI
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

        exoPlayer.addListener(exoPlayerListener)

        videoUtil.downloadManager.addListener(downloadManagerListener)
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("Release player is called")
        videoUtil.releasePlayer(exoPlayer)
    }
}
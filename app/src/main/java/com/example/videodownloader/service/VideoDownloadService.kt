package com.example.videodownloader.service

import android.app.Notification
import android.content.Context
import com.example.videodownloader.R
import com.example.videodownloader.videoutil.VideoUtil
import com.google.android.exoplayer2.offline.Download
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.offline.DownloadService
import com.google.android.exoplayer2.scheduler.Scheduler
import com.google.android.exoplayer2.ui.DownloadNotificationHelper
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

const val FOREGROUND_NOTIFICATION_ID = 1
const val CHANNEL_ID = "VideoDownloaderChannel"
const val MAX_PARALLEL_DOWNLOADS = 1

/** A service for downloading media. */
class VideoDownloadService: DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.app_name,
    R.string.channel_description
), KoinComponent {

    private val videoUtil: VideoUtil by inject()

    private lateinit var notificationHelper: DownloadNotificationHelper
    private lateinit var context: Context


    override fun onCreate() {
        super.onCreate()
        context = this
        notificationHelper = DownloadNotificationHelper(this, CHANNEL_ID)
    }

    override fun getDownloadManager(): DownloadManager {

        val downloadManager = videoUtil.downloadManager
        downloadManager.maxParallelDownloads = MAX_PARALLEL_DOWNLOADS
        return downloadManager
    }
    // Don't use any scheduler
    override fun getScheduler(): Scheduler? {
        return null
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        return notificationHelper.buildProgressNotification(
            this, R.drawable.ic_outline_cloud_download_24, null,
            getString(R.string.channel_description), downloads, notMetRequirements
        )
    }

}
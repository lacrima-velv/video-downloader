package com.example.videodownloader

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.google.android.exoplayer2.offline.Download
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.offline.DownloadService
import com.google.android.exoplayer2.scheduler.Scheduler
import com.google.android.exoplayer2.ui.DownloadNotificationHelper
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.lang.Exception

const val FOREGROUND_NOTIFICATION_ID = 1
const val CHANNEL_ID = "VideoDownloaderChannel"
const val MAX_PARALLEL_DOWNLOADS = 1

/** A service for downloading media. */
class VideoDownloadService: DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.app_name,
    R.string.app_description
), KoinComponent {

    private val videoUtil: VideoUtil by inject()

    private lateinit var notificationHelper: DownloadNotificationHelper
    private lateinit var context: Context


    override fun onCreate() {
        super.onCreate()
        context = this
        notificationHelper = DownloadNotificationHelper(this, CHANNEL_ID)
    }

//    private val downloadManagerListener = object : DownloadManager.Listener {
//        override fun onDownloadChanged(
//            downloadManager: DownloadManager,
//            download: Download,
//            finalException: Exception?
//        ) {
//            super.onDownloadChanged(downloadManager, download, finalException)
//            Timber.d("onDownloadChanged: download.bytesDownloaded is ${download.bytesDownloaded} and download.contentLength is ${download.contentLength}")
//            if (download.bytesDownloaded == download.contentLength) {
//                Timber.d("Downloaded")
//                //Timber.d("download.bytesDownloaded is ${download.bytesDownloaded} and download.contentLength is ${download.contentLength}")
//            }
//        }
//
//        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
//            super.onDownloadRemoved(downloadManager, download)
//            Timber.d("onDownloadRemoved")
//        }
//
//        override fun onInitialized(downloadManager: DownloadManager) {
//            super.onInitialized(downloadManager)
//            Timber.d("onInitialized")
//        }
//
//        override fun onDownloadsPausedChanged(
//            downloadManager: DownloadManager,
//            downloadsPaused: Boolean
//        ) {
//            super.onDownloadsPausedChanged(downloadManager, downloadsPaused)
//            if (downloadsPaused) {
//                Timber.d("DownloadsPaused")
//            } else  {
//                Timber.d("DownloadsResumed")
//            }
//        }
//    }

    override fun getDownloadManager(): DownloadManager {

        val downloadManager = videoUtil.downloadManager
        downloadManager.maxParallelDownloads = MAX_PARALLEL_DOWNLOADS
       // downloadManager.addListener(downloadManagerListener)
        return downloadManager
    }
    // TODO: Maybe I'll override it later to restart download when it failed
    override fun getScheduler(): Scheduler? {
        return null
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        return notificationHelper.buildProgressNotification(
            this, R.drawable.ic_outline_cloud_download_24, null,
            getString(R.string.app_description), downloads, notMetRequirements
        )
    }

    override fun onDestroy() {
        super.onDestroy()
       // downloadManager.removeListener(downloadManagerListener)
    }

}
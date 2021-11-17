package com.example.videodownloader

import android.content.Context
import android.os.StatFs
import com.google.android.exoplayer2.offline.DownloadIndex
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DownloadTracker(
    private val context: Context,
    private val defaultHttpDataSourceFactory: DefaultHttpDataSource.Factory,
    private val downloadManager: DownloadManager
    ) : KoinComponent {
    private val videoUtil: VideoUtil by inject()
    private var availableBytesLeft: Long = StatFs(videoUtil.getDownloadContentDirectory().path).availableBytes
    private val downloadIndex: DownloadIndex = videoUtil.downloadManager.downloadIndex
}
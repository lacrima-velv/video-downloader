package com.example.videodownloader

import android.content.Context
import com.google.android.exoplayer2.database.DatabaseProvider
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource

/**
 * Singleton DatabaseProvider to use with DownloadManager for ExoPlayer Videos
 */
class VideoDatabaseProvider private constructor(context: Context) {

    var videoDatabaseProvider: DatabaseProvider = StandaloneDatabaseProvider(context)

    companion object : SingletonHolder<VideoDatabaseProvider, Context>(::VideoDatabaseProvider)
}
package com.example.videodownloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/*
In order to make the DownloadManager be shared across all components of the app initialize it in
a custom application class
 */
class MyApp: Application() {
    //lateinit var appContainer: AppContainer
    var ID = "download_channel"

    override fun onCreate() {
        super.onCreate()
        //appContainer = AppContainer(this)
        createNotificationChannels()

        startKoin {
            //inject Android context
            androidContext(this@MyApp)
            androidLogger()
            modules(koinModule)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(ID, "Video Downloader", NotificationManager.IMPORTANCE_HIGH)
            channel.description = getString(R.string.app_description)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

    }
}
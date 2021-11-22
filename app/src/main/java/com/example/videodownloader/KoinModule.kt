package com.example.videodownloader

import com.example.videodownloader.videodb.VideoDatabaseProvider
import com.example.videodownloader.videoutil.VideoUtil
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val koinModule = module {
    single { VideoUtil(androidContext()) }
    single { VideoDatabaseProvider.getInstance(androidContext()) }
}
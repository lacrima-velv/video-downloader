package com.example.videodownloader

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val koinModule = module {
    single { VideoUtil(androidContext()) }
}
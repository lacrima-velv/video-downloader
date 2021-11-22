package com.example.videodownloader.videodb

import android.content.Context
import com.google.android.exoplayer2.database.DatabaseProvider
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider

abstract class VideoDatabaseProvider {
    companion object {
        /*
        INSTANCE will keep a reference to any database returned via getInstance
        Volatile value will never be cached.
        Changes made by one thread to shared data are visible to other threads.
         */
        @Volatile
        private var INSTANCE: DatabaseProvider? = null
        /*
        Helper function to get the database. If it has already been retrieved, the previous database
        will be returned.
         */
        fun getInstance(context: Context) : DatabaseProvider =
            /*
             Use synchronized as it may be called by multiple threads, but we need to be sure that
             the database is initialized only once
             */
            INSTANCE ?: synchronized(this) {
                INSTANCE
                    ?: buildDatabase(context).also { INSTANCE = it }
            }
        private fun buildDatabase(context: Context) =
            StandaloneDatabaseProvider(context)
    }
}
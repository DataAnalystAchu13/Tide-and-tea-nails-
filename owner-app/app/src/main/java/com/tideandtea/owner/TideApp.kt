package com.tideandtea.owner

import android.app.Application

class TideApp : Application() {
    override fun onCreate() {
        super.onCreate()
        TideMessagingService.createChannel(this)
    }
}

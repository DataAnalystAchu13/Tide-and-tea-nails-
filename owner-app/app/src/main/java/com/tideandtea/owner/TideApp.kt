package com.tideandtea.owner

import android.app.Application

class TideApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.tideandtea.owner.data.Supa.init(this)
        TideMessagingService.createChannel(this)
    }
}

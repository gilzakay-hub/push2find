package com.push2find.app

import android.app.Application
import com.push2find.app.alarm.Alarm
import com.push2find.app.alarm.Notifications

class Push2FindApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        Alarm.init(this)
    }
}

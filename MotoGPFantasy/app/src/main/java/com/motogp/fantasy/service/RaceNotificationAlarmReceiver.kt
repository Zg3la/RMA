package com.motogp.fantasy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RaceNotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        if (title.isNotBlank() && body.isNotBlank()) {
            MotoGpNotifier.show(context, title, body)
        }
    }

    companion object {
        const val ACTION_SHOW_NOTIFICATION = "com.motogp.fantasy.SHOW_RACE_NOTIFICATION"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
    }
}

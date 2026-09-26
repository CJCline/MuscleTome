package com.chy.muscletome.data.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Delivers the end-of-rest beep — scheduled by [RestTimerManager]. */
class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REST_FINISHED) return
        RestTimerManager.handleRestFinished(context)
    }

    companion object {
        const val ACTION_REST_FINISHED = "com.chy.muscletome.action.REST_FINISHED"
    }
}

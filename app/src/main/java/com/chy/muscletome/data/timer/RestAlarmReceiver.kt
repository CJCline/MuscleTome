package com.chy.muscletome.data.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Delivers the end-of-rest beep — scheduled by [RestTimerManager].
 *
 * [goAsync] keeps the broadcast alive (~10 s budget) until the tone has
 * finished playing; without it the process could be torn down as soon as
 * `onReceive` returns, cutting the beep short.
 */
class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REST_FINISHED) return
        val pending = goAsync()
        RestTimerManager.handleRestFinished(context, onDone = {
            pending.finish()
        })
    }

    companion object {
        const val ACTION_REST_FINISHED = "com.chy.muscletome.action.REST_FINISHED"
    }
}

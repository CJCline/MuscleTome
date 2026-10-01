package com.chy.muscletome.data.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class RestTimerTest {

    @Test
    fun restAlarmActionMatchesIntentFilter() {
        assertEquals(
            "com.chy.muscletome.action.REST_FINISHED",
            RestAlarmReceiver.ACTION_REST_FINISHED,
        )
    }
}

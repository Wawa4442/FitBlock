package com.fitblock.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

object UsageAccess {
    @Suppress("DEPRECATION")
    fun allowed(context:Context):Boolean = (context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
        .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),context.packageName)==AppOpsManager.MODE_ALLOWED
    fun foreground(context:Context):String? {
        if(!allowed(context))return null
        return runCatching {
            val now=System.currentTimeMillis()
            val events=(context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager).queryEvents(now-3_600_000,now)
            val event=UsageEvents.Event();var pkg:String?=null
            while(events.hasNextEvent()) {
                events.getNextEvent(event)
                @Suppress("DEPRECATION")
                when(event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND->pkg=event.packageName
                    UsageEvents.Event.MOVE_TO_BACKGROUND->if(pkg==event.packageName)pkg=null
                }
            }
            pkg
        }.getOrNull()
    }
}

package com.fitblock.app

import android.content.*
import android.app.role.RoleManager
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

data class InstalledApp(val pkg: String, val title: String)
object AppCatalog {
    fun protected(context: Context): Set<String> {
        val pm = context.packageManager
        val home = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }
        val keyboard = (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).inputMethodList.map { it.packageName }
        val dialer = (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage
        val sms = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
        return (home + keyboard + listOfNotNull(dialer, sms) + listOf(context.packageName, "com.android.settings", "com.android.systemui", "com.android.phone", "com.android.emergency", "com.google.android.apps.safetyhub", "com.google.android.permissioncontroller", "com.android.permissioncontroller", "com.google.android.apps.maps", "com.google.android.dialer", "com.android.dialer", "com.android.contacts")).toSet()
    }
    fun list(context: Context): List<InstalledApp> {
        val excluded = protected(context)
        return context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString()) }
            .distinctBy { it.pkg }.filter { it.pkg !in excluded }.sortedBy { it.title.lowercase() }
    }
}

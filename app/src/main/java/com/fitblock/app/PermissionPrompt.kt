package com.fitblock.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class PermissionPrompt(
    private val activity: ComponentActivity,
    private val onResult: (permission: String, granted: Boolean) -> Unit
) {
    private val denied by lazy { activity.getSharedPreferences("permission_prompts", 0) }
    private var pending: String? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val permission = pending ?: return@registerForActivityResult
        pending = null
        denied.edit().putBoolean(permission, !granted).apply()
        onResult(permission, granted)
    }

    fun request(permission: String) {
        if (pending != null) return
        if (activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            onResult(permission, true)
        } else if (denied.getBoolean(permission, false) && !activity.shouldShowRequestPermissionRationale(permission)) {
            activity.openAppSettings()
        } else {
            pending = permission
            launcher.launch(permission)
        }
    }
}

fun ComponentActivity.openAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}

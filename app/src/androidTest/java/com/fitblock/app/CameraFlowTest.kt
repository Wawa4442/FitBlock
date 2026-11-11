package com.fitblock.app

import android.app.UiAutomation
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraFlowTest {
    @Test fun cameraStartsOfflineAndEmptySceneCannotEarnMinutes() {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val automation=inst.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val store=Store.get(context);store.clear();store.consent=true
        automation.executeShellCommand("pm grant com.fitblock.app android.permission.CAMERA").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        context.startActivity(Intent(context,WorkoutActivity::class.java).putExtra("exercise","SQUAT").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun find(text:String,root:AccessibilityNodeInfo?=automation.rootInActiveWindow):AccessibilityNodeInfo? {
            if(root==null)return null
            if(root.text?.toString()?.contains(text,true)==true)return root
            for(i in 0 until root.childCount)find(text,root.getChild(i))?.let { return it };return null
        }
        val deadline=SystemClock.elapsedRealtime()+8000;var button:AccessibilityNodeInfo?=null
        while(SystemClock.elapsedRealtime()<deadline&&button==null){button=find("Comenzar verificación");Thread.sleep(200)}
        assertNotNull(button);assertTrue(button!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        try {
            val analysisDeadline = SystemClock.elapsedRealtime() + 20_000
            var analyzed = false
            while (SystemClock.elapsedRealtime() < analysisDeadline && !analyzed) {
                analyzed = find("Colócate de frente") != null || find("Verificación 1/") != null ||
                    find("Apoya el teléfono para mantener la cámara fija.") != null
                Thread.sleep(250)
            }
            assertNull(find("No se pudo abrir"));assertNull(find("No se pudo analizar"));assertNull(find("No hay cámara"))
            fun texts(root: AccessibilityNodeInfo?): String {
                if (root == null) return ""
                return root.text.toString() + (0 until root.childCount).joinToString { texts(root.getChild(it)) }
            }
            assertTrue("The offline model must return a result. UI: " + texts(automation.rootInActiveWindow), analyzed)
            assertNotNull(find("Pausar actividad"));assertEquals(0L,store.balance());assertEquals(0,store.totalSessions())
        } finally {
            automation.executeShellCommand("input keyevent KEYCODE_HOME").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
            Thread.sleep(500)
            store.clear()
        }
    }
}

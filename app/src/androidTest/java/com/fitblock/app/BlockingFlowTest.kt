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

/** Keep accessibility services active while observing the blocking overlay. */
@RunWith(AndroidJUnit4::class)
class BlockingFlowTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val context=inst.targetContext
    private val automation=inst.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private fun shell(command:String) { automation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() } }
    private fun node(text:String, root:AccessibilityNodeInfo?=automation.rootInActiveWindow):AccessibilityNodeInfo? {
        if(root==null)return null
        if(root.text?.toString()?.contains(text,ignoreCase=true)==true)return root
        for(i in 0 until root.childCount)node(text,root.getChild(i))?.let { return it }
        return null
    }
    private fun waitFor(text:String, timeout:Long=10_000):AccessibilityNodeInfo {
        val deadline=SystemClock.elapsedRealtime()+timeout
        while(SystemClock.elapsedRealtime()<deadline) { node(text)?.let { return it };Thread.sleep(150) }
        throw AssertionError("Missing UI text: $text")
    }
    private fun click(text:String) { assertTrue(waitFor(text).performAction(AccessibilityNodeInfo.ACTION_CLICK));Thread.sleep(400) }
    private fun launchChrome() {
        val intent=context.packageManager.getLaunchIntentForPackage("com.android.chrome")!!
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun screenshot(name:String) {
        val values=android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/FitBlockQA")
        }
        val uri=context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        context.contentResolver.openOutputStream(uri)!!.use { output->automation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,output) }
    }
    @Test fun blockerEmergencyExpiryAndExerciseNavigation() {
        val store=Store.get(context);store.clear();store.consent=true;store.selected=setOf("com.android.chrome");store.enabled=true
        shell("settings put secure enabled_accessibility_services com.fitblock.app/.BlockService")
        shell("settings put secure accessibility_enabled 1")
        shell("appops set com.fitblock.app GET_USAGE_STATS allow")
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        Thread.sleep(2000);screenshot("03-inicio.png");launchChrome()
        waitFor("UNA PAUSA PARA TI")
        screenshot("04-bloqueo.png")
        assertNotNull(waitFor("Elegir actividad"))
        click("Elegir actividad");waitFor("Actívate. Desbloquea.");screenshot("05-actividades.png")
        context.startActivity(Intent(context,MainActivity::class.java).putExtra("destination","emergency").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        click("Activar 2 minutos");assertTrue(store.emergencyActive())
        launchChrome();Thread.sleep(1200);assertNull(node("UNA PAUSA PARA TI"))
        screenshot("07-emergencia.png")
        // Only the test harness shortens the deadline; the APK has no test/unlock entry point.
        context.getSharedPreferences("fitblock_private",0).edit().putLong("emergency_until",SystemClock.elapsedRealtime()-1).commit()
        waitFor("UNA PAUSA PARA TI")
        click("Elegir actividad");waitFor("Actívate. Desbloquea.")
        store.enabled=false
    }
}

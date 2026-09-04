package com.mertg.geemu.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.mertg.geemu.MainActivity
import com.mertg.geemu.data.GeemuPreferences

class GeemuAccessibilityService : AccessibilityService() {
    private lateinit var preferences: GeemuPreferences
    private lateinit var controllerMonitor: ControllerMonitor

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        preferences = GeemuPreferences(this)
        controllerMonitor = ControllerMonitor(this) { _, changed, connected ->
            if (changed == null || !preferences.isControllerRegistered(changed)) return@ControllerMonitor
            if (connected) {
                RotationController.enterGameMode(this)
                runCatching {
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                }
            } else {
                RotationController.exitGameMode(this)
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        controllerMonitor.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (::controllerMonitor.isInitialized) controllerMonitor.stop()
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: GeemuAccessibilityService? = null

        fun goHome(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_HOME) == true
        fun isRunning(): Boolean = instance != null
    }
}

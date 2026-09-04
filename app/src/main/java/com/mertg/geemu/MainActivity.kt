package com.mertg.geemu

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mertg.geemu.data.GeemuPreferences
import com.mertg.geemu.model.ControllerDevice
import com.mertg.geemu.system.ControllerMonitor
import com.mertg.geemu.system.GeemuAccessibilityService
import com.mertg.geemu.system.RotationController
import com.mertg.geemu.ui.GeemuApp
import com.mertg.geemu.ui.theme.GeemuTheme

class MainActivity : ComponentActivity() {
    private val connectedControllers = mutableStateOf<List<ControllerDevice>>(emptyList())
    private val permissionRevision = mutableIntStateOf(0)
    private var controllerKeyHandler: ((Int) -> Boolean)? = null
    private lateinit var preferences: GeemuPreferences
    private lateinit var controllerMonitor: ControllerMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = GeemuPreferences(this)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        configureImmersiveMode()

        controllerMonitor = ControllerMonitor(this) { devices, changed, connected ->
            runOnUiThread {
                connectedControllers.value = devices
                if (changed != null && preferences.isControllerRegistered(changed)) {
                    if (connected) enterGameMode() else exitGameMode()
                }
            }
        }
        controllerMonitor.start()
        connectedControllers.value = controllerMonitor.connectedControllers()

        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED ||
            connectedControllers.value.any(preferences::isControllerRegistered)
        ) {
            enterGameMode()
        }

        setContent {
            GeemuTheme {
                GeemuApp(
                    controllers = connectedControllers.value,
                    permissionRevision = permissionRevision.intValue,
                    onControllerKeyHandlerChanged = { controllerKeyHandler = it },
                    onOpenRotationPermission = ::openRotationPermission,
                    onOpenAccessibilitySettings = ::openAccessibilitySettings
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) enterGameMode()
    }

    override fun onResume() {
        super.onResume()
        permissionRevision.intValue++
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && isNavigationKey(event.keyCode)) {
            if (controllerKeyHandler?.invoke(event.keyCode) == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        controllerMonitor.stop()
        super.onDestroy()
    }

    private fun enterGameMode() {
        RotationController.enterGameMode(this)
    }

    private fun exitGameMode() {
        RotationController.exitGameMode(this)
        GeemuAccessibilityService.goHome()
        finishAndRemoveTask()
    }

    private fun openRotationPermission() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun configureImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun isNavigationKey(keyCode: Int): Boolean = keyCode in setOf(
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_X,
        KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_START,
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_BUTTON_L1,
        KeyEvent.KEYCODE_BUTTON_R1
    )
}

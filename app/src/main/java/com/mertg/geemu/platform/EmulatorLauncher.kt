package com.mertg.geemu.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mertg.geemu.model.InstalledApp
import com.mertg.geemu.model.RomEntry

object EmulatorLauncher {
    fun launchVitaTitle(context: Context, titleId: String): Boolean {
        val intent = Intent().apply {
            setClassName("org.vita3k.emulator", "org.vita3k.emulator.Emulator")
            action = "LAUNCH_$titleId"
            putExtra("AppStartParameters", arrayOf("-r", titleId))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun launchApp(context: Context, app: InstalledApp): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: return false
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    fun launchRom(context: Context, app: InstalledApp, rom: RomEntry): Boolean {
        val uri = Uri.parse(rom.uri)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/octet-stream")
            setPackage(app.packageName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(viewIntent)
            true
        } catch (_: ActivityNotFoundException) {
            launchApp(context, app)
        } catch (_: SecurityException) {
            launchApp(context, app)
        }
    }

    fun launchPackage(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }
}

package com.mertg.geemu.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.mertg.geemu.model.GameSystem
import com.mertg.geemu.model.InstalledApp

class AppCatalog(private val context: Context) {
    private val packageManager = context.packageManager

    fun launchableApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val results = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

        return results
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { result ->
                val info = result.activityInfo.applicationInfo
                @Suppress("DEPRECATION")
                val legacyGameFlag = (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                val categoryGame = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    info.category == ApplicationInfo.CATEGORY_GAME
                InstalledApp(
                    packageName = result.activityInfo.packageName,
                    label = result.loadLabel(packageManager).toString(),
                    isRecognizedGame = categoryGame || legacyGameFlag,
                    icon = result.loadIcon(packageManager)
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun findEmulator(system: GameSystem, apps: List<InstalledApp>): InstalledApp? {
        val packages = system.packageCandidates.map(String::lowercase).toSet()
        return apps.firstOrNull { it.packageName.lowercase() in packages }
            ?: apps.firstOrNull { app ->
                system.emulatorAliases.any { alias ->
                    app.label.contains(alias, ignoreCase = true) ||
                        app.packageName.contains(alias, ignoreCase = true)
                }
            }
    }
}

package com.mertg.geemu.data

import android.content.Context
import com.mertg.geemu.model.InstalledApp
import com.mertg.geemu.model.RomEntry
import com.mertg.geemu.model.SystemCatalog
import com.mertg.geemu.model.SystemId

data class ArtworkSyncProgress(
    val completed: Int,
    val total: Int,
    val downloaded: Int,
    val currentTitle: String,
    val finished: Boolean = false
)

class ArtworkSyncManager(
    private val context: Context,
    private val preferences: GeemuPreferences,
    private val apps: List<InstalledApp>
) {
    private data class Target(
        val key: String,
        val title: String,
        val systemId: SystemId,
        val hasLocalArtwork: Boolean
    )

    fun sync(
        provider: ArtworkProvider,
        missingOnly: Boolean,
        onProgress: (ArtworkSyncProgress) -> Unit
    ): ArtworkSyncProgress {
        val repository = GameArtworkRepository(context, preferences)
        val targets = collectTargets()
        var completed = 0
        var downloaded = 0
        targets.forEach { target ->
            val alreadyCovered = target.hasLocalArtwork || repository.cached(target.key) != null
            val shouldRequest = !missingOnly || !alreadyCovered
            if (shouldRequest) {
                val result = repository.resolveFromProvider(
                    provider = provider,
                    cacheKey = target.key,
                    query = target.title,
                    systemId = target.systemId,
                    force = !missingOnly
                )
                if (result != null) downloaded++
            }
            completed++
            onProgress(ArtworkSyncProgress(completed, targets.size, downloaded, target.title))
        }
        return ArtworkSyncProgress(completed, targets.size, downloaded, "", finished = true)
            .also(onProgress)
    }

    private fun collectTargets(): List<Target> {
        val targets = mutableListOf<Target>()
        val manualPackages = preferences.manualGamePackages()
        val emulatorPackages = SystemCatalog.systems.filter { it.id != SystemId.ANDROID }
            .flatMap { it.packageCandidates }.toSet()
        val emulatorAliases = SystemCatalog.systems.filter { it.id != SystemId.ANDROID }
            .flatMap { it.emulatorAliases }
        apps.filter { app ->
            (app.isRecognizedGame || app.packageName in manualPackages) &&
                app.packageName !in emulatorPackages &&
                emulatorAliases.none { app.label.contains(it, ignoreCase = true) }
        }.forEach { app ->
            targets += Target("ANDROID:${app.packageName}", app.label, SystemId.ANDROID, false)
        }

        SystemCatalog.systems.filter { it.id != SystemId.ANDROID }.forEach { system ->
            val folder = preferences.romFolder(system.id)
            val scanned = if (folder == null || !system.supportsRomFolder) emptyList() else {
                if (system.id == SystemId.VITA) VitaLibraryScanner(context).scan(folder)
                else RomScanner(context).scan(folder, system.romExtensions)
            }
            val filtered = if (system.id == SystemId.SWITCH_EDEN || system.id == SystemId.SWITCH_CITRON) {
                SwitchContentFilter.bootableGames(scanned)
            } else scanned
            val manualVita = if (system.id == SystemId.VITA) {
                preferences.manualVitaGames().map { (id, title) ->
                    RomEntry(title, "vita://$id", "MANUEL", launchId = id)
                }
            } else emptyList()
            (filtered + manualVita).distinctBy { it.launchId ?: it.uri }.forEach { rom ->
                targets += Target(
                    key = "${system.id}:${rom.launchId ?: rom.uri}",
                    title = rom.title,
                    systemId = system.id,
                    hasLocalArtwork = rom.artworkUri != null
                )
            }
        }
        return targets.distinctBy(Target::key)
    }
}

package com.mertg.geemu.data

import android.content.Context
import android.net.Uri
import com.mertg.geemu.model.ControllerDevice
import com.mertg.geemu.model.SystemId

class GeemuPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("geemu_preferences", Context.MODE_PRIVATE)

    fun manualGamePackages(): Set<String> =
        preferences.getStringSet(KEY_MANUAL_GAMES, emptySet()).orEmpty().toSet()

    fun setManualGame(packageName: String, selected: Boolean) {
        val updated = manualGamePackages().toMutableSet().apply {
            if (selected) add(packageName) else remove(packageName)
        }
        preferences.edit().putStringSet(KEY_MANUAL_GAMES, updated).apply()
    }

    fun romFolder(systemId: SystemId): Uri? =
        preferences.getString("rom_folder_${systemId.name}", null)?.let(Uri::parse)

    fun setRomFolder(systemId: SystemId, uri: Uri) {
        preferences.edit().putString("rom_folder_${systemId.name}", uri.toString()).apply()
    }

    fun steamGridDbApiKey(): String = preferences.getString(KEY_STEAM_GRID_DB, "").orEmpty()

    fun setSteamGridDbApiKey(value: String) {
        preferences.edit().putString(KEY_STEAM_GRID_DB, value.trim()).apply()
    }

    fun rawgApiKey(): String = preferences.getString(KEY_RAWG_API_KEY, "").orEmpty()

    fun setRawgApiKey(value: String) {
        preferences.edit().putString(KEY_RAWG_API_KEY, value.trim()).apply()
    }

    fun screenScraperDeveloperId(): String = preferences.getString(KEY_SS_DEV_ID, "").orEmpty()
    fun screenScraperDeveloperPassword(): String = preferences.getString(KEY_SS_DEV_PASSWORD, "").orEmpty()
    fun screenScraperUser(): String = preferences.getString(KEY_SS_USER, "").orEmpty()
    fun screenScraperPassword(): String = preferences.getString(KEY_SS_PASSWORD, "").orEmpty()

    fun setScreenScraperCredentials(developerId: String, developerPassword: String, user: String, password: String) {
        preferences.edit()
            .putString(KEY_SS_DEV_ID, developerId.trim())
            .putString(KEY_SS_DEV_PASSWORD, developerPassword.trim())
            .putString(KEY_SS_USER, user.trim())
            .putString(KEY_SS_PASSWORD, password)
            .apply()
    }

    fun manualVitaGames(): List<Pair<String, String>> =
        preferences.getStringSet(KEY_VITA_GAMES, emptySet()).orEmpty()
            .mapNotNull { value ->
                val separator = value.indexOf('\t')
                if (separator <= 0) null else value.substring(0, separator) to value.substring(separator + 1)
            }
            .sortedBy { it.second.lowercase() }

    fun addManualVitaGame(titleId: String, title: String) {
        val id = titleId.trim().uppercase()
        val updated = preferences.getStringSet(KEY_VITA_GAMES, emptySet()).orEmpty().toMutableSet()
        updated.removeAll { it.startsWith("$id\t") }
        updated += "$id\t${title.trim()}"
        preferences.edit().putStringSet(KEY_VITA_GAMES, updated).apply()
    }

    fun removeManualVitaGame(titleId: String) {
        val updated = preferences.getStringSet(KEY_VITA_GAMES, emptySet()).orEmpty().toMutableSet()
        updated.removeAll { it.startsWith("${titleId.uppercase()}\t") }
        preferences.edit().putStringSet(KEY_VITA_GAMES, updated).apply()
    }

    fun registeredControllerIds(): Set<String> =
        preferences.getStringSet(KEY_CONTROLLERS, emptySet()).orEmpty().toSet()

    fun isControllerRegistered(device: ControllerDevice): Boolean =
        device.vendorId == RAZER_VENDOR_ID || registeredControllerIds().contains(device.identity)

    fun setControllerRegistered(device: ControllerDevice, registered: Boolean) {
        val updated = registeredControllerIds().toMutableSet().apply {
            if (registered) add(device.identity) else remove(device.identity)
        }
        preferences.edit().putStringSet(KEY_CONTROLLERS, updated).apply()
    }

    companion object {
        const val RAZER_VENDOR_ID = 0x1532
        private const val KEY_MANUAL_GAMES = "manual_games"
        private const val KEY_CONTROLLERS = "controller_ids"
        private const val KEY_STEAM_GRID_DB = "steam_grid_db_api_key"
        private const val KEY_RAWG_API_KEY = "rawg_api_key"
        private const val KEY_SS_DEV_ID = "screenscraper_developer_id"
        private const val KEY_SS_DEV_PASSWORD = "screenscraper_developer_password"
        private const val KEY_SS_USER = "screenscraper_user"
        private const val KEY_SS_PASSWORD = "screenscraper_password"
        private const val KEY_VITA_GAMES = "manual_vita_games"
    }
}

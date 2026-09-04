package com.mertg.geemu.model

import android.graphics.drawable.Drawable

enum class SystemId {
    VITA,
    SWITCH_EDEN,
    SWITCH_CITRON,
    WII_U,
    PLAYSTATION_2,
    ANDROID
}

data class GameSystem(
    val id: SystemId,
    val title: String,
    val eyebrow: String,
    val description: String,
    val accent: Long,
    val deepColor: Long,
    val emulatorAliases: List<String> = emptyList(),
    val packageCandidates: List<String> = emptyList(),
    val romExtensions: Set<String> = emptySet(),
    val supportsRomFolder: Boolean = true
)

object SystemCatalog {
    val systems = listOf(
        GameSystem(
            id = SystemId.VITA,
            title = "PLAYSTATION VITA",
            eyebrow = "VITA3K",
            description = "Vita3K kütüphaneni aç",
            accent = 0xFF48B6FF,
            deepColor = 0xFF083C6B,
            emulatorAliases = listOf("vita3k"),
            packageCandidates = listOf("org.vita3k.emulator"),
            supportsRomFolder = true
        ),
        GameSystem(
            id = SystemId.SWITCH_EDEN,
            title = "SWITCH · EDEN",
            eyebrow = "EDEN",
            description = "Switch oyunlarını Eden ile başlat",
            accent = 0xFF8BEA62,
            deepColor = 0xFF16482A,
            emulatorAliases = listOf("eden"),
            packageCandidates = listOf(
                "dev.eden.eden_emulator",
                "dev.eden.eden_nightly",
                "org.eden.emulator"
            ),
            romExtensions = setOf("nsp", "xci", "nro")
        ),
        GameSystem(
            id = SystemId.SWITCH_CITRON,
            title = "SWITCH · CITRON",
            eyebrow = "CITRON",
            description = "Switch oyunlarını Citron ile başlat",
            accent = 0xFFFFB84D,
            deepColor = 0xFF70430A,
            emulatorAliases = listOf("citron"),
            packageCandidates = listOf("org.citron.citron_emu", "org.citron.emu"),
            romExtensions = setOf("nsp", "xci", "nro")
        ),
        GameSystem(
            id = SystemId.WII_U,
            title = "WII U",
            eyebrow = "CEMU",
            description = "Wii U arşivine göz at",
            accent = 0xFF38D9F5,
            deepColor = 0xFF075365,
            emulatorAliases = listOf("cemu"),
            packageCandidates = listOf("info.cemu.Cemu", "info.cemu.cemu"),
            romExtensions = setOf("wua", "wux", "wud", "rpx")
        ),
        GameSystem(
            id = SystemId.PLAYSTATION_2,
            title = "PLAYSTATION 2",
            eyebrow = "AETHERSX2",
            description = "PS2 koleksiyonunu çalıştır",
            accent = 0xFF9D8CFF,
            deepColor = 0xFF34246A,
            emulatorAliases = listOf("aethersx2", "nethersx2"),
            packageCandidates = listOf("xyz.aethersx2.android"),
            romExtensions = setOf("iso", "chd", "cso", "bin", "gz")
        ),
        GameSystem(
            id = SystemId.ANDROID,
            title = "ANDROID GAMES",
            eyebrow = "YEREL",
            description = "Telefondaki oyunlar ve seçtiklerin",
            accent = 0xFFFF5E7A,
            deepColor = 0xFF6E1830,
            supportsRomFolder = false
        )
    )
}

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isRecognizedGame: Boolean,
    val icon: Drawable
)

data class RomEntry(
    val title: String,
    val uri: String,
    val path: String,
    val artworkUri: String? = null,
    val launchId: String? = null
)

data class ControllerDevice(
    val deviceId: Int,
    val descriptor: String,
    val name: String,
    val vendorId: Int,
    val productId: Int
) {
    val identity: String
        get() = "$vendorId:$productId:$descriptor"
}

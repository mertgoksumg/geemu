package com.mertg.geemu.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mertg.geemu.data.AppCatalog
import com.mertg.geemu.data.ArtworkProvider
import com.mertg.geemu.data.ArtworkSyncManager
import com.mertg.geemu.data.ArtworkSyncProgress
import com.mertg.geemu.R
import com.mertg.geemu.data.GeemuPreferences
import com.mertg.geemu.data.GameArtwork
import com.mertg.geemu.data.GameArtworkRepository
import com.mertg.geemu.data.RomScanner
import com.mertg.geemu.data.SwitchContentFilter
import com.mertg.geemu.data.VitaLibraryScanner
import com.mertg.geemu.model.ControllerDevice
import com.mertg.geemu.model.GameSystem
import com.mertg.geemu.model.InstalledApp
import com.mertg.geemu.model.RomEntry
import com.mertg.geemu.model.SystemCatalog
import com.mertg.geemu.model.SystemId
import com.mertg.geemu.platform.EmulatorLauncher
import com.mertg.geemu.system.GeemuAccessibilityService
import com.mertg.geemu.system.RotationController
import com.mertg.geemu.ui.theme.GeemuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface GeemuScreen {
    data object Home : GeemuScreen
    data class Library(val systemId: SystemId) : GeemuScreen
    data object Settings : GeemuScreen
    data object AppPicker : GeemuScreen
}

private data class ControllerCommand(val serial: Int = 0, val keyCode: Int = KeyEvent.KEYCODE_UNKNOWN)

@Composable
fun GeemuApp(
    controllers: List<ControllerDevice>,
    permissionRevision: Int,
    onControllerKeyHandlerChanged: (((Int) -> Boolean)?) -> Unit,
    onOpenRotationPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { GeemuPreferences(context) }
    val catalog = remember { AppCatalog(context) }
    var screen: GeemuScreen by remember { mutableStateOf(GeemuScreen.Home) }
    var selectedSystem by remember { mutableIntStateOf(0) }
    var allApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var appsRevision by remember { mutableIntStateOf(0) }
    var folderRevision by remember { mutableIntStateOf(0) }
    var pendingFolderSystem by remember { mutableStateOf<SystemId?>(null) }
    var command by remember { mutableStateOf(ControllerCommand()) }

    LaunchedEffect(appsRevision) {
        allApps = withContext(Dispatchers.IO) { catalog.launchableApps() }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val systemId = pendingFolderSystem
        if (uri != null && systemId != null) {
            RomScanner.persistFolderPermission(context, uri)
            preferences.setRomFolder(systemId, uri)
            folderRevision++
        }
        pendingFolderSystem = null
    }

    fun goBack() {
        screen = when (screen) {
            GeemuScreen.Home -> GeemuScreen.Home
            is GeemuScreen.Library -> GeemuScreen.Home
            GeemuScreen.Settings -> GeemuScreen.Home
            GeemuScreen.AppPicker -> GeemuScreen.Settings
        }
    }

    val currentKeyHandler = rememberUpdatedState<(Int) -> Boolean> { keyCode ->
        when (screen) {
            GeemuScreen.Home -> when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BUTTON_L1 -> {
                    selectedSystem = (selectedSystem - 1 + SystemCatalog.systems.size) % SystemCatalog.systems.size
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BUTTON_R1 -> {
                    selectedSystem = (selectedSystem + 1) % SystemCatalog.systems.size
                    true
                }
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    screen = GeemuScreen.Library(SystemCatalog.systems[selectedSystem].id)
                    true
                }
                KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> {
                    screen = GeemuScreen.Settings
                    true
                }
                else -> false
            }
            else -> when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                    goBack()
                    true
                }
                KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> {
                    screen = GeemuScreen.Settings
                    true
                }
                else -> {
                    command = ControllerCommand(command.serial + 1, keyCode)
                    true
                }
            }
        }
    }

    DisposableEffect(onControllerKeyHandlerChanged) {
        val handler: (Int) -> Boolean = { currentKeyHandler.value(it) }
        onControllerKeyHandlerChanged(handler)
        onDispose { onControllerKeyHandlerChanged(null) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080A0D))
    ) {
        AnimatedContent(targetState = screen, label = "screen") { target ->
            when (target) {
                GeemuScreen.Home -> HomeScreen(
                    selectedIndex = selectedSystem,
                    onSelected = { selectedSystem = it },
                    onOpen = { screen = GeemuScreen.Library(SystemCatalog.systems[it].id) },
                    onSettings = { screen = GeemuScreen.Settings }
                )
                is GeemuScreen.Library -> {
                    val system = SystemCatalog.systems.first { it.id == target.systemId }
                    LibraryScreen(
                        system = system,
                        apps = allApps,
                        preferences = preferences,
                        catalog = catalog,
                        command = command,
                        folderRevision = folderRevision,
                        onBack = ::goBack,
                        onPickFolder = {
                            pendingFolderSystem = system.id
                            val initialUri = preferences.romFolder(system.id) ?: if (system.id == SystemId.VITA) {
                                Uri.parse("content://org.vita3k.emulator.provider/root/VitaRoot")
                            } else null
                            folderPicker.launch(initialUri)
                        },
                        onManageAndroidGames = { screen = GeemuScreen.AppPicker }
                    )
                }
                GeemuScreen.Settings -> SettingsScreen(
                    controllers = controllers,
                    apps = allApps,
                    command = command,
                    preferences = preferences,
                    canRotate = remember(permissionRevision) { RotationController.canControlSystemRotation(context) },
                    accessibilityRunning = remember(permissionRevision) { GeemuAccessibilityService.isRunning() },
                    onBack = ::goBack,
                    onManageGames = { screen = GeemuScreen.AppPicker },
                    onOpenRotationPermission = onOpenRotationPermission,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings
                )
                GeemuScreen.AppPicker -> AppPickerScreen(
                    apps = allApps,
                    command = command,
                    preferences = preferences,
                    onChanged = { appsRevision++ },
                    onBack = ::goBack
                )
            }
        }
    }

}

@Composable
private fun HomeScreen(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onSettings: () -> Unit
) {
    val selected = SystemCatalog.systems[selectedIndex]
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AnimatedContent(
            targetState = selectedIndex,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val forward = (targetState - initialState + SystemCatalog.systems.size) % SystemCatalog.systems.size == 1
                if (forward) {
                    (slideInHorizontally(tween(360)) { it / 5 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(360)) { -it / 5 } + fadeOut(tween(220)))
                } else {
                    (slideInHorizontally(tween(360)) { -it / 5 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(360)) { it / 5 } + fadeOut(tween(220)))
                }
            },
            label = "home_carousel"
        ) { carouselIndex ->
            Row(modifier = Modifier.fillMaxSize()) {
                (-3..3).forEach { offset ->
                    val index = (carouselIndex + offset + SystemCatalog.systems.size) % SystemCatalog.systems.size
                    ArtworkSlice(
                        system = SystemCatalog.systems[index],
                        selected = offset == 0,
                        modifier = Modifier.weight(if (offset == 0) 1.10f else 1f),
                        onClick = {
                            if (offset == 0) onOpen(index) else onSelected(index)
                        }
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.28f),
                        0.28f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f)
                    )
                )
        )
        Column(
            modifier = Modifier.align(Alignment.Center).padding(bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(targetState = selected.title, label = "system_title") { title ->
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 42.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = (-1.6).sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        Text(
            text = "GEEMU",
            modifier = Modifier.align(Alignment.TopStart).padding(18.dp),
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Text(
            text = "⚙",
            modifier = Modifier.align(Alignment.TopEnd).clickable(onClick = onSettings).padding(18.dp),
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 17.sp
        )
        CompactHintBar(
            hints = listOf("◉  MENU", "A  SELECT", "◀ ▶  CHOOSE"),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ArtworkSlice(
    system: GameSystem,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    CarouselPanel(selected = selected, modifier = modifier, onClick = onClick) {
        Image(
            painter = painterResource(systemArtworkResource(system.id)),
            contentDescription = system.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center
        )
    }
}

@Composable
private fun CarouselPanel(
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    artwork: @Composable () -> Unit
) {
    val scale by animateFloatAsState(if (selected) 1.16f else 0.94f, tween(220), label = "carousel_panel_scale")
    val dim by animateFloatAsState(if (selected) 0.04f else 0.34f, tween(220), label = "carousel_panel_dim")
    Box(
        modifier = modifier
            .fillMaxHeight()
            .zIndex(if (selected) 2f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (selected) 28.dp.toPx() else 0f
            }
            .clickable(onClick = onClick)
    ) {
        artwork()
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    }
}

private fun systemArtworkResource(systemId: SystemId): Int = when (systemId) {
    SystemId.VITA -> R.drawable.system_vita
    SystemId.SWITCH_EDEN -> R.drawable.system_switch_eden
    SystemId.SWITCH_CITRON -> R.drawable.system_switch_citron
    SystemId.WII_U -> R.drawable.system_wii_u
    SystemId.PLAYSTATION_2 -> R.drawable.system_playstation_2
    SystemId.ANDROID -> R.drawable.system_android
}

@Composable
private fun LibraryScreen(
    system: GameSystem,
    apps: List<InstalledApp>,
    preferences: GeemuPreferences,
    catalog: AppCatalog,
    command: ControllerCommand,
    folderRevision: Int,
    onBack: () -> Unit,
    onPickFolder: () -> Unit,
    onManageAndroidGames: () -> Unit
) {
    val context = LocalContext.current
    val emulator = remember(system.id, apps) { catalog.findEmulator(system, apps) }
    val folderUri = preferences.romFolder(system.id)
    var roms by remember(system.id, folderUri, folderRevision) { mutableStateOf<List<RomEntry>>(emptyList()) }
    var selectedIndex by remember(system.id) { mutableIntStateOf(0) }
    val manual = preferences.manualGamePackages()
    val dedicatedEmulatorPackages = SystemCatalog.systems
        .filter { it.id != SystemId.ANDROID }
        .flatMap { it.packageCandidates }
        .toSet()
    val emulatorAliases = SystemCatalog.systems
        .filter { it.id != SystemId.ANDROID }
        .flatMap { it.emulatorAliases }
    val androidGames = apps.filter { app ->
        (app.isRecognizedGame || app.packageName in manual) &&
            app.packageName !in dedicatedEmulatorPackages &&
            emulatorAliases.none { app.label.contains(it, ignoreCase = true) }
    }
    val artworkRepository = remember { GameArtworkRepository(context, preferences) }
    var artwork by remember(system.id) { mutableStateOf<Map<String, GameArtwork>>(emptyMap()) }

    fun romKey(entry: RomEntry) = "${system.id}:${entry.launchId ?: entry.uri}"
    fun androidKey(app: InstalledApp) = "ANDROID:${app.packageName}"

    fun openEmulator() {
        if (emulator == null || !EmulatorLauncher.launchApp(context, emulator)) {
            toast(context, "${system.eyebrow} kurulu görünmüyor")
        }
    }

    LaunchedEffect(system.id, folderUri, folderRevision) {
        roms = withContext(Dispatchers.IO) {
            val manualVita = if (system.id == SystemId.VITA) {
                preferences.manualVitaGames().map { (titleId, title) ->
                    RomEntry(title = title, uri = "vita://$titleId", path = "MANUEL", launchId = titleId)
                }
            } else emptyList()
            val scanned = if (folderUri == null || !system.supportsRomFolder) emptyList() else {
                val scanned = if (system.id == SystemId.VITA) {
                    VitaLibraryScanner(context).scan(folderUri)
                } else {
                    RomScanner(context).scan(folderUri, system.romExtensions)
                }
                if (system.id == SystemId.SWITCH_EDEN || system.id == SystemId.SWITCH_CITRON) {
                    SwitchContentFilter.bootableGames(scanned)
                } else {
                    scanned
                }
            }
            (scanned + manualVita).distinctBy { it.launchId ?: it.uri }.sortedBy { it.title.lowercase() }
        }
    }

    LaunchedEffect(system.id, roms, androidGames) {
        artwork = withContext(Dispatchers.IO) {
            if (system.id == SystemId.ANDROID) {
                androidGames.mapNotNull { app -> artworkRepository.cached(androidKey(app))?.let { androidKey(app) to it } }.toMap()
            } else {
                roms.mapNotNull { rom -> artworkRepository.cached(romKey(rom))?.let { romKey(rom) to it } }.toMap()
            }
        }
    }

    LaunchedEffect(command.serial) {
        if (command.serial == 0) return@LaunchedEffect
        val entries = if (system.id == SystemId.ANDROID) androidGames.size else roms.size
        val maxIndex = (entries - 1).coerceAtLeast(0)
        when (command.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BUTTON_L1 ->
                if (entries > 0) selectedIndex = (selectedIndex - 1 + entries) % entries
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BUTTON_R1 ->
                if (entries > 0) selectedIndex = (selectedIndex + 1) % entries
            KeyEvent.KEYCODE_DPAD_UP -> selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_DOWN -> selectedIndex = (selectedIndex + 1).coerceAtMost(maxIndex)
            KeyEvent.KEYCODE_BUTTON_X -> {
                if (system.id == SystemId.ANDROID) onManageAndroidGames()
                else if (system.supportsRomFolder) onPickFolder()
            }
            KeyEvent.KEYCODE_BUTTON_Y -> if (system.id != SystemId.ANDROID) openEmulator()
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                when {
                    system.id == SystemId.ANDROID && androidGames.isNotEmpty() -> {
                        EmulatorLauncher.launchPackage(context, androidGames[selectedIndex.coerceAtMost(maxIndex)].packageName)
                    }
                    system.id == SystemId.ANDROID -> onManageAndroidGames()
                    roms.isNotEmpty() -> {
                        val rom = roms[selectedIndex.coerceAtMost(maxIndex)]
                        if (system.id == SystemId.VITA) {
                            rom.launchId?.let { EmulatorLauncher.launchVitaTitle(context, it) } ?: false
                        } else {
                            emulator?.let { EmulatorLauncher.launchRom(context, it, rom) } ?: false
                        }
                    }
                    system.supportsRomFolder && folderUri == null -> onPickFolder()
                    else -> openEmulator()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF080A0D), Color(system.deepColor).copy(alpha = 0.72f), Color(0xFF080A0D))
                )
            )
    ) {
        Column(Modifier.fillMaxSize()) {
            if (system.id == SystemId.ANDROID) {
                AndroidLibrary(
                    games = androidGames,
                    selectedIndex = selectedIndex,
                    artwork = artwork,
                    onSelected = { selectedIndex = it },
                    onManageAndroidGames = onManageAndroidGames
                )
            } else {
                EmulatorLibrary(
                    system = system,
                    emulator = emulator,
                    roms = roms,
                    selectedIndex = selectedIndex,
                    artwork = artwork,
                    onSelected = { selectedIndex = it },
                    folderUri = folderUri,
                    onPickFolder = onPickFolder,
                    onOpenEmulator = ::openEmulator,
                    onOpenRom = { rom ->
                        val launched = if (system.id == SystemId.VITA) {
                            rom.launchId?.let { EmulatorLauncher.launchVitaTitle(context, it) } == true
                        } else {
                            emulator?.let { EmulatorLauncher.launchRom(context, it, rom) } == true
                        }
                        if (!launched) {
                            toast(context, "Oyun doğrudan açılamadı; emülatör eşleşmesini kontrol et")
                        }
                    }
                )
            }
        }
        LibraryHeader(onBack, Modifier.align(Alignment.TopStart))
        CompactHintBar(
            hints = listOf(
                "B  GERİ",
                "A  BAŞLAT",
                if (system.id == SystemId.ANDROID) "X  OYUN EKLE" else "X  KÜTÜPHANE",
                if (system.id != SystemId.ANDROID) "Y  EMÜLATÖR" else ""
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ColumnScope.EmulatorLibrary(
    system: GameSystem,
    emulator: InstalledApp?,
    roms: List<RomEntry>,
    selectedIndex: Int,
    artwork: Map<String, GameArtwork>,
    onSelected: (Int) -> Unit,
    folderUri: Uri?,
    onPickFolder: () -> Unit,
    onOpenEmulator: () -> Unit,
    onOpenRom: (RomEntry) -> Unit
) {
    when {
        folderUri == null && roms.isEmpty() -> ActionPosterCarousel(
            system = system,
            title = if (system.id == SystemId.VITA) "VITA KÜTÜPHANESİNİ BAĞLA" else "ROM KLASÖRÜ SEÇ",
            subtitle = if (system.id == SystemId.VITA) "DOSYA SEÇİCİDEN VITA3K ALANINI SEÇ" else system.romExtensions.joinToString(" · ").uppercase(),
            onClick = onPickFolder
        )
        roms.isEmpty() -> ActionPosterCarousel(
            system = system,
            title = "OYUN BULUNAMADI",
            subtitle = "Y ile ${system.eyebrow} aç · X ile klasörü değiştir",
            onClick = onOpenEmulator
        )
        else -> RomCarousel(
            system = system,
            roms = roms,
            selectedIndex = selectedIndex,
            artwork = artwork,
            onSelected = onSelected,
            onOpen = onOpenRom
        )
    }
}

@Composable
private fun ColumnScope.AndroidLibrary(
    games: List<InstalledApp>,
    selectedIndex: Int,
    artwork: Map<String, GameArtwork>,
    onSelected: (Int) -> Unit,
    onManageAndroidGames: () -> Unit
) {
    val context = LocalContext.current
    if (games.isEmpty()) {
        ActionPosterCarousel(
            system = SystemCatalog.systems.first { it.id == SystemId.ANDROID },
            title = "OYUN EKLE",
            subtitle = "Telefondaki uygulamalardan seç",
            onClick = onManageAndroidGames
        )
    } else {
        PosterCarousel(
            count = games.size,
            selectedIndex = selectedIndex,
            artworkRes = R.drawable.system_android,
            artworkUriAt = { artwork["ANDROID:${games[it].packageName}"]?.uri },
            titleAt = { artwork["ANDROID:${games[it].packageName}"]?.canonicalTitle ?: games[it].label },
            subtitleAt = { "ANDROID" },
            onSelected = onSelected,
            iconAt = { index -> AppIcon(games[index], Modifier.size(62.dp)) },
            onOpen = { index ->
                val app = games[index]
                    if (!EmulatorLauncher.launchPackage(context, app.packageName)) {
                        toast(context, "${app.label} açılamadı")
                    }
            }
        )
    }
}

@Composable
private fun ColumnScope.RomCarousel(
    system: GameSystem,
    roms: List<RomEntry>,
    selectedIndex: Int,
    artwork: Map<String, GameArtwork>,
    onSelected: (Int) -> Unit,
    onOpen: (RomEntry) -> Unit
) {
    PosterCarousel(
        count = roms.size,
        selectedIndex = selectedIndex,
        artworkRes = systemArtworkResource(system.id),
        artworkUriAt = { index -> artwork["${system.id}:${roms[index].launchId ?: roms[index].uri}"]?.uri ?: roms[index].artworkUri },
        titleAt = { index -> artwork["${system.id}:${roms[index].launchId ?: roms[index].uri}"]?.canonicalTitle ?: roms[index].title },
        subtitleAt = { if (system.id == SystemId.VITA) roms[it].launchId.orEmpty() else system.eyebrow },
        onSelected = onSelected,
        onOpen = { onOpen(roms[it]) }
    )
}

@Composable
private fun ColumnScope.ActionPosterCarousel(
    system: GameSystem,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    PosterCarousel(
        count = 1,
        selectedIndex = 0,
        artworkRes = systemArtworkResource(system.id),
        titleAt = { title },
        subtitleAt = { subtitle },
        onSelected = {},
        onOpen = { onClick() }
    )
}

@Composable
private fun ColumnScope.PosterCarousel(
    count: Int,
    selectedIndex: Int,
    artworkRes: Int,
    artworkUriAt: (Int) -> String? = { null },
    titleAt: (Int) -> String,
    subtitleAt: (Int) -> String,
    onSelected: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    iconAt: (@Composable (Int) -> Unit)? = null
) {
    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
        AnimatedContent(
            targetState = selectedIndex.coerceIn(0, (count - 1).coerceAtLeast(0)),
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (targetState >= initialState) {
                    (slideInHorizontally(tween(340)) { it / 6 } + fadeIn(tween(250))) togetherWith
                        (slideOutHorizontally(tween(340)) { -it / 6 } + fadeOut(tween(210)))
                } else {
                    (slideInHorizontally(tween(340)) { -it / 6 } + fadeIn(tween(250))) togetherWith
                        (slideOutHorizontally(tween(340)) { it / 6 } + fadeOut(tween(210)))
                }
            },
            label = "game_carousel"
        ) { carouselIndex ->
            Row(Modifier.fillMaxSize()) {
                val activeOffsets = gameCarouselOffsets(count)
                (-3..3).forEach { offset ->
                    val selected = offset == 0
                    if (offset !in activeOffsets || count == 0) {
                        Spacer(Modifier.weight(if (selected) 1.10f else 1f).fillMaxHeight())
                        return@forEach
                    }
                    val index = (carouselIndex + offset).floorMod(count)
                    CarouselPanel(
                        selected = selected,
                        modifier = Modifier.weight(if (selected) 1.10f else 1f),
                        onClick = { if (selected) onOpen(index) else onSelected(index) }
                    ) {
                        GameArtworkImage(
                            artworkUri = artworkUriAt(index),
                            fallbackRes = artworkRes,
                            contentDescription = titleAt(index),
                            alignment = Alignment.Center
                        )
                    }
                }
            }
        }

        if (count > 0) {
            val safeIndex = selectedIndex.coerceIn(0, count - 1)
            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = maxWidth * 0.28f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                iconAt?.invoke(safeIndex)
                if (iconAt != null) Spacer(Modifier.height(8.dp))
                Text(
                    text = titleAt(safeIndex),
                    color = Color.White,
                    fontSize = 42.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = (-1.2).sp,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = subtitleAt(safeIndex),
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GameArtworkImage(
    artworkUri: String?,
    fallbackRes: Int,
    contentDescription: String,
    alignment: Alignment
) {
    var bitmap by remember(artworkUri) { mutableStateOf<ImageBitmap?>(null) }
    val context = LocalContext.current
    LaunchedEffect(artworkUri) {
        bitmap = if (artworkUri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(artworkUri))?.use(BitmapFactory::decodeStream)?.asImageBitmap()
            }.getOrNull()
        }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center
        )
    } else {
        Image(
            painter = painterResource(fallbackRes),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = alignment
        )
    }
}

@Composable
private fun LibraryHeader(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = "‹",
        modifier = modifier
            .clickable(onClick = onBack)
            .padding(start = 18.dp, top = 10.dp, end = 20.dp, bottom = 18.dp),
        color = Color.White.copy(alpha = 0.80f),
        fontSize = 30.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Light
    )
}

private fun gameCarouselOffsets(count: Int): Set<Int> = when (count.coerceAtMost(7)) {
    0 -> emptySet()
    1 -> setOf(0)
    2 -> setOf(0, 1)
    3 -> (-1..1).toSet()
    4 -> (-1..2).toSet()
    5 -> (-2..2).toSet()
    6 -> (-2..3).toSet()
    else -> (-3..3).toSet()
}

private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor

@Composable
private fun CompactHintBar(hints: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(bottom = 9.dp)
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        hints.filter(String::isNotBlank).forEach {
            Text(it, color = Color.White.copy(alpha = 0.76f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AppPickerScreen(
    apps: List<InstalledApp>,
    command: ControllerCommand,
    preferences: GeemuPreferences,
    onChanged: () -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var manualPackages by remember { mutableStateOf(preferences.manualGamePackages()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val filtered = apps.filter {
        query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)
    }
    val gridState = rememberLazyGridState()

    LaunchedEffect(selectedIndex, filtered.size) {
        if (filtered.isNotEmpty()) gridState.animateScrollToItem(selectedIndex.coerceAtMost(filtered.lastIndex))
    }

    fun toggle(app: InstalledApp) {
        val manuallySelected = app.packageName in manualPackages
        preferences.setManualGame(app.packageName, !manuallySelected)
        manualPackages = preferences.manualGamePackages()
        onChanged()
    }

    LaunchedEffect(command.serial) {
        if (command.serial == 0 || filtered.isEmpty()) return@LaunchedEffect
        val maxIndex = filtered.lastIndex
        when (command.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> selectedIndex = (selectedIndex + 1).coerceAtMost(maxIndex)
            KeyEvent.KEYCODE_DPAD_UP -> selectedIndex = (selectedIndex - 4).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_DOWN -> selectedIndex = (selectedIndex + 4).coerceAtMost(maxIndex)
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                toggle(filtered[selectedIndex.coerceAtMost(maxIndex)])
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF090C10))) {
        TopBar("ANDROID OYUNLARI", "TÜM UYGULAMALARDAN SEÇ", onBack)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Uygulama ara") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None)
            )
            Spacer(Modifier.width(16.dp))
            Text("${apps.count { it.isRecognizedGame }} otomatik · ${manualPackages.size} elle", color = Color.White.copy(alpha = 0.6f))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = gridState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filtered.size, key = { filtered[it].packageName }) { index ->
                val app = filtered[index]
                val manuallySelected = app.packageName in manualPackages
                Card(
                    modifier = Modifier
                        .border(
                            if (index == selectedIndex) 3.dp else 0.dp,
                            if (index == selectedIndex) Color(0xFFB7F500) else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { toggle(app) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141920))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(app, Modifier.size(44.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.label, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(
                                if (app.isRecognizedGame) "Android oyun olarak tanıdı" else app.packageName,
                                color = if (app.isRecognizedGame) Color(0xFF8BEA62) else Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Checkbox(checked = app.isRecognizedGame || manuallySelected, onCheckedChange = null)
                    }
                }
            }
        }
        HintBar("B  GERİ", "A  EKLE / ÇIKAR", "TÜM UYGULAMALAR")
    }
}

@Composable
private fun SettingsScreen(
    controllers: List<ControllerDevice>,
    apps: List<InstalledApp>,
    command: ControllerCommand,
    preferences: GeemuPreferences,
    canRotate: Boolean,
    accessibilityRunning: Boolean,
    onBack: () -> Unit,
    onManageGames: () -> Unit,
    onOpenRotationPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var rawgApiKey by remember { mutableStateOf(preferences.rawgApiKey()) }
    var screenScraperDevId by remember { mutableStateOf(preferences.screenScraperDeveloperId()) }
    var screenScraperDevPassword by remember { mutableStateOf(preferences.screenScraperDeveloperPassword()) }
    var screenScraperUser by remember { mutableStateOf(preferences.screenScraperUser()) }
    var screenScraperPassword by remember { mutableStateOf(preferences.screenScraperPassword()) }
    var syncProgress by remember { mutableStateOf<ArtworkSyncProgress?>(null) }
    var syncRunning by remember { mutableStateOf(false) }
    var vitaTitleId by remember { mutableStateOf("") }
    var vitaTitle by remember { mutableStateOf("") }
    var manualVitaGames by remember(revision) { mutableStateOf(preferences.manualVitaGames()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val controllerRevision = revision
    val permissionStartIndex = controllers.size
    val actionCount = controllers.size + 3
    val listState = rememberLazyListState()

    fun startArtworkSync(provider: ArtworkProvider, missingOnly: Boolean) {
        if (syncRunning) return
        val configured = when (provider) {
            ArtworkProvider.RAWG -> rawgApiKey.isNotBlank()
            ArtworkProvider.SCREEN_SCRAPER -> screenScraperDevId.isNotBlank() && screenScraperDevPassword.isNotBlank()
        }
        if (!configured) {
            toast(context, if (provider == ArtworkProvider.RAWG) "Önce RAWG API key gir" else "Önce ScreenScraper geliştirici bilgilerini gir")
            return
        }
        syncRunning = true
        syncProgress = ArtworkSyncProgress(0, 0, 0, "Kütüphane taranıyor")
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ArtworkSyncManager(context, preferences, apps).sync(provider, missingOnly) { progress ->
                    scope.launch { syncProgress = progress }
                }
            }
            syncProgress = result
            syncRunning = false
            toast(context, "${result.downloaded} görsel eşitlendi")
        }
    }

    LaunchedEffect(selectedIndex) {
        val targetItem = when {
            selectedIndex < controllers.size -> selectedIndex + 3
            controllers.isEmpty() && selectedIndex == permissionStartIndex -> 5
            controllers.isEmpty() && selectedIndex == permissionStartIndex + 1 -> 6
            controllers.isEmpty() -> 8
            selectedIndex == permissionStartIndex -> controllers.size + 4
            selectedIndex == permissionStartIndex + 1 -> controllers.size + 5
            else -> controllers.size + 7
        }
        listState.animateScrollToItem(targetItem)
    }

    LaunchedEffect(command.serial) {
        if (command.serial == 0) return@LaunchedEffect
        when (command.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_DOWN -> selectedIndex = (selectedIndex + 1).coerceAtMost(actionCount - 1)
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                when {
                    selectedIndex < controllers.size -> {
                        val device = controllers[selectedIndex]
                        if (device.vendorId != GeemuPreferences.RAZER_VENDOR_ID) {
                            preferences.setControllerRegistered(device, !preferences.isControllerRegistered(device))
                            revision++
                        }
                    }
                    selectedIndex == permissionStartIndex -> onOpenRotationPermission()
                    selectedIndex == permissionStartIndex + 1 -> onOpenAccessibilitySettings()
                    selectedIndex == permissionStartIndex + 2 -> onManageGames()
                }
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF090C10)),
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { TopBar("AYARLAR", "OYUN MODU", onBack) }
        item { SettingsHeading("CONTROLLER PROFİLLERİ") }
        item {
            Text(
                "Razer Kishi otomatik kabul edilir. Diğer bir kolu bağla ve aşağıdaki anahtarı aç; bundan sonra aynı oyun modu davranışı uygulanır.",
                color = Color.White.copy(alpha = 0.62f),
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
                fontSize = 13.sp
            )
        }
        if (controllers.isEmpty()) {
            item { SettingsCard("Bağlı controller yok", "Kolu bağladığında adı ve USB/Bluetooth kimliği burada görünür.") }
        } else {
            items(controllers.size, key = { controllers[it].identity }) { index ->
                val device = controllers[index]
                val registered = remember(device.identity, controllerRevision) {
                    preferences.isControllerRegistered(device)
                }
                SettingsToggleCard(
                    title = device.name,
                    detail = "VID ${device.vendorId} · PID ${device.productId}" +
                        if (device.vendorId == GeemuPreferences.RAZER_VENDOR_ID) " · Razer otomatik" else "",
                    checked = registered,
                    enabled = device.vendorId != GeemuPreferences.RAZER_VENDOR_ID,
                    selected = index == selectedIndex,
                    onChecked = {
                        preferences.setControllerRegistered(device, it)
                        revision++
                    }
                )
            }
        }
        item { SettingsHeading("OTOMASYON İZİNLERİ") }
        item {
            PermissionCard(
                title = "Ekran yönünü değiştir",
                detail = if (canRotate) "Hazır · yatay/dikey kilidi uygulanabilir" else "Bir kez sistem ayarı izni verilmeli",
                granted = canRotate,
                selected = selectedIndex == permissionStartIndex,
                onClick = onOpenRotationPermission
            )
        }
        item {
            PermissionCard(
                title = "Controller oyun modu",
                detail = if (accessibilityRunning) "Hazır · çıkarılınca Ana Ekran komutu gönderilebilir" else "Otomatik açma ve çıkarma davranışı için Geemu Oyun Modu’nu etkinleştir",
                granted = accessibilityRunning,
                selected = selectedIndex == permissionStartIndex + 1,
                onClick = onOpenAccessibilitySettings
            )
        }
        item { SettingsHeading("KÜTÜPHANE") }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 5.dp)) {
                Text("VITA · ANDROID 16 YEDEĞİ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "Vita3K dosya alanı görünmüyorsa oyunun adını ve Title ID'sini ekle; oyun karttan doğrudan açılır.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = vitaTitleId,
                        onValueChange = { vitaTitleId = it.uppercase().take(9) },
                        modifier = Modifier.width(170.dp),
                        singleLine = true,
                        label = { Text("Title ID · PCSE00000") }
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = vitaTitle,
                        onValueChange = { vitaTitle = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Oyunun tam adı") }
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        enabled = Regex("(?i)[A-Z]{4}\\d{5}").matches(vitaTitleId) && vitaTitle.isNotBlank(),
                        onClick = {
                            preferences.addManualVitaGame(vitaTitleId, vitaTitle)
                            vitaTitleId = ""
                            vitaTitle = ""
                            revision++
                        }
                    ) { Text("EKLE") }
                }
                manualVitaGames.forEach { (titleId, title) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("$title · $titleId", color = Color.White.copy(alpha = 0.66f), fontSize = 10.sp, modifier = Modifier.weight(1f))
                        Text(
                            "SİL",
                            color = Color(0xFFFF8C8C),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                preferences.removeManualVitaGame(titleId)
                                revision++
                            }.padding(8.dp)
                        )
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 5.dp)) {
                Text("OYUN KAPAKLARI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "Kütüphane açılırken internet kullanılmaz. Anahtarını girip istediğin servisi yalnızca aşağıdaki eşitleme düğmeleriyle çalıştır.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(
                    value = rawgApiKey,
                    onValueChange = {
                        rawgApiKey = it
                        preferences.setRawgApiKey(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("RAWG API key") },
                    visualTransformation = PasswordVisualTransformation()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { startArtworkSync(ArtworkProvider.RAWG, missingOnly = true) },
                        enabled = !syncRunning,
                        modifier = Modifier.weight(1f)
                    ) { Text("RAWG · EKSİKLERİ EŞİTLE") }
                    Button(
                        onClick = { startArtworkSync(ArtworkProvider.RAWG, missingOnly = false) },
                        enabled = !syncRunning,
                        modifier = Modifier.weight(1f)
                    ) { Text("RAWG · TAM EŞİTLE") }
                }
                Spacer(Modifier.height(14.dp))
                Text("SCREENSCRAPER", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(
                    "ScreenScraper tek key kullanmaz: geliştirici kimliği ve parolası zorunlu, kullanıcı hesabı isteğe bağlıdır.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = screenScraperDevId,
                        onValueChange = {
                            screenScraperDevId = it
                            preferences.setScreenScraperCredentials(it, screenScraperDevPassword, screenScraperUser, screenScraperPassword)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Developer ID") }
                    )
                    OutlinedTextField(
                        value = screenScraperDevPassword,
                        onValueChange = {
                            screenScraperDevPassword = it
                            preferences.setScreenScraperCredentials(screenScraperDevId, it, screenScraperUser, screenScraperPassword)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Developer password") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = screenScraperUser,
                        onValueChange = {
                            screenScraperUser = it
                            preferences.setScreenScraperCredentials(screenScraperDevId, screenScraperDevPassword, it, screenScraperPassword)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Kullanıcı adı · isteğe bağlı") }
                    )
                    OutlinedTextField(
                        value = screenScraperPassword,
                        onValueChange = {
                            screenScraperPassword = it
                            preferences.setScreenScraperCredentials(screenScraperDevId, screenScraperDevPassword, screenScraperUser, it)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Kullanıcı parolası") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { startArtworkSync(ArtworkProvider.SCREEN_SCRAPER, missingOnly = true) },
                        enabled = !syncRunning,
                        modifier = Modifier.weight(1f)
                    ) { Text("SS · EKSİKLERİ EŞİTLE") }
                    Button(
                        onClick = { startArtworkSync(ArtworkProvider.SCREEN_SCRAPER, missingOnly = false) },
                        enabled = !syncRunning,
                        modifier = Modifier.weight(1f)
                    ) { Text("SS · TAM EŞİTLE") }
                }
                syncProgress?.let { progress ->
                    Spacer(Modifier.height(9.dp))
                    Text(
                        when {
                            progress.finished -> "Tamamlandı · ${progress.downloaded}/${progress.total} görsel bulundu"
                            progress.total == 0 -> progress.currentTitle
                            else -> "${progress.completed}/${progress.total} · ${progress.downloaded} bulundu · ${progress.currentTitle}"
                        },
                        color = if (progress.finished) Color(0xFF8BEA62) else Color.White.copy(alpha = 0.68f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        item {
            SettingsActionCard(
                title = "Android oyunlarını seç",
                detail = "Telefondaki tüm başlatılabilir uygulamalar arasından oyun klasörüne ekle",
                selected = selectedIndex == permissionStartIndex + 2,
                onClick = onManageGames
            )
        }
        item {
            Text(
                "Kayıtlı özel controller profili: ${preferences.registeredControllerIds().size}",
                color = Color.White.copy(alpha = 0.42f),
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(Color.Black.copy(alpha = 0.48f))
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Text("‹", color = Color.White, fontSize = 34.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 19.sp, letterSpacing = 0.7.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.46f), fontSize = 9.sp, letterSpacing = 1.8.sp)
        }
        if (onAction != null) {
            Text("⚙  AYARLAR", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onAction).padding(10.dp))
        }
    }
}

@Composable
private fun HintBar(vararg hints: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        hints.filter { it.isNotBlank() }.forEach { hint ->
            Text(hint, color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyLibrary(title: String, detail: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(42.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("◇", color = Color.White.copy(alpha = 0.3f), fontSize = 48.sp)
        Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(detail, color = Color.White.copy(alpha = 0.58f), textAlign = TextAlign.Center, fontSize = 13.sp)
    }
}

@Composable
private fun StatusPill(text: String, positive: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (positive) Color(0xFF8BEA62) else Color(0xFFFFB84D)))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
    }
}

@Composable
private fun AppCard(app: InstalledApp, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .border(
                if (selected) 3.dp else 0.dp,
                if (selected) Color(0xFFB7F500) else Color.Transparent,
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151A21))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppIcon(app, Modifier.size(66.dp))
            Spacer(Modifier.height(10.dp))
            Text(app.label, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AppIcon(app: InstalledApp, modifier: Modifier = Modifier) {
    val bitmap = remember(app.packageName) { app.icon.toImageBitmap() }
    Image(
        bitmap = bitmap,
        contentDescription = app.label,
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Fit
    )
}

private fun Drawable.toImageBitmap(): ImageBitmap {
    val width = intrinsicWidth.takeIf { it > 0 } ?: 128
    val height = intrinsicHeight.takeIf { it > 0 } ?: 128
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

@Composable
private fun SettingsHeading(text: String) {
    Text(
        text,
        color = Color(0xFF8BEA62),
        fontWeight = FontWeight.Black,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(title: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 5.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF151A21)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(detail, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsToggleCard(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    selected: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 5.dp)
            .border(
                if (selected) 2.dp else 0.dp,
                if (selected) Color(0xFFB7F500) else Color.Transparent,
                RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF151A21)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(detail, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    detail: String,
    granted: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 5.dp)
            .border(
                if (selected) 2.dp else 0.dp,
                if (selected) Color(0xFFB7F500) else Color.Transparent,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF151A21)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(detail, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            Text(if (granted) "HAZIR" else "AYARLA", color = if (granted) Color(0xFF8BEA62) else Color(0xFFFFB84D), fontWeight = FontWeight.Black, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsActionCard(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 5.dp)
            .border(
                if (selected) 2.dp else 0.dp,
                if (selected) Color(0xFFB7F500) else Color.Transparent,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF151A21)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(detail, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            Text("AÇ  ›", color = Color(0xFF8BEA62), fontWeight = FontWeight.Black, fontSize = 11.sp)
        }
    }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

@Preview(name = "Ana ekran", widthDp = 915, heightDp = 412, showBackground = true)
@Composable
private fun HomeScreenPreview() {
    GeemuTheme {
        HomeScreen(selectedIndex = 1, onSelected = {}, onOpen = {}, onSettings = {})
    }
}

@Composable
private fun PlatformLibraryPreview(systemId: SystemId) {
    val system = SystemCatalog.systems.first { it.id == systemId }
    val sampleTitles = listOf(
        "NEON HORIZON",
        "FOREST LEGENDS",
        "CULT OF THE LAMB",
        "STARLIGHT CITY",
        "MIDNIGHT QUEST",
        "PIXEL RACERS"
    )
    GeemuTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF080A0D), Color(system.deepColor), Color(0xFF080A0D))
                    )
                )
        ) {
            Column(Modifier.fillMaxSize()) {
                PosterCarousel(
                    count = sampleTitles.size,
                    selectedIndex = 2,
                    artworkRes = systemArtworkResource(system.id),
                    titleAt = { sampleTitles[it] },
                    subtitleAt = { system.eyebrow },
                    onSelected = {},
                    onOpen = {}
                )
            }
            LibraryHeader({}, Modifier.align(Alignment.TopStart))
            CompactHintBar(
                listOf("B  GERİ", "A  BAŞLAT", "X  KÜTÜPHANE", "Y  EMÜLATÖR"),
                Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Preview(name = "İç ekran · Vita", widthDp = 915, heightDp = 412, showBackground = true)
@Composable
private fun VitaLibraryPreview() = PlatformLibraryPreview(SystemId.VITA)

@Preview(name = "İç ekran · Switch Eden", widthDp = 915, heightDp = 412, showBackground = true)
@Composable
private fun EdenLibraryPreview() = PlatformLibraryPreview(SystemId.SWITCH_EDEN)

@Preview(name = "İç ekran · Switch Citron", widthDp = 915, heightDp = 412, showBackground = true)
@Composable
private fun CitronLibraryPreview() = PlatformLibraryPreview(SystemId.SWITCH_CITRON)

@Preview(name = "İç ekran · Wii U", widthDp = 915, heightDp = 412, showBackground = true)
@Composable
private fun WiiULibraryPreview() = PlatformLibraryPreview(SystemId.WII_U)

@Preview(name = "İç ekran · PlayStation 2", widthDp = 915, heightDp = 412, showBackground = true)
@Composable
private fun PlayStation2LibraryPreview() = PlatformLibraryPreview(SystemId.PLAYSTATION_2)

@Preview(name = "İç ekran · Android", widthDp = 915, heightDp = 412, showBackground = true)
@Composable
private fun AndroidLibraryPreview() = PlatformLibraryPreview(SystemId.ANDROID)

@Preview(name = "Ayarlar", widthDp = 915, heightDp = 412, showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    val context = LocalContext.current
    GeemuTheme {
        SettingsScreen(
            controllers = emptyList(),
            apps = emptyList(),
            command = ControllerCommand(),
            preferences = GeemuPreferences(context),
            canRotate = true,
            accessibilityRunning = true,
            onBack = {},
            onManageGames = {},
            onOpenRotationPermission = {},
            onOpenAccessibilitySettings = {}
        )
    }
}

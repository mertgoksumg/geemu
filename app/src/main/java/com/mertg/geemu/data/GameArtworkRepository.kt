package com.mertg.geemu.data

import android.content.Context
import com.mertg.geemu.model.SystemId
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest

data class GameArtwork(val canonicalTitle: String, val uri: String)
enum class ArtworkProvider { RAWG, SCREEN_SCRAPER }

class GameArtworkRepository(private val context: Context, private val preferences: GeemuPreferences) {
    // v2 ignores the old Wikipedia-backed cache, which could contain unrelated photos.
    private val index = context.getSharedPreferences("game_artwork_index_v2", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "game_artwork_v2").apply { mkdirs() }

    fun cached(cacheKey: String): GameArtwork? {
        val value = index.getString(cacheKey, null) ?: return null
        val separator = value.indexOf('\n')
        if (separator < 1) return null
        val file = File(value.substring(separator + 1))
        if (!file.isFile) return null
        return GameArtwork(value.substring(0, separator), file.toURI().toString())
    }

    fun resolve(
        cacheKey: String,
        query: String,
        systemId: SystemId,
        sourceName: String? = null,
        packageName: String? = null
    ): GameArtwork? {
        cached(cacheKey)?.let { return it }
        if (query.isBlank()) return null
        val accountFree = when (systemId) {
            SystemId.ANDROID -> packageName?.let { resolveFromGooglePlay(cacheKey, query, it) }
            SystemId.PLAYSTATION_2, SystemId.VITA, SystemId.WII_U ->
                resolveFromLibretro(cacheKey, query, systemId, sourceName)
            SystemId.SWITCH_EDEN, SystemId.SWITCH_CITRON -> null
        }
        if (accountFree != null) return accountFree
        val apiKey = preferences.steamGridDbApiKey()
        return if (apiKey.isNotBlank()) resolveFromSteamGridDb(cacheKey, query, apiKey) else null
    }

    fun resolveFromProvider(
        provider: ArtworkProvider,
        cacheKey: String,
        query: String,
        systemId: SystemId,
        force: Boolean
    ): GameArtwork? {
        if (!force) cached(cacheKey)?.let { return it }
        if (query.isBlank()) return null
        return when (provider) {
            ArtworkProvider.RAWG -> resolveFromRawg(cacheKey, query, systemId)
            ArtworkProvider.SCREEN_SCRAPER -> resolveFromScreenScraper(cacheKey, query, systemId)
        }
    }

    private fun resolveFromRawg(cacheKey: String, query: String, systemId: SystemId): GameArtwork? = runCatching {
        val apiKey = preferences.rawgApiKey()
        if (apiKey.isBlank()) return null
        val response = requestJson(
            "https://api.rawg.io/api/games?key=${urlEncode(apiKey)}&search=${urlEncode(query)}&search_precise=true&page_size=8",
            null
        )
        val results = response.optJSONArray("results") ?: return null
        val best = (0 until results.length()).mapNotNull(results::optJSONObject)
            .minByOrNull { rawgScore(query, systemId, it) } ?: return null
        val title = best.optString("name", query).ifBlank { query }
        val image = best.optString("background_image").takeIf(String::isNotBlank)
            ?: best.optString("background_image_additional").takeIf(String::isNotBlank)
            ?: return null
        saveArtwork(cacheKey, title, image)
    }.getOrNull()

    private fun rawgScore(query: String, systemId: SystemId, game: JSONObject): Int {
        val wanted = normalizedTitle(query)
        val found = normalizedTitle(game.optString("name"))
        var score = kotlin.math.abs(wanted.length - found.length)
        if (wanted != found) score += if (found.contains(wanted) || wanted.contains(found)) 8 else 40
        val platformNames = game.optJSONArray("platforms")?.let { platforms ->
            (0 until platforms.length()).mapNotNull { index ->
                platforms.optJSONObject(index)?.optJSONObject("platform")?.optString("name")?.lowercase()
            }
        }.orEmpty()
        val expected = when (systemId) {
            SystemId.PLAYSTATION_2 -> "playstation 2"
            SystemId.VITA -> "playstation vita"
            SystemId.WII_U -> "wii u"
            SystemId.SWITCH_EDEN, SystemId.SWITCH_CITRON -> "nintendo switch"
            SystemId.ANDROID -> "android"
        }
        if (platformNames.none { it.contains(expected) }) score += 25
        return score
    }

    private fun resolveFromScreenScraper(cacheKey: String, query: String, systemId: SystemId): GameArtwork? = runCatching {
        val devId = preferences.screenScraperDeveloperId()
        val devPassword = preferences.screenScraperDeveloperPassword()
        if (devId.isBlank() || devPassword.isBlank()) return null
        val parameters = buildList {
            add("devid=${urlEncode(devId)}")
            add("devpassword=${urlEncode(devPassword)}")
            add("softname=Geemu")
            add("output=json")
            add("recherche=${urlEncode(query)}")
            screenScraperSystemId(systemId)?.let { add("systemeid=$it") }
            preferences.screenScraperUser().takeIf(String::isNotBlank)?.let { add("ssid=${urlEncode(it)}") }
            preferences.screenScraperPassword().takeIf(String::isNotBlank)?.let { add("sspassword=${urlEncode(it)}") }
        }.joinToString("&")
        val response = requestJson("https://api.screenscraper.fr/api2/jeuRecherche.php?$parameters", null)
        val games = response.findArray("jeux") ?: return null
        val best = (0 until games.length()).mapNotNull(games::optJSONObject)
            .minByOrNull { screenScraperTitleScore(query, it) } ?: return null
        val title = screenScraperName(best) ?: query
        val media = best.findArray("medias") ?: return null
        val image = (0 until media.length()).mapNotNull(media::optJSONObject)
            .sortedBy { screenScraperMediaScore(it.optString("type")) }
            .firstNotNullOfOrNull { it.optString("url").takeIf(String::isNotBlank) }
            ?: return null
        saveArtwork(cacheKey, title, image)
    }.getOrNull()

    private fun screenScraperTitleScore(query: String, game: JSONObject): Int {
        val found = screenScraperName(game).orEmpty()
        val left = normalizedTitle(query)
        val right = normalizedTitle(found)
        return kotlin.math.abs(left.length - right.length) + if (left == right) 0 else if (right.contains(left)) 8 else 35
    }

    private fun screenScraperName(game: JSONObject): String? {
        val names = game.findArray("noms") ?: return game.optString("nom").takeIf(String::isNotBlank)
        val preferred = listOf("us", "wor", "eu", "ss", "jp")
        val objects = (0 until names.length()).mapNotNull(names::optJSONObject)
        return preferred.firstNotNullOfOrNull { region ->
            objects.firstOrNull { it.optString("region").equals(region, true) }
                ?.optString("text")?.takeIf(String::isNotBlank)
        } ?: objects.firstNotNullOfOrNull { it.optString("text").takeIf(String::isNotBlank) }
    }

    private fun screenScraperMediaScore(type: String): Int = when (type.lowercase()) {
        "box-2d", "box-2d-side" -> 0
        "mixrbv1", "mixrbv2" -> 1
        "fanart" -> 2
        "ss", "sstitle" -> 3
        else -> 10
    }

    private fun screenScraperSystemId(systemId: SystemId): Int? = when (systemId) {
        SystemId.PLAYSTATION_2 -> 58
        SystemId.VITA -> 62
        SystemId.WII_U -> 18
        SystemId.SWITCH_EDEN, SystemId.SWITCH_CITRON -> 225
        SystemId.ANDROID -> null
    }

    private fun JSONObject.findArray(key: String): org.json.JSONArray? {
        optJSONArray(key)?.let { return it }
        keys().forEach { childKey ->
            optJSONObject(childKey)?.findArray(key)?.let { return it }
        }
        return null
    }

    private fun resolveFromGooglePlay(cacheKey: String, fallbackTitle: String, packageName: String): GameArtwork? = runCatching {
        val html = requestText(
            "https://play.google.com/store/apps/details?id=${urlEncode(packageName)}&hl=en&gl=US",
            "text/html"
        )
        val canonicalTitle = Regex("""<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.htmlDecoded()
            ?.removeSuffix(" - Apps on Google Play")?.ifBlank { fallbackTitle }
            ?: fallbackTitle
        val landscape = Regex("""https://play-lh\.googleusercontent\.com/[^\"\\\s]+=(?:w1052-h592|w526-h296)""")
            .findAll(html).map { it.value.replace("\\u003d", "=") }.firstOrNull()
            ?: return null
        saveArtwork(cacheKey, canonicalTitle, landscape)
    }.getOrNull()

    private fun resolveFromLibretro(
        cacheKey: String,
        query: String,
        systemId: SystemId,
        sourceName: String?
    ): GameArtwork? {
        val repository = when (systemId) {
            SystemId.PLAYSTATION_2 -> "Sony_-_PlayStation_2"
            SystemId.VITA -> "Sony_-_PlayStation_Vita"
            SystemId.WII_U -> "Nintendo_-_Wii_U"
            else -> return null
        }
        val rawName = sourceName?.substringAfterLast('/')?.substringBeforeLast('.')
        val candidates = listOfNotNull(rawName, query).distinct().flatMap { title ->
            listOf(title, title.substringBefore(" ["), title.substringBefore(" (Disc", title))
        }.map(::libretroFileName).distinct()
        for (candidate in candidates) {
            val url = "https://raw.githubusercontent.com/libretro-thumbnails/$repository/master/Named_Boxarts/${urlEncode(candidate)}.png"
            runCatching { saveArtwork(cacheKey, query, url) }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun resolveFromSteamGridDb(cacheKey: String, query: String, apiKey: String): GameArtwork? = runCatching {
        val search = requestJson(
            "https://www.steamgriddb.com/api/v2/search/autocomplete/${urlEncode(query)}",
            apiKey
        )
        val game = search.getJSONArray("data").optJSONObject(0) ?: return null
        val gameId = game.getLong("id")
        val canonicalTitle = game.optString("name", query).ifBlank { query }
        val grids = requestJson(
            "https://www.steamgriddb.com/api/v2/grids/game/$gameId?dimensions=600x900&types=static",
            apiKey
        ).getJSONArray("data")
        val artworkUrl = (0 until grids.length())
            .mapNotNull { grids.optJSONObject(it)?.optString("url")?.takeIf(String::isNotBlank) }
            .firstOrNull() ?: return null
        saveArtwork(cacheKey, canonicalTitle, artworkUrl)
    }.getOrNull()

    private fun saveArtwork(cacheKey: String, canonicalTitle: String, artworkUrl: String): GameArtwork {
        val output = File(directory, "${sha256(cacheKey)}.${extension(artworkUrl)}")
        download(artworkUrl, output)
        return GameArtwork(canonicalTitle, output.toURI().toString()).also {
            index.edit().putString(cacheKey, "$canonicalTitle\n${output.absolutePath}").apply()
        }
    }

    private fun requestText(url: String, accept: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return connection.useConnection {
            requestMethod = "GET"
            connectTimeout = 7_000
            readTimeout = 12_000
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Language", "en-US,en;q=0.8")
            setRequestProperty("User-Agent", USER_AGENT)
            if (responseCode !in 200..299) error("Artwork service returned $responseCode")
            inputStream.bufferedReader().use { it.readText() }
        }
    }

    private fun requestJson(url: String, apiKey: String?): JSONObject {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return connection.useConnection {
            requestMethod = "GET"
            connectTimeout = 7_000
            readTimeout = 10_000
            if (!apiKey.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
            if (responseCode !in 200..299) error("Artwork service returned $responseCode")
            JSONObject(inputStream.bufferedReader().use { it.readText() })
        }
    }

    private fun download(url: String, output: File) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.useConnection {
            connectTimeout = 7_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", USER_AGENT)
            if (responseCode !in 200..299) error("Artwork download returned $responseCode")
            inputStream.use { input -> output.outputStream().use(input::copyTo) }
        }
    }

    private inline fun <T> HttpURLConnection.useConnection(block: HttpURLConnection.() -> T): T =
        try { block() } finally { disconnect() }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    private fun normalizedTitle(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)
    private fun libretroFileName(value: String): String = value.replace(Regex("[&*/:`<>?\\\\|\"]"), "_").trim()
    private fun String.htmlDecoded(): String = replace("&amp;", "&").replace("&#39;", "'")
        .replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun extension(url: String): String = url.substringBefore('?').substringAfterLast('.', "jpg")
        .lowercase().takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Geemu/1.0"
    }
}

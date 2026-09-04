package com.mertg.geemu.data

import com.mertg.geemu.model.RomEntry

object SwitchContentFilter {
    private val titleIdPattern = Regex("(?i)(?<![0-9a-f])[0-9a-f]{16}(?![0-9a-f])")
    private val addOnPattern = Regex(
        "(?i)(^|[\\s._()\\[\\]{}-])(dlc|upd|update|patch|add[ _-]?on|expansion|bonus[ _-]?content)([\\s._()\\[\\]{}-]|$)"
    )
    private val updateVersionPattern = Regex("(?i)[\\[(]v[1-9][0-9]{3,}[\\])]")

    fun bootableGames(entries: List<RomEntry>): List<RomEntry> = entries.filter(::isBootableGame)

    fun isBootableGame(entry: RomEntry): Boolean {
        val filename = entry.path.substringAfterLast('/')
        val extension = filename.substringAfterLast('.', "").lowercase()
        if (extension == "nca") return false
        if (extension == "xci" || extension == "nro") return true
        if (extension != "nsp") return false
        if (addOnPattern.containsMatchIn(filename) || updateVersionPattern.containsMatchIn(filename)) return false

        val titleIds = titleIdPattern.findAll(filename).map { it.value }.toList()
        if (titleIds.isEmpty()) return true

        // Switch base applications use the ...000 title id. Updates end in ...800;
        // add-on content uses a non-zero content suffix.
        return titleIds.any { it.takeLast(3).equals("000", ignoreCase = true) }
    }
}

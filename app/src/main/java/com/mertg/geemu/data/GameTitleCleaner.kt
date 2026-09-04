package com.mertg.geemu.data

object GameTitleCleaner {
    private val bracketMetadata = Regex(
        "(?i)\\s*[\\[(](?:v?\\d+(?:\\.\\d+)*|base|update|upd|dlc|patch|rev(?:ision)?\\s*\\d*|[0-9a-f]{16}|[A-Z]{4}[-_]?\\d{5})[\\])]"
    )
    private val switchTitleId = Regex("(?i)\\s*[\\[(]?[0-9a-f]{16}[\\])]?")
    private val vitaOrDiscId = Regex("(?i)\\s*[\\[(]?[A-Z]{4}[-_]?\\d{5}[\\])]?")
    private val versionSuffix = Regex("(?i)\\s+(?:v(?:er(?:sion)?)?\\s*)?\\d+(?:\\.\\d+){1,3}$")
    private val dumpTags = Regex(
        "(?i)\\s*[\\[(](?:usa|europe|eur|japan|jpn|world|asia|en|multi\\d*|decrypted|encrypted|trimmed|repack|proper)[\\])]"
    )
    private val discAndAudioTags = Regex(
        "(?i)\\s*[\\[(](?:(?:disc|disk|cd)\\s*\\d+|(?:japanese|english|original)\\s+(?:voice|audio)(?:\\s+over)?)[\\])]"
    )
    private val separators = Regex("[._]+")
    private val whitespace = Regex("\\s{2,}")

    fun clean(raw: String): String {
        var title = raw.substringBeforeLast('.', raw)
        title = title.replace(bracketMetadata, "")
            .replace(switchTitleId, "")
            .replace(vitaOrDiscId, "")
            .replace(dumpTags, "")
            .replace(discAndAudioTags, "")
            .replace(versionSuffix, "")
            .replace(separators, " ")
            .replace(whitespace, " ")
            .trim(' ', '-', '_', '.', '[', ']', '(', ')')
        return title.ifBlank { raw.substringBeforeLast('.', raw).trim() }
    }
}

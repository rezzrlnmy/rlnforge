package com.rln.forge.chat

/**
 * Proposed file changes parsed from an assistant reply.
 *
 * A fenced code block is a proposal only when its header names a file path:
 *   ```kotlin:app/src/main/java/com/example/App.kt
 *   ```app/src/main/java/com/example/App.kt
 * A block without a path (```kotlin) is plain code, never a file — the
 * agent's file must never be guessed.
 *
 * Pure Kotlin, no Android — unit-tested on the JVM.
 */
data class ProposedChange(
    val path: String,
    val language: String?,
    val content: String
) {
    val lineCount: Int get() = content.lines().size
    val key: String get() = path
}

private val TEXT_EXTENSIONS = setOf(
    "kt", "kts", "java", "md", "txt", "json", "xml", "gradle",
    "properties", "css", "html", "js", "ts", "csv", "yml", "yaml"
)

fun isTextFile(path: String): Boolean {
    val ext = path.substringAfterLast('.', "").lowercase()
    return ext in TEXT_EXTENSIONS
}

private fun looksLikePath(header: String): Boolean {
    val h = header.trim()
    if (h.isEmpty()) return false
    if (h.contains('/')) return true
    val ext = h.substringAfterLast('.', "").lowercase()
    return ext in TEXT_EXTENSIONS
}

/** Extract (path, language) from a fence header, or null when not a proposal. */
fun parseFenceHeader(header: String): Pair<String, String?>? {
    val trimmed = header.trim()
    if (trimmed.isEmpty()) return null
    val colon = trimmed.indexOf(':')
    if (colon > 0) {
        val candidate = trimmed.substring(colon + 1).trim()
        if (looksLikePath(candidate)) {
            return candidate to trimmed.substring(0, colon).trim().takeIf { it.isNotEmpty() }
        }
        return null
    }
    return if (looksLikePath(trimmed)) trimmed to null else null
}

/**
 * All file proposals in a message, in order. Duplicate paths keep the last
 * occurrence (the agent's newest version of the file wins).
 */
fun parseProposedChanges(content: String): List<ProposedChange> {
    val byPath = linkedMapOf<String, ProposedChange>()
    var rest = content
    while (true) {
        val start = rest.indexOf("```")
        if (start < 0) break
        val afterFence = rest.substring(start + 3)
        val end = afterFence.indexOf("```")
        val block = if (end < 0) afterFence else afterFence.substring(0, end)
        val firstLineEnd = block.indexOf('\n')
        val header = if (firstLineEnd >= 0) block.substring(0, firstLineEnd) else block
        val code = if (firstLineEnd >= 0) block.substring(firstLineEnd + 1) else ""
        val parsed = parseFenceHeader(header)
        if (parsed != null) {
            val (path, language) = parsed
            byPath[path] = ProposedChange(path, language, code.trimEnd('\n'))
        }
        if (end < 0) break
        rest = afterFence.substring(end + 3)
    }
    return byPath.values.toList()
}

/**
 * One-line summary of what applying would change.
 * New file: "New file — N lines". Existing file: added/removed line counts
 * from the common prefix/suffix of old vs new content.
 */
fun diffSummary(proposed: ProposedChange, existingContent: String?): String {
    val newLines = proposed.content.lines()
    if (existingContent == null) {
        return "New file — ${newLines.size} lines"
    }
    val oldLines = existingContent.lines()
    var prefix = 0
    while (prefix < oldLines.size && prefix < newLines.size &&
        oldLines[prefix] == newLines[prefix]
    ) {
        prefix++
    }
    var suffix = 0
    while (suffix < oldLines.size - prefix && suffix < newLines.size - prefix &&
        oldLines[oldLines.size - 1 - suffix] == newLines[newLines.size - 1 - suffix]
    ) {
        suffix++
    }
    val removed = oldLines.size - prefix - suffix
    val added = newLines.size - prefix - suffix
    return when {
        added == 0 && removed == 0 -> "No changes"
        removed == 0 -> "$added lines added"
        added == 0 -> "$removed lines removed"
        else -> "$added lines added, $removed lines removed"
    }
}

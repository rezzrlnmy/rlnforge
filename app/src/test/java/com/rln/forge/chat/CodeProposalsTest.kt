package com.rln.forge.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeProposalsTest {

    @Test
    fun parseFenceHeader_languageColonPath() {
        assertEquals(
            "app/Main.kt" to "kotlin",
            parseFenceHeader("kotlin:app/Main.kt")
        )
        assertEquals(
            "script.py" to "python",
            parseFenceHeader("python:script.py")
        )
    }

    @Test
    fun parseFenceHeader_plainPath() {
        assertEquals("app/Main.kt" to null, parseFenceHeader("app/Main.kt"))
        assertEquals("notes.md" to null, parseFenceHeader("notes.md"))
    }

    @Test
    fun parseFenceHeader_languageOnlyIsNotAProposal() {
        assertNull(parseFenceHeader("kotlin"))
        assertNull(parseFenceHeader(""))
        assertNull(parseFenceHeader("kotlin:hello"))
    }

    @Test
    fun parseProposedChanges_bothFormats() {
        val content = "Here is the app:\n" +
            "```kotlin:app/src/Main.kt\n" +
            "fun main() {}\n" +
            "```\n" +
            "And the readme:\n" +
            "```README.md\n" +
            "# Hi\n" +
            "```\n"
        val changes = parseProposedChanges(content)
        assertEquals(2, changes.size)
        assertEquals("app/src/Main.kt", changes[0].path)
        assertEquals("kotlin", changes[0].language)
        assertEquals("fun main() {}", changes[0].content)
        assertEquals("README.md", changes[1].path)
        assertNull(changes[1].language)
    }

    @Test
    fun parseProposedChanges_blockWithoutPathIsIgnored() {
        val content = "```kotlin\nfun main() {}\n```\n"
        assertTrue(parseProposedChanges(content).isEmpty())
    }

    @Test
    fun parseProposedChanges_duplicatePathKeepsLast() {
        val content = "```a.kt\nv1\n```\n```a.kt\nv2\n```\n"
        val changes = parseProposedChanges(content)
        assertEquals(1, changes.size)
        assertEquals("v2", changes[0].content)
    }

    @Test
    fun parseProposedChanges_unterminatedFenceStillParsed() {
        val content = "```kotlin:a.kt\nfun x() {}\n"
        val changes = parseProposedChanges(content)
        assertEquals(1, changes.size)
        assertEquals("a.kt", changes[0].path)
    }

    @Test
    fun diffSummary_newFile() {
        val proposed = ProposedChange("a.kt", "kotlin", "a\nb\nc")
        assertEquals("New file — 3 lines", diffSummary(proposed, null))
    }

    @Test
    fun diffSummary_noChanges() {
        val proposed = ProposedChange("a.kt", "kotlin", "a\nb")
        assertEquals("No changes", diffSummary(proposed, "a\nb"))
    }

    @Test
    fun diffSummary_addedAndRemoved() {
        val proposed = ProposedChange("a.kt", "kotlin", "a\nx\ny")
        assertEquals("2 lines added, 1 lines removed", diffSummary(proposed, "a\nb"))
    }

    @Test
    fun diffSummary_addedOnly() {
        val proposed = ProposedChange("a.kt", "kotlin", "a\nb\nc")
        assertEquals("1 lines added", diffSummary(proposed, "a\nb"))
    }
}

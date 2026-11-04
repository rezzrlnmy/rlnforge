package com.rln.forge.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectFileStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(): ProjectFileStore = ProjectFileStore(File(temp.root, "projects"))

    @Test
    fun writeReadRoundTrip() {
        val store = store()
        assertTrue(store.write("p1", "MainActivity.kt", "hello"))
        assertEquals("hello", store.read("p1", "MainActivity.kt"))
        // Save, close, open again: same bytes back.
        assertEquals("hello", store.read("p1", "MainActivity.kt"))
    }

    @Test
    fun nestedPathCreatesFolders() {
        val store = store()
        assertTrue(store.write("p1", "ui/Theme.kt", "x"))
        assertEquals("x", store.read("p1", "ui/Theme.kt"))
        assertEquals(listOf("ui/Theme.kt"), store.listFiles("p1"))
    }

    @Test
    fun unsafePathsAreRejected() {
        val store = store()
        assertFalse(store.write("p1", "../escape.kt", "x"))
        assertFalse(store.write("p1", "..\\win.kt", "x"))
        assertFalse(store.write("p1", "a/../../escape.kt", "x"))
        assertNull(store.read("p1", "../escape.kt"))
        assertTrue(store.listFiles("p1").isEmpty())
    }

    @Test
    fun leadingSlashIsStrippedNotAnEscape() {
        val store = store()
        assertTrue(store.write("p1", "/abs.kt", "x"))
        assertEquals(listOf("abs.kt"), store.listFiles("p1"))
    }

    @Test
    fun deletePrunesEmptyParents() {
        val store = store()
        store.write("p1", "ui/Theme.kt", "x")
        store.write("p1", "ui/Other.kt", "y")
        assertTrue(store.delete("p1", "ui/Theme.kt"))
        // ui/ still has Other.kt, so the folder stays.
        assertEquals(listOf("ui/Other.kt"), store.listFiles("p1"))
        assertTrue(store.delete("p1", "ui/Other.kt"))
        assertTrue(store.listFiles("p1").isEmpty())
    }

    @Test
    fun moveRenames() {
        val store = store()
        store.write("p1", "Old.kt", "x")
        assertTrue(store.move("p1", "Old.kt", "New.kt"))
        assertEquals("x", store.read("p1", "New.kt"))
        assertNull(store.read("p1", "Old.kt"))
        assertFalse(store.move("p1", "Missing.kt", "New2.kt"))
    }

    @Test
    fun buildFileTreeGroupsFolders() {
        val tree = buildFileTree(listOf("Main.kt", "ui/Theme.kt", "ui/Colors.kt", "README.md"))
        // Files first (alphabetical), then folders.
        assertEquals("Main.kt", (tree[0] as FileNode.File).name)
        assertEquals("README.md", (tree[1] as FileNode.File).name)
        val dir = tree[2] as FileNode.Dir
        assertEquals("ui", dir.name)
        assertEquals("Colors.kt", (dir.children[0] as FileNode.File).name)
        assertEquals("Theme.kt", (dir.children[1] as FileNode.File).name)
    }
}

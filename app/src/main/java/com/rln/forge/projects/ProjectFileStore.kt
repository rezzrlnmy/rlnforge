package com.rln.forge.projects

import java.io.File

/**
 * Project files on disk, under <root>/<projectId>/<relative path>.
 *
 * Pure java.io — no Android dependency, so it is unit-testable on the JVM.
 * Paths are validated: no absolute paths, no ".." segments, no backslashes.
 * Nothing here ever escapes the project root.
 */
class ProjectFileStore(private val root: File) {

    /** Resolve a relative path inside a project, or null when it is unsafe. */
    fun resolve(projectId: String, relativePath: String): File? {
        val clean = normalize(relativePath) ?: return null
        if (projectId.isBlank() || projectId.contains('/') || projectId.contains('\\')) {
            return null
        }
        return File(File(root, projectId), clean)
    }

    /** "a//b/../c" -> null; "a/b.kt" -> "a/b.kt". Null on unsafe input. */
    fun normalize(relativePath: String): String? {
        val trimmed = relativePath.trim().trimStart('/')
        if (trimmed.isEmpty()) return null
        if (trimmed.contains('\\')) return null
        val parts = trimmed.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        return parts.joinToString("/")
    }

    fun write(projectId: String, relativePath: String, content: String): Boolean {
        val file = resolve(projectId, relativePath) ?: return false
        file.parentFile?.mkdirs()
        file.writeText(content)
        return true
    }

    fun read(projectId: String, relativePath: String): String? {
        val file = resolve(projectId, relativePath) ?: return null
        if (!file.isFile) return null
        return file.readText()
    }

    fun exists(projectId: String, relativePath: String): Boolean {
        val file = resolve(projectId, relativePath) ?: return false
        return file.isFile
    }

    /** Delete a file, then prune empty parent folders up to the project root. */
    fun delete(projectId: String, relativePath: String): Boolean {
        val file = resolve(projectId, relativePath) ?: return false
        if (!file.isFile) return false
        if (!file.delete()) return false
        var dir = file.parentFile
        val projectRoot = File(root, projectId)
        while (dir != null && dir != projectRoot && dir.isDirectory) {
            if ((dir.list()?.isEmpty()) == true) {
                dir.delete()
                dir = dir.parentFile
            } else {
                break
            }
        }
        return true
    }

    fun move(projectId: String, oldPath: String, newPath: String): Boolean {
        val from = resolve(projectId, oldPath) ?: return false
        val to = resolve(projectId, newPath) ?: return false
        if (!from.isFile || to.exists()) return false
        to.parentFile?.mkdirs()
        if (!from.renameTo(to)) return false
        var dir = from.parentFile
        val projectRoot = File(root, projectId)
        while (dir != null && dir != projectRoot && dir.isDirectory) {
            if ((dir.list()?.isEmpty()) == true) {
                dir.delete()
                dir = dir.parentFile
            } else {
                break
            }
        }
        return true
    }

    /** All files in the project, as slash-separated relative paths, sorted. */
    fun listFiles(projectId: String): List<String> {
        val projectRoot = File(root, projectId)
        if (!projectRoot.isDirectory) return emptyList()
        return projectRoot.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(projectRoot).path.replace(File.separatorChar, '/') }
            .sorted()
    }

    fun deleteProject(projectId: String) {
        File(root, projectId).deleteRecursively()
    }
}

/** A node in the project file tree shown in the UI. */
sealed interface FileNode {
    val name: String
    data class Dir(override val name: String, val children: List<FileNode>) : FileNode
    data class File(override val name: String, val path: String) : FileNode
}

/** Build a folder tree from flat relative paths. Files sort before folders. */
fun buildFileTree(paths: List<String>): List<FileNode> {
    val root = TreeDir("")
    for (path in paths) {
        val parts = path.split('/')
        var node = root
        for (i in parts.indices) {
            val part = parts[i]
            if (i == parts.lastIndex) {
                node.children.add(TreeFile(part, path))
            } else {
                val next = node.children
                    .filterIsInstance<TreeDir>()
                    .firstOrNull { it.name == part }
                    ?: TreeDir(part).also { node.children.add(it) }
                node = next
            }
        }
    }
    return root.toNodeList()
}

private class TreeDir(val name: String) {
    val children = mutableListOf<Any>()

    fun toNode(): FileNode.Dir = FileNode.Dir(name, toNodeList())

    fun toNodeList(): List<FileNode> = children.map {
        when (it) {
            is TreeDir -> it.toNode()
            is TreeFile -> FileNode.File(it.name, it.path)
            else -> error("unexpected tree node")
        }
    }.sortedWith(
        compareByDescending<FileNode> { it is FileNode.File }
            .thenBy { it.name.lowercase() }
    )
}

private class TreeFile(val name: String, val path: String)

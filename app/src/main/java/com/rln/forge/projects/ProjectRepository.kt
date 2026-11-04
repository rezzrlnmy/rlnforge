package com.rln.forge.projects

import android.content.SharedPreferences
import com.rln.forge.data.AppDatabase
import com.rln.forge.data.ProjectEntity
import com.rln.forge.data.ProjectFileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Projects: Room holds metadata only, file contents live on disk via
 * [ProjectFileStore]. The active project id is kept in plain (non-secret)
 * preferences — it is not a credential.
 */
class ProjectRepository(
    private val db: AppDatabase,
    private val fileStore: ProjectFileStore,
    private val prefs: SharedPreferences
) {

    /**
     * Bumped on every file write/rename/delete, so the file tree refreshes
     * no matter where the change came from (editor, or Apply from chat).
     */
    private val _filesChanged = MutableStateFlow(0)
    val filesChanged: StateFlow<Int> = _filesChanged.asStateFlow()

    private fun notifyFilesChanged() {
        _filesChanged.value += 1
    }

    fun observeProjects(): Flow<List<ProjectEntity>> = db.projectDao().observeProjects()

    fun observeFiles(projectId: String): Flow<List<String>> =
        db.projectDao().observeFiles(projectId).map { list -> list.map { it.path } }

    suspend fun getProject(id: String): ProjectEntity? = db.projectDao().getProject(id)

    /** The default project, created on first use. Never null after this. */
    suspend fun ensureDefaultProject(): ProjectEntity {
        val activeId = prefs.getString(KEY_ACTIVE_PROJECT, null)
        if (activeId != null) {
            db.projectDao().getProject(activeId)?.let { return it }
        }
        db.projectDao().getNewestProject()?.let {
            setActiveProject(it.id)
            return it
        }
        val now = System.currentTimeMillis()
        val project = ProjectEntity(
            id = UUID.randomUUID().toString(),
            name = "My App",
            createdAt = now,
            updatedAt = now
        )
        db.projectDao().upsert(project)
        val starter = "package com.example.myapp\n\n" +
            "import androidx.activity.ComponentActivity\n" +
            "import android.os.Bundle\n\n" +
            "class MainActivity : ComponentActivity() {\n" +
            "    override fun onCreate(savedInstanceState: Bundle?) {\n" +
            "        super.onCreate(savedInstanceState)\n" +
            "    }\n" +
            "}\n"
        fileStore.write(project.id, "MainActivity.kt", starter)
        db.projectDao().upsertFile(ProjectFileEntity(project.id, "MainActivity.kt", now))
        setActiveProject(project.id)
        return project
    }

    suspend fun createProject(name: String): ProjectEntity {
        val now = System.currentTimeMillis()
        val project = ProjectEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim().takeIf { it.isNotBlank() } ?: "Untitled",
            createdAt = now,
            updatedAt = now
        )
        db.projectDao().upsert(project)
        setActiveProject(project.id)
        return project
    }

    suspend fun renameProject(project: ProjectEntity, name: String) {
        if (name.isBlank()) return
        db.projectDao().upsert(project.copy(name = name.trim()))
    }

    suspend fun deleteProject(project: ProjectEntity) {
        fileStore.deleteProject(project.id)
        db.projectDao().deleteFilesForProject(project.id)
        db.projectDao().deleteProject(project.id)
        if (prefs.getString(KEY_ACTIVE_PROJECT, null) == project.id) {
            prefs.edit().remove(KEY_ACTIVE_PROJECT).apply()
        }
    }

    fun getActiveProjectId(): String? = prefs.getString(KEY_ACTIVE_PROJECT, null)

    fun setActiveProject(projectId: String) {
        prefs.edit().putString(KEY_ACTIVE_PROJECT, projectId).apply()
    }

    /** File tree of a project, built from the files on disk. */
    fun listTree(projectId: String): List<FileNode> =
        buildFileTree(fileStore.listFiles(projectId))

    suspend fun readFile(projectId: String, path: String): String? =
        fileStore.read(projectId, path)

    /**
     * Write a file (create folders as needed) and update its Room metadata.
     * Returns false when the path is unsafe — the file is never written.
     */
    suspend fun saveFile(projectId: String, path: String, content: String): Boolean {
        val clean = fileStore.normalize(path) ?: return false
        if (!fileStore.write(projectId, clean, content)) return false
        val now = System.currentTimeMillis()
        db.projectDao().upsertFile(ProjectFileEntity(projectId, clean, now))
        db.projectDao().getProject(projectId)?.let {
            db.projectDao().upsert(it.copy(updatedAt = now))
        }
        notifyFilesChanged()
        return true
    }

    suspend fun createFile(projectId: String, path: String): Boolean {
        val clean = fileStore.normalize(path) ?: return false
        if (fileStore.exists(projectId, clean)) return false
        return saveFile(projectId, clean, "")
    }

    suspend fun renameFile(projectId: String, oldPath: String, newPath: String): Boolean {
        val cleanNew = fileStore.normalize(newPath) ?: return false
        if (!fileStore.move(projectId, oldPath, cleanNew)) return false
        db.projectDao().deleteFile(projectId, oldPath)
        db.projectDao().upsertFile(
            ProjectFileEntity(projectId, cleanNew, System.currentTimeMillis())
        )
        notifyFilesChanged()
        return true
    }

    suspend fun deleteFile(projectId: String, path: String): Boolean {
        if (!fileStore.delete(projectId, path)) return false
        db.projectDao().deleteFile(projectId, path)
        notifyFilesChanged()
        return true
    }

    companion object {
        private const val KEY_ACTIVE_PROJECT = "active_project_id"
    }
}

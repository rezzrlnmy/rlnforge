package com.rln.forge.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rln.forge.data.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Projects + file editor state.
 *
 * The file tree is rebuilt from disk whenever the active project changes or
 * any file write/rename/delete happens (including Apply from chat).
 * Editor text is plain monospace; syntax highlighting is deliberately out
 * of scope for v0.2 — the editor must first be correct at saving.
 */
class ProjectViewModel(
    private val repository: ProjectRepository
) : ViewModel() {

    val projects: Flow<List<ProjectEntity>> = repository.observeProjects()

    private val _activeProjectId = MutableStateFlow<String?>(
        repository.getActiveProjectId()
    )
    val activeProjectId: StateFlow<String?> = _activeProjectId.asStateFlow()

    val fileTree: Flow<List<FileNode>> = combine(
        _activeProjectId,
        repository.filesChanged
    ) { id, _ -> id }.mapLatest { id ->
        if (id == null) emptyList()
        else withContext(Dispatchers.IO) { repository.listTree(id) }
    }

    // ---- Editor state ----

    private val _openPath = MutableStateFlow<String?>(null)
    val openPath: StateFlow<String?> = _openPath.asStateFlow()

    private val _editorText = MutableStateFlow("")
    val editorText: StateFlow<String> = _editorText.asStateFlow()

    private val _editorDirty = MutableStateFlow(false)
    val editorDirty: StateFlow<Boolean> = _editorDirty.asStateFlow()

    private val _editorError = MutableStateFlow<String?>(null)
    val editorError: StateFlow<String?> = _editorError.asStateFlow()

    fun clearEditorError() {
        _editorError.value = null
    }

    init {
        viewModelScope.launch {
            val project = repository.ensureDefaultProject()
            _activeProjectId.value = project.id
        }
    }

    fun selectProject(projectId: String) {
        repository.setActiveProject(projectId)
        _activeProjectId.value = projectId
    }

    fun createProject(name: String) {
        viewModelScope.launch {
            val project = repository.createProject(name)
            _activeProjectId.value = project.id
        }
    }

    fun renameProject(project: ProjectEntity, name: String) {
        viewModelScope.launch { repository.renameProject(project, name) }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.deleteProject(project)
            _activeProjectId.value = repository.ensureDefaultProject().id
        }
    }

    fun openFile(path: String) {
        viewModelScope.launch {
            val projectId = _activeProjectId.value ?: return@launch
            // Never lose unsaved edits when switching files: save first.
            if (_editorDirty.value) {
                val oldPath = _openPath.value
                if (oldPath != null && oldPath != path) {
                    repository.saveFile(projectId, oldPath, _editorText.value)
                }
            }
            val content = withContext(Dispatchers.IO) {
                repository.readFile(projectId, path)
            }
            if (content == null) {
                _editorError.value = "Could not open $path"
                return@launch
            }
            _openPath.value = path
            _editorText.value = content
            _editorDirty.value = false
        }
    }

    fun updateEditorText(text: String) {
        _editorText.value = text
        _editorDirty.value = true
    }

    /** Explicit Save. Returns through editorDirty = false on success. */
    fun saveOpenFile() {
        viewModelScope.launch {
            val projectId = _activeProjectId.value ?: return@launch
            val path = _openPath.value ?: return@launch
            val ok = repository.saveFile(projectId, path, _editorText.value)
            if (ok) {
                _editorDirty.value = false
            } else {
                _editorError.value = "Could not save $path: unsafe path"
            }
        }
    }

    /** Save on exit when dirty, then close the editor. */
    fun closeFile(onClosed: () -> Unit = {}) {
        viewModelScope.launch {
            if (_editorDirty.value) {
                val projectId = _activeProjectId.value
                val path = _openPath.value
                if (projectId != null && path != null) {
                    repository.saveFile(projectId, path, _editorText.value)
                }
            }
            _openPath.value = null
            _editorText.value = ""
            _editorDirty.value = false
            onClosed()
        }
    }

    fun createFile(path: String, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            val projectId = _activeProjectId.value ?: return@launch
            val ok = repository.createFile(projectId, path)
            if (ok) {
                onCreated(path)
            } else {
                _editorError.value = "Could not create $path (unsafe path or already exists)"
            }
        }
    }

    fun renameFile(oldPath: String, newPath: String) {
        viewModelScope.launch {
            val projectId = _activeProjectId.value ?: return@launch
            val ok = repository.renameFile(projectId, oldPath, newPath)
            if (ok && _openPath.value == oldPath) {
                _openPath.value = newPath
            }
            if (!ok) {
                _editorError.value = "Could not rename to $newPath"
            }
        }
    }

    fun deleteFile(path: String) {
        viewModelScope.launch {
            val projectId = _activeProjectId.value ?: return@launch
            repository.deleteFile(projectId, path)
            if (_openPath.value == path) {
                _openPath.value = null
                _editorText.value = ""
                _editorDirty.value = false
            }
        }
    }

    /** Read a file's current content (used for the Apply diff summary). */
    suspend fun readFileText(projectId: String, path: String): String? =
        withContext(Dispatchers.IO) { repository.readFile(projectId, path) }

    class Factory(
        private val repository: ProjectRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProjectViewModel(repository) as T
        }
    }
}

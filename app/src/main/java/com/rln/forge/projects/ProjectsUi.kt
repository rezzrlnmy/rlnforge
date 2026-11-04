package com.rln.forge.projects

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rln.forge.data.ProjectEntity

private val EDITOR_FONT_SIZE = 14.sp
private val EDITOR_LINE_HEIGHT = 20.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    viewModel: ProjectViewModel,
    onOpenEditor: (projectId: String, path: String) -> Unit
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeProjectId by viewModel.activeProjectId.collectAsStateWithLifecycle()
    val tree by viewModel.fileTree.collectAsStateWithLifecycle(initialValue = emptyList())
    val editorError by viewModel.editorError.collectAsStateWithLifecycle()

    val activeProject = projects.firstOrNull { it.id == activeProjectId } ?: projects.firstOrNull()
    var showNewProject by remember { mutableStateOf(false) }
    var showNewFile by remember { mutableStateOf(false) }
    var showRenameProject by remember { mutableStateOf(false) }
    var showDeleteProject by remember { mutableStateOf(false) }
    var fileMenuPath by remember { mutableStateOf<String?>(null) }
    var renameFilePath by remember { mutableStateOf<String?>(null) }
    var deleteFilePath by remember { mutableStateOf<String?>(null) }
    var expandedDirs by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Projects") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (activeProject != null) {
                ProjectSelector(
                    projects = projects,
                    active = activeProject,
                    onSelect = { viewModel.selectProject(it.id) },
                    onRename = { showRenameProject = true },
                    onDelete = { showDeleteProject = true }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(onClick = { showNewProject = true }) { Text("New project") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { showNewFile = true },
                    enabled = activeProject != null
                ) { Text("New file") }
            }
            editorError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (tree.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No files yet. Create one with New file.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(tree, key = { node ->
                        when (node) {
                            is FileNode.Dir -> "dir:${node.name}"
                            is FileNode.File -> "file:${node.path}"
                        }
                    }) { node ->
                        FileTreeNode(
                            node = node,
                            depth = 0,
                            parentPath = "",
                            expandedDirs = expandedDirs,
                            onToggleDir = { path ->
                                expandedDirs = if (path in expandedDirs) {
                                    expandedDirs - path
                                } else {
                                    expandedDirs + path
                                }
                            },
                            onOpenFile = { path ->
                                val projectId = activeProject?.id ?: return@FileTreeNode
                                onOpenEditor(projectId, path)
                            },
                            onLongPressFile = { path -> fileMenuPath = path }
                        )
                    }
                }
            }
        }
    }

    if (showNewProject) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewProject = false },
            title = { Text("New project") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.createProject(name.trim())
                        showNewProject = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewProject = false }) { Text("Cancel") }
            }
        )
    }

    if (showNewFile) {
        var path by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFile = false },
            title = { Text("New file") },
            text = {
                Column {
                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it },
                        label = { Text("Path") },
                        placeholder = { Text("ui/Theme.kt") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Folders are created from the path, e.g. ui/Theme.kt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = path.isNotBlank(),
                    onClick = {
                        val projectId = activeProject?.id
                        viewModel.createFile(path.trim()) { created ->
                            if (projectId != null) onOpenEditor(projectId, created)
                        }
                        showNewFile = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFile = false }) { Text("Cancel") }
            }
        )
    }

    if (showRenameProject && activeProject != null) {
        var name by remember { mutableStateOf(activeProject.name) }
        AlertDialog(
            onDismissRequest = { showRenameProject = false },
            title = { Text("Rename project") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.renameProject(activeProject, name.trim())
                        showRenameProject = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameProject = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteProject && activeProject != null) {
        AlertDialog(
            onDismissRequest = { showDeleteProject = false },
            title = { Text("Delete project?") },
            text = {
                Text("Delete ${activeProject.name} and all its files? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject(activeProject)
                        showDeleteProject = false
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteProject = false }) { Text("Cancel") }
            }
        )
    }

    fileMenuPath?.let { path ->
        AlertDialog(
            onDismissRequest = { fileMenuPath = null },
            title = { Text(path) },
            text = { Text("Choose an action for this file.") },
            confirmButton = {
                TextButton(onClick = {
                    val projectId = activeProject?.id
                    fileMenuPath = null
                    if (projectId != null) onOpenEditor(projectId, path)
                }) { Text("Open") }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = {
                        fileMenuPath = null
                        renameFilePath = path
                    }) { Text("Rename") }
                    TextButton(onClick = {
                        fileMenuPath = null
                        deleteFilePath = path
                    }) { Text("Delete") }
                }
            }
        )
    }

    renameFilePath?.let { oldPath ->
        var newPath by remember { mutableStateOf(oldPath) }
        AlertDialog(
            onDismissRequest = { renameFilePath = null },
            title = { Text("Rename file") },
            text = {
                OutlinedTextField(
                    value = newPath,
                    onValueChange = { newPath = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newPath.isNotBlank() && newPath.trim() != oldPath,
                    onClick = {
                        viewModel.renameFile(oldPath, newPath.trim())
                        renameFilePath = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameFilePath = null }) { Text("Cancel") }
            }
        )
    }

    deleteFilePath?.let { path ->
        AlertDialog(
            onDismissRequest = { deleteFilePath = null },
            title = { Text("Delete file?") },
            text = { Text("Delete $path? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFile(path)
                        deleteFilePath = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteFilePath = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectSelector(
    projects: List<ProjectEntity>,
    active: ProjectEntity,
    onSelect: (ProjectEntity) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = active.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Project") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                projects.forEach { project ->
                    DropdownMenuItem(
                        text = { Text(project.name) },
                        onClick = {
                            onSelect(project)
                            expanded = false
                        }
                    )
                }
            }
        }
        TextButton(onClick = onRename) { Text("Rename") }
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTreeNode(
    node: FileNode,
    depth: Int,
    parentPath: String,
    expandedDirs: Set<String>,
    onToggleDir: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onLongPressFile: (String) -> Unit
) {
    when (node) {
        is FileNode.Dir -> {
            val fullPath = if (parentPath.isEmpty()) node.name else "$parentPath/${node.name}"
            val expanded = fullPath in expandedDirs
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleDir(fullPath) }
                        .padding(start = (depth * 16).dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (expanded) "▾ ${node.name}" else "▸ ${node.name}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (expanded) {
                    node.children.forEach { child ->
                        FileTreeNode(
                            node = child,
                            depth = depth + 1,
                            parentPath = fullPath,
                            expandedDirs = expandedDirs,
                            onToggleDir = onToggleDir,
                            onOpenFile = onOpenFile,
                            onLongPressFile = onLongPressFile
                        )
                    }
                }
            }
        }
        is FileNode.File -> {
            Text(
                node.name,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpenFile(node.path) },
                        onLongClick = { onLongPressFile(node.path) }
                    )
                    .padding(start = (depth * 16 + 16).dp, top = 6.dp, bottom = 6.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: ProjectViewModel,
    projectId: String,
    path: String,
    onBack: () -> Unit
) {
    val editorText by viewModel.editorText.collectAsStateWithLifecycle()
    val dirty by viewModel.editorDirty.collectAsStateWithLifecycle()
    val editorError by viewModel.editorError.collectAsStateWithLifecycle()
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(projectId, path) {
        viewModel.selectProject(projectId)
        viewModel.openFile(path)
    }

    val lineCount = editorText.count { it == '\n' } + 1
    val gutter = remember(lineCount) {
        (1..lineCount).joinToString("\n")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            path.substringAfterLast('/'),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (dirty) "$path — unsaved" else path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = { showRename = true }) { Text("Rename") }
                    TextButton(onClick = { showDelete = true }) { Text("Delete") }
                    Button(onClick = { viewModel.saveOpenFile() }) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp)
        ) {
            editorError?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = gutter,
                        fontFamily = FontFamily.Monospace,
                        fontSize = EDITOR_FONT_SIZE,
                        lineHeight = EDITOR_LINE_HEIGHT,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    BasicTextField(
                        value = editorText,
                        onValueChange = { viewModel.updateEditorText(it) },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = EDITOR_FONT_SIZE,
                            lineHeight = EDITOR_LINE_HEIGHT,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showRename) {
        var newPath by remember { mutableStateOf(path) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename file") },
            text = {
                OutlinedTextField(
                    value = newPath,
                    onValueChange = { newPath = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newPath.isNotBlank() && newPath.trim() != path,
                    onClick = {
                        viewModel.renameFile(path, newPath.trim())
                        showRename = false
                        onBack()
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel") }
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete file?") },
            text = { Text("Delete $path? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFile(path)
                        showDelete = false
                        onBack()
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            }
        )
    }
}

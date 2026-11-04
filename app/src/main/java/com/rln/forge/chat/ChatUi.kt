package com.rln.forge.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rln.forge.data.ChatEntity
import com.rln.forge.data.MessageEntity
import com.rln.forge.projects.ProjectViewModel
import com.rln.forge.providers.ProviderConfig
import com.rln.forge.providers.ProviderType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatTime(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatViewModel,
    onOpenChat: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val chats by viewModel.chats.collectAsStateWithLifecycle(initialValue = emptyList())
    var renameTarget by remember { mutableStateOf<ChatEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("rlnForge") },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text("Providers") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Button(
                onClick = { viewModel.newChat() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("New chat") }
            Spacer(modifier = Modifier.height(12.dp))
            if (chats.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No chats yet. Start a new chat, then add a provider key in Providers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chats, key = { it.id }) { chat ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onOpenChat(chat.id) }
                                .padding(16.dp)
                        ) {
                            Text(chat.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                listOf(chat.model, formatTime(chat.updatedAt))
                                    .filter { it.isNotBlank() }
                                    .joinToString("  ·  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(modifier = Modifier.padding(top = 8.dp)) {
                                TextButton(onClick = {
                                    renameTarget = chat
                                    renameText = chat.title
                                }) { Text("Rename") }
                                TextButton(onClick = { viewModel.deleteChat(chat.id) }) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { chat ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename chat") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) viewModel.renameChat(chat, renameText.trim())
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    projectViewModel: ProjectViewModel,
    chatId: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val chat by viewModel.chatFlow(chatId).collectAsStateWithLifecycle(initialValue = null)
    val messages by viewModel.messagesFor(chatId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val providers by viewModel.providers.collectAsStateWithLifecycle(initialValue = emptyList())
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val sendError by viewModel.sendError.collectAsStateWithLifecycle()
    val proposalStatus by viewModel.proposalStatus.collectAsStateWithLifecycle()
    val applyNotice by viewModel.applyNotice.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
    }

    LaunchedEffect(applyNotice) {
        applyNotice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearApplyNotice()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(chat?.title ?: "Chat", style = MaterialTheme.typography.titleMedium)
                        Text(
                            chat?.model?.takeIf { it.isNotBlank() } ?: "No model selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = { showModelPicker = true }) { Text("Model") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            sendError?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                        .clickable { onOpenSettings() }
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onRetry = { viewModel.retry() }
                    )
                    if (message.role == "assistant") {
                        val proposals = remember(message.content) {
                            parseProposedChanges(message.content)
                        }
                        proposals.forEach { proposal ->
                            val key = "${message.id}::${proposal.path}"
                            ProposedChangeCard(
                                messageId = message.id,
                                proposal = proposal,
                                status = proposalStatus[key],
                                projectViewModel = projectViewModel,
                                onApply = { viewModel.applyProposal(message.id, proposal) },
                                onDiscard = { viewModel.discardProposal(message.id, proposal) }
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Message") },
                    modifier = Modifier.weight(1f),
                    maxLines = 5
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isStreaming) {
                    Button(onClick = { viewModel.stopStreaming() }) { Text("Stop") }
                } else {
                    Button(onClick = {
                        viewModel.send(input)
                        input = ""
                    }) { Text("Send") }
                }
            }
        }
    }

    val currentChat = chat
    if (showModelPicker && currentChat != null) {
        ModelPickerDialog(
            providers = providers,
            currentProviderId = currentChat.providerId,
            currentModel = currentChat.model,
            onDismiss = { showModelPicker = false },
            onSave = { provider, model ->
                viewModel.updateChatModel(currentChat, provider, model)
                showModelPicker = false
            }
        )
    }
}

@Composable
private fun MessageBubble(message: MessageEntity, onRetry: () -> Unit) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isUser) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            if (isUser) "You" else "Assistant",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (message.content.isBlank() && message.error == null) {
            Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            SelectionContainer { MarkdownText(message.content) }
        }
        message.error?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

/**
 * One file the agent proposed to create or change.
 *
 * The file on disk is NEVER touched until the user taps Apply — the card
 * only shows a summary. Discard hides the card and changes nothing.
 */
@Composable
private fun ProposedChangeCard(
    messageId: String,
    proposal: ProposedChange,
    status: ProposalStatus?,
    projectViewModel: ProjectViewModel,
    onApply: () -> Unit,
    onDiscard: () -> Unit
) {
    val activeProjectId by projectViewModel.activeProjectId
        .collectAsStateWithLifecycle()
    val existingContent by produceState<String?>(
        initialValue = null,
        activeProjectId,
        proposal.path
    ) {
        value = activeProjectId?.let { projectViewModel.readFileText(it, proposal.path) }
    }
    val summary = remember(proposal, existingContent) {
        diffSummary(proposal, existingContent)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            "Proposed change",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            proposal.path,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when (status) {
            ProposalStatus.APPLIED -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Applied",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            ProposalStatus.DISCARDED -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Discarded",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            else -> {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = onApply) { Text("Apply") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onDiscard) { Text("Discard") }
                }
            }
        }
    }
}

@Composable
private fun ModelPickerDialog(
    providers: List<ProviderConfig>,
    currentProviderId: String?,
    currentModel: String,
    onDismiss: () -> Unit,
    onSave: (ProviderConfig, String) -> Unit
) {
    // DSN Relay is PARKED: it is never offered as a model choice.
    val visibleProviders = remember(providers) {
        providers.filter { it.type != ProviderType.DSN_RELAY }
    }
    var selectedId by remember {
        mutableStateOf(currentProviderId ?: visibleProviders.firstOrNull()?.id)
    }
    var model by remember { mutableStateOf(currentModel) }
    val selected = visibleProviders.firstOrNull { it.id == selectedId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Model for this chat") },
        text = {
            Column {
                if (visibleProviders.isEmpty()) {
                    Text("No providers yet. Add one in Providers first.")
                }
                visibleProviders.forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedId = provider.id
                                model = provider.model
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = provider.id == selectedId,
                            onClick = {
                                selectedId = provider.id
                                model = provider.model
                            }
                        )
                        Column {
                            Text(provider.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                provider.model.takeIf { it.isNotBlank() } ?: "No default model",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null && model.isNotBlank(),
                onClick = { selected?.let { onSave(it, model) } }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Minimal markdown: fenced code blocks with a copy button, inline `code`
 * and **bold**. Anything else renders as plain text. No external library.
 */
@Composable
private fun MarkdownText(content: String) {
    val clipboard = LocalClipboardManager.current
    val segments = remember(content) { splitCodeFences(content) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            if (segment.isCode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.background,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            segment.language ?: "code",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(segment.text))
                        }) { Text("Copy") }
                    }
                    Text(
                        segment.text,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Text(buildInlineAnnotated(segment.text))
            }
        }
    }
}

private data class TextSegment(val text: String, val isCode: Boolean, val language: String?)

private fun splitCodeFences(content: String): List<TextSegment> {
    val result = mutableListOf<TextSegment>()
    var rest = content
    while (true) {
        val start = rest.indexOf("```")
        if (start < 0) {
            if (rest.isNotEmpty()) result.add(TextSegment(rest, false, null))
            break
        }
        if (start > 0) result.add(TextSegment(rest.substring(0, start), false, null))
        val afterFence = rest.substring(start + 3)
        val end = afterFence.indexOf("```")
        if (end < 0) {
            // Unterminated fence (common while streaming): render as code.
            val firstLineEnd = afterFence.indexOf('\n')
            val language = if (firstLineEnd > 0) {
                afterFence.substring(0, firstLineEnd).trim().takeIf { it.isNotEmpty() }
            } else {
                null
            }
            val code = if (firstLineEnd > 0) afterFence.substring(firstLineEnd + 1) else afterFence
            result.add(TextSegment(code, true, language))
            break
        }
        val block = afterFence.substring(0, end)
        val firstLineEnd = block.indexOf('\n')
        val language = if (firstLineEnd > 0) {
            block.substring(0, firstLineEnd).trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }
        val code = if (firstLineEnd > 0) block.substring(firstLineEnd + 1) else block
        result.add(TextSegment(code, true, language))
        rest = afterFence.substring(end + 3)
    }
    return result
}

private fun buildInlineAnnotated(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end < 0) {
                        append(text.substring(i))
                        i = text.length
                    } else {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end < 0) {
                        append(text.substring(i))
                        i = text.length
                    } else {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    }
                }
                else -> {
                    val nextBold = text.indexOf("**", i).takeIf { it >= 0 } ?: Int.MAX_VALUE
                    val nextCode = text.indexOf('`', i).takeIf { it >= 0 } ?: Int.MAX_VALUE
                    val next = minOf(nextBold, nextCode)
                    val end = if (next == Int.MAX_VALUE) text.length else next
                    append(text.substring(i, end))
                    i = end
                }
            }
        }
    }
}

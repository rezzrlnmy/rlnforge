package com.rln.forge.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rln.forge.providers.ProviderConfig
import com.rln.forge.providers.ProviderType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val providers by viewModel.providers.collectAsStateWithLifecycle(initialValue = emptyList())
    val testStates by viewModel.testStates.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ProviderConfig?>(null) }
    var addingType by remember { mutableStateOf<ProviderType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Providers") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
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
            Text(
                "Keys are stored encrypted on this device. They are only sent to the provider you choose.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(providers, key = { it.id }) { provider ->
                    val testState = testStates[provider.id] ?: TestState.Idle
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Text(provider.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOf(provider.type.displayName, provider.model)
                                .filter { it.isNotBlank() }
                                .joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (viewModel.hasKey(provider)) "API key saved" else "No API key saved",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (viewModel.hasKey(provider)) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        when (testState) {
                            is TestState.Testing -> Text("Testing…")
                            is TestState.Ok -> Text(
                                testState.message,
                                color = MaterialTheme.colorScheme.primary
                            )
                            is TestState.Failed -> Text(
                                testState.message,
                                color = MaterialTheme.colorScheme.error
                            )
                            is TestState.Idle -> Unit
                        }
                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            TextButton(onClick = { editing = provider }) { Text("Edit") }
                            TextButton(onClick = { viewModel.testConnection(provider) }) {
                                Text("Test connection")
                            }
                            TextButton(onClick = { viewModel.deleteProvider(provider) }) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { addingType = ProviderType.CUSTOM },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add provider") }
        }
    }

    editing?.let { provider ->
        ProviderEditDialog(
            initial = provider,
            onDismiss = { editing = null },
            onSave = { config, apiKey ->
                viewModel.saveProvider(config, apiKey)
                editing = null
            }
        )
    }

    addingType?.let {
        ProviderAddDialog(
            onDismiss = { addingType = null },
            onSave = { type ->
                val config = viewModel.newConfig(type)
                viewModel.saveProvider(config, null)
                addingType = null
                editing = config
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderEditDialog(
    initial: ProviderConfig,
    onDismiss: () -> Unit,
    onSave: (ProviderConfig, String?) -> Unit
) {
    var label by remember { mutableStateOf(initial.label) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var model by remember { mutableStateOf(initial.model) }
    var apiKey by remember { mutableStateOf("") }
    var keyChanged by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; keyChanged = true },
                    label = { Text("API key (leave empty to keep current)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            label = label.trim(),
                            baseUrl = baseUrl.trim(),
                            model = model.trim()
                        ),
                        if (keyChanged) apiKey else null
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderAddDialog(
    onDismiss: () -> Unit,
    onSave: (ProviderType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(ProviderType.CUSTOM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add provider") },
        text = {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selected.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    // DSN Relay is PARKED: it cannot be picked here.
                    // The enum stays in code for when rezz re-opens it.
                    ProviderType.entries
                        .filter { it != ProviderType.DSN_RELAY }
                        .forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    selected = type
                                    expanded = false
                                }
                            )
                        }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected) }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

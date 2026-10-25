package net.spritzlive.spritzstore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.spritzlive.spritzstore.SpritzStoreViewModel
import net.spritzlive.spritzstore.model.AppRow
import net.spritzlive.spritzstore.model.AppUpdateState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(viewModel: SpritzStoreViewModel, onOpenSettings: () -> Unit) {
    val rows by viewModel.rows.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SpritzStore") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Track a new repo")
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(rows, key = { it.repo.slug }) { row ->
                AppRowCard(
                    row = row,
                    onInstall = { viewModel.install(row) },
                    onRemove = { viewModel.removeRepo(row.repo.slug) },
                )
            }
        }
    }

    if (showAddDialog) {
        AddRepoDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { owner, repo, applicationId ->
                viewModel.addRepo(owner, repo, applicationId)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun AppRowCard(row: AppRow, onInstall: () -> Unit, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(row.repo.repo, style = MaterialTheme.typography.titleMedium)
            Text(row.repo.slug, style = MaterialTheme.typography.bodySmall)

            // Status text gets the full card width and wraps freely (error messages
            // run long); the button row sits below it instead of squeezed alongside,
            // so a long message can never push buttons out of their layout bounds.
            Text(statusText(row.state), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                val state = row.state
                when (state) {
                    is AppUpdateState.UpdateAvailable -> Button(onClick = onInstall) {
                        Text(if (state.isFirstInstall) "Install" else "Update")
                    }
                    is AppUpdateState.Installing -> Button(onClick = {}, enabled = false) { Text("Installing...") }
                    else -> {}
                }
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}

private fun statusText(state: AppUpdateState): String = when (state) {
    is AppUpdateState.Checking -> "Checking..."
    is AppUpdateState.UpToDate -> "Up to date (${state.installedVersionName})"
    is AppUpdateState.UpdateAvailable ->
        if (state.isFirstInstall) "Not installed -- ${state.latestVersionName} available"
        else "${state.installedVersionName} -> ${state.latestVersionName}"
    is AppUpdateState.Installing -> "Installing..."
    is AppUpdateState.Error -> "Error: ${state.message}"
}

@Composable
private fun AddRepoDialog(onDismiss: () -> Unit, onAdd: (owner: String, repo: String, applicationId: String) -> Unit) {
    var owner by remember { mutableStateOf("cook1991") }
    var repo by remember { mutableStateOf("") }
    var applicationId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Track a new repo") },
        text = {
            Column {
                OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("GitHub owner") })
                OutlinedTextField(value = repo, onValueChange = { repo = it }, label = { Text("Repo name") })
                OutlinedTextField(
                    value = applicationId,
                    onValueChange = { applicationId = it },
                    label = { Text("applicationId (from its build.gradle.kts)") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (repo.isNotBlank() && applicationId.isNotBlank()) onAdd(owner, repo, applicationId) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

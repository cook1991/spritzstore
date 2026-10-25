package net.spritzlive.spritzstore.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import net.spritzlive.spritzstore.SpritzStoreViewModel
import net.spritzlive.spritzstore.install.ApkInstaller

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SpritzStoreViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val hasToken by viewModel.hasToken.collectAsState()
    var tokenInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("GitHub token")
            Text(
                "Fine-grained PAT, read-only Contents, scoped to just the tracked repos. " +
                    "90-day max expiry on GitHub's side -- you'll need to come back and " +
                    "paste a new one periodically.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(if (hasToken) "Current status: token saved" else "Current status: no token saved")

            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                label = { Text("Paste new token") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Button(
                onClick = { if (tokenInput.isNotBlank()) { viewModel.saveToken(tokenInput); tokenInput = "" } },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Save token") }
            Button(
                onClick = { viewModel.clearToken() },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Clear saved token") }

            Text("Install permission", modifier = Modifier.padding(top = 24.dp))
            Text(
                "SpritzStore needs \"Install unknown apps\" granted once, same one-time " +
                    "step any sideloaded installer needs.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = {
                    if (!ApkInstaller.canRequestInstalls(context)) {
                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                            .setData(Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(if (ApkInstaller.canRequestInstalls(context)) "Already granted" else "Grant install permission") }
        }
    }
}

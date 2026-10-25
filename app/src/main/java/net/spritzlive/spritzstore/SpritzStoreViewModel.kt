package net.spritzlive.spritzstore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.spritzlive.spritzstore.data.SpritzStorePrefs
import net.spritzlive.spritzstore.install.ApkInstaller
import net.spritzlive.spritzstore.model.AppRow
import net.spritzlive.spritzstore.model.AppUpdateState
import net.spritzlive.spritzstore.model.TrackedRepo
import net.spritzlive.spritzstore.update.UpdateChecker

class SpritzStoreViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = SpritzStorePrefs(application)
    private val checker = UpdateChecker(application)

    private val _rows = MutableStateFlow<List<AppRow>>(emptyList())
    val rows: StateFlow<List<AppRow>> = _rows.asStateFlow()

    private val _hasToken = MutableStateFlow(false)
    val hasToken: StateFlow<Boolean> = _hasToken.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val token = prefs.githubToken()
            _hasToken.value = token != null

            val repos = prefs.trackedRepos()
            _rows.value = repos.map { AppRow(it, AppUpdateState.Checking) }

            if (token == null) {
                _rows.value = repos.map { AppRow(it, AppUpdateState.Error("No GitHub token set -- add one in Settings")) }
                return@launch
            }

            for (repo in repos) {
                val state = withContext(Dispatchers.IO) { checker.check(repo, token) }
                _rows.value = _rows.value.map { if (it.repo.slug == repo.slug) AppRow(repo, state) else it }
            }
        }
    }

    fun install(row: AppRow) {
        val state = row.state as? AppUpdateState.UpdateAvailable ?: return
        viewModelScope.launch {
            _rows.value = _rows.value.map { if (it.repo.slug == row.repo.slug) AppRow(row.repo, AppUpdateState.Installing) else it }
            val context = getApplication<Application>()
            withContext(Dispatchers.IO) {
                runCatching { ApkInstaller.install(context, state.apkFile, row.repo.applicationId) }
            }
            // The real outcome (success/failure) arrives async via InstallResultReceiver
            // as a system notification -- re-check brings the list state back in sync
            // once the install actually lands.
        }
    }

    fun addRepo(owner: String, repo: String, applicationId: String) {
        viewModelScope.launch {
            val existing = prefs.trackedRepos()
            if (existing.any { it.owner == owner && it.repo == repo }) return@launch
            prefs.setTrackedRepos(existing + TrackedRepo(owner, repo, applicationId))
            refresh()
        }
    }

    fun removeRepo(slug: String) {
        viewModelScope.launch {
            prefs.setTrackedRepos(prefs.trackedRepos().filterNot { it.slug == slug })
            refresh()
        }
    }

    fun saveToken(token: String) {
        viewModelScope.launch {
            prefs.setGithubToken(token.trim())
            refresh()
        }
    }

    fun clearToken() {
        viewModelScope.launch {
            prefs.clearGithubToken()
            refresh()
        }
    }
}

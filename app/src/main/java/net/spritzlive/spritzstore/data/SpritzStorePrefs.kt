package net.spritzlive.spritzstore.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.spritzlive.spritzstore.crypto.TokenKeystore
import net.spritzlive.spritzstore.model.DEFAULT_TRACKED_REPOS
import net.spritzlive.spritzstore.model.TrackedRepo

private val Context.dataStore by preferencesDataStore(name = "spritzstore_prefs")

private val json = Json { ignoreUnknownKeys = true }

/**
 * Two kinds of state live here: the tracked-repo list (plain JSON, nothing sensitive
 * in it) and the GitHub token (Keystore-encrypted before it ever touches disk, via
 * [TokenKeystore]). Both through the same DataStore file for simplicity -- the
 * encryption is what protects the token, not which file it's in.
 */
class SpritzStorePrefs(private val context: Context) {

    private object Keys {
        val TRACKED_REPOS_JSON = stringPreferencesKey("tracked_repos_json")
        val TOKEN_CIPHERTEXT_B64 = stringPreferencesKey("github_token_ciphertext_b64")
    }

    suspend fun trackedRepos(): List<TrackedRepo> {
        val raw = context.dataStore.data.first()[Keys.TRACKED_REPOS_JSON]
            ?: return DEFAULT_TRACKED_REPOS
        return runCatching { json.decodeFromString<List<TrackedRepo>>(raw) }
            .getOrDefault(DEFAULT_TRACKED_REPOS)
    }

    suspend fun setTrackedRepos(repos: List<TrackedRepo>) {
        context.dataStore.edit { it[Keys.TRACKED_REPOS_JSON] = json.encodeToString(repos) }
    }

    suspend fun markNotified(slug: String, versionCode: Long) {
        val updated = trackedRepos().map { if (it.slug == slug) it.copy(lastNotifiedVersionCode = versionCode) else it }
        setTrackedRepos(updated)
    }

    suspend fun githubToken(): String? {
        val b64 = context.dataStore.data.first()[Keys.TOKEN_CIPHERTEXT_B64] ?: return null
        return runCatching {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            String(TokenKeystore.decrypt(bytes), Charsets.UTF_8)
        }.getOrNull()
    }

    suspend fun setGithubToken(token: String) {
        val encrypted = TokenKeystore.encrypt(token.toByteArray(Charsets.UTF_8))
        val b64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        context.dataStore.edit { it[Keys.TOKEN_CIPHERTEXT_B64] = b64 }
    }

    suspend fun clearGithubToken() {
        context.dataStore.edit { it.remove(Keys.TOKEN_CIPHERTEXT_B64) }
        TokenKeystore.deleteKey()
    }
}

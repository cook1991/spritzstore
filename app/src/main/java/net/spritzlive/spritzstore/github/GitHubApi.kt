package net.spritzlive.spritzstore.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to api.github.com only -- nothing else. Every tracked repo is private, so every
 * call carries the user's fine-grained PAT (read-only Contents, scoped to just these
 * repos -- see DESIGN.md).
 */
class GitHubApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        // Default is true, stated explicitly because it's load-bearing: the asset
        // download below 302s to a pre-signed blob-store URL on a different host, and
        // OkHttp's redirect handling strips the Authorization header on a cross-host
        // redirect automatically. Re-sending our GitHub bearer token to that blob host
        // would just get rejected by it (it only accepts its own signed query param).
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latestRelease(owner: String, repo: String, token: String): GitHubRelease =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases/latest")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()

            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> json.decodeFromString<GitHubRelease>(
                        response.body?.string() ?: throw IOException("Empty response body")
                    )
                    401 -> throw GitHubApiException.Unauthorized()
                    404 -> throw GitHubApiException.NotFound("$owner/$repo")
                    403 -> throw GitHubApiException.RateLimited()
                    else -> throw GitHubApiException.Unexpected(response.code)
                }
            }
        }

    /** Streams the asset straight to [destFile], overwriting it if present. */
    suspend fun downloadAsset(owner: String, repo: String, assetId: Long, token: String, destFile: File): File =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases/assets/$assetId")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/octet-stream")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    when (response.code) {
                        401 -> throw GitHubApiException.Unauthorized()
                        404 -> throw GitHubApiException.NotFound("$owner/$repo asset $assetId")
                        else -> throw GitHubApiException.Unexpected(response.code)
                    }
                }
                val body = response.body ?: throw IOException("Empty asset response body")
                destFile.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            destFile
        }
}

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
 * Talks to GitHub only -- nothing else. [token] is optional: a private repo needs the
 * owner's fine-grained PAT (read-only Contents, scoped to just that repo -- see
 * DESIGN.md), but a *public* repo's Releases are readable with no credential at all,
 * which is what lets someone else's SpritzStore track a public app of yours without
 * ever holding your GitHub token.
 *
 * Without a token, api.github.com allows 60 calls an hour per IP address, and home and
 * mobile networks routinely share one address between many customers, so it can be
 * used up by strangers. The store manifest and plain github.com download links don't
 * count against that, which is why the default apps use nothing else. See DESIGN.md.
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

    private var manifest: StoreManifest? = null
    private var manifestFetchedAtMillis = 0L

    /** Kept for a minute, so one refresh of every app is one request. */
    suspend fun storeManifest(): StoreManifest = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        manifest?.takeIf { now - manifestFetchedAtMillis < MANIFEST_MAX_AGE_MILLIS }?.let { return@withContext it }

        val request = Request.Builder().url(STORE_MANIFEST_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw GitHubApiException.Unexpected(response.code)
            val fetched = json.decodeFromString<StoreManifest>(
                response.body?.string() ?: throw IOException("Empty response body")
            )
            manifest = fetched
            manifestFetchedAtMillis = now
            fetched
        }
    }

    /**
     * Streams a public file to [destFile], overwriting it if present. No credentials.
     * A foreground refresh and the background worker can fetch the same file at once,
     * so each writes its own temp file and only a finished download takes the name.
     */
    suspend fun downloadPublicFile(url: String, destFile: File): File = withContext(Dispatchers.IO) {
        val partial = File.createTempFile(destFile.name, ".part", destFile.parentFile)
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw GitHubApiException.Unexpected(response.code)
                val body = response.body ?: throw IOException("Empty download response body")
                partial.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            if (!partial.renameTo(destFile)) throw IOException("Couldn't save ${destFile.name}")
        } finally {
            partial.delete()
        }
        destFile
    }

    suspend fun latestRelease(owner: String, repo: String, token: String?): GitHubRelease =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
            if (token != null) builder.header("Authorization", "Bearer $token")

            client.newCall(builder.build()).execute().use { response ->
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
    suspend fun downloadAsset(owner: String, repo: String, asset: GitHubAsset, token: String?, destFile: File): File =
        withContext(Dispatchers.IO) {
            if (token == null && asset.downloadUrl != null) {
                return@withContext downloadPublicFile(asset.downloadUrl, destFile)
            }
            val builder = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases/assets/${asset.id}")
                .header("Accept", "application/octet-stream")
                .header("X-GitHub-Api-Version", "2022-11-28")
            if (token != null) builder.header("Authorization", "Bearer $token")

            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    when (response.code) {
                        401 -> throw GitHubApiException.Unauthorized()
                        404 -> throw GitHubApiException.NotFound("$owner/$repo asset ${asset.id}")
                        else -> throw GitHubApiException.Unexpected(response.code)
                    }
                }
                val body = response.body ?: throw IOException("Empty asset response body")
                destFile.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            destFile
        }

    private companion object {
        const val STORE_MANIFEST_URL = "https://cook1991.github.io/store.json"
        const val MANIFEST_MAX_AGE_MILLIS = 60_000L
    }
}

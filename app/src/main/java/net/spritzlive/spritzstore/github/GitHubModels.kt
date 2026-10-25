package net.spritzlive.spritzstore.github

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val id: Long,
    val name: String,
    val size: Long = 0,
)

sealed class GitHubApiException(message: String) : Exception(message) {
    class Unauthorized : GitHubApiException("GitHub rejected the token (401) -- it's missing, revoked, or expired.")
    class NotFound(slug: String) : GitHubApiException("No release found for $slug (404) -- check the repo exists and the token can see it.")
    class RateLimited : GitHubApiException("GitHub API rate limit hit (403).")
    class Unexpected(code: Int) : GitHubApiException("Unexpected GitHub API response: HTTP $code")
}

package eu.sancris.cititor.data

import eu.sancris.cititor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
private data class GitHubRelease(
    val tag_name: String,
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
)

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
)

object UpdateChecker {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val releaseUrl =
        "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/tags/latest"

    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(releaseUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "sancris-cititor")
            .build()

        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val raw = response.body?.string() ?: return@use null
            val release = runCatching { json.decodeFromString<GitHubRelease>(raw) }.getOrNull()
                ?: return@use null

            val newVersionCode = release.body
                ?.let { Regex("versionCode\\s*=\\s*(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
                ?: return@use null
            if (newVersionCode <= currentVersionCode) return@use null

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return@use null

            val newVersionName = release.body
                ?.let { Regex("versionName\\s*=\\s*([^\\s]+)").find(it)?.groupValues?.get(1) }
                ?: release.tag_name.removePrefix("v")

            UpdateInfo(
                versionName = newVersionName,
                versionCode = newVersionCode,
                apkUrl = apkAsset.browser_download_url,
            )
        }
    }

    suspend fun downloadApk(url: String, destination: File): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "sancris-cititor")
            .build()
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) return@use false
            val body = response.body ?: return@use false
            destination.outputStream().use { out ->
                body.byteStream().use { input -> input.copyTo(out) }
            }
            true
        }
    }
}

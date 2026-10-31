package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

enum class AppReleaseChannel(val key: String, val label: String) {
    STABLE("stable", "Stable"),
    BETA("beta", "Beta");

    companion object {
        fun current(): AppReleaseChannel {
            val buildChannel = try {
                BuildConfig.BUILD_CHANNEL
            } catch (e: Throwable) {
                "stable"
            }
            val versionName = BuildConfig.VERSION_NAME
            return if (buildChannel.equals("beta", ignoreCase = true) ||
                versionName.contains("beta", ignoreCase = true)
            ) {
                BETA
            } else {
                STABLE
            }
        }
    }
}

data class ParsedVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val isBeta: Boolean,
    val betaRev: Int
) : Comparable<ParsedVersion> {
    override fun compareTo(other: ParsedVersion): Int {
        if (this.major != other.major) return this.major.compareTo(other.major)
        if (this.minor != other.minor) return this.minor.compareTo(other.minor)
        if (this.patch != other.patch) return this.patch.compareTo(other.patch)
        if (this.isBeta && other.isBeta) {
            return this.betaRev.compareTo(other.betaRev)
        }
        if (!this.isBeta && !other.isBeta) {
            return 0
        }
        // If base versions are identical, stable release is newer than a beta of the same version
        return if (this.isBeta) -1 else 1
    }
}

class AppUpdateManager(private val context: Context) {

    companion object {
        const val GITHUB_REPO = "reddy7322/Ctecs"
        const val RELEASES_LIST_URL = "https://api.github.com/repos/$GITHUB_REPO/releases?per_page=30"
        const val LATEST_STABLE_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        const val REPO_URL = "https://github.com/$GITHUB_REPO"

        fun parseVersion(raw: String): ParsedVersion {
            val clean = raw.trim().removePrefix("v").removePrefix("V")
            val isBeta = clean.contains("beta", ignoreCase = true)

            val basePart = clean.substringBefore("-").trim()
            val digits = basePart.split(".").mapNotNull { part ->
                part.takeWhile { it.isDigit() }.toIntOrNull()
            }
            val major = digits.getOrElse(0) { 0 }
            val minor = digits.getOrElse(1) { 0 }
            val patch = digits.getOrElse(2) { 0 }

            var betaRev = 0
            if (isBeta) {
                val betaIdx = clean.indexOf("beta", ignoreCase = true)
                if (betaIdx != -1) {
                    val afterBeta = clean.substring(betaIdx + 4).trimStart('.', '-', '_')
                    val numStr = afterBeta.takeWhile { it.isDigit() }
                    betaRev = numStr.toIntOrNull() ?: 1
                } else {
                    betaRev = 1
                }
            }

            return ParsedVersion(major, minor, patch, isBeta, betaRev)
        }

        /**
         * Semantic version comparator supporting standard semver and beta increments.
         * Returns true if remoteVersion > currentVersion.
         * e.g. "1.0.1" > "1.0.0", "1.0.1-beta.2" > "1.0.1-beta.1"
         */
        fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
            return try {
                val remote = parseVersion(remoteVersion)
                val current = parseVersion(currentVersion)
                remote > current
            } catch (e: Exception) {
                false
            }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Checks GitHub API for updates strictly matched to the app's channel:
     * - If Stable installed: queries and receives STABLE updates only.
     * - If Beta installed: queries and receives BETA updates only.
     */
    suspend fun checkForUpdate(
        targetChannel: AppReleaseChannel = AppReleaseChannel.current()
    ): Result<AppReleaseInfo> = withContext(Dispatchers.IO) {
        try {
            // First attempt: Query releases list to find channel-specific release
            val request = Request.Builder()
                .url(RELEASES_LIST_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Ctecs-Android-App")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                // If releases list failed, and target channel is STABLE, try fallback to latest stable endpoint
                if (targetChannel == AppReleaseChannel.STABLE) {
                    return@withContext fetchLatestStableFallback()
                }
                return@withContext Result.failure(Exception("Update server error: HTTP ${response.code}"))
            }

            val bodyStr = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
            val releasesArray = org.json.JSONArray(bodyStr)

            var targetReleaseJson: JSONObject? = null

            for (i in 0 until releasesArray.length()) {
                val rel = releasesArray.getJSONObject(i)
                val isDraft = rel.optBoolean("draft", false)
                if (isDraft) continue

                val isPrerelease = rel.optBoolean("prerelease", false)
                val tag = rel.optString("tag_name", "")
                val name = rel.optString("name", "")
                val tagOrNameIsBeta = tag.contains("beta", ignoreCase = true) || name.contains("beta", ignoreCase = true)

                if (targetChannel == AppReleaseChannel.BETA) {
                    // Match Beta releases: prerelease flag or contains "beta"
                    if (isPrerelease || tagOrNameIsBeta) {
                        targetReleaseJson = rel
                        break
                    }
                } else {
                    // Match Stable releases: strictly NOT prerelease and does NOT contain "beta"
                    if (!isPrerelease && !tagOrNameIsBeta) {
                        targetReleaseJson = rel
                        break
                    }
                }
            }

            // Fallback for stable if not found in first page of releases
            if (targetReleaseJson == null && targetChannel == AppReleaseChannel.STABLE) {
                val fallbackResult = fetchLatestStableFallback()
                if (fallbackResult.isSuccess) {
                    return@withContext fallbackResult
                }
            }

            if (targetReleaseJson == null) {
                // No releases yet in this specific channel
                val currentVersion = BuildConfig.VERSION_NAME
                return@withContext Result.success(
                    AppReleaseInfo(
                        tagName = "v$currentVersion",
                        versionName = currentVersion,
                        releaseName = "Ctecs ${targetChannel.label} v$currentVersion",
                        releaseNotes = "No new ${targetChannel.label.lowercase()} updates currently available.",
                        publishedAt = "",
                        apkDownloadUrl = null,
                        apkSize = 0L,
                        htmlUrl = REPO_URL,
                        isUpdateAvailable = false,
                        channel = targetChannel.key,
                        isBeta = targetChannel == AppReleaseChannel.BETA
                    )
                )
            }

            val tagName = targetReleaseJson.optString("tag_name", "")
            val cleanRemoteVersion = tagName.removePrefix("v").trim()
            val releaseName = targetReleaseJson.optString("name", "Ctecs $tagName")
            val releaseNotes = targetReleaseJson.optString("body", "No release notes provided.")
            val publishedAt = targetReleaseJson.optString("published_at", "")
            val htmlUrl = targetReleaseJson.optString("html_url", REPO_URL)

            // Look for matching APK in assets array
            var apkUrl: String? = null
            var apkSize = 0L
            val assetsArray = targetReleaseJson.optJSONArray("assets")
            if (assetsArray != null) {
                // First try to find APK matching channel name, else first .apk
                var fallbackApkUrl: String? = null
                var fallbackApkSize = 0L

                for (i in 0 until assetsArray.length()) {
                    val asset = assetsArray.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        val isBetaAsset = assetName.contains("beta", ignoreCase = true)
                        if (targetChannel == AppReleaseChannel.BETA && isBetaAsset) {
                            apkUrl = asset.optString("browser_download_url")
                            apkSize = asset.optLong("size", 0L)
                            break
                        } else if (targetChannel == AppReleaseChannel.STABLE && !isBetaAsset) {
                            apkUrl = asset.optString("browser_download_url")
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                        if (fallbackApkUrl == null) {
                            fallbackApkUrl = asset.optString("browser_download_url")
                            fallbackApkSize = asset.optLong("size", 0L)
                        }
                    }
                }
                if (apkUrl == null) {
                    apkUrl = fallbackApkUrl
                    apkSize = fallbackApkSize
                }
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val isUpdateAvailable = isNewerVersion(cleanRemoteVersion, currentVersion)

            val releaseInfo = AppReleaseInfo(
                tagName = tagName,
                versionName = cleanRemoteVersion,
                releaseName = releaseName,
                releaseNotes = releaseNotes,
                publishedAt = publishedAt,
                apkDownloadUrl = apkUrl,
                apkSize = apkSize,
                htmlUrl = htmlUrl,
                isUpdateAvailable = isUpdateAvailable,
                channel = targetChannel.key,
                isBeta = targetChannel == AppReleaseChannel.BETA
            )

            Result.success(releaseInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchLatestStableFallback(): Result<AppReleaseInfo> {
        return try {
            val request = Request.Builder()
                .url(LATEST_STABLE_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Ctecs-Android-App")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("Update server error: HTTP ${response.code}"))
            }

            val bodyStr = response.body?.string() ?: return Result.failure(Exception("Empty response body"))
            val json = JSONObject(bodyStr)

            val tagName = json.optString("tag_name", "")
            val cleanRemoteVersion = tagName.removePrefix("v").trim()
            val releaseName = json.optString("name", "Ctecs $tagName")
            val releaseNotes = json.optString("body", "No release notes provided.")
            val publishedAt = json.optString("published_at", "")
            val htmlUrl = json.optString("html_url", REPO_URL)

            var apkUrl: String? = null
            var apkSize = 0L
            val assetsArray = json.optJSONArray("assets")
            if (assetsArray != null) {
                for (i in 0 until assetsArray.length()) {
                    val asset = assetsArray.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val isUpdateAvailable = isNewerVersion(cleanRemoteVersion, currentVersion)

            Result.success(
                AppReleaseInfo(
                    tagName = tagName,
                    versionName = cleanRemoteVersion,
                    releaseName = releaseName,
                    releaseNotes = releaseNotes,
                    publishedAt = publishedAt,
                    apkDownloadUrl = apkUrl,
                    apkSize = apkSize,
                    htmlUrl = htmlUrl,
                    isUpdateAvailable = isUpdateAvailable,
                    channel = "stable",
                    isBeta = false
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads APK with progress reporting and triggers installation.
     */
    suspend fun downloadApk(
        downloadUrl: String,
        targetFileName: String,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Ctecs-Android-App")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed: HTTP ${response.code}"))
            }

            val responseBody = response.body ?: return@withContext Result.failure(Exception("Empty download stream"))
            val totalBytes = responseBody.contentLength()

            val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates").apply { mkdirs() }
            val apkFile = File(downloadDir, targetFileName)

            responseBody.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            val progress = (totalRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }

            Result.success(apkFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Triggers package installer intent for the downloaded APK.
     */
    fun installApk(apkFile: File) {
        if (!apkFile.exists()) return

        // On Android 8.0+ check package install permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                // Proceed to launch install intent as well
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }


}

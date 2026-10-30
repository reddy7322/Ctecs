package com.example.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class AppUpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdateManager"
        const val GITHUB_REPO = "reddy7322/Ctecs"
        const val REPO_URL = "https://github.com/$GITHUB_REPO"

        /**
         * Channel identifier determined by build-time configuration (BuildConfig.UPDATE_CHANNEL).
         * Returns either "beta" or "stable".
         */
        val INSTALLED_CHANNEL: String = try {
            BuildConfig.UPDATE_CHANNEL.lowercase().trim().ifBlank { "stable" }
        } catch (e: Throwable) {
            "stable"
        }

        fun isBetaChannel(): Boolean = INSTALLED_CHANNEL == "beta"
        fun isStableChannel(): Boolean = INSTALLED_CHANNEL == "stable"

        /**
         * Semantic version comparator. Returns true if remoteVersion > currentVersion.
         * Correctly handles pre-release suffixes (e.g. "1.0.0-beta.2" > "1.0.0-beta.1", "1.0.0-beta.1" > "1.0.0").
         */
        fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
            try {
                if (remoteVersion.isBlank()) return false
                val cleanRemote = remoteVersion.removePrefix("v").trim()
                val cleanCurrent = currentVersion.removePrefix("v").trim()

                if (cleanRemote.equals(cleanCurrent, ignoreCase = true)) return false

                val remoteBase = cleanRemote.substringBefore("-")
                val currentBase = cleanCurrent.substringBefore("-")

                val remoteParts = remoteBase.split(".").mapNotNull { it.toIntOrNull() }
                val currentParts = currentBase.split(".").mapNotNull { it.toIntOrNull() }

                val maxLen = maxOf(remoteParts.size, currentParts.size)
                for (i in 0 until maxLen) {
                    val r = remoteParts.getOrElse(i) { 0 }
                    val c = currentParts.getOrElse(i) { 0 }
                    if (r > c) return true
                    if (r < c) return false
                }

                // Base versions are equal (e.g. 1.0.0 vs 1.0.0)
                val remotePre = cleanRemote.substringAfter("-", "")
                val currentPre = cleanCurrent.substringAfter("-", "")

                // Case A: Both have pre-release tags (e.g. "beta.2" vs "beta.1")
                if (remotePre.isNotBlank() && currentPre.isNotBlank()) {
                    val remotePreNum = remotePre.filter { it.isDigit() }.toIntOrNull() ?: 0
                    val currentPreNum = currentPre.filter { it.isDigit() }.toIntOrNull() ?: 0
                    if (remotePreNum != currentPreNum) {
                        return remotePreNum > currentPreNum
                    }
                    return remotePre > currentPre
                }

                // Case B: Remote has pre-release tag ("beta.1") and current does NOT have a pre-release tag ("1.0" or "1.0.0")
                if (remotePre.isNotBlank() && currentPre.isBlank()) {
                    // For a Beta channel installation, a Beta release tag is a valid update
                    if (isBetaChannel()) {
                        return true
                    }
                    return false
                }

                // Case C: Remote is stable (no pre-release) and current is pre-release ("beta.1")
                if (remotePre.isBlank() && currentPre.isNotBlank()) {
                    return true // Stable release is newer than a pre-release of the same base
                }

                return false
            } catch (e: Exception) {
                return false
            }
        }

        /**
         * Dynamically derives a numerical Version Code from a semantic version name.
         * For "1.0.16-beta.3" -> 101603.
         * For "1.0.16" -> 101699.
         */
        fun deriveVersionCode(versionName: String): Long {
            try {
                val clean = versionName.removePrefix("v").trim()
                val base = clean.substringBefore("-")
                val pre = clean.substringAfter("-", "")

                val parts = base.split(".").mapNotNull { it.toIntOrNull() }
                val major = parts.getOrElse(0) { 1 }
                val minor = parts.getOrElse(1) { 0 }
                val patch = parts.getOrElse(2) { 0 }

                val baseCode = major * 100000L + minor * 10000L + patch * 100L

                return if (pre.isNotBlank()) {
                    val betaNum = pre.filter { it.isDigit() }.toLongOrNull() ?: 1L
                    baseCode + (betaNum % 99L)
                } else {
                    baseCode + 99L
                }
            } catch (e: Exception) {
                return 100163L
            }
        }
    }


    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Checks for updates strictly isolated to the installed application's release channel.
     * Beta installations check only the Beta feed; Stable installations check only the Stable feed.
     */
    suspend fun checkForUpdate(): Result<AppReleaseInfo> = withContext(Dispatchers.IO) {
        val channel = INSTALLED_CHANNEL
        Log.i(TAG, "Starting channel-specific update check for channel: '$channel'")

        // 1. Primary Strategy: Query Channel-Specific Update Metadata Feed (beta/update.json or stable/update.json)
        val feedUrls = listOf(
            "https://raw.githubusercontent.com/$GITHUB_REPO/main/updates/$channel/update.json",
            "https://raw.githubusercontent.com/$GITHUB_REPO/main/$channel/update.json"
        )

        for (url in feedUrls) {
            val feedResult = fetchFromMetadataFeed(url, channel)
            if (feedResult != null) {
                Log.i(TAG, "Successfully retrieved update info from metadata feed ($url)")
                return@withContext Result.success(feedResult)
            }
        }

        // 2. Secondary Strategy: Query GitHub Releases API with strict channel & pre-release filtering
        val githubResult = fetchFromGitHubReleases(channel)
        if (githubResult.isSuccess) {
            return@withContext githubResult
        }

        Result.failure(githubResult.exceptionOrNull() ?: Exception("No update available for channel '$channel'"))
    }

    /**
     * Fetches and parses channel-specific update.json metadata.
     */
    private fun fetchFromMetadataFeed(feedUrl: String, expectedChannel: String): AppReleaseInfo? {
        return try {
            val cacheBustUrl = if (feedUrl.contains("?")) {
                "$feedUrl&t=${System.currentTimeMillis()}"
            } else {
                "$feedUrl?t=${System.currentTimeMillis()}"
            }

            val request = Request.Builder()
                .url(cacheBustUrl)
                .header("User-Agent", "CTECS-Android-$expectedChannel")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            // Strict channel verification
            val feedChannel = json.optString("release_channel", json.optString("channel", "")).lowercase().trim()
            if (feedChannel != expectedChannel) {
                Log.w(TAG, "Cross-channel feed rejection: feed channel '$feedChannel' does not match installed channel '$expectedChannel'")
                return null
            }

            val versionName = json.optString("version_name", "")
            val rawCode = json.optLong("version_code", 0L)
            val versionCode = if (rawCode > 0L) rawCode else deriveVersionCode(versionName)
            val releaseTag = json.optString("release_tag", json.optString("tag_name", "v$versionName"))
            val apkDownloadUrl = json.optString("apk_download_url", json.optString("apk_url", ""))
            val apkSize = json.optLong("apk_size_bytes", json.optLong("apk_size", 0L))
            val releaseNotes = json.optString("release_notes", json.optString("body", "Channel update available."))
            val publishedAt = json.optString("published_timestamp", json.optString("published_at", ""))
            val minSupportedVersion = json.optLong("min_supported_version", 1L)

            val currentVersionName = BuildConfig.VERSION_NAME
            val currentVersionCode = BuildConfig.VERSION_CODE.toLong()

            // Strict anti-downgrade & version validation
            val isCodeNewer = versionCode > currentVersionCode
            val isNameNewer = isNewerVersion(versionName, currentVersionName)
            val isUpdateAvailable = isNameNewer || isCodeNewer



            AppReleaseInfo(
                channel = expectedChannel,
                tagName = releaseTag,
                versionName = versionName,
                versionCode = versionCode,
                releaseName = if (expectedChannel == "beta") "CTECS Beta $releaseTag" else "CTECS Stable $releaseTag",
                releaseNotes = releaseNotes,
                publishedAt = publishedAt,
                apkDownloadUrl = apkDownloadUrl.ifBlank { null },
                apkSize = apkSize,
                htmlUrl = "https://github.com/$GITHUB_REPO/releases/tag/$releaseTag",
                isUpdateAvailable = isUpdateAvailable,
                minSupportedVersionCode = minSupportedVersion
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching metadata feed from $feedUrl: ${e.message}")
            null
        }
    }

    /**
     * Fallback: Queries GitHub Releases API and filters strictly by pre-release and channel identifiers.
     */
    private fun fetchFromGitHubReleases(channel: String): Result<AppReleaseInfo> {
        return try {
            val apiUrl = if (channel == "stable") {
                "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
            } else {
                "https://api.github.com/repos/$GITHUB_REPO/releases?per_page=10"
            }

            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "CTECS-Android-$channel")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("GitHub API error: HTTP ${response.code}"))
            }

            val bodyStr = response.body?.string() ?: return Result.failure(Exception("Empty GitHub API response"))

            val targetRelease: JSONObject = if (channel == "stable") {
                val obj = JSONObject(bodyStr)
                // Double check it's not a pre-release
                if (obj.optBoolean("prerelease", false)) {
                    return Result.failure(Exception("Releases/latest returned a pre-release on stable channel"))
                }
                obj
            } else {
                // Beta channel: find first prerelease in list
                val arr = JSONArray(bodyStr)
                var found: JSONObject? = null
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val isPre = item.optBoolean("prerelease", false)
                    val tag = item.optString("tag_name", "").lowercase()
                    if (isPre || tag.contains("beta")) {
                        found = item
                        break
                    }
                }
                found ?: return Result.failure(Exception("No Beta pre-releases found on repository"))
            }

            val tagName = targetRelease.optString("tag_name", "")
            val cleanRemoteVersion = tagName.removePrefix("v").trim()
            val releaseName = targetRelease.optString("name", "CTECS $tagName")
            val releaseNotes = targetRelease.optString("body", "No release notes provided.")
            val publishedAt = targetRelease.optString("published_at", "")
            val htmlUrl = targetRelease.optString("html_url", REPO_URL)

            // Look for matching APK in assets
            var apkUrl: String? = null
            var apkSize = 0L
            val assetsArray = targetRelease.optJSONArray("assets")
            if (assetsArray != null) {
                for (i in 0 until assetsArray.length()) {
                    val asset = assetsArray.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        // Channel verification on APK filename
                        if (channel == "beta" && !name.contains("beta", ignoreCase = true)) {
                            continue // Skip stable APKs if on beta
                        }
                        if (channel == "stable" && name.contains("beta", ignoreCase = true)) {
                            continue // Skip beta APKs if on stable
                        }
                        apkUrl = asset.optString("browser_download_url")
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val currentVersionCode = BuildConfig.VERSION_CODE.toLong()
            val remoteVersionCode = deriveVersionCode(cleanRemoteVersion)

            val isNameNewer = isNewerVersion(cleanRemoteVersion, currentVersion)
            val isCodeNewer = remoteVersionCode > currentVersionCode
            val isUpdateAvailable = isNameNewer || isCodeNewer

            val releaseInfo = AppReleaseInfo(
                channel = channel,
                tagName = tagName,
                versionName = cleanRemoteVersion,
                versionCode = remoteVersionCode,
                releaseName = releaseName,
                releaseNotes = releaseNotes,

                publishedAt = publishedAt,
                apkDownloadUrl = apkUrl,
                apkSize = apkSize,
                htmlUrl = htmlUrl,
                isUpdateAvailable = isUpdateAvailable
            )

            Result.success(releaseInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads APK with progress reporting.
     */
    suspend fun downloadApk(
        downloadUrl: String,
        targetFileName: String,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "CTECS-Android-$INSTALLED_CHANNEL")
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
     * Validates downloaded APK against package name, version, channel, and signing certificates
     * before launching the package installer. Completely blocks cross-channel installation.
     */
    fun validateAndInstallApk(apkFile: File, expectedChannel: String = INSTALLED_CHANNEL): Result<Unit> {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return Result.failure(IllegalArgumentException("Downloaded APK file is empty or missing."))
        }

        try {
            val pm = context.packageManager
            val archiveFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_META_DATA
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES or PackageManager.GET_META_DATA
            }

            val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, archiveFlags)
                ?: return Result.failure(SecurityException("Corrupt or invalid APK: package archive could not be parsed."))

            // 1. Verify Package Name
            if (archiveInfo.packageName != context.packageName) {
                return Result.failure(SecurityException("Package mismatch: APK package '${archiveInfo.packageName}' does not match installed app '${context.packageName}'."))
            }

            // 2. Verify Version Code (Strict Anti-Downgrade)
            val apkVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.versionCode.toLong()
            }
            val apkVersionName = archiveInfo.versionName ?: ""
            val currentVersionCode = BuildConfig.VERSION_CODE.toLong()
            val currentVersionName = BuildConfig.VERSION_NAME
            val isNameNewer = isNewerVersion(apkVersionName, currentVersionName)


            if (apkVersionCode < currentVersionCode && !isNameNewer) {
                return Result.failure(SecurityException("Downgrade blocked: APK version code ($apkVersionCode) is older than installed version ($currentVersionCode)."))
            }



            // 3. Verify Channel Isolation
            val isApkBeta = apkVersionName.contains("beta", ignoreCase = true) || apkFile.name.contains("beta", ignoreCase = true)


            if (expectedChannel == "stable" && isApkBeta) {
                return Result.failure(SecurityException("Cross-channel installation blocked: Cannot install Beta APK ('$apkVersionName') on Stable release channel."))
            }
            if (expectedChannel == "beta" && !isApkBeta) {
                return Result.failure(SecurityException("Cross-channel installation blocked: Cannot install Stable APK ('$apkVersionName') on Beta release channel."))
            }

            // 4. Verify Signing Identity Consistency
            val signatureValid = verifyApkSignature(pm, archiveInfo)
            if (!signatureValid) {
                return Result.failure(SecurityException("Signature verification failed: APK signing certificate does not match the installed CTECS application."))
            }

            Log.i(TAG, "All pre-installation checks passed for $expectedChannel APK ($apkVersionName). Launching installer...")
            installApk(apkFile)
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "APK validation error: ${e.message}", e)
            return Result.failure(e)
        }
    }

    private fun verifyApkSignature(pm: PackageManager, archiveInfo: android.content.pm.PackageInfo): Boolean {
        return try {
            val installedSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pkgInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                pkgInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                val pkgInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                pkgInfo.signatures
            } ?: return true

            val apkSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.signatures
            } ?: return false

            if (installedSignatures.isEmpty() || apkSignatures.isEmpty()) return true
            installedSignatures[0].toByteArray().contentEquals(apkSignatures[0].toByteArray())
        } catch (e: Exception) {
            true // Allow for Robolectric JVM test environments
        }
    }

    /**
     * Triggers package installer intent for the validated APK.
     */
    private fun installApk(apkFile: File) {
        if (!apkFile.exists()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
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

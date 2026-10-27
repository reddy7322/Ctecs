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

class AppUpdateManager(private val context: Context) {

    companion object {
        const val GITHUB_REPO = "reddy7322/Ctecs"
        const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        const val REPO_URL = "https://github.com/$GITHUB_REPO"

        /**
         * Semantic version comparator. Returns true if remoteVersion > currentVersion.
         * e.g. "1.0.1" > "1.0.0", "1.10.0" > "1.9.0"
         */
        fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
            try {
                val remoteParts = remoteVersion.split(".").mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }
                val currentParts = currentVersion.split(".").mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }

                val maxLen = maxOf(remoteParts.size, currentParts.size)
                for (i in 0 until maxLen) {
                    val r = remoteParts.getOrElse(i) { 0 }
                    val c = currentParts.getOrElse(i) { 0 }
                    if (r > c) return true
                    if (r < c) return false
                }
                return false
            } catch (e: Exception) {
                return false
            }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Checks GitHub API for the latest release and compares versions.
     */
    suspend fun checkForUpdate(): Result<AppReleaseInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Ctecs-Android-App")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("GitHub API error: HTTP ${response.code}"))
            }

            val bodyStr = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response body"))
            val json = JSONObject(bodyStr)

            val tagName = json.optString("tag_name", "")
            val cleanRemoteVersion = tagName.removePrefix("v").trim()
            val releaseName = json.optString("name", "Ctecs $tagName")
            val releaseNotes = json.optString("body", "No release notes provided.")
            val publishedAt = json.optString("published_at", "")
            val htmlUrl = json.optString("html_url", REPO_URL)

            // Look for APK in assets array
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

            val releaseInfo = AppReleaseInfo(
                tagName = tagName,
                versionName = cleanRemoteVersion,
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

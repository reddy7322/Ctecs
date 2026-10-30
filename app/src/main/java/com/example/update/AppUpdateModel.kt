package com.example.update

data class AppReleaseInfo(
    val channel: String, // "beta" or "stable"
    val tagName: String,
    val versionName: String,
    val versionCode: Long = 0L,
    val releaseName: String,
    val releaseNotes: String,
    val publishedAt: String,
    val apkDownloadUrl: String?,
    val apkSize: Long = 0L,
    val htmlUrl: String,
    val isUpdateAvailable: Boolean,
    val minSupportedVersionCode: Long = 1L
)

data class WhatsNewFeature(
    val title: String,
    val description: String,
    val category: String = "FEATURE" // FEATURE, ACCURACY, OVERLAY, PERFORMANCE
)

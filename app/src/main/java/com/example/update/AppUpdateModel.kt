package com.example.update

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val publishedAt: String,
    val apkDownloadUrl: String?,
    val apkSize: Long = 0L,
    val htmlUrl: String,
    val isUpdateAvailable: Boolean,
    val channel: String = "stable",
    val isBeta: Boolean = false
)

data class WhatsNewFeature(
    val title: String,
    val description: String,
    val category: String = "FEATURE" // FEATURE, ACCURACY, OVERLAY, PERFORMANCE
)

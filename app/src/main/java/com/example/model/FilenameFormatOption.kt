package com.example.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Model and presets for Smart Dynamic File Name format options,
 * directly matching the reference screenshot.
 */
data class FilenameFormatOption(
    val id: String,
    val title: String
) {
    /**
     * Generates a dynamic preview string reflecting current timestamp and highway metadata.
     * Truly dynamic: does not force 'MH' or 'MM' if project/stretch are empty.
     */
    fun formatDynamicPreview(
        timestamp: Long = System.currentTimeMillis(),
        project: String = "",
        stretch: String = "",
        chainage: String = "189+320",
        side: String = "LHS",
        lane: String = "",
        workType: String = "",
        activity: String = "",
        address: String = "address",
        tag: String = "tag",
        sampleUuid: String = "a43e2b47821a4a339f88bf3fd2ce10c1"
    ): String {
        val dUnder = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))
        val dPlain = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date(timestamp))

        fun clean(s: String?): String {
            if (s == null) return ""
            val t = s.trim()
            if (t.isEmpty() || t.equals("None", ignoreCase = true) || t.equals("Null", ignoreCase = true) || t.equals("N/A", ignoreCase = true)) return ""
            return t.replace("[^a-zA-Z0-9_+]".toRegex(), "")
        }

        val cleanProj = clean(project)
        val cleanStretch = clean(stretch)
        val cleanChain = clean(chainage).ifBlank { "189+320" }
        val cleanSide = clean(side)
        val cleanLane = clean(lane)
        val cleanAct = clean(activity).ifBlank { clean(workType) }

        val roadParts = mutableListOf<String>()
        if (cleanChain.isNotBlank()) roadParts.add(cleanChain)
        if (cleanSide.isNotBlank()) roadParts.add(cleanSide)
        if (cleanLane.isNotBlank()) roadParts.add(cleanLane)
        if (cleanAct.isNotBlank()) roadParts.add(cleanAct)
        val roadDetails = roadParts.joinToString("-")

        val projParts = mutableListOf<String>()
        if (cleanProj.isNotBlank()) projParts.add(cleanProj)
        if (cleanStretch.isNotBlank()) projParts.add(cleanStretch)

        val highwaySeg = when {
            projParts.isNotEmpty() && roadDetails.isNotEmpty() -> "${projParts.joinToString("--")}--$roadDetails"
            projParts.isNotEmpty() -> projParts.joinToString("--")
            else -> roadDetails
        }

        val cleanAddr = clean(address).ifBlank { "address" }
        val cleanTag = clean(tag).ifBlank { "tag" }

        return when (id) {
            "TimePhoto_YYYYMMDD_HHMMSS" -> "TimePhoto_$dUnder"
            "YYYYMMDD_HHMMSS" -> dUnder
            "YYYYMMDDHHMMSS" -> dPlain
            "YYYYMMDD_HHMMSS_highway" -> "${dUnder}_$highwaySeg"
            "YYYYMMDD_HHMMSS_address" -> "${dUnder}_$cleanAddr"
            "IndexNumber_YYYYMMDD_HHMMSS_address" -> "IndexNumber_${dUnder}_$cleanAddr"
            "YYYYMMDD_HHMMSS_uuid" -> "${dUnder}_${sampleUuid}(unique id)"
            "highway_address_YYYYMMDD_HHMMSS" -> "${highwaySeg}_${cleanAddr}_$dUnder"
            "address_highway_YYYYMMDD_HHMMSS" -> "${cleanAddr}_${highwaySeg}_$dUnder"
            "highway_YYYYMMDD_HHMMSS" -> "${highwaySeg}_$dUnder"
            "tag_highway_YYYYMMDD_HHMMSS" -> "${cleanTag}_${highwaySeg}_$dUnder"
            "YYYYMMDD_HHMMSS_chainage" -> "${dUnder}_$cleanChain"
            "chainage_address_YYYYMMDD_HHMMSS" -> "${cleanChain}_${cleanAddr}_$dUnder"
            "tag_chainage_YYYYMMDD_HHMMSS" -> "${cleanTag}_${cleanChain}_$dUnder"
            else -> "${highwaySeg}_$dUnder"
        }
    }
}

object FilenameFormats {
    /**
     * All 11 filename format presets matching the reference screenshot in exact order.
     */
    val PRESETS = listOf(
        FilenameFormatOption("TimePhoto_YYYYMMDD_HHMMSS", "TimePhoto_YYYYMMDD_HHMMSS"),
        FilenameFormatOption("YYYYMMDD_HHMMSS", "YYYYMMDD_HHMMSS"),
        FilenameFormatOption("YYYYMMDDHHMMSS", "YYYYMMDDHHMMSS"),
        FilenameFormatOption("YYYYMMDD_HHMMSS_highway", "YYYYMMDD_HHMMSS_{Highway}"),
        FilenameFormatOption("YYYYMMDD_HHMMSS_address", "YYYYMMDD_HHMMSS_address"),
        FilenameFormatOption("IndexNumber_YYYYMMDD_HHMMSS_address", "IndexNumber_YYYYMMDD_HHMMSS_address"),
        FilenameFormatOption("YYYYMMDD_HHMMSS_uuid", "YYYYMMDD_HHMMSS_uniqueId"),
        FilenameFormatOption("highway_address_YYYYMMDD_HHMMSS", "{Highway}_address_YYYYMMDD_HHMMSS"),
        FilenameFormatOption("address_highway_YYYYMMDD_HHMMSS", "address_{Highway}_YYYYMMDD_HHMMSS"),
        FilenameFormatOption("highway_YYYYMMDD_HHMMSS", "{Highway}_YYYYMMDD_HHMMSS"),
        FilenameFormatOption("tag_highway_YYYYMMDD_HHMMSS", "tag_{Highway}_YYYYMMDD_HHMMSS")
    )

    fun findPreset(id: String?): FilenameFormatOption {
        return PRESETS.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PRESETS[9] // Default: highway_YYYYMMDD_HHMMSS
    }
}

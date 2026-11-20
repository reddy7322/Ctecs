package com.example.data

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.BaseColumns
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.example.model.CaptureSnapshot
import com.example.overlay.OverlayRenderer
import com.example.overlay.OverlayTextGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class UriDeleteOutcome {
    data object Deleted : UriDeleteOutcome()
    data object NotFound : UriDeleteOutcome()
    data class RecoverableApprovalRequired(val intentSender: IntentSender?) : UriDeleteOutcome()
    data class Failed(val reason: String) : UriDeleteOutcome()
}

interface ScopedDeleteDelegate {
    fun deleteContentUri(uri: Uri): UriDeleteOutcome
    fun createBatchDeleteIntentSender(uris: List<Uri>): IntentSender?
    fun uriExists(uri: Uri): Boolean
}

class DefaultScopedDeleteDelegate(private val context: Context) : ScopedDeleteDelegate {
    override fun deleteContentUri(uri: Uri): UriDeleteOutcome {
        val resolver = context.contentResolver
        return try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val deleted = DocumentsContract.deleteDocument(resolver, uri)
                if (deleted || !uriExists(uri)) {
                    UriDeleteOutcome.Deleted
                } else {
                    UriDeleteOutcome.Failed("Storage provider refused to delete document")
                }
            } else {
                val deletedRows = resolver.delete(uri, null, null)
                if (deletedRows > 0) {
                    UriDeleteOutcome.Deleted
                } else if (!uriExists(uri)) {
                    UriDeleteOutcome.NotFound
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    UriDeleteOutcome.RecoverableApprovalRequired(null)
                } else {
                    UriDeleteOutcome.Failed("MediaStore entry could not be deleted")
                }
            }
        } catch (rse: RecoverableSecurityException) {
            UriDeleteOutcome.RecoverableApprovalRequired(rse.userAction.actionIntent.intentSender)
        } catch (se: SecurityException) {
            if (!uriExists(uri)) {
                UriDeleteOutcome.NotFound
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                UriDeleteOutcome.RecoverableApprovalRequired(null)
            } else {
                UriDeleteOutcome.Failed(se.message ?: "Permission denied while deleting media")
            }
        } catch (e: Exception) {
            if (!uriExists(uri)) {
                UriDeleteOutcome.NotFound
            } else {
                UriDeleteOutcome.Failed(e.message ?: "Failed to delete media URI")
            }
        }
    }

    override fun createBatchDeleteIntentSender(uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
            } catch (e: Exception) {
                Log.w("PhotoRepository", "MediaStore.createDeleteRequest failed: ${e.message}", e)
                null
            }
        }
        return null
    }

    override fun uriExists(uri: Uri): Boolean {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(BaseColumns._ID),
                null,
                null,
                null
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (_: FileNotFoundException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: SecurityException) {
            true
        } catch (_: Exception) {
            false
        }
    }
}

sealed class MediaDeleteResult {
    data class Success(
        val deletedPhotos: List<PhotoRecordEntity>,
        val deletedPaths: List<String>
    ) : MediaDeleteResult()

    data class RequiresSystemApproval(
        val intentSender: IntentSender,
        val targetPhotos: List<PhotoRecordEntity>,
        val pendingUris: List<Uri>,
        val alreadyDeletedPhotos: List<PhotoRecordEntity> = emptyList(),
        val alreadyDeletedPaths: List<String> = emptyList()
    ) : MediaDeleteResult()

    data class PartialFailure(
        val deletedPhotos: List<PhotoRecordEntity>,
        val failedItems: List<Pair<PhotoRecordEntity, String>>,
        val deletedPaths: List<String>
    ) : MediaDeleteResult()

    data class Failure(
        val reason: String,
        val failedItems: List<Pair<PhotoRecordEntity, String>> = emptyList()
    ) : MediaDeleteResult()
}

class PhotoRepository(
    private val context: Context,
    private val photoRecordDao: PhotoRecordDao,
    private val scopedDeleteDelegate: ScopedDeleteDelegate = DefaultScopedDeleteDelegate(context)
) {
    private val mediaMutationMutex = Mutex()
    private val pendingApprovalPaths = mutableSetOf<String>()
    private val pendingApprovalNames = mutableSetOf<String>()
    val allPhotos: Flow<List<PhotoRecordEntity>> = photoRecordDao.getAllPhotos().map { list ->
        list.distinctBy { it.fileName.ifBlank { it.filePath } }
            .map { applyRecoveredMetadata(it) }
    }

    fun searchPhotos(query: String): Flow<List<PhotoRecordEntity>> =
        photoRecordDao.searchPhotos(query).map { list ->
            list.distinctBy { it.fileName.ifBlank { it.filePath } }
                .map { applyRecoveredMetadata(it) }
        }

    companion object {
        fun buildCustomFileName(
            preset: String,
            prefix: String,
            timestamp: Long,
            chainage: String,
            address: String,
            roadName: String,
            tag: String,
            project: String = "",
            stretch: String = "",
            side: String = "",
            lane: String = "",
            workType: String = "",
            activity: String = "",
            subActivity: String = "",
            isVideo: Boolean = false
        ): String {
            val dateFmtUnder = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val dateFmtPlain = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            val dUnder = dateFmtUnder.format(Date(timestamp))
            val dPlain = dateFmtPlain.format(Date(timestamp))

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
            val cleanAct = clean(activity)
            val cleanSubAct = clean(subActivity)

            val roadParts = mutableListOf<String>()
            if (cleanChain.isNotBlank()) roadParts.add(cleanChain)
            if (cleanSide.isNotBlank()) roadParts.add(cleanSide)
            if (cleanLane.isNotBlank()) roadParts.add(cleanLane)
            if (cleanAct.isNotBlank()) roadParts.add(cleanAct)
            if (cleanSubAct.isNotBlank()) roadParts.add(cleanSubAct)
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
            val uniqueId = java.util.UUID.randomUUID().toString().replace("-", "")

            val rawName = when (preset) {
                "TimePhoto_YYYYMMDD_HHMMSS" -> if (isVideo) "TimeVideo_$dUnder" else "TimePhoto_$dUnder"
                "YYYYMMDD_HHMMSS" -> dUnder
                "YYYYMMDDHHMMSS" -> dPlain
                "YYYYMMDD_HHMMSS_highway" -> "${dUnder}_$highwaySeg"
                "YYYYMMDD_HHMMSS_address" -> "${dUnder}_$cleanAddr"
                "IndexNumber_YYYYMMDD_HHMMSS_address" -> "IndexNumber_${dUnder}_$cleanAddr"
                "YYYYMMDD_HHMMSS_uuid" -> "${dUnder}_$uniqueId"
                "highway_address_YYYYMMDD_HHMMSS" -> "${highwaySeg}_${cleanAddr}_$dUnder"
                "address_highway_YYYYMMDD_HHMMSS" -> "${cleanAddr}_${highwaySeg}_$dUnder"
                "highway_YYYYMMDD_HHMMSS" -> "${highwaySeg}_$dUnder"
                "tag_highway_YYYYMMDD_HHMMSS" -> "${cleanTag}_${highwaySeg}_$dUnder"
                "YYYYMMDD_HHMMSS_chainage" -> "${dUnder}_$cleanChain"
                "chainage_address_YYYYMMDD_HHMMSS" -> "${cleanChain}_${cleanAddr}_$dUnder"
                "tag_chainage_YYYYMMDD_HHMMSS" -> "${cleanTag}_${cleanChain}_$dUnder"
                else -> "${highwaySeg}_$dUnder"
            }

            val pfx = prefix.trim().replace("[^a-zA-Z0-9_-]".toRegex(), "")
            val finalBase = if (pfx.isNotBlank()) "${pfx}_$rawName" else rawName
            val ext = if (isVideo) ".mp4" else ".jpg"
            return "$finalBase$ext"
        }

        /**
         * Recovers the true capture-time (activity, subActivity) from a PhotoRecordEntity
         * when an older build saved the fallback default "DBM" or "Photo" instead of the
         * snapshot's actual activity.
         *
         * Returns a Pair(recoveredActivity, recoveredSubActivity) if recovery found a different
         * value, or null if the record already has an accurate activity.
         */
        fun recoverTrueActivityForRecord(record: PhotoRecordEntity): Pair<String, String>? {
            val currentAct = record.activity.trim()
            val currentSub = record.subActivity.trim()
            // Only attempt recovery on legacy records where activity was defaulted to "DBM" or "Photo"
            if (currentAct != "DBM" && currentAct != "Photo") return null

            // 1. Inspect formattedOverlayText built by ProjectProfile.buildHighwayText(displayChainage)
            val overlayFirstLine = record.formattedOverlayText.lineSequence().firstOrNull()?.trim() ?: ""
            val fileBase = record.fileName.substringBeforeLast(".")
            if (overlayFirstLine.isNotBlank() && !overlayFirstLine.equals(fileBase, ignoreCase = true)) {
                val pipeSegments = overlayFirstLine.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                val chLabel = record.chainageLabel?.trim().orEmpty()
                val sideLabel = record.roadSide.trim()
                val laneLabel = record.lane.trim()

                // Locate the road details segment (starts with chainage or contains chainage/side)
                val locSeg = pipeSegments.firstOrNull { seg ->
                    (chLabel.isNotBlank() && !chLabel.equals("None", ignoreCase = true) && seg.startsWith(chLabel, ignoreCase = true)) ||
                        Regex("""^\d+\+\d+""").containsMatchIn(seg)
                } ?: pipeSegments.firstOrNull { seg ->
                    sideLabel.isNotBlank() && !sideLabel.equals("None", ignoreCase = true) &&
                        (seg.equals(sideLabel, ignoreCase = true) || seg.startsWith("$sideLabel ", ignoreCase = true))
                }

                if (locSeg != null) {
                    var rem = locSeg.trim()
                    if (chLabel.isNotBlank() && !chLabel.equals("None", ignoreCase = true) && rem.startsWith(chLabel, ignoreCase = true)) {
                        rem = rem.substring(chLabel.length).trim()
                    } else {
                        rem = rem.replaceFirst(Regex("""^\d+\+\d+(?:\.\d+)?\s*"""), "").trim()
                    }

                    if (sideLabel.isNotBlank() && !sideLabel.equals("None", ignoreCase = true) &&
                        (rem.equals(sideLabel, ignoreCase = true) || rem.startsWith("$sideLabel ", ignoreCase = true))
                    ) {
                        rem = rem.substring(sideLabel.length).trim()
                    } else {
                        rem = rem.replaceFirst(Regex("""^(?:LHS|RHS|BHS|Median|MEDIAN|BOTH)\b\s*""", RegexOption.IGNORE_CASE), "").trim()
                    }

                    if (laneLabel.isNotBlank() && !laneLabel.equals("None", ignoreCase = true) &&
                        (rem.equals(laneLabel, ignoreCase = true) || rem.startsWith("$laneLabel ", ignoreCase = true))
                    ) {
                        rem = rem.substring(laneLabel.length).trim()
                    } else {
                        rem = rem.replaceFirst(
                            Regex("""^(?:Main Carriage|Service Rd|Shoulder|I/L|M/L|O/L|MCW)\b\s*""", RegexOption.IGNORE_CASE),
                            ""
                        ).trim()
                    }

                    // Extract optional (subActivity) at the end
                    var extractedSub = currentSub
                    val subMatch = Regex("""^(.*?)\s*\(([^()]+)\)$""").find(rem)
                    val extractedAct = if (subMatch != null) {
                        extractedSub = subMatch.groupValues[2].trim()
                        subMatch.groupValues[1].trim()
                    } else {
                        rem.trim()
                    }

                    // If nothing remained in locSeg after chainage/side/lane, the user had selected "None" (blank)
                    val normalizedAct = if (extractedAct.isEmpty() || extractedAct.equals("None", ignoreCase = true)) {
                        "None"
                    } else {
                        extractedAct
                    }

                    if (normalizedAct != currentAct || extractedSub != currentSub) {
                        return Pair(normalizedAct, extractedSub)
                    }
                    return null
                }
            }

            // 2. Fallback: inspect structured filename (e.g., Proj--Stretch--189+320-RHS-IL-BC_20260328_120000.jpg)
            val roadPartMatch = Regex("""(\d+\+\d+(?:-[A-Za-z0-9+]+)++)""").find(fileBase)
            if (roadPartMatch != null) {
                val rawGroup = roadPartMatch.groupValues[1].substringBefore("_")
                val tokens = rawGroup.split("-").filter { it.isNotBlank() }
                if (tokens.isNotEmpty()) {
                    var idx = 1 // skip chainage token at index 0
                    val cleanSide = record.roadSide.replace("[^a-zA-Z0-9_+]".toRegex(), "")
                    if (idx < tokens.size && (
                            (cleanSide.isNotBlank() && tokens[idx].equals(cleanSide, ignoreCase = true)) ||
                                tokens[idx].uppercase(Locale.US) in setOf("LHS", "RHS", "BHS", "MEDIAN", "BOTH")
                            )
                    ) {
                        idx++
                    }
                    val cleanLane = record.lane.replace("[^a-zA-Z0-9_+]".toRegex(), "")
                    if (idx < tokens.size && (
                            (cleanLane.isNotBlank() && tokens[idx].equals(cleanLane, ignoreCase = true)) ||
                                tokens[idx].uppercase(Locale.US) in setOf("IL", "ML", "OL", "SHOULDER", "SERVICERD", "MCW", "MAINCARRIAGE")
                            )
                    ) {
                        idx++
                    }
                    val remainingTokens = if (idx < tokens.size) tokens.subList(idx, tokens.size) else emptyList()
                    val recoveredFromName = if (remainingTokens.isEmpty()) "None" else remainingTokens.first()
                    val recoveredSubFromName = if (remainingTokens.size > 1) remainingTokens.drop(1).joinToString(" ") else currentSub
                    if (recoveredFromName != currentAct || recoveredSubFromName != currentSub) {
                        return Pair(recoveredFromName, recoveredSubFromName)
                    }
                }
            }

            // 3. If currentAct was "Photo" from legacy storage scan and no activity was encoded, clear it so we don't invent an activity
            if (currentAct == "Photo") {
                return Pair("", currentSub)
            }

            return null
        }

        fun applyRecoveredMetadata(record: PhotoRecordEntity): PhotoRecordEntity {
            val recovered = recoverTrueActivityForRecord(record) ?: return record
            return record.copy(
                activity = recovered.first,
                subActivity = recovered.second
            )
        }
    }

    suspend fun saveCapturedPhoto(
        sourceBitmap: Bitmap,
        snapshot: CaptureSnapshot
    ): Result<PhotoRecordEntity> = withContext(Dispatchers.IO) {
        try {
            // 1. Generate filename using active format preset
            val addrText = if (snapshot.overlayConfig.locationAddressOption != "None") {
                snapshot.overlayConfig.customAddressText.ifBlank { snapshot.overlayConfig.locationAddressOption }
            } else {
                snapshot.projectProfile.highwayId
            }
            val (authCaptureSide, useAuthCaptureSide) = OverlayTextGenerator.resolveAuthoritativeCaptureSide(snapshot)
            val resolvedCaptureSideLabel = if (useAuthCaptureSide) {
                authCaptureSide.orEmpty()
            } else {
                snapshot.projectProfile.roadSide.label
            }
            val fileName = buildCustomFileName(
                preset = snapshot.filenameFormatPreset,
                prefix = snapshot.customFilenamePrefix,
                timestamp = snapshot.timestamp,
                chainage = snapshot.displayChainage,
                address = addrText,
                roadName = snapshot.projectProfile.highwayId,
                tag = snapshot.overlayConfig.customTag,
                project = snapshot.projectProfile.highwayId,
                stretch = snapshot.projectProfile.packageSection,
                side = resolvedCaptureSideLabel,
                lane = snapshot.projectProfile.lane,
                workType = snapshot.projectProfile.workType,
                activity = snapshot.projectProfile.activity,
                subActivity = snapshot.projectProfile.subActivity,
                isVideo = false
            )

            // Reliable App-Specific Storage (guaranteed writable across all Android versions)
            val localDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir,
                "HighwayCamera"
            ).apply { mkdirs() }
            val localFile = File(localDir, fileName)

            // 2. Render permanent overlay if burn-in is enabled, or keep clean original
            val bitmapToSave = if (snapshot.overlayConfig.burnInTimestamp) {
                OverlayRenderer.renderOverlay(
                    context = context,
                    sourceBitmap = sourceBitmap,
                    snapshot = snapshot
                )
            } else {
                sourceBitmap
            }

            // 3. Save to local file with full EXIF metadata
            val savedOk = try {
                OverlayRenderer.saveBitmapWithExif(
                    bitmap = bitmapToSave,
                    destinationFile = localFile,
                    snapshot = snapshot,
                    context = context
                )
            } finally {
                if (bitmapToSave !== sourceBitmap && !bitmapToSave.isRecycled) {
                    bitmapToSave.recycle()
                }
            }

            if (!savedOk) {
                return@withContext Result.failure(Exception("Failed to encode or save JPEG to disk"))
            }

            var recordedPath = localFile.absolutePath

            // 4. Publish to Shared Device Storage via Android MediaStore (Pictures/HighwayCamera)
            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                    put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                    put(MediaStore.Images.Media.DATE_TAKEN, snapshot.timestamp)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HighwayCamera")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val itemUri = resolver.insert(imageCollection, contentValues)
                if (itemUri != null) {
                    resolver.openOutputStream(itemUri)?.use { outStream ->
                        localFile.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                        }
                        outStream.flush()
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(itemUri, contentValues, null, null)
                    }
                }
            } catch (mediaStoreEx: Exception) {
                Log.w("PhotoRepository", "Shared storage publish error (saved locally): ${mediaStoreEx.message}")
            }

            // 5. Also save un-watermarked original if user selected "Save original and stamped"
            if (snapshot.overlayConfig.burnInTimestamp && snapshot.overlayConfig.saveOriginalImage) {
                try {
                    val origFileName = "ORIG_$fileName"
                    val origLocal = File(localDir, origFileName)
                    val origSaved = OverlayRenderer.saveBitmapWithExif(
                        bitmap = sourceBitmap,
                        destinationFile = origLocal,
                        snapshot = snapshot,
                        context = context
                    )
                    if (origSaved) {
                        val resolver = context.contentResolver
                        val origCv = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, origFileName)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.DATE_TAKEN, snapshot.timestamp)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HighwayCamera")
                            }
                        }
                        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        } else {
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        }
                        val origUri = resolver.insert(collection, origCv)
                        if (origUri != null) {
                            resolver.openOutputStream(origUri)?.use { outStream ->
                                origLocal.inputStream().use { inStream ->
                                    inStream.copyTo(outStream)
                                }
                                outStream.flush()
                            }
                        }
                    }
                } catch (ignored: Exception) {}
            }

            // 6. Save record in Room Database with exact capture-time activity & subActivity
            val entity = PhotoRecordEntity(
                filePath = recordedPath,
                fileName = fileName,
                timestamp = snapshot.timestamp,
                latitude = snapshot.location?.latitude ?: 0.0,
                longitude = snapshot.location?.longitude ?: 0.0,
                accuracy = snapshot.gpsAccuracy,
                altitude = snapshot.location?.altitude ?: 0.0,
                chainageMetres = snapshot.matchResult.chainage?.metres,
                chainageLabel = snapshot.displayChainage,
                chainageConfidence = snapshot.matchResult.confidence.name,
                isManualChainage = snapshot.matchResult.isManual,
                offsetMetres = snapshot.matchResult.offsetMetres.toFloat(),
                highwayId = snapshot.projectProfile.highwayId,
                packageSection = snapshot.projectProfile.packageSection,
                roadSide = resolvedCaptureSideLabel,
                lane = snapshot.projectProfile.lane,
                activity = snapshot.projectProfile.activity,
                subActivity = snapshot.projectProfile.subActivity,
                customDescription = run {
                    val locName = snapshot.locationName.ifBlank { snapshot.overlayConfig.lastDetectedLocationName }.trim()
                    if (locName.isNotBlank() && !locName.equals("None", ignoreCase = true)) {
                        if (snapshot.projectProfile.customDescription.isNotBlank()) {
                            "${snapshot.projectProfile.customDescription} | $locName"
                        } else {
                            locName
                        }
                    } else {
                        snapshot.projectProfile.customDescription
                    }
                },
                inspectorName = snapshot.projectProfile.inspectorName,
                formattedOverlayText = run {
                    val baseText = snapshot.projectProfile.buildHighwayText(
                        chainageFormatted = snapshot.displayChainage,
                        sideOverride = authCaptureSide,
                        useSideOverride = useAuthCaptureSide
                    )
                    val locName = snapshot.locationName.ifBlank { snapshot.overlayConfig.lastDetectedLocationName }.trim()
                    if (snapshot.overlayConfig.showLocationName && locName.isNotBlank() && !locName.equals("None", ignoreCase = true)) {
                        if (baseText.isNotBlank()) "$baseText | $locName" else locName
                    } else baseText
                },
                locationName = snapshot.locationName
                    .ifBlank { snapshot.weatherObservation?.locationName.orEmpty() }
                    .ifBlank { snapshot.overlayConfig.lastDetectedLocationName }
                    .trim(),
                weatherTempC = snapshot.weatherObservation?.temperatureC,
                weatherCondition = snapshot.weatherObservation?.conditionLabel.orEmpty(),
                weatherHumidity = snapshot.weatherObservation?.humidityPercent,
                weatherWindSpeedKmh = snapshot.weatherObservation?.windSpeedKmh,
                weatherWindDirDeg = snapshot.weatherObservation?.windDirectionDegrees,
                weatherObservedAtMs = snapshot.weatherObservation?.observedAtMs,
                weatherSource = snapshot.weatherObservation?.source.orEmpty()
            )

            val newId = photoRecordDao.insertPhoto(entity)
            Result.success(entity.copy(id = newId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertRecord(record: PhotoRecordEntity): Long = withContext(Dispatchers.IO) {
        photoRecordDao.insertPhoto(record)
    }

    suspend fun deletePhoto(photo: PhotoRecordEntity): MediaDeleteResult =
        deleteMediaItems(listOf(photo))

    suspend fun deleteMediaItems(photos: List<PhotoRecordEntity>): MediaDeleteResult = withContext(Dispatchers.IO) {
        val uniqueTargets = photos.distinctBy { if (it.id != 0L) "id:${it.id}" else "path:${it.filePath}" }
        if (uniqueTargets.isEmpty()) {
            return@withContext MediaDeleteResult.Failure("No media items selected for deletion")
        }

        mediaMutationMutex.withLock {
            val succeededPhotos = mutableListOf<PhotoRecordEntity>()
            val deletedPaths = mutableListOf<String>()
            val failedItems = mutableListOf<Pair<PhotoRecordEntity, String>>()

            data class PendingApprovalItem(
                val photo: PhotoRecordEntity,
                val urisNeedingApproval: List<Uri>,
                val directIntentSender: IntentSender?
            )
            val itemsNeedingApproval = mutableListOf<PendingApprovalItem>()

            for (photo in uniqueTargets) {
                val associatedFiles = findAssociatedFiles(photo)
                val associatedUris = findAssociatedUris(photo, associatedFiles)

                val urisNeedingApproval = mutableListOf<Uri>()
                var directIntentSender: IntentSender? = null
                var uriFailureReason: String? = null

                // Attempt deletion on MediaStore / SAF URIs first so local copies & DB stay intact if OS consent is denied
                for (uri in associatedUris) {
                    when (val outcome = scopedDeleteDelegate.deleteContentUri(uri)) {
                        is UriDeleteOutcome.Deleted,
                        is UriDeleteOutcome.NotFound -> Unit
                        is UriDeleteOutcome.RecoverableApprovalRequired -> {
                            urisNeedingApproval.add(uri)
                            if (directIntentSender == null && outcome.intentSender != null) {
                                directIntentSender = outcome.intentSender
                            }
                        }
                        is UriDeleteOutcome.Failed -> {
                            if (uriFailureReason == null) uriFailureReason = outcome.reason
                        }
                    }
                }

                if (urisNeedingApproval.isNotEmpty()) {
                    itemsNeedingApproval.add(
                        PendingApprovalItem(
                            photo = photo,
                            urisNeedingApproval = urisNeedingApproval,
                            directIntentSender = directIntentSender
                        )
                    )
                    continue
                }

                if (uriFailureReason != null) {
                    failedItems.add(photo to uriFailureReason)
                    continue
                }

                // Delete all local and shared file copies on disk and verify complete removal
                val itemOutcome = deleteFilesVerifyAndPurgeDb(
                    photo = photo,
                    associatedFiles = associatedFiles,
                    associatedUris = associatedUris
                )
                if (itemOutcome == null) {
                    succeededPhotos.add(photo)
                    deletedPaths.addAll(associatedFiles.map { it.absolutePath })
                } else {
                    failedItems.add(photo to itemOutcome)
                }
            }

            if (succeededPhotos.isNotEmpty()) {
                val distinctPaths = deletedPaths.distinct()
                clearMediaCaches(succeededPhotos, distinctPaths)
                notifyMediaScannerOfDeletedPaths(distinctPaths)
            }

            if (itemsNeedingApproval.isNotEmpty()) {
                val allPendingUris = itemsNeedingApproval.flatMap { it.urisNeedingApproval }.distinct()
                val batchIntentSender = scopedDeleteDelegate.createBatchDeleteIntentSender(allPendingUris)
                    ?: itemsNeedingApproval.firstNotNullOfOrNull { it.directIntentSender }

                if (batchIntentSender != null) {
                    val approvalPhotos = itemsNeedingApproval.map { it.photo }
                    markPendingApproval(approvalPhotos)
                    return@withLock MediaDeleteResult.RequiresSystemApproval(
                        intentSender = batchIntentSender,
                        targetPhotos = approvalPhotos,
                        pendingUris = allPendingUris,
                        alreadyDeletedPhotos = succeededPhotos,
                        alreadyDeletedPaths = deletedPaths.distinct()
                    )
                } else {
                    for (item in itemsNeedingApproval) {
                        failedItems.add(item.photo to "System permission is required to delete this media file")
                    }
                }
            }

            buildFinalDeleteResult(succeededPhotos, failedItems, deletedPaths.distinct())
        }
    }

    suspend fun completeApprovedDeletion(
        targetPhotos: List<PhotoRecordEntity>,
        pendingUris: List<Uri>,
        alreadyDeletedPhotos: List<PhotoRecordEntity> = emptyList(),
        alreadyDeletedPaths: List<String> = emptyList()
    ): MediaDeleteResult = withContext(Dispatchers.IO) {
        mediaMutationMutex.withLock {
            clearPendingApproval(targetPhotos)

            val succeededPhotos = alreadyDeletedPhotos.toMutableList()
            val deletedPaths = alreadyDeletedPaths.toMutableList()
            val failedItems = mutableListOf<Pair<PhotoRecordEntity, String>>()

            // On API 29, RecoverableSecurityException approval grants permission; perform the URI delete now if still present
            for (uri in pendingUris) {
                if (scopedDeleteDelegate.uriExists(uri)) {
                    scopedDeleteDelegate.deleteContentUri(uri)
                }
            }

            for (photo in targetPhotos) {
                val associatedFiles = findAssociatedFiles(photo)
                val associatedUris = (findAssociatedUris(photo, associatedFiles) + pendingUrisForPhoto(photo, pendingUris)).distinct()

                for (uri in associatedUris) {
                    if (scopedDeleteDelegate.uriExists(uri)) {
                        scopedDeleteDelegate.deleteContentUri(uri)
                    }
                }

                val failureReason = deleteFilesVerifyAndPurgeDb(
                    photo = photo,
                    associatedFiles = associatedFiles,
                    associatedUris = associatedUris
                )
                if (failureReason == null) {
                    succeededPhotos.add(photo)
                    deletedPaths.addAll(associatedFiles.map { it.absolutePath })
                } else {
                    failedItems.add(photo to failureReason)
                }
            }

            val newlyDeleted = succeededPhotos.drop(alreadyDeletedPhotos.size)
            if (newlyDeleted.isNotEmpty()) {
                val newPaths = deletedPaths.distinct()
                clearMediaCaches(newlyDeleted, newPaths)
                notifyMediaScannerOfDeletedPaths(newPaths)
            }

            buildFinalDeleteResult(succeededPhotos, failedItems, deletedPaths.distinct())
        }
    }

    suspend fun cancelPendingDeletion(targetPhotos: List<PhotoRecordEntity>) = withContext(Dispatchers.IO) {
        mediaMutationMutex.withLock {
            clearPendingApproval(targetPhotos)
        }
    }

    private suspend fun deleteFilesVerifyAndPurgeDb(
        photo: PhotoRecordEntity,
        associatedFiles: List<File>,
        associatedUris: List<Uri>
    ): String? {
        for (file in associatedFiles) {
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.w("PhotoRepository", "Failed deleting file ${file.absolutePath}: ${e.message}", e)
            }
        }

        val survivingFile = associatedFiles.firstOrNull { it.exists() }
        if (survivingFile != null) {
            return "File still exists on device storage (${survivingFile.name})"
        }

        val survivingUri = associatedUris.firstOrNull { scopedDeleteDelegate.uriExists(it) }
        if (survivingUri != null) {
            return "MediaStore entry could not be removed ($survivingUri)"
        }

        if (photo.filePath.startsWith("content://")) {
            val primaryUri = runCatching { Uri.parse(photo.filePath) }.getOrNull()
            if (primaryUri != null && scopedDeleteDelegate.uriExists(primaryUri)) {
                return "Original document URI is still accessible"
            }
        }

        // Only remove Room records once storage & MediaStore deletion is verified
        photoRecordDao.deletePhotoAndDuplicates(
            id = photo.id,
            filePath = photo.filePath,
            fileName = photo.fileName
        )
        return null
    }

    private fun buildFinalDeleteResult(
        succeededPhotos: List<PhotoRecordEntity>,
        failedItems: List<Pair<PhotoRecordEntity, String>>,
        deletedPaths: List<String>
    ): MediaDeleteResult {
        return when {
            failedItems.isEmpty() -> MediaDeleteResult.Success(succeededPhotos, deletedPaths)
            succeededPhotos.isNotEmpty() -> MediaDeleteResult.PartialFailure(succeededPhotos, failedItems, deletedPaths)
            else -> MediaDeleteResult.Failure(
                reason = failedItems.firstOrNull()?.second ?: "Failed to delete media file",
                failedItems = failedItems
            )
        }
    }

    private fun pendingUrisForPhoto(photo: PhotoRecordEntity, pendingUris: List<Uri>): List<Uri> {
        val rawUri = if (photo.filePath.startsWith("content://")) runCatching { Uri.parse(photo.filePath) }.getOrNull() else null
        return if (rawUri != null && rawUri in pendingUris) listOf(rawUri) else emptyList()
    }

    private fun markPendingApproval(photos: List<PhotoRecordEntity>) {
        photos.forEach { photo ->
            if (photo.filePath.isNotBlank()) pendingApprovalPaths.add(photo.filePath)
            if (photo.fileName.isNotBlank()) pendingApprovalNames.add(photo.fileName)
        }
    }

    private fun clearPendingApproval(photos: List<PhotoRecordEntity>) {
        photos.forEach { photo ->
            if (photo.filePath.isNotBlank()) pendingApprovalPaths.remove(photo.filePath)
            if (photo.fileName.isNotBlank()) pendingApprovalNames.remove(photo.fileName)
        }
    }

    private fun findAssociatedFiles(photo: PhotoRecordEntity): List<File> {
        val seenCanonical = LinkedHashSet<String>()
        val result = mutableListOf<File>()

        fun addCandidate(file: File?) {
            if (file == null) return
            val key = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
            if (seenCanonical.add(key)) {
                result.add(file)
            }
        }

        if (photo.filePath.isNotBlank() && !photo.filePath.startsWith("content://")) {
            val primary = File(photo.filePath)
            addCandidate(primary)
            if (!primary.isAbsolute) {
                addCandidate(File(context.filesDir, photo.filePath))
            }
        } else if (photo.filePath.startsWith("content://")) {
            resolveDataPathFromUri(Uri.parse(photo.filePath))?.let { resolvedPath ->
                addCandidate(File(resolvedPath))
            }
        }

        photo.thumbnailPath?.takeIf { it.isNotBlank() && !it.startsWith("content://") }?.let { thumb ->
            addCandidate(File(thumb))
        }

        val targetName = photo.fileName.ifBlank {
            if (photo.filePath.isNotBlank() && !photo.filePath.startsWith("content://")) {
                File(photo.filePath).name
            } else {
                ""
            }
        }

        if (targetName.isNotBlank()) {
            val highwayDirs = listOfNotNull(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "HighwayCamera"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "HighwayCamera"),
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { File(it, "HighwayCamera") },
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let { File(it, "HighwayCamera") },
                File(context.filesDir, "HighwayCamera")
            )
            highwayDirs.forEach { dir -> addCandidate(File(dir, targetName)) }
        }

        return result
    }

    private fun resolveDataPathFromUri(uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val col = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (col >= 0) cursor.getString(col)?.takeIf { it.isNotBlank() } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findAssociatedUris(photo: PhotoRecordEntity, associatedFiles: List<File>): List<Uri> {
        val uris = LinkedHashSet<Uri>()

        if (photo.filePath.startsWith("content://")) {
            runCatching { Uri.parse(photo.filePath) }.getOrNull()?.let { uris.add(it) }
        }
        photo.thumbnailPath?.takeIf { it.startsWith("content://") }?.let { thumbUri ->
            runCatching { Uri.parse(thumbUri) }.getOrNull()?.let { uris.add(it) }
        }

        val isVideo = photo.fileName.endsWith(".mp4", ignoreCase = true) ||
            photo.filePath.endsWith(".mp4", ignoreCase = true)
        val collections = if (isVideo) {
            listOfNotNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else null,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).distinct()
        } else {
            listOfNotNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else null,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            ).distinct()
        }

        val pathCandidates = associatedFiles.map { it.absolutePath }.distinct()
        val targetName = photo.fileName.ifBlank {
            associatedFiles.firstOrNull()?.name.orEmpty()
        }

        for (collection in collections) {
            if (pathCandidates.isNotEmpty()) {
                try {
                    val placeholders = pathCandidates.joinToString(",") { "?" }
                    context.contentResolver.query(
                        collection,
                        arrayOf(BaseColumns._ID),
                        "${MediaStore.MediaColumns.DATA} IN ($placeholders)",
                        pathCandidates.toTypedArray(),
                        null
                    )?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(BaseColumns._ID)
                        while (cursor.moveToNext() && idIdx >= 0) {
                            val id = cursor.getLong(idIdx)
                            uris.add(ContentUris.withAppendedId(collection, id))
                        }
                    }
                } catch (_: Exception) {}
            }

            if (targetName.isNotBlank()) {
                try {
                    val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
                    } else {
                        "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.DATA} LIKE ?"
                    }
                    val args = arrayOf(targetName, "%HighwayCamera%")
                    context.contentResolver.query(
                        collection,
                        arrayOf(BaseColumns._ID),
                        selection,
                        args,
                        null
                    )?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(BaseColumns._ID)
                        while (cursor.moveToNext() && idIdx >= 0) {
                            val id = cursor.getLong(idIdx)
                            uris.add(ContentUris.withAppendedId(collection, id))
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        return uris.toList()
    }

    @OptIn(ExperimentalCoilApi::class)
    private fun clearMediaCaches(photos: List<PhotoRecordEntity>, paths: List<String>) {
        try {
            val loader = context.imageLoader
            val candidateKeys = buildSet {
                paths.forEach { path ->
                    if (path.isNotBlank()) {
                        add(path)
                        add("file://$path")
                        runCatching { File(path).toURI().toString() }.getOrNull()?.let { add(it) }
                    }
                }
                photos.forEach { photo ->
                    if (photo.filePath.isNotBlank()) {
                        add(photo.filePath)
                        if (!photo.filePath.startsWith("content://")) {
                            add("file://${photo.filePath}")
                        }
                    }
                    photo.thumbnailPath?.takeIf { it.isNotBlank() }?.let { thumb ->
                        add(thumb)
                        if (!thumb.startsWith("content://")) {
                            add("file://$thumb")
                        }
                    }
                }
            }
            loader.memoryCache?.let { memCache ->
                val keysToRemove = memCache.keys.filter { memKey ->
                    candidateKeys.any { candidate -> memKey.key.contains(candidate) }
                }
                keysToRemove.forEach { memCache.remove(it) }
            }
            loader.diskCache?.let { diskCache ->
                candidateKeys.forEach { key ->
                    runCatching { diskCache.remove(key) }
                }
            }
        } catch (e: Exception) {
            Log.w("PhotoRepository", "Media cache eviction warning: ${e.message}")
        }
    }

    private fun notifyMediaScannerOfDeletedPaths(paths: List<String>) {
        if (paths.isEmpty()) return
        try {
            MediaScannerConnection.scanFile(
                context,
                paths.toTypedArray(),
                null,
                null
            )
        } catch (ignored: Exception) {}
    }

    /**
     * Scans storage directories for any recorded videos or photos that are not yet indexed in Room,
     * and permanently repairs any legacy database records where activity was defaulted to "DBM".
     */
    suspend fun syncMediaFilesFromStorage(): Int = withContext(Dispatchers.IO) {
        mediaMutationMutex.withLock {
            var addedCount = 0
            try {
                val existingList = photoRecordDao.getAllPhotosList()
                val existingPaths = existingList.map { it.filePath }.toSet()
                val existingNames = mutableSetOf<String>()

                // Clean up existing duplicates and permanently repair legacy "DBM" records in database
                val seenKeys = mutableSetOf<String>()
                for (record in existingList) {
                    val key = record.fileName.ifBlank { record.filePath }
                    if (seenKeys.contains(key)) {
                        photoRecordDao.deletePhotoById(record.id)
                    } else {
                        seenKeys.add(key)
                        existingNames.add(record.fileName)
                        val recovered = recoverTrueActivityForRecord(record)
                        if (recovered != null) {
                            photoRecordDao.updatePhoto(
                                record.copy(
                                    activity = recovered.first,
                                    subActivity = recovered.second
                                )
                            )
                        }
                    }
                }

                val dirsToScan = listOfNotNull(
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "HighwayCamera"),
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "HighwayCamera"),
                    context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let { File(it, "HighwayCamera") },
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { File(it, "HighwayCamera") },
                    File(context.filesDir, "HighwayCamera")
                )

                for (dir in dirsToScan) {
                    if (!dir.exists() || !dir.isDirectory) continue
                    val files = dir.listFiles() ?: continue
                    for (file in files) {
                        if (file.isDirectory || file.length() == 0L) continue
                        val path = file.absolutePath
                        val name = file.name
                        if (path in existingPaths || name in existingNames || name in seenKeys) continue
                        if (path in pendingApprovalPaths || name in pendingApprovalNames) continue

                        val isVideo = name.endsWith(".mp4", ignoreCase = true)
                        val isPhoto = name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true) || name.endsWith(".png", ignoreCase = true)
                        if (!isVideo && !isPhoto) continue

                        var timestamp = file.lastModified()
                        val match = Regex("""(?:TimePhoto|TimeVideo|Photo|Video)_(\d{8})_(\d{6})""").find(name)
                        if (match != null) {
                            try {
                                val dateStr = "${match.groupValues[1]}_${match.groupValues[2]}"
                                val parsedDate = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(dateStr)
                                if (parsedDate != null) timestamp = parsedDate.time
                            } catch (ignored: Exception) {}
                        }

                        val baseEntity = PhotoRecordEntity(
                            filePath = path,
                            fileName = name,
                            timestamp = timestamp,
                            latitude = 0.0,
                            longitude = 0.0,
                            accuracy = 0f,
                            altitude = 0.0,
                            chainageMetres = null,
                            chainageLabel = if (name.contains("+")) {
                                Regex("""\d+\+\d+""").find(name)?.value
                            } else null,
                            chainageConfidence = "MANUAL",
                            isManualChainage = true,
                            offsetMetres = 0f,
                            highwayId = "",
                            packageSection = "",
                            roadSide = "",
                            lane = "",
                            activity = if (isVideo) "Video Recording" else "",
                            subActivity = "",
                            customDescription = "",
                            inspectorName = "",
                            formattedOverlayText = name.substringBeforeLast(".")
                        )
                        val entity = applyRecoveredMetadata(baseEntity)
                        photoRecordDao.insertPhoto(entity)
                        existingNames.add(name)
                        seenKeys.add(name)
                        addedCount++
                    }
                }
            } catch (e: Exception) {
                Log.w("PhotoRepository", "Media files sync error: ${e.message}", e)
            }
            addedCount
        }
    }
}

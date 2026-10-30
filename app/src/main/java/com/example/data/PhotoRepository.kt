package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.model.CaptureSnapshot
import com.example.overlay.OverlayRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoRepository(
    private val context: Context,
    private val photoRecordDao: PhotoRecordDao
) {
    val allPhotos: Flow<List<PhotoRecordEntity>> = photoRecordDao.getAllPhotos()

    fun searchPhotos(query: String): Flow<List<PhotoRecordEntity>> =
        photoRecordDao.searchPhotos(query)

    companion object {
        fun buildCustomFileName(
            preset: String,
            prefix: String,
            timestamp: Long,
            chainage: String,
            address: String,
            roadName: String,
            tag: String
        ): String {
            val dateFmtUnder = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val dateFmtPlain = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            val dUnder = dateFmtUnder.format(Date(timestamp))
            val dPlain = dateFmtPlain.format(Date(timestamp))

            val cleanChain = chainage.replace("[^a-zA-Z0-9+]".toRegex(), "").ifBlank { "0+000" }
            val cleanAddr = address.replace("[^a-zA-Z0-9]".toRegex(), "").ifBlank { "Location" }
            val cleanTag = tag.replace("[^a-zA-Z0-9]".toRegex(), "").ifBlank { "Survey" }

            val rawName = when (preset) {
                "YYYYMMDD_HHMMSS" -> dUnder
                "YYYYMMDDHHMMSS" -> dPlain
                "YYYYMMDD_HHMMSS_address" -> "${dUnder}_$cleanAddr"
                "YYYYMMDD_HHMMSS_chainage" -> "${dUnder}_$cleanChain"
                "IndexNumber_YYYYMMDD_HHMMSS_address" -> "IMG_${dUnder}_$cleanAddr"
                "chainage_address_YYYYMMDD_HHMMSS" -> "${cleanChain}_${cleanAddr}_$dUnder"
                "tag_chainage_YYYYMMDD_HHMMSS" -> "${cleanTag}_${cleanChain}_$dUnder"
                else -> "TimePhoto_$dUnder"
            }

            val pfx = prefix.trim().replace("[^a-zA-Z0-9_-]".toRegex(), "")
            val finalName = if (pfx.isNotBlank()) "${pfx}_$rawName" else rawName
            return "$finalName.jpg"
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
            val fileName = buildCustomFileName(
                preset = "TimePhoto_YYYYMMDD_HHMMSS",
                prefix = "",
                timestamp = snapshot.timestamp,
                chainage = snapshot.displayChainage,
                address = addrText,
                roadName = snapshot.projectProfile.highwayId,
                tag = snapshot.overlayConfig.customTag
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
            val savedOk = OverlayRenderer.saveBitmapWithExif(
                bitmap = bitmapToSave,
                destinationFile = localFile,
                snapshot = snapshot,
                context = context
            )

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

            // 6. Save record in Room Database
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
                roadSide = snapshot.projectProfile.roadSide.label,
                lane = snapshot.projectProfile.lane,
                activity = snapshot.projectProfile.activity,
                customDescription = snapshot.projectProfile.customDescription,
                inspectorName = snapshot.projectProfile.inspectorName,
                formattedOverlayText = snapshot.projectProfile.buildHighwayText(snapshot.displayChainage)
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

    suspend fun deletePhoto(photo: PhotoRecordEntity) = withContext(Dispatchers.IO) {
        val file = File(photo.filePath)
        if (file.exists()) {
            file.delete()
        }
        photoRecordDao.deletePhoto(photo)
    }
}

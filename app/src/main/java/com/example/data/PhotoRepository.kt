package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
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

    suspend fun saveCapturedPhoto(
        sourceBitmap: Bitmap,
        snapshot: CaptureSnapshot
    ): Result<PhotoRecordEntity> = withContext(Dispatchers.IO) {
        try {
            // 1. Generate filename: e.g. NH58_196+040_RHS_20261026_173200.jpg
            val dateFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val dateStr = dateFmt.format(Date(snapshot.timestamp))
            val highwayClean = snapshot.projectProfile.highwayId.replace("[^a-zA-Z0-9]".toRegex(), "")
            val chainageClean = snapshot.displayChainage.replace("[^a-zA-Z0-9+]".toRegex(), "")
            val sideClean = snapshot.projectProfile.roadSide.label

            val baseName = if (chainageClean.isNotBlank() && chainageClean != "---") {
                "${highwayClean}_${chainageClean}_${sideClean}_$dateStr"
            } else {
                "${highwayClean}_$dateStr"
            }
            val fileName = "$baseName.jpg"

            // Target directory: Pictures/HighwayCamera (Shared Public Storage)
            val picturesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "HighwayCamera"
            ).apply { mkdirs() }

            val destinationFile = File(picturesDir, fileName)

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

            // 3. Save to file with EXIF metadata
            val savedOk = OverlayRenderer.saveBitmapWithExif(
                bitmap = bitmapToSave,
                destinationFile = destinationFile,
                snapshot = snapshot
            )

            if (!savedOk) {
                return@withContext Result.failure(Exception("Failed to encode or save JPEG to disk"))
            }

            // Scan file to make it instantly visible in Gallery, Google Photos, and MediaStore
            try {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destinationFile.absolutePath),
                    arrayOf("image/jpeg")
                ) { _, _ -> }
            } catch (ignored: Exception) {}

            // Optional: Save original unmodified if enabled alongside stamped image
            if (snapshot.overlayConfig.saveOriginalImage && snapshot.overlayConfig.burnInTimestamp) {
                val origFile = File(picturesDir, "${baseName}_ORIGINAL.jpg")
                OverlayRenderer.saveBitmapWithExif(sourceBitmap, origFile, snapshot)
            }

            // 4. Save record in Room Database
            val entity = PhotoRecordEntity(
                filePath = destinationFile.absolutePath,
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

    suspend fun deletePhoto(photo: PhotoRecordEntity) = withContext(Dispatchers.IO) {
        val file = File(photo.filePath)
        if (file.exists()) {
            file.delete()
        }
        photoRecordDao.deletePhoto(photo)
    }
}

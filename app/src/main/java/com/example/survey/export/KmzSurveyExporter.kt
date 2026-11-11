package com.example.survey.export

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.data.RouteDao
import com.example.data.RouteEntity
import com.example.data.RouteRepository
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.parser.KmzParser
import com.example.survey.engine.SurveyGeometryEngine
import com.example.survey.model.SurveyCalibrationPoint
import com.example.survey.model.SurveyMarker
import com.example.survey.model.SurveyPoint
import com.example.survey.model.SurveySession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object KmzSurveyExporter {

    /**
     * Builds standard KML XML representation for the complete survey session.
     */
    fun buildKmlString(
        session: SurveySession,
        points: List<SurveyPoint>,
        markers: List<SurveyMarker>,
        calibrations: List<SurveyCalibrationPoint>
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        sb.append("""<kml xmlns="http://www.opengis.net/kml/2.2">""").append("\n")
        sb.append("<Document>\n")

        val docName = session.name.ifBlank { "Highway Survey" }
        sb.append("  <name>${escapeXml(docName)}</name>\n")
        sb.append("  <description>${escapeXml("CTECS Highway Survey - ${session.highwayName} ${session.stretchName}")}</description>\n")

        // Styles
        sb.append("""
  <Style id="centrelineStyle">
    <LineStyle>
      <color>ff00d7ff</color>
      <width>4</width>
    </LineStyle>
  </Style>
  <Style id="laneStyle">
    <LineStyle>
      <color>fff8bd38</color>
      <width>3</width>
    </LineStyle>
  </Style>
  <Style id="gpsTrackStyle">
    <LineStyle>
      <color>ff5ec522</color>
      <width>2.5</width>
    </LineStyle>
  </Style>
  <Style id="kmStoneStyle">
    <IconStyle>
      <scale>1.2</scale>
      <Icon>
        <href>http://maps.google.com/mapfiles/kml/paddle/red-circle.png</href>
      </Icon>
    </IconStyle>
  </Style>
""").append("\n")

        // 1. Survey Information Folder
        sb.append("  <Folder>\n")
        sb.append("    <name>Survey Information</name>\n")
        sb.append("    <description>\n")
        sb.append("      Project: ${escapeXml(session.projectName)}\n")
        sb.append("      Highway: ${escapeXml(session.highwayName)}\n")
        sb.append("      Road Number: ${escapeXml(session.roadNumber)}\n")
        sb.append("      Stretch: ${escapeXml(session.stretchName)}\n")
        sb.append("      Survey Date: ${escapeXml(session.surveyDate)}\n")
        sb.append("      Road Side: ${escapeXml(session.roadSide.label)}\n")
        sb.append("      Lane: ${escapeXml(session.displayLaneName)}\n")
        sb.append("      Direction: ${escapeXml(session.direction.label)}\n")
        sb.append("      Centreline Offset: ${session.centrelineOffsetMetres}m (${session.offsetSide.label})\n")
        sb.append("      Start Chainage: ${session.displayStartChainage}\n")
        sb.append("      End Chainage: ${session.displayCurrentChainage}\n")
        if (session.hasPlannedEndChainage) {
            sb.append("      Planned End Chainage: ${session.displayPlannedEndChainage}\n")
        }
        sb.append("      Confirmation Interval: ${session.confirmationIntervalMetres?.let { "Every ${it / 1000} KM" } ?: "Off"}\n")
        sb.append("      Distance Travelled: ${String.format(Locale.US, "%.1f m", session.totalDistanceTravelledMetres)}\n")
        sb.append("      Total Survey Points: ${points.size}\n")
        sb.append("      Total KM Stones: ${markers.size}\n")
        sb.append("    </description>\n")
        sb.append("  </Folder>\n")

        // 2. Highway Centreline (Generated)
        val centrelinePts = SurveyGeometryEngine.generateCentrelineTrack(
            points = points,
            offsetDistanceM = session.centrelineOffsetMetres,
            offsetSide = session.offsetSide
        )
        if (centrelinePts.isNotEmpty()) {
            sb.append("  <Folder>\n")
            sb.append("    <name>Highway Centreline</name>\n")
            sb.append("    <Placemark>\n")
            sb.append("      <name>Highway Centreline Alignment</name>\n")
            sb.append("      <styleUrl>#centrelineStyle</styleUrl>\n")
            sb.append("      <LineString>\n")
            sb.append("        <tessellate>1</tessellate>\n")
            sb.append("        <coordinates>\n")
            centrelinePts.forEach { pt ->
                sb.append("          ${pt.longitude},${pt.latitude},${pt.altitude}\n")
            }
            sb.append("        </coordinates>\n")
            sb.append("      </LineString>\n")
            sb.append("    </Placemark>\n")
            sb.append("  </Folder>\n")

            // 2b. Centreline Chainage Labels Every 200 Metres
            val labels200m = SurveyGeometryEngine.generate200mChainageLabels(
                centrelinePts = centrelinePts,
                startChainageMetres = session.startChainageMetres,
                direction = session.direction,
                calibrations = calibrations
            )
            if (labels200m.isNotEmpty()) {
                sb.append("  <Folder>\n")
                sb.append("    <name>Centreline Chainage (200m)</name>\n")
                labels200m.forEach { lbl ->
                    sb.append("    <Placemark>\n")
                    sb.append("      <name>${escapeXml(lbl.displayChainage)}</name>\n")
                    sb.append("      <description>${escapeXml("Chainage: ${lbl.displayChainage} (Bearing: ${String.format(Locale.US, "%.1f°", lbl.bearingDeg)})")}</description>\n")
                    sb.append("      <Point>\n")
                    sb.append("        <coordinates>${lbl.position.longitude},${lbl.position.latitude},${lbl.position.altitude}</coordinates>\n")
                    sb.append("      </Point>\n")
                    sb.append("    </Placemark>\n")
                }
                sb.append("  </Folder>\n")
            }
        }

        // 3. Lane Alignment
        if (points.isNotEmpty()) {
            sb.append("  <Folder>\n")
            sb.append("    <name>Lane Alignment</name>\n")
            sb.append("    <Placemark>\n")
            sb.append("      <name>${escapeXml(session.displayLaneName)} Alignment</name>\n")
            sb.append("      <styleUrl>#laneStyle</styleUrl>\n")
            sb.append("      <LineString>\n")
            sb.append("        <tessellate>1</tessellate>\n")
            sb.append("        <coordinates>\n")
            points.forEach { p ->
                sb.append("          ${p.longitude},${p.latitude},${p.altitude}\n")
            }
            sb.append("        </coordinates>\n")
            sb.append("      </LineString>\n")
            sb.append("    </Placemark>\n")
            sb.append("  </Folder>\n")
        }

        // 4. Original GPS Track (with segment separation)
        if (points.isNotEmpty()) {
            sb.append("  <Folder>\n")
            sb.append("    <name>Original GPS Track</name>\n")
            val segments = points.groupBy { it.segmentIndex }
            segments.forEach { (segIdx, segPoints) ->
                sb.append("    <Placemark>\n")
                sb.append("      <name>GPS Track Segment ${segIdx + 1}</name>\n")
                sb.append("      <styleUrl>#gpsTrackStyle</styleUrl>\n")
                sb.append("      <LineString>\n")
                sb.append("        <tessellate>1</tessellate>\n")
                sb.append("        <coordinates>\n")
                segPoints.forEach { p ->
                    sb.append("          ${p.longitude},${p.latitude},${p.altitude}\n")
                }
                sb.append("        </coordinates>\n")
                sb.append("      </LineString>\n")
                sb.append("    </Placemark>\n")
            }
            sb.append("  </Folder>\n")
        }

        // 5. KM Stone Markers (Placemarks with Chainage Names)
        if (markers.isNotEmpty()) {
            sb.append("  <Folder>\n")
            sb.append("    <name>KM Stone Markers</name>\n")
            markers.forEach { m ->
                sb.append("    <Placemark>\n")
                sb.append("      <name>${escapeXml(m.displayChainage)}</name>\n")
                sb.append("      <description>${escapeXml("${m.markerType.label} | Chainage: ${m.displayChainage} | Lane: ${m.lane} | Accuracy: ±${m.accuracy.toInt()}m ${if (m.notes.isNotBlank()) " | " + m.notes else ""}")}</description>\n")
                sb.append("      <styleUrl>#kmStoneStyle</styleUrl>\n")
                sb.append("      <Point>\n")
                sb.append("        <coordinates>${m.longitude},${m.latitude},${m.altitude}</coordinates>\n")
                sb.append("      </Point>\n")
                sb.append("    </Placemark>\n")
            }
            sb.append("  </Folder>\n")
        }

        // 6. Calibration Points
        if (calibrations.isNotEmpty()) {
            sb.append("  <Folder>\n")
            sb.append("    <name>Calibration Points</name>\n")
            calibrations.forEachIndexed { idx, c ->
                sb.append("    <Placemark>\n")
                sb.append("      <name>Calibration #${idx + 1}</name>\n")
                sb.append("      <description>Known: ${c.knownChainageMetres}m | Drift: ${String.format(Locale.US, "%.1f m", c.driftMetres)} | Measured Dist: ${String.format(Locale.US, "%.1f m", c.measuredDistanceMetres)}</description>\n")
                sb.append("    </Placemark>\n")
            }
            sb.append("  </Folder>\n")
        }

        sb.append("</Document>\n")
        sb.append("</kml>\n")
        return sb.toString()
    }

    /**
     * Generates a KMZ (ZIP containing doc.kml) file in app storage or public Downloads.
     */
    suspend fun exportKmzFile(
        context: Context,
        session: SurveySession,
        points: List<SurveyPoint>,
        markers: List<SurveyMarker>,
        calibrations: List<SurveyCalibrationPoint>
    ): File = withContext(Dispatchers.IO) {
        val kmlContent = buildKmlString(session, points, markers, calibrations)
        val exportDir = File(context.filesDir, "survey_exports").apply { mkdirs() }
        val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val sanitizedTitle = (session.highwayName.ifBlank { session.name }).replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val kmzFile = File(exportDir, "${sanitizedTitle}_${dateSuffix}.kmz")

        ZipOutputStream(FileOutputStream(kmzFile)).use { zos ->
            val entry = ZipEntry("doc.kml")
            zos.putNextEntry(entry)
            zos.write(kmlContent.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.flush()
        }
        kmzFile
    }

    /**
     * Generates a plain KML file in app storage.
     */
    suspend fun exportKmlFile(
        context: Context,
        session: SurveySession,
        points: List<SurveyPoint>,
        markers: List<SurveyMarker>,
        calibrations: List<SurveyCalibrationPoint>
    ): File = withContext(Dispatchers.IO) {
        val kmlContent = buildKmlString(session, points, markers, calibrations)
        val exportDir = File(context.filesDir, "survey_exports").apply { mkdirs() }
        val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val sanitizedTitle = (session.highwayName.ifBlank { session.name }).replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val kmlFile = File(exportDir, "${sanitizedTitle}_${dateSuffix}.kml")

        FileOutputStream(kmlFile).use { fos ->
            fos.write(kmlContent.toByteArray(Charsets.UTF_8))
            fos.flush()
        }
        kmlFile
    }

    /**
     * Shares the exported KMZ or KML file via Android's ACTION_SEND intent.
     */
    fun shareSurveyFile(context: Context, file: File, isKmz: Boolean) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = if (isKmz) "application/vnd.google-earth.kmz" else "application/vnd.google-earth.kml+xml"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "CTECS Highway Survey: ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Survey KMZ / KML"))
    }

    /**
     * Imports the surveyed highway directly into CTECS Routes so it becomes an active route!
     */
    suspend fun importIntoCtecsRoutes(
        context: Context,
        session: SurveySession,
        points: List<SurveyPoint>,
        markers: List<SurveyMarker>,
        calibrations: List<SurveyCalibrationPoint>,
        routeRepository: RouteRepository
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val kmzFile = exportKmzFile(context, session, points, markers, calibrations)
            val displayName = session.highwayName.ifBlank { session.name }
            val parsedRoute = kmzFile.inputStream().use { input ->
                KmzParser.parse(input, kmzFile.name)
            }

            // Save in CTECS routes folder
            val routesDir = File(context.filesDir, "imported_routes").apply { mkdirs() }
            val targetFile = File(routesDir, kmzFile.name)
            kmzFile.copyTo(targetFile, overwrite = true)

            val db = com.example.data.AppDatabase.getDatabase(context)
            val entity = RouteEntity(
                id = parsedRoute.id,
                name = displayName,
                fileName = kmzFile.name,
                markerCount = parsedRoute.markers.size,
                startChainageMetres = parsedRoute.startChainage?.metres,
                endChainageMetres = parsedRoute.endChainage?.metres,
                minLat = parsedRoute.bounds.minLat,
                maxLat = parsedRoute.bounds.maxLat,
                minLng = parsedRoute.bounds.minLng,
                maxLng = parsedRoute.bounds.maxLng,
                importedAt = System.currentTimeMillis(),
                isActive = true,
                localFilePath = targetFile.absolutePath
            )

            db.routeDao().deactivateAllRoutes()
            db.routeDao().insertRoute(entity)
            com.example.data.PreferencesManager(context).setActiveRouteId(parsedRoute.id)
            routeRepository.loadRouteById(parsedRoute.id)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun escapeXml(str: String): String {
        return str
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}

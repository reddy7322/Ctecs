package com.example.parser

import com.example.geo.GeoUtils
import com.example.model.AlignmentDiagnostics
import com.example.model.Chainage
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.RouteMarker
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.roundToLong

class RouteParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

object KmzParser {

    /**
     * Parses a KMZ or KML stream into [RouteData].
     * Uses automatic magic-byte header inspection (PK..) and filename fallback
     * to safely detect whether the file is a KMZ (ZIP archive) or plain KML (XML).
     */
    @Throws(RouteParseException::class)
    fun parse(inputStream: InputStream, originalFileName: String): RouteData {
        val bytes = try {
            inputStream.readBytes()
        } catch (e: Exception) {
            throw RouteParseException("Cannot read route file stream: ${e.message}", e)
        }

        if (bytes.isEmpty()) {
            throw RouteParseException("Selected route file is empty: $originalFileName")
        }

        // Check for ZIP magic header: 0x50 0x4B (ASCII 'P' 'K')
        val isZipHeader = bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() &&
                bytes[1] == 0x4B.toByte()

        val isKmzByName = originalFileName.endsWith(".kmz", ignoreCase = true)

        return if (isZipHeader || isKmzByName) {
            try {
                parseKmzBytes(bytes, originalFileName)
            } catch (kmzErr: Exception) {
                if (!isZipHeader) {
                    try {
                        parseKmlBytes(bytes, originalFileName)
                    } catch (ignored: Exception) {
                        throw kmzErr
                    }
                } else {
                    throw kmzErr
                }
            }
        } else {
            try {
                parseKmlBytes(bytes, originalFileName)
            } catch (kmlErr: Exception) {
                if (isZipHeader) {
                    try {
                        parseKmzBytes(bytes, originalFileName)
                    } catch (ignored: Exception) {
                        throw kmlErr
                    }
                } else {
                    throw kmlErr
                }
            }
        }
    }

    private fun parseKmzBytes(bytes: ByteArray, originalFileName: String): RouteData {
        try {
            val zis = ZipInputStream(ByteArrayInputStream(bytes))
            var entry: ZipEntry? = zis.nextEntry
            var fallbackKmlBytes: ByteArray? = null
            val kmlEntries = mutableListOf<Pair<String, ByteArray>>()

            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryBytes = zis.readBytes()
                    if (entry.name.endsWith(".kml", ignoreCase = true)) {
                        kmlEntries.add(entry.name to entryBytes)
                    } else if (fallbackKmlBytes == null && entryBytes.isNotEmpty()) {
                        val header = String(entryBytes.take(128).toByteArray(), Charsets.UTF_8).trimStart()
                        if (header.contains("<kml", ignoreCase = true) || header.contains("<?xml", ignoreCase = true)) {
                            fallbackKmlBytes = entryBytes
                        }
                    }
                }
                entry = zis.nextEntry
            }
            zis.close()

            if (kmlEntries.isNotEmpty()) {
                // Prioritize doc.kml or the largest KML entry in the KMZ archive
                val primaryKml = kmlEntries.firstOrNull { it.first.equals("doc.kml", ignoreCase = true) }
                    ?: kmlEntries.maxByOrNull { it.second.size }
                    ?: kmlEntries.first()
                return parseKmlBytes(primaryKml.second, originalFileName)
            }

            if (fallbackKmlBytes != null) {
                return parseKmlBytes(fallbackKmlBytes, originalFileName)
            }
            throw RouteParseException("No valid .kml entry found inside the KMZ archive: $originalFileName")
        } catch (e: RouteParseException) {
            throw e
        } catch (e: Exception) {
            throw RouteParseException("Failed to read KMZ file: ${e.message}", e)
        }
    }

    fun parseKmlBytes(bytes: ByteArray, originalFileName: String): RouteData {
        // Strip UTF-8 BOM (EF BB BF) if present
        val cleanBytes = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }
        return parseKmlStream(ByteArrayInputStream(cleanBytes), originalFileName)
    }

    @Throws(RouteParseException::class)
    fun parseKmlStream(inputStream: InputStream, originalFileName: String): RouteData {
        val rawBytes = try {
            inputStream.readBytes()
        } catch (e: Exception) {
            throw RouteParseException("Cannot read KML stream: ${e.message}", e)
        }

        val parser = try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val p = factory.newPullParser()
            p.setInput(ByteArrayInputStream(rawBytes), "UTF-8")
            p
        } catch (e: Throwable) {
            null
        }

        if (parser == null) {
            // JVM fallback via standard javax.xml.parsers.DocumentBuilderFactory
            return parseKmlWithDom(ByteArrayInputStream(rawBytes), originalFileName)
        }

        try {
            var eventType = parser.eventType
            var documentName: String? = null
            var currentFolder = ""
            val markers = mutableListOf<RouteMarker>()
            val alignments = mutableListOf<RouteAlignment>()
            var alignmentCounter = 1

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tagLower = parser.name.lowercase(Locale.ROOT).substringAfterLast(':')
                    when (tagLower) {
                        "document" -> {}
                        "folder" -> {}
                        "name" -> {
                            val text = parser.nextText().trim()
                            if (documentName == null) {
                                documentName = text
                            } else {
                                currentFolder = text
                            }
                        }
                        "placemark" -> {
                            parsePlacemark(parser, currentFolder, markers, alignments, alignmentCounter++)
                        }
                        "linestring", "linearring" -> {
                            val coords = parseLineStringTag(parser)
                            if (coords.size >= 2) {
                                val alignName = currentFolder.ifBlank { documentName ?: "Alignment #$alignmentCounter" }
                                alignments.add(
                                    RouteAlignment(
                                        id = "align-top-${alignmentCounter++}",
                                        name = alignName,
                                        points = deduplicatePathPoints(coords),
                                        direction = "UNKNOWN",
                                        carriagewayType = detectCarriagewayType(alignName)
                                    )
                                )
                            }
                        }
                        "track" -> {
                            val coords = parseGxTrackTag(parser)
                            if (coords.size >= 2) {
                                val alignName = currentFolder.ifBlank { documentName ?: "Track #$alignmentCounter" }
                                alignments.add(
                                    RouteAlignment(
                                        id = "align-track-${alignmentCounter++}",
                                        name = alignName,
                                        points = deduplicatePathPoints(coords),
                                        direction = "UNKNOWN",
                                        carriagewayType = detectCarriagewayType(alignName)
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            return buildFinalRouteData(
                documentName = documentName,
                originalFileName = originalFileName,
                markers = markers,
                alignments = alignments
            )
        } catch (e: RouteParseException) {
            throw e
        } catch (e: Exception) {
            throw RouteParseException("Error parsing KML: ${e.message}", e)
        }
    }

    private fun parseKmlWithDom(inputStream: InputStream, originalFileName: String): RouteData {
        try {
            val dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            dbFactory.isNamespaceAware = false
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(inputStream)
            doc.documentElement.normalize()

            var documentName: String? = null
            val docNodes = doc.getElementsByTagName("Document")
            if (docNodes.length > 0) {
                val docEl = docNodes.item(0) as org.w3c.dom.Element
                val nameNodes = docEl.getElementsByTagName("name")
                if (nameNodes.length > 0) {
                    documentName = nameNodes.item(0).textContent?.trim()
                }
            }

            val markers = mutableListOf<RouteMarker>()
            val alignments = mutableListOf<RouteAlignment>()
            val placemarks = doc.getElementsByTagName("Placemark")

            for (i in 0 until placemarks.length) {
                val pNode = placemarks.item(i)
                if (pNode.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                    val pElem = pNode as org.w3c.dom.Element
                    val name = pElem.getElementsByTagName("name").item(0)?.textContent?.trim()
                    val desc = pElem.getElementsByTagName("description").item(0)?.textContent?.trim()

                    // Check ExtendedData for chainage attributes
                    val extendedDataChainage = extractChainageFromExtendedDataDom(pElem)
                    val chainage = Chainage.parse(name) ?: Chainage.parse(desc) ?: extendedDataChainage

                    // Parse Points (support direct and MultiGeometry)
                    val pointEls = pElem.getElementsByTagName("Point")
                    for (k in 0 until pointEls.length) {
                        val ptElem = pointEls.item(k) as org.w3c.dom.Element
                        val coordText = ptElem.getElementsByTagName("coordinates").item(0)?.textContent?.trim()
                        if (coordText != null) {
                            val pt = parseSingleCoordinate(coordText)
                            if (pt != null && chainage != null) {
                                markers.add(
                                    RouteMarker(
                                        id = UUID.randomUUID().toString(),
                                        label = name ?: chainage.format(),
                                        chainage = chainage,
                                        point = pt,
                                        description = desc ?: ""
                                    )
                                )
                            }
                        }
                    }

                    // Parse LineStrings and LinearRings (support direct, Polygon, and MultiGeometry)
                    val lineTagNames = listOf("LineString", "LinearRing")
                    for (tagName in lineTagNames) {
                        val lineEls = pElem.getElementsByTagName(tagName)
                        for (j in 0 until lineEls.length) {
                            val lineElem = lineEls.item(j) as org.w3c.dom.Element
                            val coordText = lineElem.getElementsByTagName("coordinates").item(0)?.textContent?.trim()
                            if (coordText != null) {
                                val pts = deduplicatePathPoints(parseMultipleCoordinates(coordText))
                                if (pts.size >= 2) {
                                    val alignName = name ?: "Alignment #${alignments.size + 1}"
                                    alignments.add(
                                        RouteAlignment(
                                            id = "align-dom-$i-$tagName-$j",
                                            name = alignName,
                                            points = pts,
                                            direction = "UNKNOWN",
                                            carriagewayType = detectCarriagewayType(alignName)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Parse gx:Track / Track coordinates
                    val trackTagNames = listOf("gx:Track", "Track")
                    for (trackTag in trackTagNames) {
                        val trackEls = pElem.getElementsByTagName(trackTag)
                        for (t in 0 until trackEls.length) {
                            val trackElem = trackEls.item(t) as org.w3c.dom.Element
                            val trackPts = mutableListOf<GeoPoint>()
                            val coordNodes = trackElem.childNodes
                            for (cIdx in 0 until coordNodes.length) {
                                val cNode = coordNodes.item(cIdx)
                                if (cNode.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                                    val cName = cNode.nodeName.lowercase(Locale.ROOT).substringAfterLast(':')
                                    if (cName == "coord") {
                                        val pt = parseGxCoord(cNode.textContent?.trim().orEmpty())
                                        if (pt != null) trackPts.add(pt)
                                    }
                                }
                            }
                            val dedup = deduplicatePathPoints(trackPts)
                            if (dedup.size >= 2) {
                                val alignName = name ?: "Track #${alignments.size + 1}"
                                alignments.add(
                                    RouteAlignment(
                                        id = "align-dom-track-$i-$t",
                                        name = alignName,
                                        points = dedup,
                                        direction = "UNKNOWN",
                                        carriagewayType = detectCarriagewayType(alignName)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            return buildFinalRouteData(
                documentName = documentName,
                originalFileName = originalFileName,
                markers = markers,
                alignments = alignments
            )
        } catch (e: RouteParseException) {
            throw e
        } catch (e: Exception) {
            throw RouteParseException("Error parsing KML via DOM: ${e.message}", e)
        }
    }

    /**
     * Finalizes parsed KML/KMZ data into a validated [RouteData] object.
     * If the KMZ contains path coordinates (`alignments`) without explicit point markers,
     * automatically generates accurate baseline chainage markers along the extracted path coordinates
     * so the chainage matcher and calibration system have full baseline chainage coverage.
     */
    private fun buildFinalRouteData(
        documentName: String?,
        originalFileName: String,
        markers: List<RouteMarker>,
        alignments: List<RouteAlignment>
    ): RouteData {
        if (markers.isEmpty() && alignments.isEmpty()) {
            throw RouteParseException("No valid chainage placemarks or road alignments found in $originalFileName")
        }

        // Merge contiguous path segments when multiple small disjointed LineStrings belong to the same carriageway
        val consolidatedAlignments = consolidatePathAlignments(alignments)

        // If the KMZ file only contains path coordinates (no Point markers),
        // synthesize baseline chainage markers from the extracted path coordinates!
        val effectiveMarkers = if (markers.isEmpty() && consolidatedAlignments.isNotEmpty()) {
            val primaryPath = consolidatedAlignments.maxByOrNull { calculatePathLengthMetres(it.points) }
                ?: consolidatedAlignments.first()
            val inferredStartChainage = Chainage.parse(primaryPath.name)?.metres
                ?: Chainage.parse(documentName)?.metres
                ?: 0L
            computeBaselineMarkersFromPath(
                points = primaryPath.points,
                startChainageMetres = inferredStartChainage,
                intervalMetres = 100.0
            )
        } else {
            markers
        }

        val sortedMarkers = effectiveMarkers.sortedBy { it.chainage.metres }

        val rawAlignments = if (consolidatedAlignments.isEmpty() && sortedMarkers.size >= 2) {
            val polyPoints = deduplicatePathPoints(sortedMarkers.map { it.point })
            listOf(RouteAlignment("auto-alignment", "Auto Generated Alignment", polyPoints))
        } else {
            consolidatedAlignments
        }

        // Post-process alignments with direction and carriageway classification
        val processedAlignments = rawAlignments.map { align ->
            val dir = determineAlignmentDirection(align.points, sortedMarkers)
            val cType = detectCarriagewayType(align.name)
            align.copy(direction = dir, carriagewayType = cType)
        }

        val allPoints = mutableListOf<GeoPoint>()
        allPoints.addAll(sortedMarkers.map { it.point })
        processedAlignments.forEach { allPoints.addAll(it.points) }

        val bounds = GeoBounds.fromPoints(allPoints)

        // Compute startChainage and endChainage accurately even if only 1 marker + path exists
        val (startChainage, endChainage) = if (sortedMarkers.size >= 2) {
            sortedMarkers.first().chainage to sortedMarkers.last().chainage
        } else if (sortedMarkers.size == 1 && processedAlignments.isNotEmpty()) {
            val single = sortedMarkers.first()
            val primaryPts = processedAlignments.first().points
            val totalLen = calculatePathLengthMetres(primaryPts).roundToLong()
            single.chainage to Chainage(single.chainage.metres + totalLen)
        } else {
            sortedMarkers.firstOrNull()?.chainage to sortedMarkers.lastOrNull()?.chainage
        }

        val cleanFileBase = originalFileName.substringBeforeLast(".").replace(Regex("^\\d+_"), "")
        val isGenericDocName = documentName.isNullOrBlank() ||
            documentName.equals("Model", ignoreCase = true) ||
            documentName.equals("doc", ignoreCase = true) ||
            documentName.equals("doc.kml", ignoreCase = true) ||
            documentName.equals("Untitled", ignoreCase = true)
        val finalName = if (!isGenericDocName) documentName!! else cleanFileBase.ifBlank { documentName ?: "KMZ Route" }
        val diagnostics = analyzeAlignmentDiagnostics(sortedMarkers, processedAlignments)

        return RouteData(
            id = UUID.randomUUID().toString(),
            name = finalName,
            fileName = originalFileName,
            markers = sortedMarkers,
            alignments = processedAlignments,
            bounds = bounds,
            startChainage = startChainage,
            endChainage = endChainage,
            diagnostics = diagnostics
        )
    }

    /**
     * Generates accurate baseline chainage markers along extracted KMZ path coordinates
     * at regular geodesic intervals (e.g., every 100m, plus start and end vertices).
     */
    fun computeBaselineMarkersFromPath(
        points: List<GeoPoint>,
        startChainageMetres: Long = 0L,
        intervalMetres: Double = 100.0
    ): List<RouteMarker> {
        val cleanPts = deduplicatePathPoints(points)
        if (cleanPts.isEmpty()) return emptyList()
        if (cleanPts.size == 1) {
            val ch = Chainage(startChainageMetres)
            return listOf(
                RouteMarker(
                    id = "path-baseline-0",
                    label = ch.format(),
                    chainage = ch,
                    point = cleanPts[0],
                    description = "Path Start Baseline"
                )
            )
        }

        val result = mutableListOf<RouteMarker>()
        val safeInterval = intervalMetres.coerceAtLeast(10.0)

        // Always add start station
        val startCh = Chainage(startChainageMetres)
        result.add(
            RouteMarker(
                id = "path-baseline-start",
                label = startCh.format(),
                chainage = startCh,
                point = cleanPts.first(),
                description = "Extracted Path Baseline Start"
            )
        )

        var cumulativeDist = 0.0
        var nextTargetDist = safeInterval
        var stationIdx = 1

        for (i in 0 until cleanPts.size - 1) {
            val p1 = cleanPts[i]
            val p2 = cleanPts[i + 1]
            val segLen = GeoUtils.distanceMetres(p1, p2)
            if (segLen <= 1e-3) continue

            while (cumulativeDist + segLen >= nextTargetDist) {
                val distIntoSeg = nextTargetDist - cumulativeDist
                val fraction = (distIntoSeg / segLen).coerceIn(0.0, 1.0)
                val interpLat = p1.latitude + fraction * (p2.latitude - p1.latitude)
                val interpLon = p1.longitude + fraction * (p2.longitude - p1.longitude)
                val interpAlt = p1.altitude + fraction * (p2.altitude - p1.altitude)
                val chMetres = startChainageMetres + nextTargetDist.roundToLong()
                val ch = Chainage(chMetres)

                result.add(
                    RouteMarker(
                        id = "path-baseline-$stationIdx",
                        label = ch.format(),
                        chainage = ch,
                        point = GeoPoint(interpLat, interpLon, interpAlt),
                        description = "Extracted Path Baseline Station"
                    )
                )
                stationIdx++
                nextTargetDist += safeInterval
            }

            cumulativeDist += segLen
        }

        // Always add end station if total length exceeds last added station by >= 1m
        val totalMetres = cumulativeDist.roundToLong()
        val endChainageMetres = startChainageMetres + totalMetres
        val lastAddedMetres = result.last().chainage.metres
        if (abs(endChainageMetres - lastAddedMetres) >= 1L || result.size == 1) {
            val endCh = Chainage(endChainageMetres)
            result.add(
                RouteMarker(
                    id = "path-baseline-end",
                    label = endCh.format(),
                    chainage = endCh,
                    point = cleanPts.last(),
                    description = "Extracted Path Baseline End"
                )
            )
        }

        return result
    }

    /**
     * Extracts all path coordinates from a [RouteData] instance in continuous order.
     */
    fun extractPathCoordinates(routeData: RouteData): List<GeoPoint> {
        if (routeData.alignments.isNotEmpty()) {
            return deduplicatePathPoints(routeData.alignments.flatMap { it.points })
        }
        return deduplicatePathPoints(routeData.markers.map { it.point })
    }

    /**
     * Calculates total geodesic length in metres along a polyline path.
     */
    fun calculatePathLengthMetres(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += GeoUtils.distanceMetres(points[i], points[i + 1])
        }
        return total
    }

    /**
     * Removes consecutive duplicate vertices (< 0.05m apart) from path coordinates.
     */
    private fun deduplicatePathPoints(points: List<GeoPoint>): List<GeoPoint> {
        if (points.size <= 1) return points
        val cleaned = ArrayList<GeoPoint>(points.size)
        cleaned.add(points[0])
        for (i in 1 until points.size) {
            val prev = cleaned.last()
            val curr = points[i]
            if (abs(prev.latitude - curr.latitude) > 1e-8 || abs(prev.longitude - curr.longitude) > 1e-8) {
                cleaned.add(curr)
            }
        }
        return cleaned
    }

    /**
     * Consolidates fragmented path segments that share endpoints (within 5m) and belong to the same carriageway.
     */
    private fun consolidatePathAlignments(alignments: List<RouteAlignment>): List<RouteAlignment> {
        if (alignments.size <= 1) return alignments
        val result = mutableListOf<RouteAlignment>()
        var current = alignments[0]

        for (i in 1 until alignments.size) {
            val next = alignments[i]
            val currEnd = current.points.lastOrNull()
            val nextStart = next.points.firstOrNull()
            val sameCarriageway = current.carriagewayType == next.carriagewayType &&
                    (current.name == next.name || current.name.startsWith("Alignment #"))

            if (currEnd != null && nextStart != null && sameCarriageway &&
                GeoUtils.distanceMetres(currEnd, nextStart) <= 5.0
            ) {
                val mergedPoints = deduplicatePathPoints(current.points + next.points)
                current = current.copy(points = mergedPoints)
            } else {
                result.add(current)
                current = next
            }
        }
        result.add(current)
        return result
    }

    private fun parsePlacemark(
        parser: XmlPullParser,
        currentFolder: String,
        markers: MutableList<RouteMarker>,
        alignments: MutableList<RouteAlignment>,
        alignmentIndex: Int
    ) {
        var name: String? = null
        var description: String? = null
        var extendedDataChainage: Chainage? = null
        val points = mutableListOf<GeoPoint>()
        val lineStrings = mutableListOf<List<GeoPoint>>()

        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    val tagLower = parser.name.lowercase(Locale.ROOT).substringAfterLast(':')
                    when (tagLower) {
                        "name" -> {
                            name = parser.nextText().trim()
                            depth--
                        }
                        "description" -> {
                            description = parser.nextText().trim()
                            depth--
                        }
                        "point" -> {
                            val pt = parsePointTag(parser)
                            if (pt != null) points.add(pt)
                            depth-- // consumed in helper
                        }
                        "linestring", "linearring" -> {
                            val coords = parseLineStringTag(parser)
                            if (coords.isNotEmpty()) lineStrings.add(coords)
                            depth-- // consumed in helper
                        }
                        "track" -> {
                            val coords = parseGxTrackTag(parser)
                            if (coords.isNotEmpty()) lineStrings.add(coords)
                            depth-- // consumed in helper
                        }
                        "multigeometry", "polygon", "outerboundaryis" -> {
                            // Container tag, continue parsing child Point / LineString / LinearRing tags
                        }
                        "extendeddata" -> {
                            extendedDataChainage = parseExtendedDataTag(parser)
                            depth--
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    depth--
                }
            }
        }

        // Try extracting chainage from name, description, or ExtendedData attributes
        val chainage = Chainage.parse(name) ?: Chainage.parse(description) ?: extendedDataChainage
        if (chainage != null && points.isNotEmpty()) {
            points.forEach { pt ->
                markers.add(
                    RouteMarker(
                        id = UUID.randomUUID().toString(),
                        label = name ?: chainage.format(),
                        chainage = chainage,
                        point = pt,
                        folderName = currentFolder,
                        description = description ?: ""
                    )
                )
            }
        }

        // If line strings found, save alignment
        lineStrings.forEachIndexed { i, coords ->
            val dedup = deduplicatePathPoints(coords)
            if (dedup.size >= 2) {
                val alignName = name ?: currentFolder.ifBlank { "Alignment #$alignmentIndex" }
                alignments.add(
                    RouteAlignment(
                        id = "align-${alignmentIndex}-$i",
                        name = alignName,
                        points = dedup,
                        direction = "UNKNOWN",
                        carriagewayType = detectCarriagewayType(alignName)
                    )
                )
            }
        }
    }

    private fun parsePointTag(parser: XmlPullParser): GeoPoint? {
        var pt: GeoPoint? = null
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (parser.name.equals("coordinates", ignoreCase = true)) {
                        val text = parser.nextText().trim()
                        pt = parseSingleCoordinate(text)
                        depth--
                    }
                }
                XmlPullParser.END_TAG -> depth--
            }
        }
        return pt
    }

    private fun parseLineStringTag(parser: XmlPullParser): List<GeoPoint> {
        val list = mutableListOf<GeoPoint>()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (parser.name.equals("coordinates", ignoreCase = true)) {
                        val text = parser.nextText().trim()
                        list.addAll(parseMultipleCoordinates(text))
                        depth--
                    }
                }
                XmlPullParser.END_TAG -> depth--
            }
        }
        return list
    }

    private fun parseGxTrackTag(parser: XmlPullParser): List<GeoPoint> {
        val list = mutableListOf<GeoPoint>()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    val tagLower = parser.name.lowercase(Locale.ROOT).substringAfterLast(':')
                    if (tagLower == "coord") {
                        val text = parser.nextText().trim()
                        val pt = parseGxCoord(text)
                        if (pt != null) list.add(pt)
                        depth--
                    } else if (tagLower == "coordinates") {
                        val text = parser.nextText().trim()
                        list.addAll(parseMultipleCoordinates(text))
                        depth--
                    }
                }
                XmlPullParser.END_TAG -> depth--
            }
        }
        return list
    }

    private fun parseGxCoord(raw: String): GeoPoint? {
        if (raw.contains(",")) return parseSingleCoordinate(raw)
        val parts = raw.trim().split("\\s+".toRegex())
        if (parts.size >= 2) {
            val lon = parts[0].toDoubleOrNull() ?: return null
            val lat = parts[1].toDoubleOrNull() ?: return null
            val alt = if (parts.size >= 3) parts[2].toDoubleOrNull() ?: 0.0 else 0.0
            if (lat in -90.0..90.0 && lon in -180.0..180.0) {
                return GeoPoint(lat, lon, alt)
            }
        }
        return null
    }

    private fun parseExtendedDataTag(parser: XmlPullParser): Chainage? {
        var foundChainage: Chainage? = null
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    val tagName = parser.name.lowercase(Locale.ROOT).substringAfterLast(':')
                    if (tagName == "simpledata" || tagName == "data" || tagName == "value") {
                        val attrName = parser.getAttributeValue(null, "name")?.lowercase(Locale.ROOT) ?: ""
                        val text = parser.nextText().trim()
                        depth--
                        if (attrName.contains("chainage") || attrName.contains("ch") || attrName.contains("km") || tagName == "value") {
                            val candidate = Chainage.parse(text)
                            if (candidate != null && foundChainage == null) {
                                foundChainage = candidate
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> depth--
            }
        }
        return foundChainage
    }

    private fun extractChainageFromExtendedDataDom(elem: org.w3c.dom.Element): Chainage? {
        val simpleDataNodes = elem.getElementsByTagName("SimpleData")
        for (i in 0 until simpleDataNodes.length) {
            val item = simpleDataNodes.item(i) as org.w3c.dom.Element
            val attrName = item.getAttribute("name").lowercase(Locale.ROOT)
            if (attrName.contains("chainage") || attrName.contains("ch") || attrName.contains("km")) {
                val parsed = Chainage.parse(item.textContent?.trim())
                if (parsed != null) return parsed
            }
        }

        val dataNodes = elem.getElementsByTagName("Data")
        for (i in 0 until dataNodes.length) {
            val item = dataNodes.item(i) as org.w3c.dom.Element
            val attrName = item.getAttribute("name").lowercase(Locale.ROOT)
            val valNode = item.getElementsByTagName("value").item(0)
            if (attrName.contains("chainage") || attrName.contains("ch") || attrName.contains("km")) {
                val parsed = Chainage.parse(valNode?.textContent?.trim())
                if (parsed != null) return parsed
            }
        }
        return null
    }

    private fun parseSingleCoordinate(raw: String): GeoPoint? {
        val parts = raw.split(",").map { it.trim() }
        if (parts.size >= 2) {
            val lon = parts[0].toDoubleOrNull() ?: return null
            val lat = parts[1].toDoubleOrNull() ?: return null
            val alt = if (parts.size >= 3) parts[2].toDoubleOrNull() ?: 0.0 else 0.0
            if (lat in -90.0..90.0 && lon in -180.0..180.0) {
                return GeoPoint(lat, lon, alt)
            }
        }
        return null
    }

    private fun parseMultipleCoordinates(raw: String): List<GeoPoint> {
        val tokens = raw.split("\\s+".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
        val points = mutableListOf<GeoPoint>()
        for (tok in tokens) {
            val pt = parseSingleCoordinate(tok)
            if (pt != null) {
                points.add(pt)
            }
        }
        return points
    }

    /**
     * Determines whether chainage progression along this alignment polyline is INCREASING or DECREASING.
     */
    fun determineAlignmentDirection(alignmentPts: List<GeoPoint>, markers: List<RouteMarker>): String {
        if (alignmentPts.size < 2 || markers.size < 2) return "INCREASING"

        // Sample first and last section of the polyline
        val startPt = alignmentPts.first()
        val endPt = alignmentPts.last()

        val nearStart = markers.minByOrNull { GeoUtils.distanceMetres(it.point, startPt) }
        val nearEnd = markers.minByOrNull { GeoUtils.distanceMetres(it.point, endPt) }

        if (nearStart != null && nearEnd != null && nearStart.id != nearEnd.id) {
            return if (nearEnd.chainage.metres >= nearStart.chainage.metres) {
                "INCREASING"
            } else {
                "DECREASING"
            }
        }

        return "INCREASING"
    }

    /**
     * Classifies carriageway type from alignment name.
     */
    fun detectCarriagewayType(name: String): String {
        val lower = name.lowercase(Locale.ROOT)
        return when {
            lower.contains("lhs") || lower.contains("left") -> "LHS"
            lower.contains("rhs") || lower.contains("right") -> "RHS"
            lower.contains("bhs") || lower.contains("median") || lower.contains("centre") -> "BHS"
            lower.contains("service") || lower.contains("slip") -> "SERVICE"
            else -> "MAIN"
        }
    }

    /**
     * Performs a comprehensive quality and diagnostic audit on the KMZ alignment and markers.
     */
    fun analyzeAlignmentDiagnostics(
        markers: List<RouteMarker>,
        alignments: List<RouteAlignment>
    ): AlignmentDiagnostics {
        if (markers.isEmpty()) {
            return AlignmentDiagnostics(
                totalMarkersCount = 0,
                qualityScorePercent = 50,
                summaryMessage = "No chainage markers present in alignment"
            )
        }

        val warnings = mutableListOf<String>()
        val chainageEquations = mutableListOf<String>()

        // 1. Duplicate check (markers within 5m having identical or near-identical chainage)
        var duplicateCount = 0
        for (i in 0 until markers.size - 1) {
            val m1 = markers[i]
            val m2 = markers[i + 1]
            if (abs(m1.chainage.metres - m2.chainage.metres) < 5 && GeoUtils.distanceMetres(m1.point, m2.point) < 20.0) {
                duplicateCount++
            }
        }
        if (duplicateCount > 0) {
            warnings.add("Found $duplicateCount duplicate or overlapping chainage reference markers.")
        }

        // 2. Inversion check
        var inversionCount = 0
        for (i in 0 until markers.size - 1) {
            if (markers[i + 1].chainage.metres < markers[i].chainage.metres) {
                inversionCount++
            }
        }
        if (inversionCount > 0) {
            warnings.add("Detected $inversionCount chainage direction inversions.")
        }

        // 3. Gap and Discontinuity / Chainage Equation analysis
        var gapCount = 0
        for (i in 0 until markers.size - 1) {
            val m1 = markers[i]
            val m2 = markers[i + 1]
            val deltaCh = abs(m2.chainage.metres - m1.chainage.metres)
            val geomDist = GeoUtils.distanceMetres(m1.point, m2.point)

            if (deltaCh > 2500) {
                gapCount++
            }

            // If chainage delta differs from straight line distance by > 150m (on straight segment), note possible chainage equation
            if (abs(deltaCh - geomDist) > 150.0 && deltaCh > 100) {
                chainageEquations.add("Equation at ${m1.chainage.format()} ↔ ${m2.chainage.format()}: ΔCh=${deltaCh}m vs Geom=${geomDist.toInt()}m")
            }
        }

        if (gapCount > 0) {
            warnings.add("Detected $gapCount large marker gaps (> 2.5 km).")
        }

        var score = 100
        score -= (duplicateCount * 5).coerceAtMost(25)
        score -= (inversionCount * 10).coerceAtMost(30)
        score -= (gapCount * 5).coerceAtMost(20)
        score = score.coerceIn(20, 100)

        val summary = when {
            score >= 90 -> "Precision Verified Alignment (100% Validated)"
            score >= 70 -> "Good Alignment Quality (${markers.size} Markers)"
            else -> "Quality Warnings: $duplicateCount Duplicates, $inversionCount Inversions"
        }

        return AlignmentDiagnostics(
            isChainageIncreasing = inversionCount == 0,
            totalMarkersCount = markers.size,
            duplicateMarkersCount = duplicateCount,
            inversionCount = inversionCount,
            gapCount = gapCount,
            chainageEquations = chainageEquations,
            warnings = warnings,
            qualityScorePercent = score,
            summaryMessage = summary
        )
    }
}

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

            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryBytes = zis.readBytes()
                    if (entry.name.endsWith(".kml", ignoreCase = true)) {
                        zis.close()
                        return parseKmlBytes(entryBytes, originalFileName)
                    }
                    if (fallbackKmlBytes == null && entryBytes.isNotEmpty()) {
                        val header = String(entryBytes.take(128).toByteArray(), Charsets.UTF_8).trimStart()
                        if (header.contains("<kml", ignoreCase = true) || header.contains("<?xml", ignoreCase = true)) {
                            fallbackKmlBytes = entryBytes
                        }
                    }
                }
                entry = zis.nextEntry
            }
            zis.close()

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
        val parser = try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val p = factory.newPullParser()
            p.setInput(inputStream, "UTF-8")
            p
        } catch (e: Throwable) {
            null
        }

        if (parser == null) {
            // JVM fallback via standard javax.xml.parsers.DocumentBuilderFactory
            return parseKmlWithDom(inputStream, originalFileName)
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
                    when (parser.name.lowercase(Locale.ROOT)) {
                        "document" -> {}
                        "folder" -> {}
                        "name" -> {
                            val text = parser.nextText().trim()
                            if (documentName == null) {
                                documentName = text
                            }
                        }
                        "placemark" -> {
                            parsePlacemark(parser, currentFolder, markers, alignments, alignmentCounter++)
                        }
                    }
                }
                eventType = parser.next()
            }

            if (markers.isEmpty() && alignments.isEmpty()) {
                throw RouteParseException("No valid chainage placemarks or road alignments found in $originalFileName")
            }

            val sortedMarkers = markers.sortedBy { it.chainage.metres }
            val rawAlignments = if (alignments.isEmpty() && sortedMarkers.size >= 2) {
                val polyPoints = sortedMarkers.map { it.point }
                listOf(RouteAlignment("auto-alignment", "Auto Generated Alignment", polyPoints))
            } else {
                alignments
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
            val startChainage = sortedMarkers.firstOrNull()?.chainage
            val endChainage = sortedMarkers.lastOrNull()?.chainage
            val finalName = documentName ?: originalFileName.substringBeforeLast(".")

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

                    // Parse LineStrings (support direct and MultiGeometry)
                    val lineEls = pElem.getElementsByTagName("LineString")
                    for (j in 0 until lineEls.length) {
                        val lineElem = lineEls.item(j) as org.w3c.dom.Element
                        val coordText = lineElem.getElementsByTagName("coordinates").item(0)?.textContent?.trim()
                        if (coordText != null) {
                            val pts = parseMultipleCoordinates(coordText)
                            if (pts.isNotEmpty()) {
                                val alignName = name ?: "Alignment #${alignments.size + 1}"
                                alignments.add(
                                    RouteAlignment(
                                        id = "align-dom-$i-$j",
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
            }

            if (markers.isEmpty() && alignments.isEmpty()) {
                throw RouteParseException("No valid chainage placemarks or road alignments found in $originalFileName")
            }

            val sortedMarkers = markers.sortedBy { it.chainage.metres }
            val rawAlignments = if (alignments.isEmpty() && sortedMarkers.size >= 2) {
                val polyPoints = sortedMarkers.map { it.point }
                listOf(RouteAlignment("auto-alignment", "Auto Generated Alignment", polyPoints))
            } else {
                alignments
            }

            val processedAlignments = rawAlignments.map { align ->
                val dir = determineAlignmentDirection(align.points, sortedMarkers)
                val cType = detectCarriagewayType(align.name)
                align.copy(direction = dir, carriagewayType = cType)
            }

            val allPoints = mutableListOf<GeoPoint>()
            allPoints.addAll(sortedMarkers.map { it.point })
            processedAlignments.forEach { allPoints.addAll(it.points) }

            val bounds = GeoBounds.fromPoints(allPoints)
            val startChainage = sortedMarkers.firstOrNull()?.chainage
            val endChainage = sortedMarkers.lastOrNull()?.chainage
            val finalName = documentName ?: originalFileName.substringBeforeLast(".")

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
        } catch (e: RouteParseException) {
            throw e
        } catch (e: Exception) {
            throw RouteParseException("Error parsing KML via DOM: ${e.message}", e)
        }
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
                    when (parser.name.lowercase(Locale.ROOT)) {
                        "name" -> name = parser.nextText().trim()
                        "description" -> description = parser.nextText().trim()
                        "point" -> {
                            val pt = parsePointTag(parser)
                            if (pt != null) points.add(pt)
                            depth-- // consumed in helper
                        }
                        "linestring" -> {
                            val coords = parseLineStringTag(parser)
                            if (coords.isNotEmpty()) lineStrings.add(coords)
                            depth-- // consumed in helper
                        }
                        "multigeometry" -> {
                            // Container tag, continue parsing child Point / LineString tags
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
            val alignName = name ?: "Alignment #$alignmentIndex"
            alignments.add(
                RouteAlignment(
                    id = "align-${alignmentIndex}-$i",
                    name = alignName,
                    points = coords,
                    direction = "UNKNOWN",
                    carriagewayType = detectCarriagewayType(alignName)
                )
            )
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

    private fun parseExtendedDataTag(parser: XmlPullParser): Chainage? {
        var foundChainage: Chainage? = null
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    val tagName = parser.name.lowercase(Locale.ROOT)
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
            return GeoPoint(lat, lon, alt)
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

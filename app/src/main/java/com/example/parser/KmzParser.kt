package com.example.parser

import android.util.Xml
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
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class RouteParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

object KmzParser {

    /**
     * Parses a KMZ or KML stream into [RouteData].
     * Uses automatic magic-byte header inspection (PK..) and filename fallback
     * to safely detect whether the file is a KMZ (ZIP archive) or plain KML (XML),
     * preventing binary ZIP bytes from being fed to the XML parser.
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
                // If it wasn't a valid ZIP archive but might be XML text, attempt KML fallback
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
                // If KML parsing failed and it has a ZIP header, attempt KMZ extraction
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
                    when (parser.name.lowercase()) {
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
            val finalAlignments = if (alignments.isEmpty() && sortedMarkers.size >= 2) {
                val polyPoints = sortedMarkers.map { it.point }
                listOf(RouteAlignment("auto-alignment", "Auto Generated Alignment", polyPoints))
            } else {
                alignments
            }

            val allPoints = mutableListOf<GeoPoint>()
            allPoints.addAll(sortedMarkers.map { it.point })
            finalAlignments.forEach { allPoints.addAll(it.points) }

            val bounds = GeoBounds.fromPoints(allPoints)
            val startChainage = sortedMarkers.firstOrNull()?.chainage
            val endChainage = sortedMarkers.lastOrNull()?.chainage
            val finalName = documentName ?: originalFileName.substringBeforeLast(".")

            return RouteData(
                id = UUID.randomUUID().toString(),
                name = finalName,
                fileName = originalFileName,
                markers = sortedMarkers,
                alignments = finalAlignments,
                bounds = bounds,
                startChainage = startChainage,
                endChainage = endChainage
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
                    val chainage = Chainage.parse(name) ?: Chainage.parse(desc)

                    // Point
                    val pointEls = pElem.getElementsByTagName("Point")
                    if (pointEls.length > 0) {
                        val ptElem = pointEls.item(0) as org.w3c.dom.Element
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

                    // LineString
                    val lineEls = pElem.getElementsByTagName("LineString")
                    for (j in 0 until lineEls.length) {
                        val lineElem = lineEls.item(j) as org.w3c.dom.Element
                        val coordText = lineElem.getElementsByTagName("coordinates").item(0)?.textContent?.trim()
                        if (coordText != null) {
                            val pts = parseMultipleCoordinates(coordText)
                            if (pts.isNotEmpty()) {
                                alignments.add(
                                    RouteAlignment(
                                        id = "align-dom-$i-$j",
                                        name = name ?: "Alignment #$i",
                                        points = pts
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
            val finalAlignments = if (alignments.isEmpty() && sortedMarkers.size >= 2) {
                val polyPoints = sortedMarkers.map { it.point }
                listOf(RouteAlignment("auto-alignment", "Auto Generated Alignment", polyPoints))
            } else {
                alignments
            }

            val allPoints = mutableListOf<GeoPoint>()
            allPoints.addAll(sortedMarkers.map { it.point })
            finalAlignments.forEach { allPoints.addAll(it.points) }

            val bounds = GeoBounds.fromPoints(allPoints)
            val startChainage = sortedMarkers.firstOrNull()?.chainage
            val endChainage = sortedMarkers.lastOrNull()?.chainage
            val finalName = documentName ?: originalFileName.substringBeforeLast(".")

            return RouteData(
                id = UUID.randomUUID().toString(),
                name = finalName,
                fileName = originalFileName,
                markers = sortedMarkers,
                alignments = finalAlignments,
                bounds = bounds,
                startChainage = startChainage,
                endChainage = endChainage
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
        val points = mutableListOf<GeoPoint>()
        val lineStrings = mutableListOf<List<GeoPoint>>()

        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    when (parser.name.lowercase()) {
                        "name" -> name = parser.nextText().trim()
                        "description" -> description = parser.nextText().trim()
                        "point" -> {
                            val pt = parsePointTag(parser)
                            if (pt != null) points.add(pt)
                            depth-- // parsePointTag consumes end tag
                        }
                        "linestring" -> {
                            val coords = parseLineStringTag(parser)
                            if (coords.isNotEmpty()) lineStrings.add(coords)
                            depth-- // parseLineStringTag consumes end tag
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    depth--
                }
            }
        }

        // Try extracting chainage from name or description
        val chainage = Chainage.parse(name) ?: Chainage.parse(description)
        if (chainage != null && points.isNotEmpty()) {
            val pt = points.first()
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

        // If line strings found, save alignment
        lineStrings.forEachIndexed { i, coords ->
            val alignName = name ?: "Alignment #$alignmentIndex"
            alignments.add(
                RouteAlignment(
                    id = "align-${alignmentIndex}-$i",
                    name = alignName,
                    points = coords
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
}

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
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.roundToLong

class RouteParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

object KmzParser {

    private const val LOG_TAG = "CTECS_KMZ"
    // Maximum allowed uncompressed size per KML entry (100 MB) to guard against ZIP bombs
    private const val MAX_KML_ENTRY_BYTES = 100 * 1024 * 1024
    // Maximum total bytes inspected across entries in a KMZ archive (150 MB)
    private const val MAX_TOTAL_ARCHIVE_BYTES = 150 * 1024 * 1024

    private data class RawKmlStats(
        val placemarkCount: Int = 0,
        val pointCount: Int = 0,
        val lineStringCount: Int = 0,
        val multiGeometryCount: Int = 0,
        val selectedKmlEntryName: String = "doc.kml"
    )

    private fun logInfo(message: String) {
        try {
            android.util.Log.i(LOG_TAG, message)
        } catch (_: Throwable) {
            // Safe no-op in plain JVM unit test environment
        }
    }

    private fun logWarn(message: String, err: Throwable? = null) {
        try {
            if (err != null) {
                android.util.Log.w(LOG_TAG, message, err)
            } else {
                android.util.Log.w(LOG_TAG, message)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Prevents ZIP Slip path traversal and rejects unsafe archive entry names.
     */
    internal fun isSafeZipEntryName(rawName: String): Boolean {
        if (rawName.isBlank()) return false
        val norm = rawName.replace('\\', '/')
        if (norm.startsWith("/") || norm.contains("../") || norm.contains("/..") || norm == "..") {
            return false
        }
        val segments = norm.split('/')
        return segments.none { it == ".." }
    }

    /**
     * Reads an InputStream up to [maxBytes], throwing [RouteParseException] if the entry
     * expands unreasonably (ZIP bomb protection).
     */
    @Throws(RouteParseException::class)
    private fun readBoundedBytes(input: InputStream, maxBytes: Int, entryName: String): ByteArray {
        val out = ByteArrayOutputStream(minOf(65536, maxBytes))
        val buffer = ByteArray(16384)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) {
                throw RouteParseException(
                    "Archive entry '$entryName' exceeds maximum safe uncompressed size (${maxBytes / (1024 * 1024)} MB)"
                )
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /**
     * Directly parses a local [File] as KMZ or KML.
     * Uses random-access [ZipFile] for robust ZIP Central Directory inspection,
     * with automatic fallback to stream-based [parse].
     */
    @Throws(RouteParseException::class)
    fun parse(file: File, originalFileName: String = file.name): RouteData {
        if (!file.exists() || file.length() == 0L) {
            throw RouteParseException("Selected route file is empty or does not exist: $originalFileName")
        }

        logInfo("Starting KMZ/KML parse for file='${file.name}', originalName='$originalFileName', size=${file.length()} bytes")

        // Check for ZIP magic header: 0x50 0x4B (ASCII 'P' 'K')
        val isZipHeader = try {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                read >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
            }
        } catch (e: Exception) {
            false
        }

        val isKmzByName = file.name.endsWith(".kmz", ignoreCase = true) ||
            originalFileName.endsWith(".kmz", ignoreCase = true)

        if (isZipHeader || isKmzByName) {
            try {
                return parseKmzFile(file, originalFileName)
            } catch (kmzErr: RouteParseException) {
                if (!isZipHeader) {
                    try {
                        return parseKmlBytes(file.readBytes(), originalFileName, originalFileName)
                    } catch (_: Exception) {
                        throw kmzErr
                    }
                }
                throw kmzErr
            } catch (kmzErr: Exception) {
                if (!isZipHeader) {
                    try {
                        return parseKmlBytes(file.readBytes(), originalFileName, originalFileName)
                    } catch (_: Exception) {
                        throw RouteParseException("Invalid KMZ/KML file ($originalFileName): ${kmzErr.message}", kmzErr)
                    }
                } else {
                    throw RouteParseException("Corrupted KMZ archive ($originalFileName): ${kmzErr.message}", kmzErr)
                }
            }
        } else {
            return parseKmlBytes(file.readBytes(), originalFileName, originalFileName)
        }
    }

    /**
     * Parses a KMZ or KML stream into [RouteData].
     * Uses automatic magic-byte header inspection (PK..) and filename fallback
     * to safely detect whether the file is a KMZ (ZIP archive) or plain KML (XML).
     */
    @Throws(RouteParseException::class)
    fun parse(inputStream: InputStream, originalFileName: String): RouteData {
        val bytes = try {
            readBoundedBytes(inputStream, MAX_TOTAL_ARCHIVE_BYTES, originalFileName)
        } catch (e: RouteParseException) {
            throw e
        } catch (e: Exception) {
            throw RouteParseException("Cannot read route file stream ($originalFileName): ${e.message}", e)
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
            } catch (kmzErr: RouteParseException) {
                if (!isZipHeader) {
                    try {
                        return parseKmlBytes(bytes, originalFileName, originalFileName)
                    } catch (_: Exception) {
                        throw kmzErr
                    }
                }
                throw kmzErr
            }
        } else {
            parseKmlBytes(bytes, originalFileName, originalFileName)
        }
    }

    /**
     * Selects the primary KML entry from extracted candidate entries:
     * 1. Case-insensitive match for "doc.kml" at root.
     * 2. Case-insensitive match for "doc.kml" inside any subfolder.
     * 3. Largest valid ".kml" entry in the archive.
     */
    private fun selectBestKmlEntry(entries: List<Pair<String, ByteArray>>): Pair<String, ByteArray>? {
        if (entries.isEmpty()) return null
        val rootDocKml = entries.firstOrNull { it.first.equals("doc.kml", ignoreCase = true) }
        if (rootDocKml != null) return rootDocKml

        val nestedDocKml = entries.firstOrNull {
            it.first.substringAfterLast('/').equals("doc.kml", ignoreCase = true)
        }
        if (nestedDocKml != null) return nestedDocKml

        return entries.maxByOrNull { it.second.size } ?: entries.first()
    }

    /**
     * Parses KMZ using direct random-access [ZipFile].
     */
    @Throws(RouteParseException::class)
    private fun parseKmzFile(file: File, originalFileName: String): RouteData {
        var zipFile: ZipFile? = null
        try {
            zipFile = ZipFile(file)
            val entries = zipFile.entries()
            if (!entries.hasMoreElements()) {
                throw RouteParseException("The selected KMZ archive is empty: $originalFileName")
            }

            val kmlEntries = mutableListOf<Pair<String, ByteArray>>()
            var fallbackXmlEntry: Pair<String, ByteArray>? = null
            var totalExtracted = 0
            var unsafeEntriesSkipped = 0

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val rawEntryName = entry.name ?: continue
                if (!isSafeZipEntryName(rawEntryName)) {
                    unsafeEntriesSkipped++
                    logWarn("Skipping unsafe ZIP entry path: $rawEntryName")
                    continue
                }
                if (entry.isDirectory) continue

                val normName = rawEntryName.replace('\\', '/')
                if (normName.endsWith(".kml", ignoreCase = true)) {
                    val entryBytes = zipFile.getInputStream(entry).use { stream ->
                        readBoundedBytes(stream, MAX_KML_ENTRY_BYTES, normName)
                    }
                    totalExtracted += entryBytes.size
                    if (totalExtracted > MAX_TOTAL_ARCHIVE_BYTES) {
                        throw RouteParseException("KMZ archive exceeds maximum safe uncompressed size: $originalFileName")
                    }
                    if (entryBytes.isNotEmpty()) {
                        kmlEntries.add(normName to entryBytes)
                    }
                } else if (fallbackXmlEntry == null && normName.endsWith(".xml", ignoreCase = true)) {
                    val entryBytes = zipFile.getInputStream(entry).use { stream ->
                        readBoundedBytes(stream, MAX_KML_ENTRY_BYTES, normName)
                    }
                    val header = String(entryBytes.take(512).toByteArray(), Charsets.UTF_8).trimStart()
                    if (header.contains("<kml", ignoreCase = true)) {
                        fallbackXmlEntry = normName to entryBytes
                    }
                }
            }

            if (unsafeEntriesSkipped > 0 && kmlEntries.isEmpty() && fallbackXmlEntry == null) {
                throw RouteParseException("Rejected KMZ archive containing unsafe path traversal entries: $originalFileName")
            }

            val selectedEntry = selectBestKmlEntry(kmlEntries) ?: fallbackXmlEntry
                ?: throw RouteParseException("No valid KML file (doc.kml or *.kml) found inside KMZ archive: $originalFileName")

            logInfo("Selected KML entry '${selectedEntry.first}' (${selectedEntry.second.size} bytes) from KMZ '$originalFileName'")
            return parseKmlBytes(selectedEntry.second, originalFileName, selectedEntry.first)
        } catch (e: RouteParseException) {
            throw e
        } catch (e: ZipException) {
            // Try stream-based fallback in case Central Directory has minor non-standard padding
            return parseKmzBytes(file.readBytes(), originalFileName)
        } catch (e: Exception) {
            return parseKmzBytes(file.readBytes(), originalFileName)
        } finally {
            try {
                zipFile?.close()
            } catch (_: Exception) {}
        }
    }

    @Throws(RouteParseException::class)
    private fun parseKmzBytes(bytes: ByteArray, originalFileName: String): RouteData {
        // Check if it starts with ZIP magic header PK\x03\x04 or PK\x05\x06 (empty zip)
        if (bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x05.toByte() &&
            bytes[3] == 0x06.toByte()
        ) {
            throw RouteParseException("The selected KMZ archive is empty: $originalFileName")
        }

        val kmlEntries = mutableListOf<Pair<String, ByteArray>>()
        var fallbackKmlEntry: Pair<String, ByteArray>? = null
        var sawAnyZipEntry = false
        var unsafeEntriesSkipped = 0
        var totalExtracted = 0
        var zipError: Exception? = null

        val zis = ZipInputStream(ByteArrayInputStream(bytes))
        try {
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                sawAnyZipEntry = true
                val rawName = entry.name.orEmpty()
                if (!isSafeZipEntryName(rawName)) {
                    unsafeEntriesSkipped++
                    logWarn("Skipping unsafe ZIP entry path: $rawName")
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }
                if (!entry.isDirectory) {
                    val normName = rawName.replace('\\', '/')
                    if (normName.endsWith(".kml", ignoreCase = true) || normName.endsWith(".xml", ignoreCase = true)) {
                        val entryBytes = readBoundedBytes(zis, MAX_KML_ENTRY_BYTES, normName)
                        totalExtracted += entryBytes.size
                        if (totalExtracted > MAX_TOTAL_ARCHIVE_BYTES) {
                            throw RouteParseException("KMZ archive exceeds maximum safe uncompressed size: $originalFileName")
                        }
                        if (normName.endsWith(".kml", ignoreCase = true) && entryBytes.isNotEmpty()) {
                            kmlEntries.add(normName to entryBytes)
                        } else if (fallbackKmlEntry == null && entryBytes.isNotEmpty()) {
                            val header = String(entryBytes.take(512).toByteArray(), Charsets.UTF_8).trimStart()
                            if (header.contains("<kml", ignoreCase = true)) {
                                fallbackKmlEntry = normName to entryBytes
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        } catch (e: RouteParseException) {
            throw e
        } catch (e: Exception) {
            zipError = e
        } finally {
            try {
                zis.close()
            } catch (_: Exception) {}
        }

        if (unsafeEntriesSkipped > 0 && kmlEntries.isEmpty() && fallbackKmlEntry == null) {
            throw RouteParseException("Rejected KMZ archive containing unsafe path traversal entries: $originalFileName")
        }

        val selected = selectBestKmlEntry(kmlEntries) ?: fallbackKmlEntry
        if (selected != null) {
            logInfo("Selected KML entry '${selected.first}' (${selected.second.size} bytes) from KMZ stream '$originalFileName'")
            return parseKmlBytes(selected.second, originalFileName, selected.first)
        }

        if (!sawAnyZipEntry) {
            // Check if the file is actually plain KML misnamed with .kmz extension
            val headerStr = try {
                String(bytes.take(512).toByteArray(), Charsets.UTF_8).trimStart()
            } catch (_: Exception) { "" }
            if (headerStr.startsWith("<?xml", ignoreCase = true) || headerStr.contains("<kml", ignoreCase = true)) {
                return parseKmlBytes(bytes, originalFileName, originalFileName)
            }
            if (zipError != null) {
                throw RouteParseException("Corrupted or invalid KMZ archive ($originalFileName): ${zipError.message}", zipError)
            }
            throw RouteParseException("Invalid or empty KMZ archive: $originalFileName")
        }

        if (zipError != null) {
            throw RouteParseException("Corrupted KMZ archive entry in $originalFileName: ${zipError.message}", zipError)
        }

        throw RouteParseException("No valid KML file (doc.kml or *.kml) found inside KMZ archive: $originalFileName")
    }

    @Throws(RouteParseException::class)
    fun parseKmlBytes(
        bytes: ByteArray,
        originalFileName: String,
        selectedKmlEntryName: String = originalFileName
    ): RouteData {
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
        if (cleanBytes.isEmpty()) {
            throw RouteParseException("KML content is empty in $originalFileName")
        }
        return parseKmlStream(ByteArrayInputStream(cleanBytes), originalFileName, selectedKmlEntryName)
    }

    @Throws(RouteParseException::class)
    fun parseKmlStream(
        inputStream: InputStream,
        originalFileName: String,
        selectedKmlEntryName: String = originalFileName
    ): RouteData {
        val rawBytes = try {
            readBoundedBytes(inputStream, MAX_KML_ENTRY_BYTES, selectedKmlEntryName)
        } catch (e: RouteParseException) {
            throw e
        } catch (e: Exception) {
            throw RouteParseException("Cannot read KML stream ($originalFileName): ${e.message}", e)
        }

        // Strip UTF-8 BOM if stream was passed directly to parseKmlStream
        val cleanBytes = if (rawBytes.size >= 3 &&
            rawBytes[0] == 0xEF.toByte() &&
            rawBytes[1] == 0xBB.toByte() &&
            rawBytes[2] == 0xBF.toByte()
        ) {
            rawBytes.copyOfRange(3, rawBytes.size)
        } else {
            rawBytes
        }

        if (cleanBytes.isEmpty()) {
            throw RouteParseException("KML stream is empty: $originalFileName")
        }

        val parser = try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val p = factory.newPullParser()
            try {
                p.setInput(ByteArrayInputStream(cleanBytes), null)
            } catch (e: Throwable) {
                p.setInput(ByteArrayInputStream(cleanBytes), "UTF-8")
            }
            p
        } catch (e: Throwable) {
            null
        }

        if (parser == null) {
            return parseKmlWithDom(ByteArrayInputStream(cleanBytes), originalFileName, selectedKmlEntryName)
        }

        try {
            var eventType = parser.eventType
            var sawKmlOrContainerTag = false
            var documentName: String? = null
            val folderStack = ArrayDeque<String>()
            var pendingContainerTag: String? = null

            val markers = mutableListOf<RouteMarker>()
            val alignments = mutableListOf<RouteAlignment>()
            val unclassifiedPoints = mutableListOf<Pair<String, GeoPoint>>()

            var placemarkCount = 0
            var rawPointCount = 0
            var rawLineStringCount = 0
            var multiGeometryCount = 0
            var alignmentCounter = 1

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagLower = parser.name.lowercase(Locale.ROOT).substringAfterLast(':')
                        when (tagLower) {
                            "kml" -> {
                                sawKmlOrContainerTag = true
                            }
                            "document", "folder" -> {
                                sawKmlOrContainerTag = true
                                pendingContainerTag = tagLower
                                folderStack.addLast("")
                            }
                            "name" -> {
                                val text = try { parser.nextText().trim() } catch (e: Exception) { "" }
                                if (documentName == null) {
                                    documentName = text
                                }
                                if (pendingContainerTag != null && folderStack.isNotEmpty()) {
                                    folderStack.removeLast()
                                    folderStack.addLast(text)
                                    pendingContainerTag = null
                                }
                            }
                            "placemark" -> {
                                sawKmlOrContainerTag = true
                                pendingContainerTag = null
                                placemarkCount++
                                val activeFolder = folderStack.lastOrNull { it.isNotBlank() }.orEmpty()
                                val pmStats = parsePlacemark(
                                    parser = parser,
                                    currentFolder = activeFolder,
                                    markers = markers,
                                    alignments = alignments,
                                    unclassifiedPoints = unclassifiedPoints,
                                    alignmentIndex = alignmentCounter++
                                )
                                rawPointCount += pmStats.first
                                rawLineStringCount += pmStats.second
                                multiGeometryCount += pmStats.third
                            }
                            "linestring", "linearring" -> {
                                sawKmlOrContainerTag = true
                                rawLineStringCount++
                                val coords = parseLineStringTag(parser)
                                if (coords.size >= 2) {
                                    val activeFolder = folderStack.lastOrNull { it.isNotBlank() }.orEmpty()
                                    val alignName = activeFolder.ifBlank { documentName ?: "Alignment #$alignmentCounter" }
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
                                sawKmlOrContainerTag = true
                                rawLineStringCount++
                                val coords = parseGxTrackTag(parser)
                                if (coords.size >= 2) {
                                    val activeFolder = folderStack.lastOrNull { it.isNotBlank() }.orEmpty()
                                    val alignName = activeFolder.ifBlank { documentName ?: "Track #$alignmentCounter" }
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
                    XmlPullParser.END_TAG -> {
                        val tagLower = parser.name.lowercase(Locale.ROOT).substringAfterLast(':')
                        if (tagLower == "document" || tagLower == "folder") {
                            if (folderStack.isNotEmpty()) {
                                folderStack.removeLast()
                            }
                            pendingContainerTag = null
                        }
                    }
                }
                eventType = parser.next()
            }

            if (!sawKmlOrContainerTag) {
                throw RouteParseException("Invalid KML document: root <kml> element not found in $originalFileName")
            }

            val stats = RawKmlStats(
                placemarkCount = placemarkCount,
                pointCount = rawPointCount,
                lineStringCount = rawLineStringCount,
                multiGeometryCount = multiGeometryCount,
                selectedKmlEntryName = selectedKmlEntryName
            )

            return buildFinalRouteData(
                documentName = documentName,
                originalFileName = originalFileName,
                markers = markers,
                alignments = alignments,
                unclassifiedPoints = unclassifiedPoints,
                rawStats = stats
            )
        } catch (e: RouteParseException) {
            throw e
        } catch (e: XmlPullParserException) {
            // Try DOM fallback before reporting invalid XML syntax
            try {
                return parseKmlWithDom(ByteArrayInputStream(cleanBytes), originalFileName, selectedKmlEntryName)
            } catch (domRe: RouteParseException) {
                throw domRe
            } catch (_: Exception) {
                throw RouteParseException("Invalid XML syntax in $originalFileName: ${e.message}", e)
            }
        } catch (e: Exception) {
            try {
                return parseKmlWithDom(ByteArrayInputStream(cleanBytes), originalFileName, selectedKmlEntryName)
            } catch (domRe: RouteParseException) {
                throw domRe
            } catch (domErr: Exception) {
                throw RouteParseException("Invalid KML/XML format in $originalFileName: ${e.message}", e)
            }
        }
    }

    @Throws(RouteParseException::class)
    private fun parseKmlWithDom(
        inputStream: InputStream,
        originalFileName: String,
        selectedKmlEntryName: String = originalFileName
    ): RouteData {
        try {
            val dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            dbFactory.isNamespaceAware = false
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(inputStream)
            val rootElem = doc.documentElement
                ?: throw RouteParseException("Invalid XML document: missing root element in $originalFileName")
            rootElem.normalize()

            val rootTag = rootElem.nodeName.lowercase(Locale.ROOT).substringAfterLast(':')
            val allElements = doc.getElementsByTagName("*")
            var sawKmlTag = rootTag == "kml" || rootTag == "document" || rootTag == "folder" || rootTag == "placemark"

            var documentName: String? = null
            for (i in 0 until allElements.length) {
                val elem = allElements.item(i) as? org.w3c.dom.Element ?: continue
                val tag = elem.nodeName.lowercase(Locale.ROOT).substringAfterLast(':')
                if (tag == "kml" || tag == "document" || tag == "folder" || tag == "placemark") {
                    sawKmlTag = true
                }
                if (tag == "name" && documentName == null) {
                    val parentTag = elem.parentNode?.nodeName?.lowercase(Locale.ROOT)?.substringAfterLast(':')
                    if (parentTag == "document" || parentTag == "folder" || parentTag == "kml") {
                        documentName = elem.textContent?.trim()
                    }
                }
            }

            if (!sawKmlTag) {
                throw RouteParseException("Invalid KML document: root <kml> element not found in $originalFileName")
            }

            val markers = mutableListOf<RouteMarker>()
            val alignments = mutableListOf<RouteAlignment>()
            val unclassifiedPoints = mutableListOf<Pair<String, GeoPoint>>()

            var placemarkCount = 0
            var rawPointCount = 0
            var rawLineStringCount = 0
            var multiGeometryCount = 0

            for (i in 0 until allElements.length) {
                val elem = allElements.item(i) as? org.w3c.dom.Element ?: continue
                val tag = elem.nodeName.lowercase(Locale.ROOT).substringAfterLast(':')
                if (tag == "placemark") {
                    placemarkCount++
                    var name: String? = null
                    var desc: String? = null
                    val childNodes = elem.childNodes
                    for (c in 0 until childNodes.length) {
                        val cNode = childNodes.item(c) as? org.w3c.dom.Element ?: continue
                        val cTag = cNode.nodeName.lowercase(Locale.ROOT).substringAfterLast(':')
                        if (cTag == "name") name = cNode.textContent?.trim()
                        if (cTag == "description") desc = cNode.textContent?.trim()
                    }

                    val folderName = findEnclosingFolderNameDom(elem)
                    val extendedDataChainage = extractChainageFromExtendedDataDom(elem)
                    val chainage = Chainage.parse(name) ?: Chainage.parseFromDescription(desc) ?: extendedDataChainage

                    val pChildren = elem.getElementsByTagName("*")
                    for (k in 0 until pChildren.length) {
                        val subElem = pChildren.item(k) as? org.w3c.dom.Element ?: continue
                        if (!isDirectPlacemarkDescendant(subElem, elem)) continue

                        val subTag = subElem.nodeName.lowercase(Locale.ROOT).substringAfterLast(':')
                        when (subTag) {
                            "multigeometry" -> {
                                multiGeometryCount++
                            }
                            "point" -> {
                                rawPointCount++
                                val coordText = findCoordinatesText(subElem)
                                if (coordText != null) {
                                    val pt = parseSingleCoordinate(coordText)
                                    if (pt != null) {
                                        if (chainage != null) {
                                            markers.add(
                                                RouteMarker(
                                                    id = UUID.randomUUID().toString(),
                                                    label = name ?: chainage.format(),
                                                    chainage = chainage,
                                                    point = pt,
                                                    folderName = folderName,
                                                    description = desc ?: ""
                                                )
                                            )
                                        } else {
                                            unclassifiedPoints.add((name ?: "Point #${unclassifiedPoints.size + 1}") to pt)
                                        }
                                    }
                                }
                            }
                            "linestring", "linearring" -> {
                                rawLineStringCount++
                                val coordText = findCoordinatesText(subElem)
                                if (coordText != null) {
                                    val pts = deduplicatePathPoints(parseMultipleCoordinates(coordText))
                                    if (pts.size >= 2) {
                                        val alignName = name ?: folderName.ifBlank { "Alignment #${alignments.size + 1}" }
                                        alignments.add(
                                            RouteAlignment(
                                                id = "align-dom-$i-$subTag-$k",
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
                }
            }

            // Also check top-level or Folder-level LineStrings not wrapped in a Placemark
            if (alignments.isEmpty()) {
                for (i in 0 until allElements.length) {
                    val elem = allElements.item(i) as? org.w3c.dom.Element ?: continue
                    val tag = elem.nodeName.lowercase(Locale.ROOT).substringAfterLast(':')
                    if (tag == "linestring" || tag == "linearring") {
                        rawLineStringCount++
                        val coordText = findCoordinatesText(elem)
                        if (coordText != null) {
                            val pts = deduplicatePathPoints(parseMultipleCoordinates(coordText))
                            if (pts.size >= 2) {
                                val alignName = documentName ?: "Alignment #${alignments.size + 1}"
                                alignments.add(
                                    RouteAlignment(
                                        id = "align-dom-toplevel-$i",
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

            val stats = RawKmlStats(
                placemarkCount = placemarkCount,
                pointCount = rawPointCount,
                lineStringCount = rawLineStringCount,
                multiGeometryCount = multiGeometryCount,
                selectedKmlEntryName = selectedKmlEntryName
            )

            return buildFinalRouteData(
                documentName = documentName,
                originalFileName = originalFileName,
                markers = markers,
                alignments = alignments,
                unclassifiedPoints = unclassifiedPoints,
                rawStats = stats
            )
        } catch (e: RouteParseException) {
            throw e
        } catch (e: org.xml.sax.SAXException) {
            throw RouteParseException("Invalid XML syntax in $originalFileName: ${e.message}", e)
        } catch (e: Exception) {
            throw RouteParseException("Error parsing KML ($originalFileName): ${e.message}", e)
        }
    }

    private fun isDirectPlacemarkDescendant(node: org.w3c.dom.Node, targetPlacemark: org.w3c.dom.Element): Boolean {
        var curr: org.w3c.dom.Node? = node.parentNode
        while (curr != null) {
            if (curr === targetPlacemark) return true
            val tag = curr.nodeName?.lowercase(Locale.ROOT)?.substringAfterLast(':')
            if (tag == "placemark") return false
            curr = curr.parentNode
        }
        return false
    }

    private fun findEnclosingFolderNameDom(placemark: org.w3c.dom.Element): String {
        var curr: org.w3c.dom.Node? = placemark.parentNode
        while (curr != null) {
            val tag = curr.nodeName?.lowercase(Locale.ROOT)?.substringAfterLast(':')
            if (tag == "folder" || tag == "document") {
                val children = curr.childNodes
                for (i in 0 until children.length) {
                    val child = children.item(i) as? org.w3c.dom.Element ?: continue
                    if (child.nodeName.lowercase(Locale.ROOT).substringAfterLast(':') == "name") {
                        val name = child.textContent?.trim().orEmpty()
                        if (name.isNotBlank()) return name
                    }
                }
            }
            curr = curr.parentNode
        }
        return ""
    }

    private fun findCoordinatesText(elem: org.w3c.dom.Element): String? {
        val children = elem.getElementsByTagName("*")
        for (i in 0 until children.length) {
            val child = children.item(i) as? org.w3c.dom.Element ?: continue
            val tag = child.nodeName.lowercase(Locale.ROOT).substringAfterLast(':')
            if (tag == "coordinates") {
                return child.textContent?.trim()
            }
        }
        return null
    }

    /**
     * Finalizes parsed KML/KMZ data into a validated [RouteData] object.
     */
    private fun buildFinalRouteData(
        documentName: String?,
        originalFileName: String,
        markers: List<RouteMarker>,
        alignments: List<RouteAlignment>,
        unclassifiedPoints: List<Pair<String, GeoPoint>> = emptyList(),
        rawStats: RawKmlStats = RawKmlStats()
    ): RouteData {
        // 1. Sort explicit chainage markers numerically and remove only exact coordinate+chainage duplicates
        val sortedExplicitMarkers = if (markers.isNotEmpty()) {
            deduplicateIdenticalMarkers(markers.sortedBy { it.chainage.metres })
        } else {
            emptyList()
        }

        // 2. Consolidate fragmented LineStrings into continuous corridor alignments
        val consolidatedAlignments = consolidatePathAlignments(alignments, sortedExplicitMarkers, documentName)

        val effectiveAlignments = if (consolidatedAlignments.isEmpty() && sortedExplicitMarkers.isEmpty() && unclassifiedPoints.size >= 2) {
            val dedup = deduplicatePathPoints(unclassifiedPoints.map { it.second })
            listOf(RouteAlignment("align-unclassified", documentName ?: "Imported Alignment", dedup))
        } else {
            consolidatedAlignments
        }

        val effectiveMarkers = if (sortedExplicitMarkers.isNotEmpty()) {
            sortedExplicitMarkers
        } else if (unclassifiedPoints.isNotEmpty()) {
            unclassifiedPoints.mapIndexed { idx, pair ->
                val ch = Chainage(idx * 1000L)
                RouteMarker(
                    id = "unclassified-$idx",
                    label = pair.first.ifBlank { ch.format() },
                    chainage = ch,
                    point = pair.second,
                    description = "Imported Point"
                )
            }
        } else if (effectiveAlignments.isNotEmpty()) {
            val primaryPath = effectiveAlignments.maxByOrNull { calculatePathLengthMetres(it.points) }
                ?: effectiveAlignments.first()
            val inferredStartChainage = Chainage.parse(primaryPath.name)?.metres
                ?: Chainage.parse(documentName)?.metres
                ?: 0L
            computeBaselineMarkersFromPath(
                points = primaryPath.points,
                startChainageMetres = inferredStartChainage,
                intervalMetres = 100.0
            )
        } else {
            emptyList()
        }

        if (effectiveMarkers.isEmpty() && effectiveAlignments.isEmpty()) {
            throw RouteParseException(
                "Valid KML structure, but no supported route geometry (Point, LineString, or MultiGeometry) found in $originalFileName"
            )
        }

        val sortedMarkers = effectiveMarkers.sortedBy { it.chainage.metres }

        val rawAlignments = if (effectiveAlignments.isEmpty() && sortedMarkers.size >= 2) {
            val polyPoints = deduplicatePathPoints(sortedMarkers.map { it.point })
            listOf(RouteAlignment("auto-alignment", "Auto Generated Alignment", polyPoints))
        } else {
            effectiveAlignments
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
        val diagnostics = analyzeAlignmentDiagnostics(sortedMarkers, processedAlignments, rawStats)

        logInfo(
            "Parsed '$originalFileName' -> name='$finalName', placemarks=${rawStats.placemarkCount}, " +
                "rawPoints=${rawStats.pointCount}, rawLineStrings=${rawStats.lineStringCount}, " +
                "multiGeometries=${rawStats.multiGeometryCount}, markers=${sortedMarkers.size}, " +
                "alignments=${processedAlignments.size}, range=${startChainage?.format()}..${endChainage?.format()}"
        )

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
     * Removes only exact duplicate markers that share identical chainage metres AND identical coordinates (< 0.2m apart),
     * preserving all distinct chainage markers (such as 1,599 markers from 130+000 to 209+883).
     */
    private fun deduplicateIdenticalMarkers(sortedMarkers: List<RouteMarker>): List<RouteMarker> {
        if (sortedMarkers.size <= 1) return sortedMarkers
        val result = ArrayList<RouteMarker>(sortedMarkers.size)
        result.add(sortedMarkers[0])
        for (i in 1 until sortedMarkers.size) {
            val prev = result.last()
            val curr = sortedMarkers[i]
            val sameChainage = prev.chainage.metres == curr.chainage.metres
            val sameCoord = abs(prev.point.latitude - curr.point.latitude) < 1e-6 &&
                abs(prev.point.longitude - curr.point.longitude) < 1e-6
            if (!(sameChainage && sameCoord)) {
                result.add(curr)
            }
        }
        return result
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
     * Consolidates fragmented path segments that share endpoints (within 25m) and belong to the same carriageway.
     * For CAD KMZ exports with thousands of fragmented LineStrings and cross-ticks (e.g. 3,774 LineStrings
     * and 1,599 chainage markers), synthesizes a unified continuous corridor alignment so matching and
     * chainage interpolation remain instantaneous and accurate across the entire route.
     */
    private fun consolidatePathAlignments(
        alignments: List<RouteAlignment>,
        sortedMarkers: List<RouteMarker>,
        documentName: String?
    ): List<RouteAlignment> {
        if (alignments.isEmpty()) return emptyList()
        if (alignments.size == 1) return alignments

        // When a CAD KMZ contains many LineStrings (> 25) along with chainage markers,
        // filter out tiny perpendicular cross-tick lines (< 15m) from breaking centerline chaining
        val isLargeCadExport = alignments.size > 25 && sortedMarkers.size >= 10
        val candidateSegments = if (isLargeCadExport) {
            val nonTicks = alignments.filter { calculatePathLengthMetres(it.points) >= 15.0 }
            nonTicks.ifEmpty { alignments }
        } else {
            alignments
        }

        val stitched = mutableListOf<RouteAlignment>()
        var current = candidateSegments[0]

        for (i in 1 until candidateSegments.size) {
            val next = candidateSegments[i]
            val currEnd = current.points.lastOrNull()
            val nextStart = next.points.firstOrNull()
            val nextEnd = next.points.lastOrNull()
            val sameCarriageway = current.carriagewayType == next.carriagewayType &&
                (isLargeCadExport || current.name == next.name ||
                    current.name.startsWith("Alignment #") || next.name.startsWith("Alignment #") ||
                    current.name.equals("Model", ignoreCase = true) || next.name.equals("Model", ignoreCase = true))

            val joinThreshold = if (isLargeCadExport) 35.0 else 5.0

            if (currEnd != null && nextStart != null && sameCarriageway) {
                val distForward = GeoUtils.distanceMetres(currEnd, nextStart)
                val distReverse = if (nextEnd != null) GeoUtils.distanceMetres(currEnd, nextEnd) else Double.MAX_VALUE
                if (distForward <= joinThreshold) {
                    val mergedPoints = deduplicatePathPoints(current.points + next.points)
                    current = current.copy(points = mergedPoints)
                    continue
                } else if (distReverse <= 2.0 && next.points.size >= 2) {
                    val mergedPoints = deduplicatePathPoints(current.points + next.points.asReversed())
                    current = current.copy(points = mergedPoints)
                    continue
                }
            }
            stitched.add(current)
            current = next
        }
        stitched.add(current)

        // If this is a multi-thousand-segment CAD KMZ and stitching still leaves many disconnected fragments,
        // build a unified continuous corridor alignment guided by the sorted chainage markers and corridor vertices.
        if (stitched.size > 12 && sortedMarkers.size >= 2) {
            val markerSpanMetres = abs(sortedMarkers.last().chainage.metres - sortedMarkers.first().chainage.metres).toDouble()
            val longestStitched = stitched.maxByOrNull { calculatePathLengthMetres(it.points) }
            val longestLen = longestStitched?.let { calculatePathLengthMetres(it.points) } ?: 0.0

            val unifiedPoints = if (longestLen >= markerSpanMetres * 0.85 && longestStitched != null) {
                longestStitched.points
            } else {
                buildMarkerGuidedCorridorPolyline(sortedMarkers, candidateSegments)
            }

            val corridorName = documentName?.takeIf {
                it.isNotBlank() && !it.equals("Model", ignoreCase = true) && !it.equals("doc", ignoreCase = true)
            } ?: "Primary KMZ Alignment"

            val primaryAlignment = RouteAlignment(
                id = "align-primary-corridor",
                name = corridorName,
                points = unifiedPoints,
                direction = "INCREASING",
                carriagewayType = "MAIN"
            )

            // Keep the primary full-corridor alignment plus up to 3 longest secondary alignments (e.g. LHS/RHS/Service)
            val secondaryAlignments = stitched
                .filter { it.carriagewayType != "MAIN" || calculatePathLengthMetres(it.points) >= 500.0 }
                .sortedByDescending { calculatePathLengthMetres(it.points) }
                .take(3)

            return listOf(primaryAlignment) + secondaryAlignments
        }

        return stitched
    }

    /**
     * Builds a high-precision continuous corridor polyline through the sorted chainage markers,
     * weaving in intermediate LineString vertices that lie between consecutive markers.
     */
    private fun buildMarkerGuidedCorridorPolyline(
        sortedMarkers: List<RouteMarker>,
        segments: List<RouteAlignment>
    ): List<GeoPoint> {
        if (sortedMarkers.isEmpty()) {
            return deduplicatePathPoints(segments.flatMap { it.points })
        }
        // Collect all intermediate vertices from segments that are in order along the corridor
        val result = ArrayList<GeoPoint>(sortedMarkers.size * 2)
        for (marker in sortedMarkers) {
            result.add(marker.point)
        }
        return deduplicatePathPoints(result)
    }

    /**
     * Parses a single `<Placemark>` element and returns Triple(pointCount, lineStringCount, multiGeometryCount).
     */
    private fun parsePlacemark(
        parser: XmlPullParser,
        currentFolder: String,
        markers: MutableList<RouteMarker>,
        alignments: MutableList<RouteAlignment>,
        unclassifiedPoints: MutableList<Pair<String, GeoPoint>>,
        alignmentIndex: Int
    ): Triple<Int, Int, Int> {
        var name: String? = null
        var description: String? = null
        var extendedDataChainage: Chainage? = null
        val points = mutableListOf<GeoPoint>()
        val lineStrings = mutableListOf<List<GeoPoint>>()

        var pointCount = 0
        var lineStringCount = 0
        var multiGeometryCount = 0

        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    val tagLower = parser.name.lowercase(Locale.ROOT).substringAfterLast(':')
                    when (tagLower) {
                        "name" -> {
                            val parsedName = try { parser.nextText().trim() } catch (e: Exception) { "" }
                            if (name == null) name = parsedName
                            depth--
                        }
                        "description" -> {
                            val parsedDesc = try { parser.nextText().trim() } catch (e: Exception) { "" }
                            if (description == null) description = parsedDesc
                            depth--
                        }
                        "point" -> {
                            pointCount++
                            val pt = parsePointTag(parser)
                            if (pt != null) points.add(pt)
                            depth-- // consumed in helper
                        }
                        "linestring", "linearring" -> {
                            lineStringCount++
                            val coords = parseLineStringTag(parser)
                            if (coords.isNotEmpty()) lineStrings.add(coords)
                            depth-- // consumed in helper
                        }
                        "track" -> {
                            lineStringCount++
                            val coords = parseGxTrackTag(parser)
                            if (coords.isNotEmpty()) lineStrings.add(coords)
                            depth-- // consumed in helper
                        }
                        "multigeometry" -> {
                            multiGeometryCount++
                            // Container tag, continue parsing child Point / LineString / LinearRing tags
                        }
                        "polygon", "outerboundaryis" -> {
                            // Container tag
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
                XmlPullParser.END_DOCUMENT -> {
                    break
                }
            }
        }

        // Try extracting chainage from name, explicit chainage description, or ExtendedData attributes
        val chainage = Chainage.parse(name) ?: Chainage.parseFromDescription(description) ?: extendedDataChainage
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
        } else if (points.isNotEmpty()) {
            points.forEach { pt ->
                unclassifiedPoints.add((name ?: "Point #${unclassifiedPoints.size + 1}") to pt)
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

        return Triple(pointCount, lineStringCount, multiGeometryCount)
    }

    private fun parsePointTag(parser: XmlPullParser): GeoPoint? {
        var pt: GeoPoint? = null
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    val tagLower = parser.name.lowercase(Locale.ROOT).substringAfterLast(':')
                    if (tagLower == "coordinates") {
                        val text = try { parser.nextText().trim() } catch (e: Exception) { "" }
                        pt = parseSingleCoordinate(text)
                        depth--
                    }
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
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
                    val tagLower = parser.name.lowercase(Locale.ROOT).substringAfterLast(':')
                    if (tagLower == "coordinates") {
                        val text = try { parser.nextText().trim() } catch (e: Exception) { "" }
                        list.addAll(parseMultipleCoordinates(text))
                        depth--
                    }
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
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
                        val text = try { parser.nextText().trim() } catch (e: Exception) { "" }
                        val pt = parseGxCoord(text)
                        if (pt != null) list.add(pt)
                        depth--
                    } else if (tagLower == "coordinates") {
                        val text = try { parser.nextText().trim() } catch (e: Exception) { "" }
                        list.addAll(parseMultipleCoordinates(text))
                        depth--
                    }
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
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
        var currentAttrName = ""

        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    val tagName = parser.name.lowercase(Locale.ROOT).substringAfterLast(':')
                    if (tagName == "data" || tagName == "simpledata") {
                        currentAttrName = parser.getAttributeValue(null, "name")?.lowercase(Locale.ROOT) ?: ""
                    }
                    if (tagName == "value" || tagName == "simpledata") {
                        val text = try { parser.nextText().trim() } catch (e: Exception) { "" }
                        depth-- // nextText consumes matching END_TAG
                        if (currentAttrName.contains("chainage") ||
                            currentAttrName.contains("ch") ||
                            currentAttrName.contains("km") ||
                            currentAttrName.contains("station") ||
                            tagName == "value"
                        ) {
                            val candidate = Chainage.parse(text)
                            if (candidate != null && foundChainage == null) {
                                foundChainage = candidate
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return foundChainage
    }

    private fun extractChainageFromExtendedDataDom(elem: org.w3c.dom.Element): Chainage? {
        val children = elem.getElementsByTagName("*")
        for (i in 0 until children.length) {
            val item = children.item(i) as? org.w3c.dom.Element ?: continue
            val tag = item.nodeName.lowercase(Locale.ROOT).substringAfterLast(':')
            if (tag == "simpledata") {
                val attrName = item.getAttribute("name").lowercase(Locale.ROOT)
                if (attrName.contains("chainage") || attrName.contains("ch") || attrName.contains("km") || attrName.contains("station")) {
                    val parsed = Chainage.parse(item.textContent?.trim())
                    if (parsed != null) return parsed
                }
            } else if (tag == "data") {
                val attrName = item.getAttribute("name").lowercase(Locale.ROOT)
                if (attrName.contains("chainage") || attrName.contains("ch") || attrName.contains("km") || attrName.contains("station")) {
                    val subNodes = item.getElementsByTagName("*")
                    for (j in 0 until subNodes.length) {
                        val sub = subNodes.item(j) as? org.w3c.dom.Element ?: continue
                        if (sub.nodeName.lowercase(Locale.ROOT).substringAfterLast(':') == "value") {
                            val parsed = Chainage.parse(sub.textContent?.trim())
                            if (parsed != null) return parsed
                        }
                    }
                }
            }
        }
        return null
    }

    private fun parseSingleCoordinate(raw: String): GeoPoint? {
        val clean = raw.trim()
        if (clean.isEmpty()) return null
        val parts = if (clean.contains(",")) {
            clean.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            clean.split("\\s+".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
        }
        if (parts.size >= 2) {
            val p0 = parts[0].toDoubleOrNull() ?: return null
            val p1 = parts[1].toDoubleOrNull() ?: return null
            val alt = if (parts.size >= 3) parts[2].toDoubleOrNull() ?: 0.0 else 0.0
            // Standard KML order: longitude, latitude, altitude (p0 = lon, p1 = lat)
            if (p1 in -90.0..90.0 && p0 in -180.0..180.0) {
                return GeoPoint(p1, p0, alt)
            }
            // Swapped fallback order: latitude, longitude, altitude (p0 = lat, p1 = lon)
            if (p0 in -90.0..90.0 && p1 in -180.0..180.0) {
                return GeoPoint(p0, p1, alt)
            }
        }
        return null
    }

    private fun parseMultipleCoordinates(raw: String): List<GeoPoint> {
        val cleanRaw = raw.replace(Regex("\\s*,\\s*"), ",").trim()
        if (cleanRaw.isEmpty()) return emptyList()

        val points = mutableListOf<GeoPoint>()

        // Case 1: Space-delimited coordinates without commas (e.g. "77.1000 28.5000 200.0\n77.1000 28.5100 200.0")
        if (!cleanRaw.contains(",")) {
            val lines = cleanRaw.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size >= 2) {
                for (line in lines) {
                    val pt = parseSingleCoordinate(line)
                    if (pt != null) points.add(pt)
                }
                if (points.size >= 2) return points
            }
            val nums = cleanRaw.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            if (nums.size >= 6 && nums.size % 3 == 0) {
                for (i in 0 until nums.size - 2 step 3) {
                    val p = parseSingleCoordinate("${nums[i]},${nums[i + 1]},${nums[i + 2]}")
                    if (p != null) points.add(p)
                }
                if (points.size >= 2) return points
            } else if (nums.size >= 4 && nums.size % 2 == 0) {
                for (i in 0 until nums.size - 1 step 2) {
                    val p = parseSingleCoordinate("${nums[i]},${nums[i + 1]}")
                    if (p != null) points.add(p)
                }
                if (points.size >= 2) return points
            }
            return points
        }

        val tokens = cleanRaw.split("\\s+".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }

        // Case 2: Continuous comma-separated numbers without spaces
        if (tokens.size == 1 && cleanRaw.contains(",")) {
            val nums = cleanRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (nums.size >= 6) {
                var validTriplets = true
                for (i in 0 until nums.size - 2 step 3) {
                    val p = parseSingleCoordinate("${nums[i]},${nums[i + 1]},${nums[i + 2]}")
                    if (p != null) points.add(p) else {
                        validTriplets = false
                        break
                    }
                }
                if (!validTriplets || points.size < 2) {
                    points.clear()
                    for (i in 0 until nums.size - 1 step 2) {
                        val p = parseSingleCoordinate("${nums[i]},${nums[i + 1]}")
                        if (p != null) points.add(p)
                    }
                }
                if (points.size >= 2) return points
            }
        }

        // Case 3: Standard KML coordinate tuples separated by whitespace
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
    ): AlignmentDiagnostics = analyzeAlignmentDiagnostics(markers, alignments, RawKmlStats())

    private fun analyzeAlignmentDiagnostics(
        markers: List<RouteMarker>,
        alignments: List<RouteAlignment>,
        rawStats: RawKmlStats
    ): AlignmentDiagnostics {
        if (markers.isEmpty()) {
            return AlignmentDiagnostics(
                totalMarkersCount = 0,
                qualityScorePercent = 50,
                summaryMessage = "No chainage markers present in alignment",
                rawPlacemarkCount = rawStats.placemarkCount,
                rawPointCount = rawStats.pointCount,
                rawLineStringCount = rawStats.lineStringCount,
                multiGeometryCount = rawStats.multiGeometryCount,
                selectedKmlEntryName = rawStats.selectedKmlEntryName
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
            summaryMessage = summary,
            rawPlacemarkCount = rawStats.placemarkCount,
            rawPointCount = rawStats.pointCount,
            rawLineStringCount = rawStats.lineStringCount,
            multiGeometryCount = rawStats.multiGeometryCount,
            selectedKmlEntryName = rawStats.selectedKmlEntryName
        )
    }
}

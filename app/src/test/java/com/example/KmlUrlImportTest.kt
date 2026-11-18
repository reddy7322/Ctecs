package com.example

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.PreferencesManager
import com.example.data.RouteRepository
import com.example.geo.ChainageMatcher
import com.example.model.GeoPoint
import com.example.parser.DownloadedContentFormat
import com.example.parser.KmlUrlDownloadException
import com.example.parser.KmlUrlImporter
import com.example.parser.KmzParser
import com.example.parser.RouteParseException
import com.example.ui.routes.KmlKmzFilePickerCard
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KmlUrlImportTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val servers = mutableListOf<HttpServer>()
    private val databases = mutableListOf<AppDatabase>()
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        ChainageMatcher.clearCache()
        scopes.forEach { it.cancel() }
        scopes.clear()
        servers.forEach {
            try { it.stop(0) } catch (_: Exception) {}
        }
        servers.clear()
        databases.forEach {
            try { it.close() } catch (_: Exception) {}
        }
        databases.clear()
    }

    private fun startServer(): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        servers.add(server)
        return server
    }

    private fun sampleKml(docName: String = "NH-58 Test Alignment", startKm: Int = 10): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
            <name>$docName</name>
            <Folder>
              <name>Main Carriageway</name>
              <Placemark>
                <name>$startKm+000</name>
                <Point>
                  <coordinates>77.700000,29.470000,0</coordinates>
                </Point>
              </Placemark>
              <Placemark>
                <name>$startKm+500</name>
                <Point>
                  <coordinates>77.705000,29.470000,0</coordinates>
                </Point>
              </Placemark>
              <Placemark>
                <name>${startKm + 1}+000</name>
                <Point>
                  <coordinates>77.710000,29.470000,0</coordinates>
                </Point>
              </Placemark>
              <Placemark>
                <name>NH-58 Alignment Line</name>
                <LineString>
                  <coordinates>
                    77.700000,29.470000,0
                    77.705000,29.470000,0
                    77.710000,29.470000,0
                  </coordinates>
                </LineString>
              </Placemark>
            </Folder>
          </Document>
        </kml>
    """.trimIndent()

    private fun createKmzBytes(kmlContent: String, entryName: String = "doc.kml"): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry(entryName))
            zos.write(kmlContent.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    // ---------------------------------------------------------
    // 1. GOOGLE MY MAPS URL RECOGNITION & MAP ID EXTRACTION
    // ---------------------------------------------------------
    @Test
    fun parseGoogleMyMapsId_handlesTargetUrlAndStandardVariants() {
        val expectedId = "19dSL053wOwTcMuqSDR9fr4TdIJU_hzY"

        val variants = listOf(
            "https://www.google.com/maps/d/edit?mid=$expectedId",
            "https://www.google.com/maps/d/u/0/edit?mid=$expectedId&usp=sharing",
            "https://www.google.com/maps/d/viewer?mid=$expectedId&ll=29.47,77.70&z=12",
            "https://www.google.com/maps/d/embed?mid=$expectedId",
            "https://www.google.com/maps/d/kml?mid=$expectedId",
            "https://www.google.com/maps/d/kml?mid=$expectedId&forcekml=1",
            "https://www.google.co.in/maps/d/edit?mid=$expectedId",
            "https://mymaps.google.com/maps/d/viewer?mid=$expectedId",
            "  www.google.com/maps/d/edit?mid=$expectedId  "
        )

        variants.forEach { url ->
            assertTrue("Should recognize My Maps URL: $url", KmlUrlImporter.isGoogleMyMapsUrl(url))
            assertEquals("Should extract mid for: $url", expectedId, KmlUrlImporter.parseGoogleMyMapsId(url))
        }

        // Non-My-Maps URLs must return null
        assertNull(KmlUrlImporter.parseGoogleMyMapsId("https://example.com/highway.kml"))
        assertNull(KmlUrlImporter.parseGoogleMyMapsId("https://www.google.com/maps/@29.47,77.70,15z"))
        assertFalse(KmlUrlImporter.isGoogleMyMapsUrl("https://cdn.example.org/routes/corridor?id=42"))
    }

    @Test
    fun resolveDownloadPlan_convertsMyMapsEditUrlToKmlAndForceKmlCandidates() {
        val plan = KmlUrlImporter.resolveDownloadPlan(
            "https://www.google.com/maps/d/edit?mid=19dSL053wOwTcMuqSDR9fr4TdIJU_hzY"
        )
        assertEquals("19dSL053wOwTcMuqSDR9fr4TdIJU_hzY", plan.googleMyMapsId)
        assertEquals(
            listOf(
                "https://www.google.com/maps/d/kml?mid=19dSL053wOwTcMuqSDR9fr4TdIJU_hzY",
                "https://www.google.com/maps/d/kml?mid=19dSL053wOwTcMuqSDR9fr4TdIJU_hzY&forcekml=1"
            ),
            plan.candidateUrls
        )
    }

    // ---------------------------------------------------------
    // 2. HTTP DOWNLOAD: GOOGLE MY MAPS, DIRECT KML (NO .kml), REDIRECTS & KMZ DETECTION
    // ---------------------------------------------------------
    @Test
    fun downloadRoute_googleMyMapsEditLink_fetchesKmzFromKmlEndpointAndParsesIdentically() {
        val server = startServer()
        val kmzBytes = createKmzBytes(sampleKml(docName = "MyMaps NH-58 Project"))

        server.createContext("/maps/d/kml") { exchange ->
            val query = exchange.requestURI.query.orEmpty()
            if (query.contains("mid=19dSL053wOwTcMuqSDR9fr4TdIJU_hzY")) {
                exchange.responseHeaders.add("Content-Type", "application/vnd.google-earth.kmz")
                exchange.responseHeaders.add("Content-Disposition", "attachment; filename=\"MyMaps NH-58 Project.kmz\"")
                exchange.sendResponseHeaders(200, kmzBytes.size.toLong())
                exchange.responseBody.use { it.write(kmzBytes) }
            } else {
                exchange.sendResponseHeaders(404, 0)
                exchange.responseBody.close()
            }
        }

        val origin = "http://127.0.0.1:${server.address.port}"
        val payload = KmlUrlImporter.downloadRouteInternal(
            rawUrl = "https://www.google.com/maps/d/edit?mid=19dSL053wOwTcMuqSDR9fr4TdIJU_hzY",
            myMapsOriginOverride = origin
        )

        assertEquals(DownloadedContentFormat.KMZ, payload.format)
        assertEquals("19dSL053wOwTcMuqSDR9fr4TdIJU_hzY", payload.googleMyMapsId)
        assertTrue(payload.resolvedFileName.endsWith(".kmz"))

        val route = KmzParser.parseBytes(payload.bytes, payload.resolvedFileName)
        assertEquals("MyMaps NH-58 Project", route.name)
        assertEquals(3, route.markers.size)
        assertEquals(1, route.alignments.size)
        assertEquals(10_000L, route.startChainage?.metres)
        assertEquals(11_000L, route.endChainage?.metres)
    }

    @Test
    fun downloadRoute_directExtensionlessUrlWithRedirectsAndGenericMimeType_succeeds() {
        val server = startServer()
        val kmlWithBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            sampleKml(docName = "Hosted Direct KML Route", startKm = 55).toByteArray(Charsets.UTF_8)

        server.createContext("/export/route") { exchange ->
            exchange.responseHeaders.add("Location", "/cdn/raw/payload?token=abc")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/cdn/raw/payload") { exchange ->
            // Generic octet-stream MIME type, NO .kml in URL path
            exchange.responseHeaders.add("Content-Type", "application/octet-stream")
            exchange.sendResponseHeaders(200, kmlWithBom.size.toLong())
            exchange.responseBody.use { it.write(kmlWithBom) }
        }

        val baseUrl = "http://127.0.0.1:${server.address.port}"
        val payload = KmlUrlImporter.downloadRoute("$baseUrl/export/route?id=99")

        assertEquals(DownloadedContentFormat.KML, payload.format)
        assertTrue(payload.resolvedFileName.endsWith(".kml"))

        val route = KmzParser.parseBytes(payload.bytes, payload.resolvedFileName)
        assertEquals("Hosted Direct KML Route", route.name)
        assertEquals(3, route.markers.size)
        assertEquals(55_000L, route.startChainage?.metres)
        assertEquals(56_000L, route.endChainage?.metres)
    }

    @Test
    fun downloadRoute_primaryKmzReturnsHtmlFallbackToForceKml_succeeds() {
        val server = startServer()
        val rawKml = sampleKml(docName = "ForceKML Fallback Route").toByteArray(Charsets.UTF_8)

        server.createContext("/maps/d/kml") { exchange ->
            val query = exchange.requestURI.query.orEmpty()
            if (query.contains("forcekml=1")) {
                exchange.responseHeaders.add("Content-Type", "application/vnd.google-earth.kml+xml")
                exchange.sendResponseHeaders(200, rawKml.size.toLong())
                exchange.responseBody.use { it.write(rawKml) }
            } else {
                val html = "<html><body>Temporary error</body></html>".toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "text/html")
                exchange.sendResponseHeaders(200, html.size.toLong())
                exchange.responseBody.use { it.write(html) }
            }
        }

        val origin = "http://127.0.0.1:${server.address.port}"
        val payload = KmlUrlImporter.downloadRouteInternal(
            rawUrl = "https://www.google.com/maps/d/viewer?mid=19dSL053wOwTcMuqSDR9fr4TdIJU_hzY",
            myMapsOriginOverride = origin
        )

        assertEquals(DownloadedContentFormat.KML, payload.format)
        assertTrue(payload.resolvedFileName.endsWith(".kml"))
        val route = KmzParser.parseBytes(payload.bytes, payload.resolvedFileName)
        assertEquals("ForceKML Fallback Route", route.name)
    }

    // ---------------------------------------------------------
    // 3. HTML / PRIVATE / LOGIN / ERROR PAGE REJECTION
    // ---------------------------------------------------------
    @Test
    fun downloadRoute_rejectsPrivateOrHtmlResponsesClearly() {
        val server = startServer()

        server.createContext("/private-map") { exchange ->
            exchange.sendResponseHeaders(403, 0)
            exchange.responseBody.close()
        }
        server.createContext("/login-redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/ServiceLogin?continue=maps")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/ServiceLogin") { exchange ->
            val html = "<!DOCTYPE html><html><head><title>Sign in</title></head><body>accounts.google.com</body></html>".toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html")
            exchange.sendResponseHeaders(200, html.size.toLong())
            exchange.responseBody.use { it.write(html) }
        }
        server.createContext("/html-error-200") { exchange ->
            val html = "<!DOCTYPE html><html><body>Page not found</body></html>".toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html")
            exchange.sendResponseHeaders(200, html.size.toLong())
            exchange.responseBody.use { it.write(html) }
        }

        val base = "http://127.0.0.1:${server.address.port}"

        listOf("$base/private-map", "$base/login-redirect", "$base/html-error-200").forEach { url ->
            try {
                KmlUrlImporter.downloadRoute(url)
                fail("Expected KmlUrlDownloadException for $url")
            } catch (e: KmlUrlDownloadException) {
                assertFalse(e.message.isNullOrBlank())
            }
        }
    }

    @Test
    fun kmzParser_explicitlyRejectsHtmlPayloadEvenIfPassedDirectly() {
        val html = "<!DOCTYPE html><html><body><p>Not a KML</p></body></html>".toByteArray(Charsets.UTF_8)
        try {
            KmzParser.parseBytes(html, "bad_response.kml")
            fail("Expected RouteParseException for HTML input")
        } catch (e: RouteParseException) {
            assertTrue(e.message?.contains("HTML", ignoreCase = true) == true)
        }
    }

    // ---------------------------------------------------------
    // 4. END-TO-END REPOSITORY PERSISTENCE & ACTIVE ROUTE PRESERVATION
    // ---------------------------------------------------------
    @Test
    fun routeRepository_importRouteFromUrl_persistsOfflineAndPreservesPreviousRouteOnFailure() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        databases.add(db)
        val prefs = PreferencesManager(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes.add(it) }
        val repo = RouteRepository(context, db.routeDao(), prefs, scope)

        val server = startServer()
        val kmlBytes = sampleKml(docName = "Expressway Corridor", startKm = 120).toByteArray(Charsets.UTF_8)
        val brokenXml = "<kml><Document><name>Broken</name><Placemark></Document>".toByteArray(Charsets.UTF_8)

        server.createContext("/good-kml") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/xml")
            exchange.sendResponseHeaders(200, kmlBytes.size.toLong())
            exchange.responseBody.use { it.write(kmlBytes) }
        }
        server.createContext("/bad-html") { exchange ->
            val html = "<!DOCTYPE html><html><body>Forbidden</body></html>".toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html")
            exchange.sendResponseHeaders(200, html.size.toLong())
            exchange.responseBody.use { it.write(html) }
        }
        server.createContext("/broken-kml") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/xml")
            exchange.sendResponseHeaders(200, brokenXml.size.toLong())
            exchange.responseBody.use { it.write(brokenXml) }
        }

        val base = "http://127.0.0.1:${server.address.port}"

        // Step A: Import valid extensionless KML URL
        val successResult = repo.importRouteFromUrl("$base/good-kml")
        assertTrue("Expected URL import to succeed: ${successResult.exceptionOrNull()}", successResult.isSuccess)
        val imported = successResult.getOrThrow()
        assertEquals("Expressway Corridor", imported.name)
        assertEquals(3, imported.markers.size)

        // Verify persisted locally on disk + Room DAO for offline use
        val entities = db.routeDao().getAllRoutes().first()
        assertEquals(1, entities.size)
        val storedFile = entities.first().localFilePath?.let { File(it) }
        assertNotNull(storedFile)
        assertTrue(storedFile!!.exists() && storedFile.length() > 0L)

        // Verify live ChainageMatcher uses imported URL route identically
        val match = ChainageMatcher.match(
            gpsPoint = GeoPoint(29.470000, 77.705000),
            gpsAccuracy = 5f,
            routeData = imported
        )
        assertNotNull(match.chainage)
        assertEquals(120_500L, match.chainage!!.metres)

        // Step B: Attempt failing HTML URL import -> previous active route MUST remain untouched
        val htmlFail = repo.importRouteFromUrl("$base/bad-html")
        assertTrue(htmlFail.isFailure)
        assertEquals(imported.id, repo.activeRouteData.value?.id)
        assertEquals("Expressway Corridor", repo.activeRouteData.value?.name)

        // Step C: Attempt broken XML URL import -> previous active route MUST remain untouched
        val brokenFail = repo.importRouteFromUrl("$base/broken-kml")
        assertTrue(brokenFail.isFailure)
        assertEquals(imported.id, repo.activeRouteData.value?.id)
        assertEquals(1, db.routeDao().getAllRoutes().first().size)
    }

    // ---------------------------------------------------------
    // 5. UI WORKFLOW TEST FOR KML / MY MAPS URL DIALOG
    // ---------------------------------------------------------
    @Test
    fun kmlKmzFilePickerCard_urlImportDialogSubmitsGoogleMyMapsUrl() {
        var submittedUrl: String? = null
        val targetUrl = "https://www.google.com/maps/d/edit?mid=19dSL053wOwTcMuqSDR9fr4TdIJU_hzY"

        composeTestRule.setContent {
            MaterialTheme {
                KmlKmzFilePickerCard(
                    onFileSelected = { _, _ -> },
                    isImporting = false,
                    onImportFromUrl = { url -> submittedUrl = url }
                )
            }
        }

        composeTestRule.onNodeWithTag("import_kml_url_button")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.onNodeWithTag("kml_url_input")
            .assertIsDisplayed()
            .performTextInput(targetUrl)

        composeTestRule.onNodeWithTag("mymaps_detected_badge")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("kml_url_submit_button")
            .performClick()

        assertEquals(targetUrl, submittedUrl)
    }
}

package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.PhotoRecordEntity
import com.example.data.PreferencesManager
import com.example.model.CameraMode
import com.example.model.CaptureSnapshot
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.CoordinateFormat
import com.example.model.DirectionControlMode
import com.example.model.DirectionDetectionState
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.OverlayConfig
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.SmartDirectionState
import com.example.model.WeatherConditionType
import com.example.model.WeatherDisplayFormat
import com.example.model.WeatherObservation
import com.example.model.WeatherUiState
import com.example.overlay.OverlayRenderer
import com.example.overlay.OverlayTextGenerator
import com.example.overlay.SmartTimestampComposer
import com.example.ui.home.AnimatedWeatherIcon
import com.example.ui.home.HomeLiveWeatherCard
import com.example.weather.OpenMeteoWeatherFetcher
import com.example.weather.SharedWeatherRepository
import com.example.weather.WeatherRemoteFetcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive verification suite for CTECS Dynamic Live Weather Card & Smart Timestamp
 * Metadata Integration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WeatherAndSmartTimestampTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun makeTimestampMs(
        year: Int,
        monthZeroBased: Int,
        day: Int,
        hour24: Int,
        minute: Int,
        tz: TimeZone
    ): Long {
        return Calendar.getInstance(tz).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour24)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun sampleObservation(
        tempC: Double = 29.0,
        feelsLikeC: Double = 31.0,
        humidity: Int = 62,
        windKmh: Double = 14.2,
        windDirDeg: Int = 225,
        condition: WeatherConditionType = WeatherConditionType.SUNNY,
        conditionLabel: String = "Sunny",
        locationName: String = "Bahadarabad, Uttarakhand",
        observedAtMs: Long
    ): WeatherObservation {
        return WeatherObservation(
            temperatureC = tempC,
            feelsLikeC = feelsLikeC,
            humidityPercent = humidity,
            windSpeedKmh = windKmh,
            windDirectionDegrees = windDirDeg,
            condition = condition,
            conditionLabel = conditionLabel,
            wmoCode = 0,
            isDay = true,
            latitude = 29.9166,
            longitude = 78.0523,
            locationName = locationName,
            observedAtMs = observedAtMs,
            fetchedAtMs = observedAtMs,
            source = WeatherObservation.DEFAULT_SOURCE
        )
    }

    // -------------------------------------------------------------------------
    // 1. Exact 4-Line Format Match & Multi-Location/TimeZone Dynamic Variations
    // -------------------------------------------------------------------------

    @Test
    fun `exact 4-line smart timestamp matches specification format dynamically`() {
        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        val captureTime = makeTimestampMs(2026, Calendar.NOVEMBER, 20, 3, 51, ist)
        val obs = sampleObservation(
            tempC = 29.0,
            locationName = "Bahadarabad, Uttarakhand",
            observedAtMs = captureTime - 120_000L
        )
        val config = OverlayConfig(
            showWeatherInfo = true,
            coordinatePrecision = 4,
            weatherShowTemperature = true,
            weatherShowCondition = false,
            weatherShowLocationName = true,
            weatherShowCoordinates = true,
            weatherShowHumidity = false,
            weatherShowWindSpeed = false,
            weatherShowWindDirection = false,
            weatherDisplayFormat = WeatherDisplayFormat.COMPACT
        )
        val project = ProjectProfile(
            highwayId = "MH",
            packageSection = "",
            workType = "MM",
            roadSide = RoadSide.NONE,
            lane = "MCW",
            activity = "BC"
        )

        val lines = OverlayTextGenerator.generateLines(
            config = config,
            project = project,
            location = GeoPoint(29.9166, 78.0523),
            accuracy = 4.2f,
            compassHeading = "NE",
            speedMps = null,
            bearing = null,
            chainageStr = "190+675",
            cameraMode = CameraMode.HIGHWAY,
            coordinateFormat = CoordinateFormat.DECIMAL,
            is24HourFormat = false,
            timestamp = captureTime,
            isForCapture = true,
            locationName = "Bahadarabad, Uttarakhand",
            weatherObservation = obs,
            timeZone = ist
        )

        assertEquals(
            listOf(
                "20 November 2026 3:51 AM",
                "29.9166°N 78.0523°E",
                "Bahadarabad, Uttarakhand | 29°C",
                "MH | MM | 190+675 MCW BC"
            ),
            lines
        )
    }

    @Test
    fun `dynamic 4-line timestamp updates accurately across different locations, temperatures, timezones and chainages`() {
        val utc = TimeZone.getTimeZone("UTC")
        val captureTime = makeTimestampMs(2026, Calendar.JULY, 5, 16, 25, utc)
        val obs = sampleObservation(
            tempC = 17.6,
            condition = WeatherConditionType.RAINY,
            conditionLabel = "Light Rain",
            locationName = "Sydney, NSW",
            observedAtMs = captureTime - 60_000L
        )
        val config = OverlayConfig(
            showWeatherInfo = true,
            coordinatePrecision = 5,
            weatherShowTemperature = true,
            weatherShowCondition = true,
            weatherShowLocationName = true,
            weatherShowCoordinates = true
        )
        val project = ProjectProfile(
            highwayId = "NH-48",
            packageSection = "Pkg-2",
            workType = "Overlay",
            roadSide = RoadSide.LHS,
            lane = "O/L",
            activity = "DBM"
        )

        val lines = SmartTimestampComposer.composeLines(
            config = config,
            project = project,
            location = GeoPoint(-33.86882, 151.20929),
            chainageStr = "245+310",
            coordinateFormat = CoordinateFormat.DECIMAL,
            is24HourFormat = true,
            timestampMs = captureTime,
            locationName = "Sydney, NSW",
            weatherObservation = obs,
            authoritativeSideOverride = "LHS",
            useAuthoritativeSide = true,
            timeZone = utc
        )

        assertEquals(4, lines.size)
        assertEquals("05 July 2026 16:25", lines[0])
        assertEquals("33.86882°S 151.20929°E", lines[1])
        assertEquals("Sydney, NSW | 18°C Light Rain", lines[2])
        assertEquals("NH-48 | Pkg-2 | Overlay | 245+310 LHS O/L DBM", lines[3])
    }

    // -------------------------------------------------------------------------
    // 2. Date/Time Formatting, Coordinate Precision, Hemispheres & DMS
    // -------------------------------------------------------------------------

    @Test
    fun `coordinates support precision 4 to 7, all 4 hemispheres and DMS format`() {
        assertEquals(
            "34.0522°N 118.2437°W",
            SmartTimestampComposer.formatCoordinatesLine(GeoPoint(34.052235, -118.243683), precision = 4)
        )
        assertEquals(
            "22.906847°S 43.172896°W",
            SmartTimestampComposer.formatCoordinatesLine(GeoPoint(-22.9068467, -43.1728965), precision = 6)
        )
        assertEquals(
            "29.9166123°N 78.0523456°E",
            SmartTimestampComposer.formatCoordinatesLine(GeoPoint(29.9166123, 78.0523456), precision = 7)
        )
        val dms = SmartTimestampComposer.formatCoordinatesLine(
            GeoPoint(-33.8688, -70.6693),
            precision = 4,
            coordinateFormat = CoordinateFormat.DMS
        )
        assertTrue("Expected S hemisphere in DMS: $dms", dms.contains("\"S"))
        assertTrue("Expected W hemisphere in DMS: $dms", dms.contains("\"W"))
    }

    @Test
    fun `date time formatting supports 12H, 24H, custom formats and Time in below positioning`() {
        val utc = TimeZone.getTimeZone("UTC")
        val ts = makeTimestampMs(2026, Calendar.MARCH, 9, 14, 5, utc)

        assertEquals(
            "09 March 2026 2:05 PM",
            SmartTimestampComposer.formatDateTimeLine(ts, "dd MMMM yyyy h:mm a", is24HourFormat = false, timeZone = utc)
        )
        assertEquals(
            "09 March 2026 14:05",
            SmartTimestampComposer.formatDateTimeLine(ts, "dd MMMM yyyy h:mm a", is24HourFormat = true, timeZone = utc)
        )
        assertEquals(
            "2026-03-09 14:05",
            SmartTimestampComposer.formatDateTimeLine(ts, "yyyy-MM-dd h:mm a", is24HourFormat = true, timeZone = utc)
        )

        val linesBelow = SmartTimestampComposer.composeLines(
            config = OverlayConfig(showWeatherInfo = true, timePosition = "Time in below"),
            project = ProjectProfile(highwayId = "MH", workType = "MM", roadSide = RoadSide.NONE, lane = "MCW", activity = "BC"),
            location = GeoPoint(29.9166, 78.0523),
            chainageStr = "190+675",
            coordinateFormat = CoordinateFormat.DECIMAL,
            is24HourFormat = false,
            timestampMs = ts,
            locationName = "Haridwar, Uttarakhand",
            weatherObservation = sampleObservation(tempC = 25.0, observedAtMs = ts),
            timeZone = utc
        )
        assertEquals("09 March 2026 2:05 PM", linesBelow.last())
    }

    // -------------------------------------------------------------------------
    // 3. Individual Toggles, Compact vs Detailed, Default-OFF Legacy Preservation
    // -------------------------------------------------------------------------

    @Test
    fun `default showWeatherInfo OFF preserves legacy OverlayTextGenerator output untouched`() {
        val config = OverlayConfig() // showWeatherInfo defaults to false
        assertFalse(config.showWeatherInfo)

        val project = ProjectProfile(
            highwayId = "NH-58",
            packageSection = "",
            workType = "MM",
            roadSide = RoadSide.RHS,
            lane = "I/L",
            activity = "BC"
        )
        val ts = makeTimestampMs(2026, Calendar.NOVEMBER, 20, 9, 15, TimeZone.getDefault())
        val obs = sampleObservation(tempC = 32.0, observedAtMs = ts)

        val withObs = OverlayTextGenerator.generateLines(
            config = config,
            project = project,
            location = GeoPoint(29.9166, 78.0523, altitude = 285.0),
            accuracy = 4.0f,
            compassHeading = "N",
            speedMps = null,
            bearing = null,
            chainageStr = "190+675",
            cameraMode = CameraMode.HIGHWAY,
            coordinateFormat = CoordinateFormat.DECIMAL,
            is24HourFormat = false,
            timestamp = ts,
            isForCapture = true,
            locationName = "Bahadarabad, Uttarakhand",
            weatherObservation = obs
        )
        val withoutObs = OverlayTextGenerator.generateLines(
            config = config,
            project = project,
            location = GeoPoint(29.9166, 78.0523, altitude = 285.0),
            accuracy = 4.0f,
            compassHeading = "N",
            speedMps = null,
            bearing = null,
            chainageStr = "190+675",
            cameraMode = CameraMode.HIGHWAY,
            coordinateFormat = CoordinateFormat.DECIMAL,
            is24HourFormat = false,
            timestamp = ts,
            isForCapture = true,
            locationName = "Bahadarabad, Uttarakhand",
            weatherObservation = null
        )

        assertEquals(withoutObs, withObs)
        assertFalse("Legacy output must not contain °C when Weather Info is OFF", withObs.any { it.contains("°C") })
    }

    @Test
    fun `independent weather toggles and Compact vs Detailed display formats behave accurately without duplicates`() {
        val utc = TimeZone.getTimeZone("UTC")
        val ts = makeTimestampMs(2026, Calendar.OCTOBER, 12, 11, 30, utc)
        val obs = sampleObservation(
            tempC = 27.4,
            humidity = 68,
            windKmh = 12.0,
            windDirDeg = 90,
            condition = WeatherConditionType.PARTLY_CLOUDY,
            conditionLabel = "Partly Cloudy",
            locationName = "Roorkee, Uttarakhand",
            observedAtMs = ts - 60_000L
        )
        val project = ProjectProfile(highwayId = "NH-334", workType = "MM", roadSide = RoadSide.NONE, lane = "MCW", activity = "BC")

        // Compact with all weather metrics ON
        val compactCfg = OverlayConfig(
            showWeatherInfo = true,
            weatherShowTemperature = true,
            weatherShowCondition = true,
            weatherShowLocationName = true,
            weatherShowCoordinates = true,
            weatherShowHumidity = true,
            weatherShowWindSpeed = true,
            weatherShowWindDirection = true,
            weatherDisplayFormat = WeatherDisplayFormat.COMPACT
        )
        val compactLines = SmartTimestampComposer.composeLines(
            config = compactCfg,
            project = project,
            location = GeoPoint(29.8543, 77.8880),
            chainageStr = "152+400",
            coordinateFormat = CoordinateFormat.DECIMAL,
            is24HourFormat = false,
            timestampMs = ts,
            locationName = "Roorkee, Uttarakhand",
            weatherObservation = obs,
            timeZone = utc
        )
        assertEquals(4, compactLines.size)
        assertEquals("Roorkee, Uttarakhand | 27°C Partly Cloudy | RH 68% | 12 km/h E", compactLines[2])

        // Detailed format splits humidity/wind onto a clean secondary weather line
        val detailedLines = SmartTimestampComposer.composeLines(
            config = compactCfg.copy(weatherDisplayFormat = WeatherDisplayFormat.DETAILED),
            project = project,
            location = GeoPoint(29.8543, 77.8880),
            chainageStr = "152+400",
            coordinateFormat = CoordinateFormat.DECIMAL,
            is24HourFormat = false,
            timestampMs = ts,
            locationName = "Roorkee, Uttarakhand",
            weatherObservation = obs,
            timeZone = utc
        )
        assertEquals(5, detailedLines.size)
        assertEquals("Roorkee, Uttarakhand | 27°C | Partly Cloudy", detailedLines[2])
        assertEquals("Humidity 68% | Wind 12 km/h E", detailedLines[3])

        // Disabling coordinates & location hides only those tokens
        val minimalWeatherCfg = compactCfg.copy(
            weatherShowCoordinates = false,
            weatherShowLocationName = false,
            weatherShowHumidity = false,
            weatherShowWindSpeed = false,
            weatherShowWindDirection = false,
            weatherShowCondition = false,
            weatherShowTemperature = true
        )
        val minimalLines = SmartTimestampComposer.composeLines(
            config = minimalWeatherCfg,
            project = project,
            location = GeoPoint(29.8543, 77.8880),
            chainageStr = "152+400",
            coordinateFormat = CoordinateFormat.DECIMAL,
            is24HourFormat = false,
            timestampMs = ts,
            locationName = "Roorkee, Uttarakhand",
            weatherObservation = obs,
            timeZone = utc
        )
        assertEquals(3, minimalLines.size)
        assertFalse(minimalLines.any { it.contains("°N") })
        assertFalse(minimalLines.any { it.contains("Roorkee") })
        assertEquals("27°C", minimalLines[1])
    }

    // -------------------------------------------------------------------------
    // 4. Authoritative LHS/RHS Direction & Missing GPS / Stale Weather Handling
    // -------------------------------------------------------------------------

    @Test
    fun `authoritative SmartDirectionState is respected and uncertain side is omitted cleanly`() {
        val utc = TimeZone.getTimeZone("UTC")
        val ts = makeTimestampMs(2026, Calendar.AUGUST, 1, 8, 0, utc)
        val project = ProjectProfile(highwayId = "NH-58", workType = "MM", roadSide = RoadSide.RHS, lane = "MCW", activity = "BC")

        val confirmedSnapshot = CaptureSnapshot(
            timestamp = ts,
            location = GeoPoint(29.9166, 78.0523),
            locationName = "Bahadarabad, Uttarakhand",
            matchResult = ChainageMatchResult(
                chainage = Chainage.fromMetres(190_675L),
                confidence = MatchConfidence.HIGH,
                source = ChainageSource.KMZ,
                smartDirectionState = SmartDirectionState(
                    confirmedSide = "LHS",
                    detectionState = DirectionDetectionState.LHS_CONFIRMED,
                    isManualOverride = false,
                    confidenceScore = 0.94f
                )
            ),
            projectProfile = project,
            overlayConfig = OverlayConfig(showWeatherInfo = true),
            weatherObservation = sampleObservation(tempC = 28.0, observedAtMs = ts)
        )
        val (confirmedSide, useConfirmed) = OverlayTextGenerator.resolveAuthoritativeCaptureSide(confirmedSnapshot)
        val confirmedLines = SmartTimestampComposer.composeLines(
            config = confirmedSnapshot.overlayConfig,
            project = project,
            location = confirmedSnapshot.location,
            chainageStr = confirmedSnapshot.displayChainage,
            coordinateFormat = CoordinateFormat.DECIMAL,
            is24HourFormat = false,
            timestampMs = ts,
            locationName = confirmedSnapshot.locationName,
            weatherObservation = confirmedSnapshot.weatherObservation,
            authoritativeSideOverride = confirmedSide,
            useAuthoritativeSide = useConfirmed,
            timeZone = utc
        )
        assertTrue(confirmedLines.last().contains("190+675 LHS MCW BC"))

        val uncertainSnapshot = confirmedSnapshot.copy(
            matchResult = confirmedSnapshot.matchResult.copy(
                smartDirectionState = SmartDirectionState(
                    confirmedSide = null,
                    detectionState = DirectionDetectionState.DIRECTION_UNCERTAIN,
                    isManualOverride = false,
                    confidenceScore = 0.2f
                )
            )
        )
        val (uncertainSide, useUncertain) = OverlayTextGenerator.resolveAuthoritativeCaptureSide(uncertainSnapshot)
        val uncertainLines = SmartTimestampComposer.composeLines(
            config = uncertainSnapshot.overlayConfig,
            project = project,
            location = uncertainSnapshot.location,
            chainageStr = uncertainSnapshot.displayChainage,
            coordinateFormat = CoordinateFormat.DECIMAL,
            is24HourFormat = false,
            timestampMs = ts,
            locationName = uncertainSnapshot.locationName,
            weatherObservation = uncertainSnapshot.weatherObservation,
            authoritativeSideOverride = uncertainSide,
            useAuthoritativeSide = useUncertain,
            timeZone = utc
        )
        assertFalse("Uncertain direction must not stamp guessed RHS", uncertainLines.last().contains("RHS"))
        assertFalse("Uncertain direction must not stamp guessed LHS", uncertainLines.last().contains("LHS"))
    }

    @Test
    fun `missing GPS, missing weather, and stale weather never fabricate fake values`() {
        val utc = TimeZone.getTimeZone("UTC")
        val captureTime = makeTimestampMs(2026, Calendar.NOVEMBER, 20, 10, 0, utc)
        val staleObs = sampleObservation(
            tempC = 29.0,
            observedAtMs = captureTime - (60L * 60L * 1000L) // 60 min old > 45 min max
        )
        val project = ProjectProfile(highwayId = "MH", workType = "MM", roadSide = RoadSide.NONE, lane = "MCW", activity = "BC")

        val staleLines = SmartTimestampComposer.composeLines(
            config = OverlayConfig(showWeatherInfo = true, weatherOmitIfStale = true),
            project = project,
            location = null, // missing GPS
            chainageStr = "190+675",
            coordinateFormat = CoordinateFormat.DECIMAL,
            is24HourFormat = false,
            timestampMs = captureTime,
            locationName = "Bahadarabad, Uttarakhand",
            weatherObservation = staleObs,
            timeZone = utc
        )

        assertEquals(3, staleLines.size)
        assertEquals("20 November 2026 10:00 AM", staleLines[0])
        assertEquals("Bahadarabad, Uttarakhand", staleLines[1]) // stale 29°C cleanly omitted
        assertEquals("MH | MM | 190+675 MCW BC", staleLines[2])
        assertFalse(staleLines.any { it.contains("°C") || it.contains("0.0000") })
    }

    // -------------------------------------------------------------------------
    // 5. SharedWeatherRepository Caching, Deduplication, Open-Meteo Parsing & Non-Blocking Capture
    // -------------------------------------------------------------------------

    @Test
    fun `Open-Meteo JSON parser maps all WMO codes, day-night, and 16-point wind compass accurately`() {
        val json = """
            {
              "current": {
                "time": 1795146660,
                "temperature_2m": 29.3,
                "relative_humidity_2m": 58,
                "apparent_temperature": 31.1,
                "is_day": 1,
                "weather_code": 95,
                "wind_speed_10m": 18.4,
                "wind_direction_10m": 225
              }
            }
        """.trimIndent()

        val parsed = OpenMeteoWeatherFetcher.parseOpenMeteoCurrentResponse(
            jsonBody = json,
            latitude = 29.9166,
            longitude = 78.0523,
            fetchedAtMs = 1_795_146_700_000L
        )
        assertNotNull(parsed)
        parsed!!
        assertEquals(29, parsed.roundedTempC)
        assertEquals(31, parsed.roundedFeelsLikeC)
        assertEquals(58, parsed.humidityPercent)
        assertEquals(WeatherConditionType.THUNDERSTORM, parsed.condition)
        assertEquals("SW", parsed.windDirectionCompass)
        assertEquals("18 km/h", parsed.formatWindSpeed())

        // Verify all 8 WeatherConditionType categories map cleanly
        assertEquals(WeatherConditionType.SUNNY, WeatherConditionType.fromWmoCode(0, isDay = true).first)
        assertEquals(WeatherConditionType.NIGHT, WeatherConditionType.fromWmoCode(0, isDay = false).first)
        assertEquals(WeatherConditionType.PARTLY_CLOUDY, WeatherConditionType.fromWmoCode(2, isDay = true).first)
        assertEquals(WeatherConditionType.CLOUDY, WeatherConditionType.fromWmoCode(3, isDay = true).first)
        assertEquals(WeatherConditionType.FOGGY, WeatherConditionType.fromWmoCode(45, isDay = true).first)
        assertEquals(WeatherConditionType.RAINY, WeatherConditionType.fromWmoCode(63, isDay = true).first)
        assertEquals(WeatherConditionType.SNOWY, WeatherConditionType.fromWmoCode(73, isDay = true).first)
        assertEquals(WeatherConditionType.THUNDERSTORM, WeatherConditionType.fromWmoCode(99, isDay = true).first)
    }

    @Test
    fun `SharedWeatherRepository deduplicates requests, respects spatial-temporal cache, handles offline backoff and never blocks capture`() = runBlocking {
        var fakeNowMs = 1_795_146_660_000L
        val fetchCount = AtomicInteger(0)
        var shouldFailNetwork = false

        val fetcher = WeatherRemoteFetcher { lat, lng, nowMs ->
            fetchCount.incrementAndGet()
            if (shouldFailNetwork) return@WeatherRemoteFetcher null
            sampleObservation(
                tempC = 29.0,
                observedAtMs = nowMs
            ).copy(latitude = lat, longitude = lng)
        }
        val repo = SharedWeatherRepository(fetcher = fetcher, clockMs = { fakeNowMs })

        // First GPS fix triggers exactly 1 fetch
        val p1 = GeoPoint(29.9166, 78.0523)
        val first = repo.refreshForLocation(p1, resolvedLocationName = "Bahadarabad, Uttarakhand")
        assertNotNull(first)
        assertEquals(1, fetchCount.get())
        assertTrue(repo.weatherUiState.value is WeatherUiState.Available)

        // Nearby GPS fix within same cell & TTL does NOT trigger duplicate API call
        fakeNowMs += 2L * 60L * 1000L // +2 min (<10 min TTL)
        repo.refreshForLocation(GeoPoint(29.9170, 78.0525), resolvedLocationName = "Bahadarabad, Uttarakhand")
        assertEquals(1, fetchCount.get())

        // Synchronous non-blocking capture check returns fresh observation immediately
        val (capObs, capFresh) = repo.getObservationForCapture(captureTimeMs = fakeNowMs, omitIfStale = true)
        assertNotNull(capObs)
        assertTrue(capFresh)

        // Advance past 45-minute capture staleness window: observation flagged stale
        val (_, staleFlag) = repo.getObservationForCapture(
            captureTimeMs = fakeNowMs + (50L * 60L * 1000L),
            omitIfStale = true
        )
        assertFalse(staleFlag)

        // Move >2 km and simulate offline failure -> enters backoff without fabricating weather
        repo.clearForTesting()
        shouldFailNetwork = true
        fakeNowMs += 15L * 60L * 1000L
        val offlineRes = repo.refreshForLocation(GeoPoint(30.1000, 78.2000))
        assertNull(offlineRes)
        assertEquals(2, fetchCount.get())
        assertTrue(repo.weatherUiState.value is WeatherUiState.Unavailable)

        // Immediate retry within 60s backoff window does NOT hammer network
        fakeNowMs += 10_000L
        repo.refreshForLocation(GeoPoint(30.1000, 78.2000))
        assertEquals(2, fetchCount.get())
    }

    // -------------------------------------------------------------------------
    // 6. Portrait & Landscape Bitmap Rendering & EXIF Metadata Preservation
    // -------------------------------------------------------------------------

    @Test
    fun `OverlayRenderer renders 4-line weather timestamp cleanly in Portrait and Landscape and preserves EXIF metadata`() {
        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        val captureTime = makeTimestampMs(2026, Calendar.NOVEMBER, 20, 3, 51, ist)
        val obs = sampleObservation(
            tempC = 29.0,
            humidity = 64,
            windKmh = 11.0,
            windDirDeg = 180,
            condition = WeatherConditionType.SUNNY,
            conditionLabel = "Sunny",
            locationName = "Bahadarabad, Uttarakhand",
            observedAtMs = captureTime - 30_000L
        )
        val snapshot = CaptureSnapshot(
            timestamp = captureTime,
            location = GeoPoint(29.9166, 78.0523, altitude = 294.0),
            locationName = "Bahadarabad, Uttarakhand",
            gpsAccuracy = 3.8f,
            compassAzimuth = 45f,
            matchResult = ChainageMatchResult(
                chainage = Chainage.fromMetres(190_675L),
                confidence = MatchConfidence.HIGH,
                source = ChainageSource.KMZ,
                smartDirectionState = SmartDirectionState(
                    confirmedSide = "RHS",
                    detectionState = DirectionDetectionState.RHS_CONFIRMED,
                    isManualOverride = false,
                    confidenceScore = 0.92f
                )
            ),
            projectProfile = ProjectProfile(
                highwayId = "MH",
                workType = "MM",
                roadSide = RoadSide.RHS,
                lane = "MCW",
                activity = "BC"
            ),
            overlayConfig = OverlayConfig(
                showWeatherInfo = true,
                showMapInset = false,
                weatherShowTemperature = true,
                weatherShowLocationName = true,
                weatherShowCoordinates = true
            ),
            weatherObservation = obs,
            weatherIsFreshAtCapture = true,
            is24HourFormat = false,
            coordinateFormat = CoordinateFormat.DECIMAL
        )

        // Portrait 1080x1920
        val portraitSrc = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
        val portraitOut = OverlayRenderer.renderOverlay(context, portraitSrc, snapshot, isVideoFrame = false)
        assertEquals(1080, portraitOut.width)
        assertEquals(1920, portraitOut.height)

        // Landscape 1920x1080
        val landscapeSrc = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
        val landscapeOut = OverlayRenderer.renderOverlay(context, landscapeSrc, snapshot, isVideoFrame = false)
        assertEquals(1920, landscapeOut.width)
        assertEquals(1080, landscapeOut.height)

        // Save with EXIF & verify all standard + weather tags are intact
        val outFile = File(context.cacheDir, "ctecs_weather_exif_test.jpg")
        if (outFile.exists()) outFile.delete()
        val saved = OverlayRenderer.saveBitmapWithExif(
            bitmap = landscapeOut,
            destinationFile = outFile,
            snapshot = snapshot,
            jpegQuality = 92,
            context = context
        )
        assertTrue(saved)
        assertTrue(outFile.exists())

        val exif = ExifInterface(outFile.absolutePath)
        assertNotNull(exif.getAttribute(ExifInterface.TAG_DATETIME))
        assertEquals("N", exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF))
        assertEquals("E", exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF))
        val description = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION).orEmpty()
        val userComment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT).orEmpty()

        assertTrue("EXIF description should contain location: $description", description.contains("Bahadarabad, Uttarakhand"))
        assertTrue("EXIF description should contain weather temp: $description", description.contains("29°C"))
        assertTrue("EXIF description should contain chainage: $description", description.contains("190+675"))
        assertTrue("EXIF user comment should contain weather metadata: $userComment", userComment.contains("Weather: 29°C Sunny"))
        assertTrue("EXIF user comment should contain authoritative side: $userComment", userComment.contains("RHS"))

        portraitSrc.recycle()
        portraitOut.recycle()
        landscapeSrc.recycle()
        landscapeOut.recycle()
        outFile.delete()
    }

    // -------------------------------------------------------------------------
    // 7. Room PhotoRecordEntity & PreferencesManager Persistence
    // -------------------------------------------------------------------------

    @Test
    fun `Room PhotoRecordEntity stores and retrieves capture-time weather metadata accurately`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val entity = PhotoRecordEntity(
                filePath = "/tmp/ctecs_test_photo.jpg",
                fileName = "MH_20261120_035100.jpg",
                timestamp = 1_795_146_660_000L,
                latitude = 29.9166,
                longitude = 78.0523,
                accuracy = 4.0f,
                chainageMetres = 190_675L,
                chainageLabel = "190+675",
                highwayId = "MH",
                roadSide = "RHS",
                lane = "MCW",
                activity = "BC",
                locationName = "Bahadarabad, Uttarakhand",
                weatherTempC = 29.0,
                weatherCondition = "Sunny",
                weatherHumidity = 62,
                weatherWindSpeedKmh = 14.2,
                weatherWindDirDeg = 225,
                weatherObservedAtMs = 1_795_146_600_000L,
                weatherSource = "Open-Meteo"
            )
            val id = db.photoRecordDao().insertPhoto(entity)
            val loaded = db.photoRecordDao().getPhotoById(id)
            assertNotNull(loaded)
            loaded!!
            assertEquals("Bahadarabad, Uttarakhand", loaded.cleanLocationName())
            assertTrue(loaded.hasWeatherMetadata())
            assertEquals("29°C • Sunny • Humidity 62% • Wind 14 km/h SW", loaded.formatWeatherMetadataSummary())
            assertEquals("Open-Meteo", loaded.weatherSource)
        } finally {
            db.close()
        }
    }

    @Test
    fun `PreferencesManager persists Weather Information settings and cached observation JSON round-trip`() = runBlocking {
        val prefs = PreferencesManager(context)
        val updated = OverlayConfig(
            showWeatherInfo = true,
            weatherShowTemperature = true,
            weatherShowCondition = true,
            weatherShowLocationName = true,
            weatherShowCoordinates = false,
            weatherShowHumidity = true,
            weatherShowWindSpeed = true,
            weatherShowWindDirection = false,
            weatherDisplayFormat = WeatherDisplayFormat.DETAILED,
            weatherOmitIfStale = false
        )
        prefs.updateOverlayConfig(updated)
        val restored = prefs.overlayConfigFlow.first()
        assertEquals(true, restored.showWeatherInfo)
        assertEquals(true, restored.weatherShowCondition)
        assertEquals(false, restored.weatherShowCoordinates)
        assertEquals(true, restored.weatherShowHumidity)
        assertEquals(true, restored.weatherShowWindSpeed)
        assertEquals(false, restored.weatherShowWindDirection)
        assertEquals(WeatherDisplayFormat.DETAILED, restored.weatherDisplayFormat)
        assertEquals(false, restored.weatherOmitIfStale)

        val obs = sampleObservation(tempC = 24.5, observedAtMs = 1_795_146_660_000L)
        prefs.saveCachedWeatherObservation(obs)
        val restoredObs = prefs.getInitialCachedWeatherObservation()
        assertNotNull(restoredObs)
        assertEquals(24.5, restoredObs!!.temperatureC, 0.01)
        assertEquals("Bahadarabad, Uttarakhand", restoredObs.locationName)
        assertEquals(WeatherConditionType.SUNNY, restoredObs.condition)

        // Reset showWeatherInfo back to default OFF so shared test prefs stay clean
        prefs.updateOverlayConfig(OverlayConfig())
    }

    // -------------------------------------------------------------------------
    // 8. Home Screen Live Weather Card Compose UI (Light & Dark Themes, All 8 Conditions, Pause)
    // -------------------------------------------------------------------------

    @Test
    fun `HomeLiveWeatherCard displays complete live weather telemetry in Dark theme and pauses animation when hidden`() {
        val ts = makeTimestampMs(2026, Calendar.NOVEMBER, 20, 9, 45, TimeZone.getDefault())
        val obs = sampleObservation(
            tempC = 29.0,
            feelsLikeC = 31.0,
            humidity = 62,
            windKmh = 14.0,
            windDirDeg = 225,
            condition = WeatherConditionType.SUNNY,
            conditionLabel = "Sunny",
            locationName = "Bahadarabad, Uttarakhand",
            observedAtMs = ts
        )
        var refreshClicks = 0

        composeRule.setContent {
            MaterialTheme {
                HomeLiveWeatherCard(
                    weatherUiState = WeatherUiState.Available(obs),
                    resolvedLocationName = "Bahadarabad, Uttarakhand",
                    is24HourFormat = false,
                    isDark = true,
                    mainTextColor = ComposeColor.White,
                    subTextColor = ComposeColor.LightGray,
                    isCompact = false,
                    isVisible = false, // Verifies paused animations settle cleanly for waitForIdle()
                    onRefresh = { refreshClicks++ }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_live_weather_card").assertIsDisplayed()
        composeRule.onNodeWithTag("weather_temp_text").assertTextContains("29°C")
        composeRule.onNodeWithTag("weather_condition_text").assertTextContains("Sunny")
        composeRule.onNodeWithTag("weather_location_text").assertTextContains("Bahadarabad, Uttarakhand")
        composeRule.onNodeWithTag("weather_updated_text").assertTextContains("Updated", substring = true)
        composeRule.onNodeWithTag("weather_feels_like_text", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("weather_humidity_text", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("weather_wind_text", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Feels 31°C").assertIsDisplayed()
        composeRule.onNodeWithText("Humidity 62%").assertIsDisplayed()
        composeRule.onNodeWithText("14 km/h SW").assertIsDisplayed()

        composeRule.onNodeWithTag("weather_refresh_button").performClick()
        assertEquals(1, refreshClicks)
    }

    @Test
    fun `HomeLiveWeatherCard supports Light theme, offline fallback, and all 8 animated weather icons`() {
        composeRule.setContent {
            MaterialTheme {
                Column {
                    HomeLiveWeatherCard(
                        weatherUiState = WeatherUiState.Unavailable(reason = "Weather unavailable offline"),
                        resolvedLocationName = "",
                        is24HourFormat = true,
                        isDark = false,
                        mainTextColor = ComposeColor.Black,
                        subTextColor = ComposeColor.DarkGray,
                        isCompact = true,
                        isVisible = false,
                        onRefresh = {}
                    )
                    WeatherConditionType.entries.forEach { condition ->
                        AnimatedWeatherIcon(
                            condition = condition,
                            isDark = false,
                            isAnimating = false
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("weather_status_title").assertTextContains("Weather unavailable offline")
    }
}

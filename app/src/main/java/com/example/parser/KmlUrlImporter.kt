package com.example.parser

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit

class KmlUrlDownloadException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

enum class DownloadedContentFormat {
    KMZ,
    KML,
    HTML_LOGIN,
    HTML_PAGE,
    UNKNOWN
}

data class DownloadedRoutePayload(
    val bytes: ByteArray,
    val resolvedFileName: String,
    val suggestedDisplayName: String?,
    val format: DownloadedContentFormat,
    val sourceUrl: String,
    val finalUrl: String,
    val googleMyMapsId: String? = null
)

data class ResolvedDownloadPlan(
    val candidateUrls: List<String>,
    val googleMyMapsId: String?,
    val suggestedBaseName: String?
)

/**
 * Resolves, downloads, and validates KML/KMZ route alignment data from:
 * 1. Google My Maps URLs (e.g. `https://www.google.com/maps/d/edit?mid=...`)
 * 2. Direct KML or KMZ HTTP/HTTPS URLs (with or without `.kml`/`.kmz` extension,
 *    including redirects and varied server MIME types).
 */
object KmlUrlImporter {

    private const val LOG_TAG = "CTECS_KML_URL"
    private const val MAX_DOWNLOAD_BYTES = 50 * 1024 * 1024 // 50 MB
    private const val HEADER_INSPECT_BYTES = 4096

    val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun logInfo(msg: String) {
        try {
            android.util.Log.i(LOG_TAG, msg)
        } catch (_: Throwable) {}
    }

    private fun logWarn(msg: String, err: Throwable? = null) {
        try {
            if (err != null) android.util.Log.w(LOG_TAG, msg, err)
            else android.util.Log.w(LOG_TAG, msg)
        } catch (_: Throwable) {}
    }

    /**
     * Normalizes user-pasted URL text by trimming whitespace/linebreaks and adding `https://`
     * when a bare domain such as `www.google.com/maps/d/...` is pasted.
     */
    fun normalizeUrlInput(rawInput: String): String {
        val trimmed = rawInput
            .trim()
            .replace("\r", "")
            .replace("\n", "")
        if (trimmed.isEmpty()) return ""
        val lower = trimmed.lowercase(Locale.ROOT)
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> trimmed
            trimmed.contains("://") -> trimmed
            else -> "https://$trimmed"
        }
    }

    /**
     * Returns true if the URL points to a Google My Maps project (`/maps/d/...` or `mymaps.google.*`).
     */
    fun isGoogleMyMapsUrl(rawUrl: String): Boolean {
        return parseGoogleMyMapsId(rawUrl) != null
    }

    /**
     * Extracts the Google My Maps `mid` Map ID from supported URL formats:
     * - `https://www.google.com/maps/d/edit?mid=<ID>`
     * - `https://www.google.com/maps/d/u/0/edit?mid=<ID>&usp=sharing`
     * - `https://www.google.com/maps/d/viewer?mid=<ID>`
     * - `https://www.google.com/maps/d/embed?mid=<ID>`
     * - `https://www.google.com/maps/d/kml?mid=<ID>`
     * - `https://mymaps.google.com/...?mid=<ID>`
     * - Regional Google domains (`google.co.in`, `google.co.uk`, etc.)
     */
    fun parseGoogleMyMapsId(rawUrl: String): String? {
        val normalized = normalizeUrlInput(rawUrl)
        if (normalized.isBlank()) return null
        val httpUrl = normalized.toHttpUrlOrNull() ?: return null
        val host = httpUrl.host.lowercase(Locale.ROOT)
        val path = httpUrl.encodedPath.lowercase(Locale.ROOT)

        val isGoogleDomain = host == "mymaps.google.com" ||
            host.startsWith("mymaps.google.") ||
            host == "google.com" ||
            host.startsWith("www.google.") ||
            host.startsWith("maps.google.") ||
            Regex("^(?:[a-z0-9-]+\\.)*google\\.[a-z.]{2,}$").matches(host)

        val isMyMapsPath = path.contains("/maps/d/") ||
            host.startsWith("mymaps.google.")

        if (!isGoogleDomain || !isMyMapsPath) return null

        val queryMid = httpUrl.queryParameter("mid")?.trim()
        if (!queryMid.isNullOrBlank() && isValidMyMapsId(queryMid)) {
            return queryMid
        }

        // Check URL fragment in case sharing wrapper embedded mid after '#'
        val fragment = httpUrl.fragment
        if (!fragment.isNullOrBlank()) {
            val fragmentMatch = Regex("(?:^|[?&])mid=([A-Za-z0-9_-]{4,})").find(fragment)
            val fragMid = fragmentMatch?.groupValues?.getOrNull(1)
            if (!fragMid.isNullOrBlank() && isValidMyMapsId(fragMid)) {
                return fragMid
            }
        }

        return null
    }

    private fun isValidMyMapsId(candidate: String): Boolean {
        return candidate.length >= 4 && candidate.all { it.isLetterOrDigit() || it == '_' || it == '-' }
    }

    /**
     * Resolves an input URL into ordered HTTP/HTTPS download candidate URLs.
     */
    @Throws(KmlUrlDownloadException::class)
    fun resolveDownloadPlan(
        rawUrl: String,
        myMapsOriginOverride: String? = null
    ): ResolvedDownloadPlan {
        val normalized = normalizeUrlInput(rawUrl)
        if (normalized.isBlank()) {
            throw KmlUrlDownloadException("Please enter a valid KML, KMZ, or Google My Maps URL.")
        }

        val parsed = normalized.toHttpUrlOrNull()
            ?: throw KmlUrlDownloadException("Invalid URL format. Please paste a complete http:// or https:// link.")

        if (parsed.scheme != "http" && parsed.scheme != "https") {
            throw KmlUrlDownloadException("Unsupported URL scheme '${parsed.scheme}'. Only http:// and https:// URLs are supported.")
        }

        val mapId = parseGoogleMyMapsId(normalized)
        if (mapId != null) {
            val origin = (myMapsOriginOverride?.trimEnd('/')) ?: "https://www.google.com"
            val lid = parsed.queryParameter("lid")?.trim()?.takeIf { it.isNotBlank() }
            val forceKmlExplicit = parsed.queryParameter("forcekml") == "1"

            val lidSuffix = if (lid != null) "&lid=$lid" else ""
            val kmzUrl = "$origin/maps/d/kml?mid=$mapId$lidSuffix"
            val forceKmlUrl = "$origin/maps/d/kml?mid=$mapId$lidSuffix&forcekml=1"

            val candidates = if (forceKmlExplicit) {
                listOf(forceKmlUrl, kmzUrl)
            } else {
                // KMZ preserves full Google My Maps styling/folders/doc.kml; forcekml=1 acts as clean XML fallback
                listOf(kmzUrl, forceKmlUrl)
            }

            return ResolvedDownloadPlan(
                candidateUrls = candidates,
                googleMyMapsId = mapId,
                suggestedBaseName = "mymaps_$mapId"
            )
        }

        val lastSegment = parsed.pathSegments
            .lastOrNull { it.isNotBlank() }
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.takeIf { it.isNotBlank() && !it.equals("/", ignoreCase = true) }

        return ResolvedDownloadPlan(
            candidateUrls = listOf(parsed.toString()),
            googleMyMapsId = null,
            suggestedBaseName = lastSegment
        )
    }

    /**
     * Inspects downloaded bytes, HTTP content-type, and final resolved URL to accurately determine
     * whether the payload is KMZ (ZIP archive), KML (XML), an HTML login redirect, or an HTML error page.
     */
    fun detectContentFormat(
        bytes: ByteArray,
        contentType: String?,
        finalUrl: String? = null
    ): DownloadedContentFormat {
        if (bytes.isEmpty()) return DownloadedContentFormat.UNKNOWN

        // 1. Check ZIP/KMZ magic bytes ('PK' = 0x50 0x4B)
        if (bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte()
        ) {
            return DownloadedContentFormat.KMZ
        }

        // 2. Inspect text header after stripping UTF-8 BOM if present
        val startOffset = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) 3 else 0

        val inspectLen = minOf(bytes.size - startOffset, HEADER_INSPECT_BYTES)
        if (inspectLen <= 0) return DownloadedContentFormat.UNKNOWN

        val headerText = String(bytes, startOffset, inspectLen, Charsets.UTF_8).trimStart()
        val headerLower = headerText.lowercase(Locale.ROOT)
        val mimeLower = contentType?.lowercase(Locale.ROOT).orEmpty()
        val finalUrlLower = finalUrl?.lowercase(Locale.ROOT).orEmpty()

        val isLoginHost = finalUrlLower.contains("accounts.google.com") ||
            finalUrlLower.contains("/servicelogin") ||
            finalUrlLower.contains("/v3/signin")

        // Check for HTML content BEFORE KML (HTML error pages may mention "kml" in text!)
        val looksLikeHtmlTag = headerLower.startsWith("<!doctype html") ||
            headerLower.startsWith("<html") ||
            headerLower.startsWith("<head") ||
            headerLower.startsWith("<body") ||
            headerLower.startsWith("<script")

        if (looksLikeHtmlTag) {
            return if (isLoginHost ||
                headerLower.contains("accounts.google.com") ||
                headerLower.contains("servicelogin") ||
                headerLower.contains("sign in")
            ) {
                DownloadedContentFormat.HTML_LOGIN
            } else {
                DownloadedContentFormat.HTML_PAGE
            }
        }

        // Check for XML/KML root structure
        if (headerLower.contains("<kml") ||
            (headerLower.startsWith("<?xml") && !mimeLower.contains("text/html"))
        ) {
            // Confirm it isn't XHTML disguised as <?xml ...><html
            if (headerLower.contains("<html")) {
                return if (isLoginHost) DownloadedContentFormat.HTML_LOGIN else DownloadedContentFormat.HTML_PAGE
            }
            if (headerLower.contains("<kml") ||
                mimeLower.contains("kml") ||
                mimeLower.contains("xml")
            ) {
                return DownloadedContentFormat.KML
            }
        }

        if (mimeLower.contains("text/html") || mimeLower.contains("application/xhtml")) {
            return if (isLoginHost) DownloadedContentFormat.HTML_LOGIN else DownloadedContentFormat.HTML_PAGE
        }

        return DownloadedContentFormat.UNKNOWN
    }

    /**
     * Downloads KML or KMZ alignment data from a Google My Maps URL or direct KML/KMZ URL.
     * Supports redirects, extensionless URLs, and varied content types while strictly rejecting
     * private/HTML error pages.
     */
    @Throws(KmlUrlDownloadException::class)
    fun downloadRoute(
        rawUrl: String,
        client: OkHttpClient = defaultClient
    ): DownloadedRoutePayload {
        return downloadRouteInternal(rawUrl = rawUrl, client = client, myMapsOriginOverride = null)
    }

    @Throws(KmlUrlDownloadException::class)
    internal fun downloadRouteInternal(
        rawUrl: String,
        client: OkHttpClient = defaultClient,
        myMapsOriginOverride: String? = null
    ): DownloadedRoutePayload {
        val plan = resolveDownloadPlan(rawUrl, myMapsOriginOverride)
        var lastError: KmlUrlDownloadException? = null

        for ((index, candidateUrl) in plan.candidateUrls.withIndex()) {
            logInfo("Fetching KML/KMZ route URL attempt ${index + 1}/${plan.candidateUrls.size}: $candidateUrl")
            try {
                val payload = executeSingleDownload(
                    requestUrl = candidateUrl,
                    originalSourceUrl = normalizeUrlInput(rawUrl),
                    suggestedBaseName = plan.suggestedBaseName,
                    googleMyMapsId = plan.googleMyMapsId,
                    client = client,
                    myMapsOriginOverride = myMapsOriginOverride,
                    allowMyMapsRedirectFollowup = true
                )
                return payload
            } catch (e: KmlUrlDownloadException) {
                lastError = e
                // Do not retry secondary candidate if server explicitly denied access (private map)
                val msgLower = e.message.orEmpty().lowercase(Locale.ROOT)
                val isPermissionDenied = msgLower.contains("private") ||
                    msgLower.contains("permission") ||
                    msgLower.contains("sign-in") ||
                    msgLower.contains("401") ||
                    msgLower.contains("403")
                if (isPermissionDenied || index == plan.candidateUrls.lastIndex) {
                    throw e
                }
                logWarn("Primary URL attempt failed (${e.message}); trying fallback candidate")
            }
        }

        throw lastError ?: KmlUrlDownloadException("Failed to download KML/KMZ from URL.")
    }

    @Throws(KmlUrlDownloadException::class)
    private fun executeSingleDownload(
        requestUrl: String,
        originalSourceUrl: String,
        suggestedBaseName: String?,
        googleMyMapsId: String?,
        client: OkHttpClient,
        myMapsOriginOverride: String?,
        allowMyMapsRedirectFollowup: Boolean
    ): DownloadedRoutePayload {
        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .header("User-Agent", "Mozilla/5.0 (Linux; Android) CTECS/1.0")
            .header(
                "Accept",
                "application/vnd.google-earth.kmz, application/vnd.google-earth.kml+xml, application/xml, text/xml, application/zip, application/octet-stream, */*"
            )
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: UnknownHostException) {
            throw KmlUrlDownloadException(
                "Unable to reach server. Please check your internet connection and verify the URL.",
                e
            )
        } catch (e: SocketTimeoutException) {
            throw KmlUrlDownloadException(
                "Connection timed out while downloading KML/KMZ data. Please try again.",
                e
            )
        } catch (e: IOException) {
            throw KmlUrlDownloadException(
                "Network error while downloading KML/KMZ URL: ${e.message ?: "connection failed"}",
                e
            )
        }

        response.use { resp ->
            val finalUrl = resp.request.url.toString()
            val finalHostLower = resp.request.url.host.lowercase(Locale.ROOT)
            val finalPathLower = resp.request.url.encodedPath.lowercase(Locale.ROOT)

            if (finalHostLower.contains("accounts.google.com") ||
                finalPathLower.contains("/servicelogin") ||
                finalPathLower.contains("/v3/signin")
            ) {
                throw KmlUrlDownloadException(
                    "This Google My Maps project is private or requires Google Sign-In. " +
                        "In Google My Maps, open Share and enable 'Anyone with the link can view', then try again."
                )
            }

            if (!resp.isSuccessful) {
                val errMsg = when (resp.code) {
                    401, 403 -> if (googleMyMapsId != null || finalUrl.contains("/maps/d/")) {
                        "Access denied (HTTP ${resp.code}). This Google My Maps project is private or restricted. " +
                            "Set sharing to 'Anyone with the link can view' and retry."
                    } else {
                        "Access denied (HTTP ${resp.code}). The server requires authentication or permission to download this URL."
                    }
                    404 -> if (googleMyMapsId != null) {
                        "Google My Maps project not found (HTTP 404). Please verify the link and Map ID."
                    } else {
                        "KML/KMZ file not found at this URL (HTTP 404)."
                    }
                    else -> "Server returned HTTP ${resp.code} (${resp.message.ifBlank { "Error" }}) while downloading route."
                }
                throw KmlUrlDownloadException(errMsg)
            }

            // If user pasted a short redirect link (e.g. goo.gl / custom shortener) that resolved to
            // a Google My Maps viewer/edit HTML URL, automatically follow through to its /maps/d/kml endpoint!
            if (allowMyMapsRedirectFollowup && googleMyMapsId == null) {
                val redirectedMapId = parseGoogleMyMapsId(finalUrl)
                if (redirectedMapId != null) {
                    val origin = myMapsOriginOverride?.trimEnd('/') ?: "${resp.request.url.scheme}://${resp.request.url.host}" +
                        if ((resp.request.url.scheme == "https" && resp.request.url.port != 443) ||
                            (resp.request.url.scheme == "http" && resp.request.url.port != 80)
                        ) ":${resp.request.url.port}" else ""
                    val kmlEndpoint = "$origin/maps/d/kml?mid=$redirectedMapId"
                    logInfo("Short/redirect URL resolved to Google My Maps ($redirectedMapId); fetching $kmlEndpoint")
                    return executeSingleDownload(
                        requestUrl = kmlEndpoint,
                        originalSourceUrl = originalSourceUrl,
                        suggestedBaseName = "mymaps_$redirectedMapId",
                        googleMyMapsId = redirectedMapId,
                        client = client,
                        myMapsOriginOverride = myMapsOriginOverride,
                        allowMyMapsRedirectFollowup = false
                    )
                }
            }

            val body = resp.body ?: throw KmlUrlDownloadException("Server returned an empty response body.")
            val contentLength = body.contentLength()
            if (contentLength > MAX_DOWNLOAD_BYTES) {
                throw KmlUrlDownloadException("Remote KML/KMZ file exceeds maximum allowed size (50 MB).")
            }

            val bytes = readBoundedResponseBytes(body.byteStream(), MAX_DOWNLOAD_BYTES)
            if (bytes.isEmpty()) {
                throw KmlUrlDownloadException("Downloaded KML/KMZ file is empty (0 bytes).")
            }

            val contentType = resp.header("Content-Type")
            val contentDisposition = resp.header("Content-Disposition")
            val format = detectContentFormat(bytes, contentType, finalUrl)

            when (format) {
                DownloadedContentFormat.HTML_LOGIN -> {
                    throw KmlUrlDownloadException(
                        "This Google My Maps project is private or requires sign-in. " +
                            "Enable 'Anyone with the link can view' sharing and try again."
                    )
                }
                DownloadedContentFormat.HTML_PAGE -> {
                    val hint = if (googleMyMapsId != null) {
                        "Google My Maps returned an HTML page instead of KML/KMZ data. " +
                            "Verify the map exists and sharing is set to 'Anyone with the link can view'."
                    } else {
                        "The URL returned an HTML webpage instead of valid KML or KMZ geographic data."
                    }
                    throw KmlUrlDownloadException(hint)
                }
                DownloadedContentFormat.UNKNOWN -> {
                    throw KmlUrlDownloadException(
                        "Downloaded content is not a valid KML XML or KMZ archive (Content-Type: ${contentType ?: "unknown"})."
                    )
                }
                DownloadedContentFormat.KMZ, DownloadedContentFormat.KML -> {
                    // Valid candidate format! Resolve deterministic filename matching actual detected content.
                    val dispositionName = parseContentDispositionFileName(contentDisposition)
                    val resolvedFileName = resolveEffectiveFileName(
                        dispositionName = dispositionName,
                        suggestedBaseName = suggestedBaseName,
                        finalHttpUrl = resp.request.url,
                        googleMyMapsId = googleMyMapsId,
                        format = format
                    )
                    val suggestedDisplayName = dispositionName
                        ?.substringBeforeLast('.')
                        ?.takeIf { it.isNotBlank() && !it.equals("doc", ignoreCase = true) }

                    logInfo(
                        "Downloaded ${bytes.size} bytes as $format -> fileName='$resolvedFileName' from '$finalUrl'"
                    )

                    return DownloadedRoutePayload(
                        bytes = bytes,
                        resolvedFileName = resolvedFileName,
                        suggestedDisplayName = suggestedDisplayName,
                        format = format,
                        sourceUrl = originalSourceUrl,
                        finalUrl = finalUrl,
                        googleMyMapsId = googleMyMapsId
                    )
                }
            }
        }
    }

    private fun readBoundedResponseBytes(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream(16384)
        val buffer = ByteArray(16384)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) {
                throw KmlUrlDownloadException("Downloaded KML/KMZ file exceeds maximum allowed size (50 MB).")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    internal fun parseContentDispositionFileName(header: String?): String? {
        if (header.isNullOrBlank()) return null

        // RFC 5987 filename*=UTF-8''encoded_name.kmz
        val extMatch = Regex("filename\\*=([^;]+)", RegexOption.IGNORE_CASE).find(header)
        if (extMatch != null) {
            val raw = extMatch.groupValues[1].trim().trim('"')
            val afterEncoding = raw.substringAfter("''", raw)
            val decoded = try {
                java.net.URLDecoder.decode(afterEncoding, "UTF-8")
            } catch (_: Exception) {
                afterEncoding
            }.substringAfterLast('/').substringAfterLast('\\').trim()
            if (decoded.isNotBlank()) return decoded
        }

        // Standard filename="name.kmz" or filename=name.kmz
        val stdMatch = Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(header)
        if (stdMatch != null) {
            val name = stdMatch.groupValues[1]
                .trim()
                .substringAfterLast('/')
                .substringAfterLast('\\')
            if (name.isNotBlank()) return name
        }

        return null
    }

    internal fun resolveEffectiveFileName(
        dispositionName: String?,
        suggestedBaseName: String?,
        finalHttpUrl: HttpUrl,
        googleMyMapsId: String?,
        format: DownloadedContentFormat
    ): String {
        val ext = if (format == DownloadedContentFormat.KMZ) "kmz" else "kml"

        val rawCandidate = when {
            !dispositionName.isNullOrBlank() -> dispositionName
            !googleMyMapsId.isNullOrBlank() -> "mymaps_$googleMyMapsId"
            !suggestedBaseName.isNullOrBlank() -> suggestedBaseName
            else -> finalHttpUrl.pathSegments.lastOrNull { it.isNotBlank() } ?: "url_alignment"
        }

        val cleanedBase = rawCandidate
            .substringBefore('?')
            .substringBefore('#')
            .let { name ->
                if (name.endsWith(".kmz", true) ||
                    name.endsWith(".kml", true) ||
                    name.endsWith(".xml", true) ||
                    name.endsWith(".zip", true)
                ) {
                    name.substringBeforeLast('.')
                } else {
                    name
                }
            }
            .replace("[^a-zA-Z0-9._ -]".toRegex(), "_")
            .trim()
            .ifBlank {
                if (!googleMyMapsId.isNullOrBlank()) "mymaps_$googleMyMapsId" else "imported_url_alignment"
            }

        return "$cleanedBase.$ext"
    }
}

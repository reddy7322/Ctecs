package com.example.siteregister.network

import android.util.Log
import com.example.BuildConfig
import com.example.siteregister.model.OrganizationType
import com.example.siteregister.model.ProjectCodeRecord
import com.example.siteregister.model.ProjectInfo
import com.example.siteregister.model.UserSession
import com.example.siteregister.model.VerifiedProjectCodeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Production OkHttp client for the existing CTECS Supabase project:
 *   Project Name: Ctecs
 *   Project URL:  https://ytrolykqyjqwjwtifxmi.supabase.co
 *
 * Security boundaries:
 * - Reads only the publishable (anon) key from BuildConfig.SUPABASE_PUBLISHABLE_KEY (configured via
 *   the Secrets panel in AI Studio). Never bundles or sends any service_role key.
 * - All privileged operations execute via PostgreSQL RLS + SECURITY DEFINER RPC endpoints.
 * - Never logs passwords, bearer tokens, or API keys.
 */
class SupabaseSiteRegisterClient(
    private val baseUrl: String = BuildConfig.SUPABASE_URL.trimEnd('/'),
    private val publishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY
) {
    companion object {
        private const val TAG = "SupabaseSiteClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private val realtimeRef = AtomicInteger(1)

    val isConfigured: Boolean
        get() = baseUrl.startsWith("https://") &&
            publishableKey.isNotBlank() &&
            !publishableKey.startsWith("YOUR_")

    data class RealtimeChangeEvent(
        val table: String,
        val eventType: String, // INSERT, UPDATE, DELETE
        val payloadJson: String,
        val receivedAtMs: Long = System.currentTimeMillis()
    )

    // =========================================================================
    // 1. SUPABASE AUTHENTICATION (SIGN UP, SIGN IN, REFRESH, PASSWORD RESET, LOGOUT)
    // =========================================================================

    suspend fun signUp(
        email: String,
        password: String,
        fullName: String,
        username: String
    ): Result<UserSession> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase publishable key not configured in Secrets panel."))
        }
        try {
            val payload = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
                put("data", JSONObject().apply {
                    put("full_name", fullName.trim())
                    put("username", username.trim())
                })
            }
            val req = Request.Builder()
                .url("$baseUrl/auth/v1/signup")
                .addHeader("apikey", publishableKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException(extractError(raw, "Registration failed (${resp.code})")))
                }
                val json = JSONObject(raw)
                val userObj = json.optJSONObject("user") ?: json
                val userId = userObj.optString("id").ifBlank { return@withContext Result.failure(IllegalStateException("Missing user id from Supabase Auth.")) }
                val accessToken = json.optString("access_token")
                val refreshToken = json.optString("refresh_token")
                val expiresIn = json.optLong("expires_in", 3600L)
                val confirmed = !userObj.isNull("email_confirmed_at") || accessToken.isNotBlank()

                Result.success(
                    UserSession(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresAtEpochMs = System.currentTimeMillis() + expiresIn * 1000L,
                        userId = userId,
                        email = email.trim(),
                        emailVerified = confirmed
                    )
                )
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun signInWithPassword(email: String, password: String): Result<UserSession> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase publishable key not configured in Secrets panel."))
        }
        try {
            val payload = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
            }
            val req = Request.Builder()
                .url("$baseUrl/auth/v1/token?grant_type=password")
                .addHeader("apikey", publishableKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException(extractError(raw, "Invalid email or password.")))
                }
                parseTokenResponse(raw, email.trim())
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun refreshSession(refreshToken: String): Result<UserSession> = withContext(Dispatchers.IO) {
        if (!isConfigured || refreshToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Unable to refresh session."))
        }
        try {
            val payload = JSONObject().apply { put("refresh_token", refreshToken) }
            val req = Request.Builder()
                .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", publishableKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException(extractError(raw, "Session expired. Please sign in again.")))
                }
                parseTokenResponse(raw, "")
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.failure(IllegalStateException("Supabase publishable key not configured in Secrets panel."))
        }
        try {
            val payload = JSONObject().apply { put("email", email.trim()) }
            val req = Request.Builder()
                .url("$baseUrl/auth/v1/recover")
                .addHeader("apikey", publishableKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val raw = resp.body?.string().orEmpty()
                    return@withContext Result.failure(IllegalStateException(extractError(raw, "Password reset failed.")))
                }
                Result.success(Unit)
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun logout(accessToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured || accessToken.isBlank()) return@withContext Result.success(Unit)
        try {
            val req = Request.Builder()
                .url("$baseUrl/auth/v1/logout")
                .addHeader("apikey", publishableKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(ByteArray(0).toRequestBody(JSON_MEDIA_TYPE))
                .build()
            httpClient.newCall(req).execute().close()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    // =========================================================================
    // 2. SECURE RPC & POSTGREST OPERATIONS
    // =========================================================================

    suspend fun validateProjectCodeRemote(code: String, accessToken: String? = null): Result<VerifiedProjectCodeResult> =
        withContext(Dispatchers.IO) {
            if (!isConfigured) {
                return@withContext Result.failure(IllegalStateException("Supabase key not configured."))
            }
            try {
                val payload = JSONObject().apply { put("p_code", code.trim().uppercase()) }
                val rpcRes = callRpc("validate_project_code", payload, accessToken)
                rpcRes.map { raw ->
                    val obj = JSONObject(raw)
                    val valid = obj.optBoolean("valid", false)
                    if (!valid) {
                        VerifiedProjectCodeResult(false, obj.optString("error", "Invalid project code."))
                    } else {
                        val permittedArr = obj.optJSONArray("permitted_organization_types")
                        val permitted = mutableListOf<OrganizationType>()
                        if (permittedArr != null) {
                            for (i in 0 until permittedArr.length()) {
                                permitted += OrganizationType.fromDb(permittedArr.optString(i))
                            }
                        }
                        val proj = ProjectInfo(
                            id = obj.optString("project_id"),
                            projectName = obj.optString("project_name"),
                            highwayName = obj.optString("highway_name"),
                            nhShNumber = obj.optString("nh_sh_number"),
                            packageNumber = obj.optString("package_number"),
                            projectLocation = obj.optString("project_location"),
                            stateName = obj.optString("state_name"),
                            contractorName = obj.optString("contractor_name"),
                            concessionaireName = obj.optString("concessionaire_name"),
                            clientName = obj.optString("client_name"),
                            pmcOrganization = obj.optString("pmc_organization"),
                            ieOrganization = obj.optString("ie_organization"),
                            assignedPmcName = obj.optString("assigned_pmc_name"),
                            assignedPmcContact = obj.optString("assigned_pmc_contact"),
                            projectIdentification = obj.optString("project_identification"),
                            projectStatus = obj.optString("project_status", "ACTIVE"),
                            registrationStatus = obj.optString("registration_status", "OPEN"),
                            permittedOrganizationTypes = permitted.ifEmpty { OrganizationType.entries }
                        )
                        val codeRec = ProjectCodeRecord(
                            id = obj.optString("invitation_id"),
                            projectId = proj.id,
                            projectCode = obj.optString("project_code", code.trim().uppercase()),
                            permittedOrganizationTypes = proj.permittedOrganizationTypes
                        )
                        VerifiedProjectCodeResult(true, null, proj, codeRec)
                    }
                }
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    suspend fun callRpc(
        functionName: String,
        params: JSONObject,
        accessToken: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.failure(IllegalStateException("Supabase key not configured."))
        try {
            val reqBuilder = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/$functionName")
                .addHeader("apikey", publishableKey)
                .addHeader("Content-Type", "application/json")
                .post(params.toString().toRequestBody(JSON_MEDIA_TYPE))
            if (!accessToken.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $accessToken")
            }
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException(extractError(raw, "RPC $functionName failed (${resp.code})")))
                }
                Result.success(raw)
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun upsertTableRow(
        table: String,
        bodyJson: JSONObject,
        accessToken: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.failure(IllegalStateException("Supabase key not configured."))
        try {
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/$table")
                .addHeader("apikey", publishableKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException(extractError(raw, "Sync failed on $table (${resp.code})")))
                }
                Result.success(raw)
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun selectRows(
        table: String,
        queryFilter: String,
        accessToken: String
    ): Result<JSONArray> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.failure(IllegalStateException("Supabase key not configured."))
        try {
            val url = if (queryFilter.isBlank()) "$baseUrl/rest/v1/$table?select=*" else "$baseUrl/rest/v1/$table?$queryFilter"
            val req = Request.Builder()
                .url(url)
                .addHeader("apikey", publishableKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException(extractError(raw, "Query $table failed (${resp.code})")))
                }
                Result.success(JSONArray(raw.ifBlank { "[]" }))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun uploadStorageObject(
        bucket: String,
        objectPath: String,
        bytes: ByteArray,
        mimeType: String,
        accessToken: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.failure(IllegalStateException("Supabase key not configured."))
        try {
            val cleanPath = objectPath.trimStart('/')
            val req = Request.Builder()
                .url("$baseUrl/storage/v1/object/$bucket/$cleanPath")
                .addHeader("apikey", publishableKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("x-upsert", "true")
                .post(bytes.toRequestBody(mimeType.toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException(extractError(raw, "Storage upload failed (${resp.code})")))
                }
                Result.success("$bucket/$cleanPath")
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    // =========================================================================
    // 3. SUPABASE REALTIME WEBSOCKET SUBSCRIPTION (PHOENIX CHANNELS PROTOCOL)
    // =========================================================================

    fun observeProjectRealtimeChanges(
        projectId: String,
        accessToken: String
    ): Flow<RealtimeChangeEvent> = callbackFlow {
        if (!isConfigured || accessToken.isBlank() || projectId.isBlank()) {
            close()
            return@callbackFlow
        }

        val wsBase = baseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        val encodedKey = URLEncoder.encode(publishableKey, "UTF-8")
        val wsUrl = "$wsBase/realtime/v1/websocket?apikey=$encodedKey&vsn=1.0.0"

        val request = Request.Builder().url(wsUrl).build()
        val topic = "realtime:public:site_register_$projectId"

        val socket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val joinMsg = JSONObject().apply {
                    put("topic", topic)
                    put("event", "phx_join")
                    put("ref", realtimeRef.getAndIncrement().toString())
                    put("payload", JSONObject().apply {
                        put("access_token", accessToken)
                        put("config", JSONObject().apply {
                            put("postgres_changes", JSONArray().apply {
                                listOf(
                                    "site_registers", "site_register_entries", "site_measurements",
                                    "site_segments", "tackcoat_tests", "site_observations",
                                    "site_signatures", "site_approvals", "site_edit_requests",
                                    "site_edit_permissions", "registration_requests"
                                ).forEach { tbl ->
                                    put(JSONObject().apply {
                                        put("event", "*")
                                        put("schema", "public")
                                        put("table", tbl)
                                        put("filter", "project_id=eq.$projectId")
                                    })
                                }
                            })
                        })
                    })
                }
                webSocket.send(joinMsg.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    val event = msg.optString("event")
                    if (event == "postgres_changes") {
                        val data = msg.optJSONObject("payload")?.optJSONObject("data")
                        if (data != null) {
                            trySend(
                                RealtimeChangeEvent(
                                    table = data.optString("table"),
                                    eventType = data.optString("type"),
                                    payloadJson = data.toString()
                                )
                            )
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Ignored malformed realtime message: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Realtime channel closed: ${t.message}")
            }
        })

        awaitClose {
            try {
                val leaveMsg = JSONObject().apply {
                    put("topic", topic)
                    put("event", "phx_leave")
                    put("ref", realtimeRef.getAndIncrement().toString())
                    put("payload", JSONObject())
                }
                socket.send(leaveMsg.toString())
                socket.close(1000, "Unsubscribed from CTECS Site Register")
            } catch (_: Throwable) {}
        }
    }

    private fun parseTokenResponse(raw: String, fallbackEmail: String): Result<UserSession> {
        val json = JSONObject(raw)
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        val expiresIn = json.optLong("expires_in", 3600L)
        val userObj = json.optJSONObject("user")
        val uid = userObj?.optString("id").orEmpty()
        val email = userObj?.optString("email").orEmpty().ifBlank { fallbackEmail }
        val verified = userObj == null || !userObj.isNull("email_confirmed_at")
        if (access.isBlank() || uid.isBlank()) {
            return Result.failure(IllegalStateException("Authentication token response incomplete."))
        }
        return Result.success(
            UserSession(
                accessToken = access,
                refreshToken = refresh,
                expiresAtEpochMs = System.currentTimeMillis() + expiresIn * 1000L,
                userId = uid,
                email = email,
                emailVerified = verified
            )
        )
    }

    private fun extractError(raw: String, fallback: String): String {
        return try {
            val obj = JSONObject(raw)
            obj.optString("error_description")
                .ifBlank { obj.optString("msg") }
                .ifBlank { obj.optString("message") }
                .ifBlank { obj.optString("error") }
                .ifBlank { fallback }
        } catch (_: Throwable) {
            fallback
        }
    }
}

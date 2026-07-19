package com.example.missiontrackermap.repository

import android.util.Log
import com.example.missiontrackermap.SupabaseConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "SupabaseAuthManager"

@Serializable
private data class LoginRequest(val email: String, val password: String)

@Serializable
private data class LoginUser(val id: String)

@Serializable
private data class LoginResponse(val access_token: String, val user: LoginUser)

/**
 * Handles Supabase email/password authentication and JWT token management.
 */
class SupabaseAuthManager {

    private var jwt: String? = null
    private var userId: String? = null
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    fun login(email: String, password: String): Result<String> {
        val url = "${SupabaseConfig.URL}/auth/v1/token?grant_type=password"
        val body = json.encodeToString(LoginRequest.serializer(), LoginRequest(email, password))
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", SupabaseConfig.ANON_KEY)
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorMsg = try {
                        // Try to extract error_description from JSON response
                        val errorJson = json.parseToJsonElement(responseBody)
                        errorJson.toString()
                    } catch (_: Exception) {
                        responseBody
                    }
                    Log.e(TAG, "Login failed: $errorMsg")
                    return Result.failure(Exception("Login failed: $errorMsg"))
                }
                val loginResponse = json.decodeFromString<LoginResponse>(responseBody)
                jwt = loginResponse.access_token
                userId = loginResponse.user.id
                Log.i(TAG, "Login successful for user ${loginResponse.user.id}")
                Result.success(loginResponse.user.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login error: ${e.message}", e)
            Result.failure(e)
        }
    }

    val isAuthenticated: Boolean get() = jwt != null

    fun getAuthHeaders(): Map<String, String> {
        val token = jwt ?: throw IllegalStateException("Not authenticated")
        return mapOf(
            "apikey" to SupabaseConfig.ANON_KEY,
            "Authorization" to "Bearer $token"
        )
    }

    fun getUserId(): String? = userId

    fun logout() {
        jwt = null
        userId = null
        Log.i(TAG, "Logged out")
    }
}

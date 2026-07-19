package com.example.missiontrackermap.repository

import com.example.missiontrackermap.model.CalibrationData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SupabaseSyncTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var tempDir: File
    private lateinit var mockContext: android.content.Context

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("temp_sync_test", "").apply {
            delete()
            mkdir()
        }
        mockContext = object : android.content.ContextWrapper(null) {
            override fun getFilesDir(): File = tempDir
        }
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private class MockInterceptor : Interceptor {
        var imageRequestsCount = 0
        var lastIfNoneMatchHeader: String? = null
        var responseEtag: String? = "\"test-etag-123\""
        var return304 = false

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val urlString = request.url.toString()

            if (urlString.contains("/rest/v1/missions")) {
                val jsonResponse = """
                    [
                        {
                            "version": 2,
                            "json_data": {
                                "version": 2,
                                "image": "test-image.png",
                                "imageWidth": 100,
                                "imageHeight": 100,
                                "points": []
                            }
                        }
                    ]
                """.trimIndent()
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonResponse.toResponseBody("application/json".toMediaType()))
                    .build()
            } else if (urlString.contains("/storage/v1/object/public/mission-images")) {
                imageRequestsCount++
                lastIfNoneMatchHeader = request.header("If-None-Match")

                if (return304 || (lastIfNoneMatchHeader != null && lastIfNoneMatchHeader == responseEtag)) {
                    return Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(304)
                        .message("Not Modified")
                        .body("".toResponseBody(null))
                        .build()
                } else {
                    val bodyBytes = byteArrayOf(9, 8, 7, 6)
                    val builder = Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(bodyBytes.toResponseBody("image/png".toMediaType()))
                    
                    if (responseEtag != null) {
                        builder.header("ETag", responseEtag!!)
                    }
                    return builder.build()
                }
            }

            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(404)
                .message("Not Found")
                .body("".toResponseBody(null))
                .build()
        }
    }

    @Test
    fun downloadMission_noEtagFile_downloadsImageAndSavesEtag() {
        val interceptor = MockInterceptor()
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val syncManager = SupabaseSyncManager(mockContext, client)

        syncManager.downloadMission("test-mission")

        val missionDir = File(File(tempDir, "missions"), "test-mission")
        val imageFile = File(missionDir, "test-image.png")
        val etagFile = File(missionDir, "test-image.png.etag")
        
        assertTrue(imageFile.exists(), "Image file should be created")
        assertTrue(etagFile.exists(), "Etag file should be created")
        assertEquals(4, imageFile.length(), "Image size should be 4 bytes")
        assertEquals("\"test-etag-123\"", etagFile.readText().trim())
        assertEquals(1, interceptor.imageRequestsCount)
    }

    @Test
    fun downloadMission_etagMatches_returns304AndSkipsWritingImage() {
        val interceptor = MockInterceptor()
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val syncManager = SupabaseSyncManager(mockContext, client)

        // 1. Initial download
        syncManager.downloadMission("test-mission")
        assertEquals(1, interceptor.imageRequestsCount)

        // Modify file contents locally to verify it isn't overwritten
        val missionDir = File(File(tempDir, "missions"), "test-mission")
        val imageFile = File(missionDir, "test-image.png")
        imageFile.writeBytes(byteArrayOf(1, 1, 1))

        // 2. Second download (should hit 304 and skip write)
        syncManager.downloadMission("test-mission")
        assertEquals(2, interceptor.imageRequestsCount)
        assertEquals("\"test-etag-123\"", interceptor.lastIfNoneMatchHeader)
        
        assertEquals(3, imageFile.length(), "Image file should not be overwritten (size 3)")
        assertTrue(imageFile.readBytes().contentEquals(byteArrayOf(1, 1, 1)))
    }

    @Test
    fun downloadMission_etagChanged_redownloadsAndUpdatesEtag() {
        val interceptor = MockInterceptor()
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val syncManager = SupabaseSyncManager(mockContext, client)

        // 1. Initial download
        syncManager.downloadMission("test-mission")
        assertEquals(1, interceptor.imageRequestsCount)

        // 2. Server ETag changes
        interceptor.responseEtag = "\"new-etag-456\""

        // 3. Second download (should hit 200 and overwrite)
        syncManager.downloadMission("test-mission")
        assertEquals(2, interceptor.imageRequestsCount)
        assertEquals("\"test-etag-123\"", interceptor.lastIfNoneMatchHeader)

        val missionDir = File(File(tempDir, "missions"), "test-mission")
        val imageFile = File(missionDir, "test-image.png")
        val etagFile = File(missionDir, "test-image.png.etag")

        assertEquals(4, imageFile.length(), "Image size should be updated to 4 bytes")
        assertTrue(imageFile.readBytes().contentEquals(byteArrayOf(9, 8, 7, 6)))
        assertEquals("\"new-etag-456\"", etagFile.readText().trim())
    }

    @Test
    fun parseSupabaseMissionVersion_validJson_succeeds() {
        val jsonString = """
            [
                {"id": "scarif", "version": 2},
                {"id": "tatooine", "version": 5}
            ]
        """.trimIndent()

        val parsed = json.decodeFromString<List<SupabaseMissionVersion>>(jsonString)
        assertEquals(2, parsed.size)
        assertEquals("scarif", parsed[0].id)
        assertEquals(2, parsed[0].version)
        assertEquals("tatooine", parsed[1].id)
        assertEquals(5, parsed[1].version)
    }

    @Test
    fun parseSupabaseMissionDetail_validJson_succeeds() {
        val jsonString = """
            [
                {
                    "version": 2,
                    "json_data": {
                        "version": 2,
                        "image": "scarif.png",
                        "imageWidth": 4220,
                        "imageHeight": 5964,
                        "points": [
                            {
                                "name": "01",
                                "pixel": {"x": 2980.0, "y": 1348.0},
                                "gps": {"latitude": 50.880185, "longitude": 15.1371217},
                                "missionObjective": "Objective 1"
                            }
                        ]
                    }
                }
            ]
        """.trimIndent()

        val parsed = json.decodeFromString<List<SupabaseMissionDetail>>(jsonString)
        assertEquals(1, parsed.size)
        assertEquals(2, parsed[0].version)
        
        val rawJsonData = parsed[0].json_data.toString()
        val calibration = json.decodeFromString<CalibrationData>(rawJsonData)
        
        assertEquals(2, calibration.version)
        assertEquals("scarif.png", calibration.image)
        assertEquals(1, calibration.points.size)
        assertEquals("01", calibration.points[0].name)
        assertEquals("Objective 1", calibration.points[0].missionObjective)
    }
}

package com.project200.feature.matching

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kakao.vectormap.MapAuthException
import com.project200.feature.matching.map.MatchingMapReadiness
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MatchingMapEvidenceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder(InstrumentationRegistry.getInstrumentation().targetContext.filesDir)

    @Test
    fun mapCallbacksAppendSafeOrderedEventsWithoutChangingOtherFixtureEvidence() {
        val context = debugContext()
        val file = temporaryFolder.newFile(MatchingFixtureEvidence.FILE_NAME)
        file.writeText(JSONObject().put("runId", "0123456789abcdef").put("events", JSONArray()).toString())
        val secret = "https://private.invalid/request?token=synthetic-secret"
        val first = MatchingFixtureEvidence.mapStarted(context)
        assertEquals(1, first)
        MatchingFixtureEvidence.map(context, first, MatchingMapReadiness().ready())
        MatchingFixtureEvidence.chat(context, secret, 42, 73)
        MatchingFixtureEvidence.map(context, first, MatchingMapReadiness().failed(MapAuthException(1001, secret)))
        val second = MatchingFixtureEvidence.mapStarted(context)
        assertEquals(5, second)
        MatchingFixtureEvidence.map(context, second, MatchingMapReadiness().ready())

        val document = JSONObject(file.readText())
        assertEquals("0123456789abcdef", document.getString("runId"))
        val events = document.getJSONArray("events")
        assertEquals(6, events.length())
        assertEquals(secret, events.getJSONObject(2).getString("peerId"))
        val maps = (0 until events.length()).map { events.getJSONObject(it) }.filter { it.getString("type") == "map" }
        assertEquals(listOf(1, 2, 4, 5, 6), maps.map { it.getInt("sequence") })
        assertEquals(listOf(1, 1, 1, 5, 5), maps.map { it.getInt("attempt") })
        assertEquals(listOf("loading", "ready", "failed", "loading", "ready"), maps.map { it.getString("state") })
        val failed = maps[2]
        assertEquals("initialization", failed.getString("category"))
        assertEquals(1001, failed.getInt("sdkErrorCode"))
        maps.forEach { event ->
            assertFalse(event.toString().contains(secret))
            assertFalse(event.has("message"))
            assertFalse(event.has("cause"))
        }
        assertEquals(384, Os.stat(file.path).st_mode and 511)
    }

    @Test
    fun absentArmingAndTheExistingThousandEventLimitRemainEnforced() {
        val context = debugContext()
        assertEquals(null, MatchingFixtureEvidence.mapStarted(context))
        assertTrue(temporaryFolder.root.listFiles().isNullOrEmpty())
        val events = JSONArray()
        repeat(1000) { events.put(JSONObject().put("type", "denied")) }
        val file = temporaryFolder.newFile(MatchingFixtureEvidence.FILE_NAME)
        val original = JSONObject().put("runId", "0123456789abcdef").put("events", events).toString()
        file.writeText(original)

        try {
            MatchingFixtureEvidence.mapStarted(context)
            throw AssertionError("Expected matching_evidence_limit")
        } catch (failure: IllegalStateException) {
            assertEquals("matching_evidence_limit", failure.message)
        }
        assertEquals(original, file.readText())
    }

    private fun debugContext(): Context {
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val debugApplicationInfo = ApplicationInfo(baseContext.applicationInfo).apply {
            flags = flags or ApplicationInfo.FLAG_DEBUGGABLE
        }
        return object : ContextWrapper(baseContext) {
            override fun getApplicationInfo(): ApplicationInfo = debugApplicationInfo

            override fun getFilesDir(): File = temporaryFolder.root
        }
    }
}

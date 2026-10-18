package com.project200.feature.matching

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.project200.domain.model.BaseResult
import com.project200.feature.matching.map.MatchingMapReadiness
import kotlinx.coroutines.CancellationException
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
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MatchingPlaceLoadEvidenceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder(InstrumentationRegistry.getInstrumentation().targetContext.filesDir)

    @Test
    fun actualTerminalsAndUnfinishedRequestsUseOnlySafeFieldsAndOriginalSequence() {
        val context = debugContext()
        val file = armedFile()
        val first = checkNotNull(MatchingFixtureEvidence.placeLoadStarted(context))
        val mapAttempt = MatchingFixtureEvidence.mapStarted(context)
        MatchingFixtureEvidence.map(context, mapAttempt, MatchingMapReadiness().ready())
        MatchingFixtureEvidence.chat(context, SECRET, 42, 73)
        val second = checkNotNull(MatchingFixtureEvidence.placeLoadStarted(context))
        MatchingFixtureEvidence.placeLoadFinished(context, second, BaseResult.Error(SECRET, SECRET, IllegalStateException(SECRET)))
        MatchingFixtureEvidence.placeLoadThrown(context, first, CancellationException(SECRET))
        val third = checkNotNull(MatchingFixtureEvidence.placeLoadStarted(context))
        MatchingFixtureEvidence.placeLoadFinished(context, third, BaseResult.Success(listOf(SECRET)))
        val fourth = checkNotNull(MatchingFixtureEvidence.placeLoadStarted(context))
        MatchingFixtureEvidence.placeLoadThrown(context, fourth, IOException(SECRET))
        MatchingFixtureEvidence.placeLoadStarted(context)

        val document = JSONObject(file.readText())
        assertEquals("0123456789abcdef", document.getString("runId"))
        val events = document.getJSONArray("events")
        assertEquals(12, events.length())
        val loads = (0 until events.length()).map { events.getJSONObject(it) }.filter { it.getString("type") == "place_load" }
        assertEquals(listOf(1, 5, 6, 7, 8, 9, 10, 11, 12), loads.map { it.getInt("sequence") })
        assertEquals(listOf(1, 5, 5, 1, 8, 8, 10, 10, 12), loads.map { it.getInt("request") })
        assertEquals(
            listOf("started", "started", "error", "cancelled", "started", "success", "started", "exception", "started"),
            loads.map { it.getString("state") },
        )
        assertEquals("unknown", loads[2].getString("category"))
        assertEquals("network", loads[7].getString("category"))
        loads.forEach { event ->
            val keys = setOf("type", "sequence", "request", "state", "elapsedMs")
            val expected = if (event.getString("state") in setOf("error", "exception")) keys + "category" else keys
            assertEquals(expected, event.keys().asSequence().toSet())
            assertFalse(event.toString().contains(SECRET))
            assertTrue(event.getLong("elapsedMs") >= 0)
            if (event.getString("state") == "started") assertEquals(0L, event.getLong("elapsedMs"))
        }
        assertEquals("loading", events.getJSONObject(1).getString("state"))
        assertEquals("ready", events.getJSONObject(2).getString("state"))
        assertEquals(SECRET, events.getJSONObject(3).getString("peerId"))
        assertEquals(384, Os.stat(file.path).st_mode and 511)
    }

    @Test
    fun missingStartAndRemovedArmingNeverCreateTerminalEvidence() {
        val context = debugContext()
        assertEquals(null, MatchingFixtureEvidence.placeLoadStarted(context))
        assertTrue(temporaryFolder.root.listFiles().isNullOrEmpty())
        val file = armedFile()
        val original = file.readText()

        MatchingFixtureEvidence.placeLoadFinished(context, null, BaseResult.Success(Unit))
        MatchingFixtureEvidence.placeLoadThrown(context, null, IllegalStateException(SECRET))
        assertEquals(original, file.readText())
        val request = checkNotNull(MatchingFixtureEvidence.placeLoadStarted(context))
        assertTrue(file.delete())
        MatchingFixtureEvidence.placeLoadFinished(context, request, BaseResult.Success(Unit))
        MatchingFixtureEvidence.placeLoadThrown(context, request, CancellationException(SECRET))
        assertFalse(file.exists())
    }

    @Test
    fun brokenEvidenceAndTheExistingEventLimitDoNotReplaceRequestResultsWithDiagnosticFailures() {
        val context = debugContext()
        val file = armedFile()
        val request = checkNotNull(MatchingFixtureEvidence.placeLoadStarted(context))
        file.writeText("{")

        assertEquals(null, MatchingFixtureEvidence.placeLoadStarted(context))
        MatchingFixtureEvidence.placeLoadFinished(context, request, BaseResult.Error(message = SECRET))
        MatchingFixtureEvidence.placeLoadThrown(context, request, IllegalStateException(SECRET))
        assertEquals("{", file.readText())

        val events = JSONArray()
        repeat(1000) { events.put(JSONObject().put("type", "denied")) }
        val limited = JSONObject().put("runId", "0123456789abcdef").put("events", events).toString()
        file.writeText(limited)

        assertEquals(null, MatchingFixtureEvidence.placeLoadStarted(context))
        MatchingFixtureEvidence.placeLoadFinished(context, request, BaseResult.Success(Unit))
        assertEquals(limited, file.readText())
    }

    private fun armedFile(): File =
        temporaryFolder.newFile(MatchingFixtureEvidence.FILE_NAME).apply {
            writeText(JSONObject().put("runId", "0123456789abcdef").put("events", JSONArray()).toString())
        }

    private fun debugContext(): Context {
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val debugApplicationInfo =
            ApplicationInfo(baseContext.applicationInfo).apply {
                flags = flags or ApplicationInfo.FLAG_DEBUGGABLE
            }
        return object : ContextWrapper(baseContext) {
            override fun getApplicationInfo(): ApplicationInfo = debugApplicationInfo

            override fun getFilesDir(): File = temporaryFolder.root
        }
    }

    private companion object {
        const val SECRET = "https://private.invalid/request?token=synthetic-secret"
    }
}

package com.project200.feature.matching

import android.content.Context
import android.content.pm.ApplicationInfo
import com.project200.domain.model.BaseResult
import com.project200.feature.matching.map.MatchingMapLabelKind
import com.project200.feature.matching.map.MatchingMapReadiness
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MatchingFixtureEvidenceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context = mockk<Context>()
    private val applicationInfo = mockk<ApplicationInfo>()

    @Before
    fun setUp() {
        every { context.applicationInfo } returns applicationInfo
        every { context.filesDir } returns temporaryFolder.root
    }

    @Test
    fun `release stays unarmed even when the evidence file exists`() {
        applicationInfo.flags = 0
        temporaryFolder.newFile(MatchingFixtureEvidence.FILE_NAME)

        assertFalse(MatchingFixtureEvidence.isArmed(context))
        verify(exactly = 0) { context.filesDir }
    }

    @Test
    fun `debug without fixture evidence stays unarmed`() {
        applicationInfo.flags = ApplicationInfo.FLAG_DEBUGGABLE

        assertFalse(MatchingFixtureEvidence.isArmed(context))
    }

    @Test
    fun `debug requires an actual evidence file rather than a directory`() {
        applicationInfo.flags = ApplicationInfo.FLAG_DEBUGGABLE
        temporaryFolder.newFolder(MatchingFixtureEvidence.FILE_NAME)

        assertFalse(MatchingFixtureEvidence.isArmed(context))
    }

    @Test
    fun `debug is armed only while fixture evidence exists`() {
        applicationInfo.flags = ApplicationInfo.FLAG_DEBUGGABLE
        val evidence = temporaryFolder.newFile(MatchingFixtureEvidence.FILE_NAME)

        assertTrue(MatchingFixtureEvidence.isArmed(context))
        assertTrue(evidence.delete())
        assertFalse(MatchingFixtureEvidence.isArmed(context))
        assertFalse(File(temporaryFolder.root, MatchingFixtureEvidence.FILE_NAME).exists())
    }

    @Test
    fun `release map diagnostics never inspect or write private evidence`() {
        applicationInfo.flags = 0
        val file = temporaryFolder.newFile(MatchingFixtureEvidence.FILE_NAME)
        file.writeText("unchanged-private-evidence")

        assertEquals(null, MatchingFixtureEvidence.mapStarted(context))
        MatchingFixtureEvidence.map(context, 1, MatchingMapReadiness())
        MatchingFixtureEvidence.mapLabelClick(context, 1, MatchingMapLabelKind.CLUSTER)

        assertEquals("unchanged-private-evidence", file.readText())
        verify(exactly = 0) { context.filesDir }
    }

    @Test
    fun `unarmed debug map diagnostics do not create an evidence file`() {
        applicationInfo.flags = ApplicationInfo.FLAG_DEBUGGABLE

        assertEquals(null, MatchingFixtureEvidence.mapStarted(context))
        MatchingFixtureEvidence.map(context, null, MatchingMapReadiness())
        MatchingFixtureEvidence.mapLabelClick(context, null, MatchingMapLabelKind.PLACE)

        assertFalse(File(temporaryFolder.root, MatchingFixtureEvidence.FILE_NAME).exists())
    }

    @Test
    fun `release place diagnostics never read or write existing private evidence`() {
        applicationInfo.flags = 0
        val file = temporaryFolder.newFile(MatchingFixtureEvidence.FILE_NAME)
        file.writeText("unchanged-private-evidence")
        val request = MatchingPlaceLoadRequest(sequence = 1, startedAtNanos = System.nanoTime())

        assertEquals(null, MatchingFixtureEvidence.placeLoadStarted(context))
        MatchingFixtureEvidence.placeLoadFinished(context, request, BaseResult.Success(emptyList<Any>()))
        MatchingFixtureEvidence.placeLoadFinished(context, request, BaseResult.Error(message = "private-error"))
        MatchingFixtureEvidence.placeLoadThrown(context, request, IllegalStateException("private-error"))

        assertEquals("unchanged-private-evidence", file.readText())
        verify(exactly = 0) { context.filesDir }
    }

    @Test
    fun `unarmed debug place diagnostics never create private evidence`() {
        applicationInfo.flags = ApplicationInfo.FLAG_DEBUGGABLE
        val request = MatchingFixtureEvidence.placeLoadStarted(context)

        assertEquals(null, request)
        MatchingFixtureEvidence.placeLoadFinished(context, request, BaseResult.Success(emptyList<Any>()))
        MatchingFixtureEvidence.placeLoadThrown(context, request, IllegalStateException("private-error"))

        assertFalse(File(temporaryFolder.root, MatchingFixtureEvidence.FILE_NAME).exists())
    }
}

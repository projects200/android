package com.project200.feature.matching

import android.content.Context
import android.content.pm.ApplicationInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
}

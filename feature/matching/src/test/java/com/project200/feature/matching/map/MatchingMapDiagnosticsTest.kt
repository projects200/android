package com.project200.feature.matching.map

import com.kakao.vectormap.MapAuthException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class MatchingMapDiagnosticsTest {
    @Test
    fun `public SDK codes distinguish authentication from initialization and rendering`() {
        val expected =
            mapOf(
                -1 to MatchingMapErrorKind.UNKNOWN,
                -2 to MatchingMapErrorKind.NETWORK,
                -3 to MatchingMapErrorKind.NETWORK,
                -4 to MatchingMapErrorKind.NETWORK,
                400 to MatchingMapErrorKind.REQUEST,
                401 to MatchingMapErrorKind.AUTH,
                403 to MatchingMapErrorKind.AUTH,
                429 to MatchingMapErrorKind.QUOTA,
                499 to MatchingMapErrorKind.NETWORK,
                1001 to MatchingMapErrorKind.INITIALIZATION,
                1002 to MatchingMapErrorKind.RENDERING,
                1003 to MatchingMapErrorKind.AUTH,
                987654 to MatchingMapErrorKind.UNKNOWN,
            )

        expected.forEach { (code, kind) ->
            val failure = MatchingMapFailure.from(MapAuthException(code, SECRET))

            assertEquals(kind, failure.kind)
            assertEquals(code, failure.sdkErrorCode)
            assertFalse(failure.toString().contains(SECRET))
        }
    }

    @Test
    fun `classification reads only the public integer and never exception details`() {
        val error = mockk<MapAuthException>()
        every { error.getErrorCode() } returns 1001

        assertEquals(MatchingMapErrorKind.INITIALIZATION, MatchingMapFailure.from(error).kind)
        verify(exactly = 0) { error.message }
        verify(exactly = 0) { error.cause }
        verify(exactly = 0) { error.stackTrace }
        verify(exactly = 0) { error.toString() }
    }

    @Test
    fun `direct supported network exceptions have no fabricated SDK code`() {
        listOf(
            ConnectException(SECRET),
            SocketTimeoutException(SECRET),
            UnknownHostException(SECRET),
            SSLException(SECRET),
        ).forEach { error ->
            val failure = MatchingMapFailure.from(error)

            assertEquals(MatchingMapErrorKind.NETWORK, failure.kind)
            assertEquals(null, failure.sdkErrorCode)
            assertFalse(failure.toString().contains(SECRET))
        }
    }

    @Test
    fun `arbitrary exception messages and nested causes do not imply authentication`() {
        val error = IllegalStateException(SECRET, MapAuthException(1003, SECRET))
        val failure = MatchingMapFailure.from(error)

        assertEquals(MatchingMapErrorKind.UNKNOWN, failure.kind)
        assertEquals(null, failure.sdkErrorCode)
        assertFalse(failure.toString().contains(SECRET))
    }

    @Test
    fun `failed callback is terminal even if the SDK reports ready later`() {
        val loading = MatchingMapReadiness()
        val failed = loading.failed(MapAuthException(1002, SECRET))

        assertEquals(MatchingMapStatus.LOADING, loading.status)
        assertEquals(MatchingMapStatus.FAILED, failed.status)
        assertEquals("matching_map_failed", failed.testTag)
        assertSame(failed, failed.ready())
        assertSame(failed, failed.failed(MapAuthException(401, SECRET)))
        assertEquals(MatchingMapErrorKind.RENDERING, failed.failure?.kind)
    }

    private companion object {
        const val SECRET = "https://private.invalid/map?token=synthetic-secret"
    }
}

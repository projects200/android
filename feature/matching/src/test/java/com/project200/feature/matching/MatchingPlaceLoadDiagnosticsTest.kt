package com.project200.feature.matching

import com.project200.domain.model.BaseResult
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException

class MatchingPlaceLoadDiagnosticsTest {
    @Test
    fun `only established error codes produce a known category`() {
        val expected =
            mapOf(
                "NETWORK_ERROR" to MatchingPlaceLoadErrorKind.NETWORK,
                "MAPPING_ERROR" to MatchingPlaceLoadErrorKind.MAPPING,
                "AUTHENTICATION_FAILED" to MatchingPlaceLoadErrorKind.AUTH,
                "USER_ID_HEADER_MISSING" to MatchingPlaceLoadErrorKind.AUTH,
                "401" to MatchingPlaceLoadErrorKind.AUTH,
                "403" to MatchingPlaceLoadErrorKind.AUTH,
                "500" to MatchingPlaceLoadErrorKind.SERVER,
                "503" to MatchingPlaceLoadErrorKind.SERVER,
                "599" to MatchingPlaceLoadErrorKind.SERVER,
                "UNKNOWN_ERROR" to MatchingPlaceLoadErrorKind.UNKNOWN,
                "404" to MatchingPlaceLoadErrorKind.UNKNOWN,
                "5000" to MatchingPlaceLoadErrorKind.UNKNOWN,
                "+500" to MatchingPlaceLoadErrorKind.UNKNOWN,
                " 503" to MatchingPlaceLoadErrorKind.UNKNOWN,
                SECRET to MatchingPlaceLoadErrorKind.UNKNOWN,
            )

        expected.forEach { (code, kind) ->
            val category = MatchingPlaceLoadErrorKind.from(BaseResult.Error(code, SECRET))

            assertEquals(kind, category)
            assertFalse(category.value.contains(SECRET))
        }
        assertEquals(MatchingPlaceLoadErrorKind.UNKNOWN, MatchingPlaceLoadErrorKind.from(BaseResult.Error(message = SECRET)))
    }

    @Test
    fun `mapping code takes precedence over an IO cause and unknown codes use only the direct cause type`() {
        val cause = IOException(SECRET)

        assertEquals(MatchingPlaceLoadErrorKind.MAPPING, MatchingPlaceLoadErrorKind.from(BaseResult.Error("MAPPING_ERROR", SECRET, cause)))
        assertEquals(MatchingPlaceLoadErrorKind.NETWORK, MatchingPlaceLoadErrorKind.from(BaseResult.Error(SECRET, SECRET, cause)))
        assertEquals(MatchingPlaceLoadErrorKind.NETWORK, MatchingPlaceLoadErrorKind.from(cause))
        assertEquals(MatchingPlaceLoadErrorKind.UNKNOWN, MatchingPlaceLoadErrorKind.from(IllegalStateException(SECRET, cause)))
    }

    @Test
    fun `exception classification never reads raw exception details`() {
        val failure = mockk<IllegalStateException>()

        assertEquals(MatchingPlaceLoadErrorKind.UNKNOWN, MatchingPlaceLoadErrorKind.from(failure))
        verify(exactly = 0) { failure.message }
        verify(exactly = 0) { failure.cause }
        verify(exactly = 0) { failure.stackTrace }
        verify(exactly = 0) { failure.toString() }
    }

    private companion object {
        const val SECRET = "https://private.invalid/request?token=synthetic-secret"
    }
}

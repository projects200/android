package com.project200.common.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NetworkMonitorTest {
    @Test
    fun `인터넷이 있어도 기본 네트워크 검증 전에는 재시도 연결로 인정하지 않는다`() {
        val context = mockk<Context>()
        val manager = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns manager
        every { manager.activeNetwork } returns network
        every { manager.getNetworkCapabilities(network) } returns networkCapabilities(validated = false)
        val monitor = NetworkMonitor(context)
        assertThat(monitor.isCurrentlyConnected()).isTrue()
        assertThat(monitor.hasValidatedInternet()).isFalse()
        every { manager.getNetworkCapabilities(network) } returns networkCapabilities(validated = true)
        assertThat(monitor.hasValidatedInternet()).isTrue()
        every { manager.activeNetwork } returns null
        assertThat(monitor.hasValidatedInternet()).isFalse()
    }

    @Test
    fun `기본망 capability 변경과 상실을 관찰하고 보조망 연결은 검증 상태를 만들지 않는다`() =
        runTest {
            val context = mockk<Context>()
            val manager = mockk<ConnectivityManager>(relaxed = true)
            val network = mockk<Network>()
            val secondary = mockk<Network>()
            val callback = slot<ConnectivityManager.NetworkCallback>()
            val capabilities = networkCapabilities(validated = false)
            every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns manager
            every { manager.activeNetwork } returns network
            every { manager.getNetworkCapabilities(network) } returns capabilities
            every { manager.registerDefaultNetworkCallback(capture(callback)) } returns Unit
            val monitor = NetworkMonitor(context)
            val values = mutableListOf<Boolean>()
            val collection =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    monitor.validatedInternet.collect { values.add(it) }
                }
            runCurrent()
            assertThat(values.last()).isFalse()
            callback.captured.onAvailable(secondary)
            runCurrent()
            assertThat(values.last()).isFalse()
            val validated = networkCapabilities(validated = true)
            callback.captured.onCapabilitiesChanged(network, validated)
            runCurrent()
            assertThat(values.last()).isTrue()
            callback.captured.onCapabilitiesChanged(network, capabilities)
            runCurrent()
            assertThat(values.last()).isFalse()
            callback.captured.onCapabilitiesChanged(network, validated)
            runCurrent()
            callback.captured.onLost(network)
            runCurrent()
            assertThat(values.last()).isFalse()
            collection.cancel()
            runCurrent()
            verify(exactly = 1) { manager.unregisterNetworkCallback(callback.captured) }
        }

    private fun networkCapabilities(validated: Boolean): NetworkCapabilities =
        mockk {
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns validated
        }
}

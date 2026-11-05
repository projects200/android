package com.project200.common.utils

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.annotation.RequiresPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // 현재 인터넷 연결 여부 확인
        @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
        fun isCurrentlyConnected(): Boolean {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
        fun hasValidatedInternet(): Boolean {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        /** 기본 네트워크만 판정합니다. 보조 네트워크 onAvailable은 연결 증거가 아닙니다. */
        val validatedInternet =
            callbackFlow {
                trySend(false)
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            trySend(hasValidatedInternet())
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            capabilities: NetworkCapabilities,
                        ) {
                            if (network == connectivityManager.activeNetwork) {
                                trySend(
                                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                                )
                            }
                        }

                        override fun onLost(network: Network) {
                            trySend(network != connectivityManager.activeNetwork && hasValidatedInternet())
                        }
                    }
                connectivityManager.registerDefaultNetworkCallback(callback)
                trySend(hasValidatedInternet())
                awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()

        // 네트워크 상태 변경 감지 Flow
        val networkState =
            callbackFlow {
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            trySend(true)
                        }

                        override fun onLost(network: Network) {
                            trySend(false)
                        }
                    }

                val request =
                    NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()

                connectivityManager.registerNetworkCallback(request, callback)
                awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
            }
    }

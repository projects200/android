package com.project200.presentation.navigator

import android.content.Context

interface ActivityNavigator {
    fun navigateToMain(context: Context)

    fun navigateToLogin(context: Context)

    fun navigateToWeb(
        context: Context,
        url: String,
    )
}

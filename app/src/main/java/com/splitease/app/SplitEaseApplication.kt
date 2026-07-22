package com.splitease.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Enables Hilt dependency injection for the app graph.
 */
@HiltAndroidApp
class SplitEaseApplication : Application()

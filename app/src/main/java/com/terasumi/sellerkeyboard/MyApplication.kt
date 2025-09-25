package com.terasumi.sellerkeyboard

import android.app.Application
import dev.convex.android.ConvexClient

class MyApplication : Application() {
    lateinit var convex: ConvexClient

    override fun onCreate() {
        super.onCreate()
        // Initialize Convex client once for the application lifetime.
        convex = ConvexClient(getString(R.string.convex_url))
    }
}


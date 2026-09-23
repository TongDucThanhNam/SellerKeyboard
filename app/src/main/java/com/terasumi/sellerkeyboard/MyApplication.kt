package com.terasumi.sellerkeyboard

import android.app.Application
import com.terasumi.sellerkeyboard.backend.ConvexAuthRepository
import com.terasumi.sellerkeyboard.backend.SnippetBackupRepository
import dev.convex.android.ConvexClient

class MyApplication : Application() {
    private val convexClientDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ConvexClient(getString(R.string.convex_url))
    }
    val convexClient: ConvexClient get() = convexClientDelegate.value

    private val convexAuthRepositoryDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ConvexAuthRepository(this, convexClient)
    }
    val convexAuthRepository: ConvexAuthRepository get() = convexAuthRepositoryDelegate.value

    private val snippetBackupRepositoryDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SnippetBackupRepository(convexClient, convexAuthRepository)
    }
    val snippetBackupRepository: SnippetBackupRepository get() = snippetBackupRepositoryDelegate.value

    internal fun cloudDependencyInitializationState(): CloudDependencyInitializationState =
        CloudDependencyInitializationState(
            client = convexClientDelegate.isInitialized(),
            auth = convexAuthRepositoryDelegate.isInitialized(),
            backup = snippetBackupRepositoryDelegate.isInitialized()
        )
}

internal data class CloudDependencyInitializationState(
    val client: Boolean,
    val auth: Boolean,
    val backup: Boolean
)

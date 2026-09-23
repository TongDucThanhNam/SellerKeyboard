package com.terasumi.sellerkeyboard.translation

import android.os.Build
import androidx.annotation.Keep

@Keep
internal object NativeTranslationLogGuard {
    private const val UNSUPPORTED_PRIORITY = Int.MIN_VALUE
    private val libraryLoaded: Boolean by lazy {
        runCatching { System.loadLibrary("sellerprivacy") }.isSuccess
    }

    fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && libraryLoaded && nativeIsSupported()

    fun begin(): Scope? {
        if (!isSupported()) return null
        val previousPriority = nativeBegin()
        return if (previousPriority == UNSUPPORTED_PRIORITY) null else Scope(previousPriority)
    }

    internal class Scope(private val previousPriority: Int) : AutoCloseable {
        private var closed = false

        @Synchronized
        override fun close() {
            if (closed) return
            closed = true
            nativeRestore(previousPriority)
        }
    }

    private external fun nativeIsSupported(): Boolean
    private external fun nativeBegin(): Int
    private external fun nativeRestore(previousPriority: Int)
}

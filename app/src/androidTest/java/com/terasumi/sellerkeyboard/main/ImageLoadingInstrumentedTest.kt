package com.terasumi.sellerkeyboard.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ImageLoadingInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun selectedImage_decodesAsBitmapWithoutNetwork() = withImage { file ->
        val request = Glide.with(context).asBitmap().load(file).submit()
        try {
            val decoded = request.get(10, TimeUnit.SECONDS)
            assertEquals(2, decoded.width)
            assertEquals(2, decoded.height)
            assertEquals(Color.BLUE, decoded.getPixel(0, 0))
        } finally {
            Glide.with(context).clear(request)
        }
    }

    @Test
    fun imeImage_decodesIntoCustomDrawableTarget() = withImage { file ->
        val completed = CountDownLatch(1)
        val resource = AtomicReference<Drawable>()
        val failure = AtomicReference<String>()
        val target = object : CustomTarget<Drawable>() {
            override fun onResourceReady(drawable: Drawable, transition: Transition<in Drawable>?) {
                resource.set(drawable)
                completed.countDown()
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                failure.set("Glide failed to decode the local IME image")
                completed.countDown()
            }

            override fun onLoadCleared(placeholder: Drawable?) = Unit
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            instrumentation.runOnMainSync { Glide.with(context).load(file).into(target) }
            assertTrue("Image decode timed out", completed.await(10, TimeUnit.SECONDS))
            assertNull(failure.get())
            assertEquals(2, requireNotNull(resource.get()).intrinsicWidth)
            assertEquals(2, requireNotNull(resource.get()).intrinsicHeight)
        } finally {
            instrumentation.runOnMainSync { Glide.with(context).clear(target) }
        }
    }

    private fun withImage(test: (File) -> Unit) {
        val file = File.createTempFile("lint-image-regression-", ".png", context.cacheDir)
        val bitmap = createBitmap(2, 2).apply { eraseColor(Color.BLUE) }
        try {
            file.outputStream().use { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            test(file)
        } finally {
            bitmap.recycle()
            check(file.delete()) { "Could not remove the image regression fixture" }
        }
    }
}

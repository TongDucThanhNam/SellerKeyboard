package com.terasumi.sellerkeyboard.main

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.terasumi.sellerkeyboard.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class QuantityResourcesInstrumentedTest {
    @Test
    fun englishQuantities_handleZeroSingularAndPlural() {
        val resources = resourcesFor("en")
        for (count in 0..2) {
            val snippet = if (count == 1) "snippet" else "snippets"
            val image = if (count == 1) "image" else "images"
            val match = if (count == 1) "match" else "matches"
            assertEquals("$count $snippet", resources.getQuantityString(R.plurals.snippet_total_count, count, count))
            assertEquals("$count $image", resources.getQuantityString(R.plurals.snippet_images_count, count, count))
            assertEquals("$count $image selected", resources.getQuantityString(R.plurals.snippet_images_selected, count, count))
            assertEquals("Insert $count cloud $snippet.", resources.getQuantityString(R.plurals.cloud_restore_insert_count, count, count))
            assertEquals("$count exact $match will be skipped.", resources.getQuantityString(R.plurals.cloud_restore_skip_count, count, count))
        }
    }

    @Test
    fun vietnameseQuantities_keepCountsAndDiacritics() {
        val resources = resourcesFor("vi")
        for (count in 0..2) {
            assertEquals("$count đoạn", resources.getQuantityString(R.plurals.snippet_total_count, count, count))
            assertEquals("$count ảnh", resources.getQuantityString(R.plurals.snippet_images_count, count, count))
            assertEquals("Đã chọn $count ảnh", resources.getQuantityString(R.plurals.snippet_images_selected, count, count))
            val inserted = resources.getQuantityString(R.plurals.cloud_restore_insert_count, count, count)
            val skipped = resources.getQuantityString(R.plurals.cloud_restore_skip_count, count, count)
            assertEquals("Chèn $count snippet từ cloud. Bỏ qua $count snippet trùng khớp.",
                resources.getString(R.string.cloud_restore_message, inserted, skipped))
        }
    }

    private fun resourcesFor(languageTag: String): Resources {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(languageTag))
        }
        return context.createConfigurationContext(configuration).resources
    }
}

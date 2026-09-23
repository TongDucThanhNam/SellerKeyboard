package com.terasumi.sellerkeyboard.autofill

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.util.TypedValue
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.ImageView
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.common.ImageViewStyle
import androidx.autofill.inline.common.TextViewStyle
import androidx.autofill.inline.common.ViewStyle
import androidx.autofill.inline.v1.InlineSuggestionUi
import kotlin.math.roundToInt

@RequiresApi(Build.VERSION_CODES.R)
object AndroidInlineAutofillRequestFactory {
    fun create(context: Context, uiExtras: Bundle): InlineSuggestionsRequest {
        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val bounds = InlineAutofillRequestConfig.presentationBounds(
            density = context.resources.displayMetrics.density,
            isLandscape = isLandscape
        )
        val presentationSpec = InlinePresentationSpec.Builder(
            Size(bounds.minWidthPx, bounds.minHeightPx),
            Size(bounds.maxWidthPx, bounds.maxHeightPx)
        )
            .setStyle(createStyle(context))
            .build()

        return InlineSuggestionsRequest.Builder(listOf(presentationSpec))
            .setMaxSuggestionCount(InlineAutofillRequestConfig.MAX_SUGGESTION_COUNT)
            .setExtras(Bundle(uiExtras))
            .build()
    }

    fun inflateSize(): Size {
        return Size(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun createStyle(context: Context): Bundle {
        val isNightMode = (
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            ) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isNightMode) {
            Color.WHITE
        } else {
            Color.rgb(35, 38, 42)
        }
        val secondaryTextColor = if (isNightMode) {
            Color.argb(204, 255, 255, 255)
        } else {
            Color.argb(177, 35, 38, 42)
        }
        val density = context.resources.displayMetrics.density
        val chipStyle = ViewStyle.Builder().build()
        val titleStyle = TextViewStyle.Builder()
            .setTextColor(textColor)
            .setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            .build()
        val subtitleStyle = TextViewStyle.Builder()
            .setTextColor(secondaryTextColor)
            .setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            .build()
        val iconStyle = ImageViewStyle.Builder()
            .setScaleType(ImageView.ScaleType.CENTER_INSIDE)
            .setMaxWidth(dpToPx(20, density))
            .setMaxHeight(dpToPx(20, density))
            .setTintList(ColorStateList.valueOf(textColor))
            .build()
        val style = InlineSuggestionUi.newStyleBuilder()
            .setChipStyle(chipStyle)
            .setSingleIconChipStyle(chipStyle)
            .setTitleStyle(titleStyle)
            .setSubtitleStyle(subtitleStyle)
            .setStartIconStyle(iconStyle)
            .setEndIconStyle(iconStyle)
            .setSingleIconChipIconStyle(iconStyle)
            .setLayoutDirection(context.resources.configuration.layoutDirection)
            .build()

        return UiVersions.newStylesBuilder()
            .addStyle(style)
            .build()
    }

    private fun dpToPx(dp: Int, density: Float): Int {
        return (dp * density).roundToInt().coerceAtLeast(1)
    }
}

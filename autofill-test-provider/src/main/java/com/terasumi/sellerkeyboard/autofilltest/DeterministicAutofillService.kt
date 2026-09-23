package com.terasumi.sellerkeyboard.autofilltest

import android.app.Activity
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.Presentations
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.autofill.inline.v1.InlineSuggestionUi

class DeterministicAutofillService : AutofillService() {
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        val targetPackage = structure?.activityComponent?.packageName.orEmpty()
        val ids = structure?.let(::findAutofillIds).orEmpty()
        val inlineRequest = request.inlineSuggestionsRequest
        val spec = inlineRequest?.inlinePresentationSpecs?.firstOrNull()
        if (cancellationSignal.isCanceled || ids.isEmpty() || spec == null) {
            ProviderStatus.recordRequest(
                context = this,
                targetPackage = targetPackage,
                inlineRequestPresent = inlineRequest != null,
                maxSuggestionCount = inlineRequest?.maxSuggestionCount ?: 0,
                datasetReturned = false
            )
            callback.onSuccess(null)
            return
        }

        val attribution = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ProviderAttributionActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val content = InlineSuggestionUi.newContentBuilder(attribution)
            .setTitle(DATASET_LABEL)
            .setSubtitle("example.invalid")
            .setContentDescription(DATASET_LABEL)
            .build()
        val inlinePresentation = InlinePresentation(content.slice, spec, false)
        val menuPresentation = RemoteViews(packageName, R.layout.autofill_dataset).apply {
            setTextViewText(R.id.dataset_label, DATASET_LABEL)
        }
        val presentations = Presentations.Builder()
            .setMenuPresentation(menuPresentation)
            .setInlinePresentation(inlinePresentation)
            .build()
        val dataset = Dataset.Builder(presentations).setId(DATASET_ID)
        ids[View.AUTOFILL_HINT_USERNAME]?.let { id ->
            dataset.setField(
                id,
                Field.Builder().setValue(AutofillValue.forText(TEST_USERNAME)).build()
            )
        }
        ids[View.AUTOFILL_HINT_PASSWORD]?.let { id ->
            dataset.setField(
                id,
                Field.Builder().setValue(AutofillValue.forText(TEST_PASSWORD)).build()
            )
        }
        callback.onSuccess(FillResponse.Builder().addDataset(dataset.build()).build())
        ProviderStatus.recordRequest(
            context = this,
            targetPackage = targetPackage,
            inlineRequestPresent = true,
            maxSuggestionCount = inlineRequest.maxSuggestionCount,
            datasetReturned = true
        )
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private fun findAutofillIds(structure: AssistStructure): Map<String, AutofillId> {
        val result = mutableMapOf<String, AutofillId>()
        repeat(structure.windowNodeCount) { windowIndex ->
            collectAutofillIds(structure.getWindowNodeAt(windowIndex).rootViewNode, result)
        }
        return result
    }

    private fun collectAutofillIds(
        node: AssistStructure.ViewNode,
        result: MutableMap<String, AutofillId>
    ) {
        val id = node.autofillId
        if (id != null) {
            node.autofillHints.orEmpty().forEach { hint ->
                if (hint == View.AUTOFILL_HINT_USERNAME || hint == View.AUTOFILL_HINT_PASSWORD) {
                    result[hint] = id
                }
            }
        }
        repeat(node.childCount) { childIndex ->
            collectAutofillIds(node.getChildAt(childIndex), result)
        }
    }

    companion object {
        const val TEST_USERNAME = "seller-autofill@example.invalid"
        const val TEST_PASSWORD = "Deterministic-Autofill-42!"
        const val DATASET_LABEL = "Seller test credential"
        private const val DATASET_ID = "sellerkeyboard-deterministic-credential"
    }
}

class ProviderAttributionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}

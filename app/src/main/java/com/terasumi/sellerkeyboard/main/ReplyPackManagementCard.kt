package com.terasumi.sellerkeyboard.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.database.DatabaseHelper
import com.terasumi.sellerkeyboard.database.SellerReplyTemplateDao
import com.terasumi.sellerkeyboard.seller.ReplyPackCodec
import com.terasumi.sellerkeyboard.seller.ReplyPackImportResult
import com.terasumi.sellerkeyboard.seller.ReplyPack
import com.terasumi.sellerkeyboard.seller.SellerReplyTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReplyPackManagementCard() {
    val context = LocalContext.current
    val dao = remember(context) { SellerReplyTemplateDao(DatabaseHelper(context)) }
    val scope = rememberCoroutineScope()
    var payload by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("No import or export prepared.") }
    var pendingImport by remember { mutableStateOf<List<SellerReplyTemplate>>(emptyList()) }
    var packId by remember { mutableStateOf("") }
    var packName by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Reply pack import and export")
            Text(
                "Export includes a versioned JSON preview. Import validates first and never " +
                    "changes local replies until you confirm."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = packId,
                    onValueChange = { packId = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Pack ID (optional)") }
                )
                OutlinedTextField(
                    value = packName,
                    onValueChange = { packName = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Pack name") }
                )
            }
            OutlinedTextField(
                value = payload,
                onValueChange = {
                    payload = it
                    pendingImport = emptyList()
                    status = "Payload changed; validate it before importing."
                },
                modifier = Modifier.fillMaxWidth().height(160.dp),
                label = { Text("Versioned reply pack JSON") }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val allTemplates = withContext(Dispatchers.IO) { dao.listActive(limit = 500) }
                            val selectedPackId = packId.trim().ifBlank { null }
                            val templates = if (selectedPackId == null) {
                                allTemplates
                            } else {
                                allTemplates.filter { it.replyPackId == selectedPackId }
                            }
                            val pack = selectedPackId?.let {
                                ReplyPack(
                                    id = it,
                                    name = packName.trim().ifBlank { it }
                                )
                            }
                            payload = ReplyPackCodec.export(
                                templates = templates,
                                nowMillis = System.currentTimeMillis(),
                                pack = pack
                            )
                            pendingImport = emptyList()
                            status = "Export preview: ${templates.size} local replies, schema " +
                                ReplyPackCodec.SCHEMA_VERSION
                        }
                    }
                ) { Text("Prepare export") }
                OutlinedButton(
                    enabled = payload.isNotBlank(),
                    onClick = {
                        when (val result = ReplyPackCodec.validateAndDecode(payload)) {
                            is ReplyPackImportResult.Valid -> {
                                pendingImport = result.templates
                                packId = result.pack?.id.orEmpty()
                                packName = result.pack?.name.orEmpty()
                                status = "Valid import preview: ${result.templates.size} replies" +
                                    (result.pack?.let { " from ${it.name}." } ?: ".")
                            }
                            is ReplyPackImportResult.Invalid -> {
                                pendingImport = emptyList()
                                status = "Invalid import: ${result.reason}"
                            }
                        }
                    }
                ) { Text("Validate import") }
            }
            Text(status)
            Button(
                enabled = pendingImport.isNotEmpty(),
                onClick = {
                    val confirmed = pendingImport
                    scope.launch {
                        val imported = withContext(Dispatchers.IO) {
                            confirmed.count(dao::upsert)
                        }
                        pendingImport = emptyList()
                        status = "Imported $imported of ${confirmed.size} validated replies."
                    }
                }
            ) { Text("Confirm validated import") }
        }
    }
}

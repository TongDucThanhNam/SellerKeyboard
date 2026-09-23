package com.terasumi.sellerkeyboard.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.terasumi.sellerkeyboard.database.DatabaseHelper
import com.terasumi.sellerkeyboard.database.SellerReplyTemplateDao
import com.terasumi.sellerkeyboard.seller.SellerReplyTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private enum class DestructiveReplyAction { ARCHIVE, DELETE }

@Composable
fun ReplyTemplateManagementCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dao = remember(context) { SellerReplyTemplateDao(DatabaseHelper(context)) }
    val scope = rememberCoroutineScope()
    var templates by remember { mutableStateOf(emptyList<SellerReplyTemplate>()) }
    var editor by remember { mutableStateOf<SellerReplyTemplate?>(null) }
    var pendingAction by remember {
        mutableStateOf<Pair<SellerReplyTemplate, DestructiveReplyAction>?>(null)
    }
    var status by remember { mutableStateOf("") }

    suspend fun refresh() {
        templates = withContext(Dispatchers.IO) { dao.listActive(limit = 500) }
    }

    LaunchedEffect(Unit) { refresh() }

    pendingAction?.let { (template, action) ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(if (action == DestructiveReplyAction.ARCHIVE) "Archive reply?" else "Delete reply?") },
            text = {
                Text(
                    if (action == DestructiveReplyAction.ARCHIVE) {
                        "${template.title} will leave the active reply list."
                    } else {
                        "${template.title} will be permanently deleted. Export a backup first if needed."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingAction = null
                        scope.launch {
                            val changed = withContext(Dispatchers.IO) {
                                when (action) {
                                    DestructiveReplyAction.ARCHIVE -> dao.archive(template.id)
                                    DestructiveReplyAction.DELETE -> dao.delete(template.id)
                                }
                            }
                            status = if (changed) "Reply updated." else "Reply was not changed."
                            refresh()
                        }
                    }
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text("Cancel") }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Structured seller replies")
            Text("Create, edit, duplicate, archive, or delete reply templates. Destructive actions require confirmation.")
            OutlinedButton(
                onClick = {
                    val now = System.currentTimeMillis()
                    editor = SellerReplyTemplate(
                        id = UUID.randomUUID().toString(),
                        title = "",
                        body = "",
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                }
            ) { Text("Create reply") }

            editor?.let { template ->
                ReplyTemplateEditor(
                    template = template,
                    onChange = { editor = it },
                    onCancel = { editor = null },
                    onSave = { updated ->
                        scope.launch {
                            val saved = withContext(Dispatchers.IO) {
                                dao.upsert(updated.copy(updatedAtMillis = System.currentTimeMillis()))
                            }
                            status = if (saved) "Reply saved." else "Reply could not be saved."
                            if (saved) editor = null
                            refresh()
                        }
                    }
                )
            }

            if (status.isNotBlank()) Text(status)
            templates.forEach { template ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("${if (template.favorite) "★ " else ""}${template.title}")
                    Text("${template.category} · used ${template.usageCount} times")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { editor = template }) { Text("Edit") }
                        TextButton(
                            onClick = {
                                val now = System.currentTimeMillis()
                                val duplicate = template.copy(
                                    id = UUID.randomUUID().toString(),
                                    title = "Copy of ${template.title}",
                                    favorite = false,
                                    usageCount = 0,
                                    createdAtMillis = now,
                                    updatedAtMillis = now
                                )
                                scope.launch {
                                    withContext(Dispatchers.IO) { dao.upsert(duplicate) }
                                    status = "Reply duplicated."
                                    refresh()
                                }
                            }
                        ) { Text("Duplicate") }
                        TextButton(
                            onClick = { pendingAction = template to DestructiveReplyAction.ARCHIVE }
                        ) { Text("Archive") }
                        TextButton(
                            onClick = { pendingAction = template to DestructiveReplyAction.DELETE }
                        ) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyTemplateEditor(
    template: SellerReplyTemplate,
    onChange: (SellerReplyTemplate) -> Unit,
    onCancel: () -> Unit,
    onSave: (SellerReplyTemplate) -> Unit
) {
    OutlinedTextField(
        value = template.title,
        onValueChange = { onChange(template.copy(title = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Title") },
        singleLine = true
    )
    OutlinedTextField(
        value = template.body,
        onValueChange = { onChange(template.copy(body = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Body and {{variables}}") },
        minLines = 3
    )
    OutlinedTextField(
        value = template.category,
        onValueChange = { onChange(template.copy(category = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Category") },
        singleLine = true
    )
    OutlinedTextField(
        value = template.tags.joinToString(", "),
        onValueChange = { value ->
            onChange(template.copy(tags = value.csvValues()))
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Tags, comma separated") },
        singleLine = true
    )
    OutlinedTextField(
        value = template.shortcut.orEmpty(),
        onValueChange = { value ->
            onChange(template.copy(shortcut = value.trim().ifBlank { null }))
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Shortcut, for example /ship") },
        singleLine = true
    )
    OutlinedTextField(
        value = template.mediaUrls.joinToString(", "),
        onValueChange = { value -> onChange(template.copy(mediaUrls = value.csvValues())) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Media or link attachments") },
        singleLine = true
    )
    OutlinedTextField(
        value = template.replyPackId.orEmpty(),
        onValueChange = { value ->
            onChange(template.copy(replyPackId = value.trim().ifBlank { null }))
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Reply pack ID") },
        singleLine = true
    )
    OutlinedTextField(
        value = template.profileIds.joinToString(", "),
        onValueChange = { value -> onChange(template.copy(profileIds = value.csvValues())) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Profile IDs") },
        singleLine = true
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = template.favorite,
            onCheckedChange = { onChange(template.copy(favorite = it)) }
        )
        Text("Favorite")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
        Button(
            enabled = template.title.isNotBlank() && template.body.isNotBlank(),
            onClick = { onSave(template) }
        ) { Text("Save reply") }
    }
}

private fun String.csvValues(): List<String> = split(',')
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()

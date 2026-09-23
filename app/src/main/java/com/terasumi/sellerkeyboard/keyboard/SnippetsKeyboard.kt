package com.terasumi.sellerkeyboard.keyboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.os.Trace
import com.terasumi.sellerkeyboard.database.DatabaseHelper
import com.terasumi.sellerkeyboard.database.SellerReplyTemplateDao
import com.terasumi.sellerkeyboard.keyboard.theme.KeyboardColors
import com.terasumi.sellerkeyboard.keyboard.theme.LightKeyboardColors
import com.terasumi.sellerkeyboard.seller.SellerReplyTemplate
import com.terasumi.sellerkeyboard.seller.TemplateRenderResult
import com.terasumi.sellerkeyboard.seller.TemplateRenderer
import com.terasumi.sellerkeyboard.seller.TemplateVariableMapper
import com.terasumi.sellerkeyboard.seller.ReplySortMode
import com.terasumi.sellerkeyboard.seller.SellerSearchNormalizer
import com.terasumi.sellerkeyboard.clipboard.ClipboardEntityParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SnippetsKeyboard(
    myColor: KeyboardColors,
    isLandscape: Boolean,
    activeProfileId: String = "global",
    preferredReplyPackId: String? = null,
    onOpenTool: (String) -> Unit = {},
    onAction: (KeyboardAction) -> Unit
) {
    val context = LocalContext.current
    val dao = remember(context) { SellerReplyTemplateDao(DatabaseHelper(context)) }
    val scope = rememberCoroutineScope()
    val clipboardRepository = remember(context) { context.clipboardRepository() }
    val clipboardItems by clipboardRepository.observeRecent().collectAsState()
    val entityParser = remember { ClipboardEntityParser() }
    val variableSuggestions = remember(clipboardItems) {
        clipboardItems
            .flatMap { entityParser.parse(it.text) }
            .mapNotNull { entity ->
                TemplateVariableMapper.suggestedVariable(entity)?.let { it to entity.value }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.distinct() }
    }
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(ReplySortMode.SMART) }
    var templates by remember { mutableStateOf(emptyList<SellerReplyTemplate>()) }
    var selectedTemplate by remember { mutableStateOf<SellerReplyTemplate?>(null) }
    var queryEditing by remember { mutableStateOf(false) }

    suspend fun refresh() {
        templates = withContext(Dispatchers.IO) {
            dao.listActive(
                query = query,
                sortMode = sortMode,
                profileId = activeProfileId,
                preferredReplyPackId = preferredReplyPackId
            )
        }
    }

    LaunchedEffect(query, sortMode, activeProfileId, preferredReplyPackId) { refresh() }

    val matchingCommands = remember(query) {
        if (query.isBlank()) {
            emptyList()
        } else {
            SELLER_COMMANDS.filter { command ->
                SellerSearchNormalizer.normalize(query) in
                    SellerSearchNormalizer.normalize(command.label)
            }
        }
    }

    val selected = selectedTemplate
    if (selected != null) {
        TemplateFillReview(
            template = selected,
            myColor = myColor,
            isLandscape = isLandscape,
            variableSuggestions = variableSuggestions,
            onCancel = { selectedTemplate = null },
            onCommit = { text ->
                Trace.beginSection("SellerKeyboard.templateInsertion")
                try {
                    onAction(KeyboardAction.InsertTemplate(text))
                    selectedTemplate = null
                    scope.launch(Dispatchers.IO) { dao.markUsed(selected.id) }
                } finally {
                    Trace.endSection()
                }
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .height(if (isLandscape) 190.dp else 300.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        InImeTextField(
            value = query,
            label = "Search replies, tags, or /shortcut",
            accessibilityLabel = "Search seller replies and commands",
            onClick = { queryEditing = true },
            modifier = Modifier
                .fillMaxWidth()
        )
        if (queryEditing) {
            CompactInImeTextKeypad(
                value = query,
                onValueChange = { query = it },
                onDone = { queryEditing = false }
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ReplySortMode.entries, key = { it.name }) { mode ->
                    FilterChip(
                        selected = sortMode == mode,
                        onClick = { sortMode = mode },
                        label = { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) }
                    )
                }
                item {
                    TextButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { dao.resetUsageSignals() }
                                refresh()
                            }
                        }
                    ) { Text("Reset ranking") }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(matchingCommands, key = { "command:${it.toolId}" }) { command ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Seller command ${command.label}"
                            }
                            .clickable { onOpenTool(command.toolId) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Open ${command.label}",
                            color = myColor.iconColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                items(templates, key = SellerReplyTemplate::id) { template ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Reply ${template.title}" }
                            .clickable { selectedTemplate = template }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = template.title,
                                color = myColor.iconColor,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = listOfNotNull(template.category, template.shortcut)
                                    .filter(String::isNotBlank)
                                    .joinToString(" · "),
                                color = myColor.iconColor.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                        IconButton(
                            onClick = {
                                val next = !template.favorite
                                templates = templates.map {
                                    if (it.id == template.id) it.copy(favorite = next) else it
                                }
                                scope.launch(Dispatchers.IO) { dao.setFavorite(template.id, next) }
                            },
                            modifier = Modifier.semantics {
                                contentDescription = if (template.favorite) {
                                    "Remove favorite for ${template.title}"
                                } else {
                                    "Add favorite for ${template.title}"
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (template.favorite) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = null,
                                tint = myColor.iconColor
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class SellerCommand(val toolId: String, val label: String)

private val SELLER_COMMANDS = listOf(
    SellerCommand("products", "Products and promotions"),
    SellerCommand("clipboard", "Clipboard and commercial fragments"),
    SellerCommand("calculator", "Calculator and seller formulas"),
    SellerCommand("translate", "Vietnamese English translation"),
    SellerCommand("voice", "Voice input"),
    SellerCommand("emoji", "Emoji"),
    SellerCommand("settings", "Keyboard settings")
)

@Composable
private fun TemplateFillReview(
    template: SellerReplyTemplate,
    myColor: KeyboardColors,
    isLandscape: Boolean,
    variableSuggestions: Map<String, List<String>>,
    onCancel: () -> Unit,
    onCommit: (String) -> Unit
) {
    val variables = remember(template.body) { TemplateRenderer.variables(template.body).sorted() }
    val values = remember(template.id) { mutableStateMapOf<String, String>() }
    var activeVariable by remember(template.id) { mutableStateOf<String?>(null) }
    val result = TemplateRenderer.render(template.body, values)

    activeVariable?.let { variable ->
        Column(
            modifier = Modifier
                .height(if (isLandscape) 190.dp else 300.dp)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = template.title,
                color = myColor.iconColor,
                fontWeight = FontWeight.SemiBold
            )
            InImeTextField(
                value = values[variable].orEmpty(),
                label = variable.replace('_', ' '),
                accessibilityLabel = "Template variable $variable",
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            )
            CompactInImeTextKeypad(
                value = values[variable].orEmpty(),
                onValueChange = { values[variable] = it },
                onDone = { activeVariable = null }
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .height(if (isLandscape) 190.dp else 300.dp)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = template.title,
            color = myColor.iconColor,
            fontWeight = FontWeight.SemiBold
        )
        if (variables.isEmpty()) {
            Text(
                text = template.body,
                color = myColor.iconColor,
                maxLines = if (isLandscape) 2 else 5,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(variables, key = { it }) { variable ->
                    InImeTextField(
                        value = values[variable].orEmpty(),
                        label = variable.replace('_', ' '),
                        accessibilityLabel = "Template variable $variable",
                        onClick = { activeVariable = variable },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                    val suggestions = variableSuggestions[variable].orEmpty()
                    if (suggestions.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            suggestions.take(2).forEach { suggestion ->
                                TextButton(onClick = { values[variable] = suggestion }) {
                                    Text("Use $suggestion", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (result !is TemplateRenderResult.Ready) {
            Text(
                text = "Resolve every required variable before insertion.",
                color = myColor.iconColor
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel) { Text("Back") }
            Button(
                enabled = result is TemplateRenderResult.Ready,
                onClick = { onCommit((result as TemplateRenderResult.Ready).text) },
                modifier = Modifier.semantics {
                    contentDescription = "Insert reviewed reply"
                }
            ) {
                Text("Insert reviewed reply")
            }
        }
    }
}

@Preview
@Composable
fun SnippetKeyboardPreview() {
    SnippetsKeyboard(LightKeyboardColors, false, onAction = {})
}

package com.terasumi.sellerkeyboard.main

import SnippetsDao
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.database.DatabaseHelper
import com.terasumi.sellerkeyboard.database.Snippets
import com.terasumi.sellerkeyboard.ui.theme.SellerKeyboardTheme
import com.terasumi.sellerkeyboard.ui.theme.spacing
import com.terasumi.sellerkeyboard.ui.theme.sizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSnippetScreen(navController: NavHostController, snippetId: Int) {
    val context = LocalContext.current

    var snippet by remember { mutableStateOf<Snippets?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    var isSaving by remember { mutableStateOf(false) }
    var attemptedSave by remember { mutableStateOf(false) }
    var isLoadingImages by remember { mutableStateOf(false) }

    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var lastRemoved by remember { mutableStateOf<Pair<Int, Pair<Uri, Bitmap>?>?>(null) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    val resources = context.resources
    val editSnippet = remember { resources.getString(R.string.edit_snippet) }
    val enterLabel = remember { resources.getString(R.string.enter_label) }
    val enterContent = remember { resources.getString(R.string.enter_content) }
    val save = remember { resources.getString(R.string.save) }
    val tapToAddImages = remember { "Bấm để thêm hình ảnh" }
    val updatedSuccessfully = remember { "Updated successfully" }

    val MAX_IMAGES = 5

    DisposableEffect(Unit) {
        onDispose {
            snippet = null
            title = ""
            content = ""
            imageUris = emptyList()
            bitmaps = emptyList()
            previewIndex = null
            lastRemoved = null
        }
    }

    LaunchedEffect(snippetId) {
        isLoadingImages = true
        snippet = withContext(Dispatchers.IO) {
            DatabaseHelper(context).use { db ->
                val dao = SnippetsDao(db)
                dao.fetchSnippetById(snippetId)
            }
        }
    }

    LaunchedEffect(snippet) {
        snippet?.let { currentSnippet ->
            title = currentSnippet.title
            content = currentSnippet.content

            val urls = currentSnippet.imageUrls
            if (urls.isNotEmpty()) {
                isLoadingImages = true
                val uris = urls.map { it.toUri() }
                imageUris = uris
                val loaded = mutableListOf<Bitmap>()
                uris.forEach { uri ->
                    runCatching { loaded += loadBitmap(context, uri) }
                }
                bitmaps = loaded
            } else {
                imageUris = emptyList()
                bitmaps = emptyList()
            }
        }
        isLoadingImages = false
    }

    val imageResource = if (isSystemInDarkTheme()) {
        R.drawable.bxs_image_add
    } else {
        R.drawable.bx_image_add
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        val distinctUris = uris.distinct()
        val limited = if (distinctUris.size > MAX_IMAGES) distinctUris.take(MAX_IMAGES) else distinctUris
        imageUris = limited
        isLoadingImages = true
        scope.launch {
            val loaded = limited.map { loadBitmap(context, it) }
            bitmaps = loaded
            isLoadingImages = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = editSnippet) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = MaterialTheme.spacing.large)
                    .padding(vertical = MaterialTheme.spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
            ) {
                val fieldShape = RoundedCornerShape(18.dp)
                val colorScheme = MaterialTheme.colorScheme

                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = colorScheme.surface),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .padding(bottom = MaterialTheme.spacing.medium),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = MaterialTheme.spacing.large,
                                vertical = MaterialTheme.spacing.medium
                            ),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
                    ) {
                        OutlinedTextField(
                            value = title,
                            maxLines = 1,
                            onValueChange = { if (it.length <= 50) title = it },
                            label = { Text(enterLabel) },
                            placeholder = { Text("e.g. Order confirmation message") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(MaterialTheme.sizes.inputHeight),
                            trailingIcon = {
                                if (title.isNotEmpty()) {
                                    IconButton(onClick = { title = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear title"
                                        )
                                    }
                                }
                            },
                            shape = fieldShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colorScheme.primary,
                                unfocusedBorderColor = colorScheme.outline.copy(alpha = 0.6f),
                                errorBorderColor = colorScheme.error,
                                focusedContainerColor = colorScheme.surface,
                                unfocusedContainerColor = colorScheme.surface,
                                disabledContainerColor = colorScheme.surface,
                                errorContainerColor = colorScheme.surface
                            ),
                            isError = attemptedSave && title.isBlank(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = {
                                focusManager.moveFocus(FocusDirection.Down)
                            }),
                            supportingText = {
                                val isError = attemptedSave && title.isBlank()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isError) "Title required" else "Visible to your whole team",
                                        color = if (isError) colorScheme.error else colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = "${title.length}/50",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 4.dp
                                            ),
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        )

                        OutlinedTextField(
                            value = content,
                            onValueChange = { if (it.length <= 1000) content = it },
                            label = { Text(enterContent) },
                            placeholder = { Text("Write the snippet content here...") },
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            trailingIcon = {
                                if (content.isNotEmpty()) {
                                    IconButton(onClick = { content = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear content"
                                        )
                                    }
                                }
                            },
                            shape = fieldShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colorScheme.primary,
                                unfocusedBorderColor = colorScheme.outline.copy(alpha = 0.6f),
                                errorBorderColor = colorScheme.error,
                                focusedContainerColor = colorScheme.surface,
                                unfocusedContainerColor = colorScheme.surface,
                                disabledContainerColor = colorScheme.surface,
                                errorContainerColor = colorScheme.surface
                            ),
                            isError = attemptedSave && content.isBlank(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            supportingText = {
                                val isError = attemptedSave && content.isBlank()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isError) "Content required" else "Share clear, reusable snippets",
                                        color = if (isError) colorScheme.error else colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = "${content.length}/1000",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 4.dp
                                            ),
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = MaterialTheme.spacing.small),
                            color = colorScheme.outline.copy(alpha = 0.12f)
                        )

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MaterialTheme.spacing.medium),
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(colorScheme.secondaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            tint = colorScheme.onSecondaryContainer
                                        )
                                    }
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xSmall)
                                    ) {
                                        Text(
                                            text = "Images",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Thêm hình ảnh để làm cho đoạn trích của bạn nổi bật hơn (tùy chọn)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val visibleCount = 4
                                    val total = bitmaps.size
                                    val showCount = if (total > visibleCount) visibleCount else total
                                    val canAddMore = total < MAX_IMAGES

                                    itemsIndexed(bitmaps.take(showCount)) { index, bitmap ->
                                        Surface(
                                            modifier = Modifier
                                                .size(MaterialTheme.sizes.imageThumb)
                                                .clip(MaterialTheme.shapes.medium)
                                                .border(
                                                    1.dp,
                                                    colorScheme.outline.copy(alpha = 0.25f),
                                                    MaterialTheme.shapes.medium
                                                )
                                                .clickable { previewIndex = index },
                                            shape = MaterialTheme.shapes.medium,
                                            tonalElevation = 3.dp,
                                            shadowElevation = 2.dp,
                                            color = colorScheme.surface
                                        ) {
                                            Box {
                                                Image(
                                                    bitmap = bitmap.asImageBitmap(),
                                                    contentDescription = "Selected image ${index + 1}",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )

                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(MaterialTheme.spacing.small)
                                                        .size(MaterialTheme.sizes.iconMedium)
                                                        .clip(CircleShape)
                                                        .background(colorScheme.surface.copy(alpha = 0.9f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    IconButton(
                                                        onClick = {
                                                            val removedUri = imageUris.getOrNull(index)
                                                            val removedBmp = bitmaps.getOrNull(index)

                                                            val newUris = imageUris.toMutableList().apply {
                                                                if (index in indices) removeAt(index)
                                                            }
                                                            val newBitmaps = bitmaps.toMutableList().apply {
                                                                if (index in indices) removeAt(index)
                                                            }

                                                            imageUris = newUris
                                                            bitmaps = newBitmaps
                                                            lastRemoved = Pair(
                                                                index,
                                                                if (removedUri != null && removedBmp != null) {
                                                                    Pair(removedUri, removedBmp)
                                                                } else null
                                                            )

                                                            scope.launch {
                                                                val result = snackbarHostState.showSnackbar(
                                                                    message = "Image removed",
                                                                    actionLabel = "Undo"
                                                                )
                                                                if (result == SnackbarResult.ActionPerformed) {
                                                                    lastRemoved?.let { (restoreIndex, data) ->
                                                                        data?.let { (uri, bmp) ->
                                                                            val urisMutable = imageUris.toMutableList()
                                                                            val bitmapsMutable = bitmaps.toMutableList()
                                                                            val insertIndex = if (restoreIndex <= urisMutable.size) restoreIndex else urisMutable.size
                                                                            urisMutable.add(insertIndex, uri)
                                                                            bitmapsMutable.add(insertIndex, bmp)
                                                                            imageUris = urisMutable
                                                                            bitmaps = bitmapsMutable
                                                                        }
                                                                    }
                                                                }
                                                                lastRemoved = null
                                                            }
                                                        },
                                                        modifier = Modifier.size(MaterialTheme.sizes.iconMedium)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Clear,
                                                            contentDescription = "Remove image",
                                                            tint = colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    item {
                                        Surface(
                                            modifier = Modifier
                                                .size(MaterialTheme.sizes.imageThumb)
                                                .clip(MaterialTheme.shapes.medium)
                                                .border(
                                                    1.dp,
                                                    colorScheme.outline.copy(alpha = 0.25f),
                                                    MaterialTheme.shapes.medium
                                                )
                                                .clickable(enabled = canAddMore) {
                                                    if (canAddMore) imagePickerLauncher.launch("image/*")
                                                },
                                            shape = MaterialTheme.shapes.medium,
                                            tonalElevation = 2.dp,
                                            shadowElevation = 1.dp,
                                            color = colorScheme.surface
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                when {
                                                    isLoadingImages -> {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(MaterialTheme.sizes.iconMedium)
                                                        )
                                                    }
                                                    bitmaps.isEmpty() -> {
                                                        Image(
                                                            painter = painterResource(id = imageResource),
                                                            contentDescription = null,
                                                            modifier = Modifier.size(64.dp)
                                                        )
                                                    }
                                                    total > visibleCount -> {
                                                        Box(
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Add,
                                                                contentDescription = "Add images",
                                                                tint = colorScheme.primary,
                                                                modifier = Modifier.size(MaterialTheme.sizes.iconMedium)
                                                            )
                                                            Box(
                                                                modifier = Modifier
                                                                    .align(Alignment.TopEnd)
                                                                    .padding(MaterialTheme.spacing.small)
                                                                    .clip(CircleShape)
                                                                    .background(colorScheme.secondaryContainer),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = "+${total - visibleCount}",
                                                                    color = colorScheme.onSecondaryContainer,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    modifier = Modifier.padding(
                                                                        horizontal = MaterialTheme.spacing.xSmall
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    }
                                                    else -> {
                                                        Icon(
                                                            imageVector = Icons.Default.Add,
                                                            contentDescription = "Add images",
                                                            tint = if (canAddMore) colorScheme.primary else colorScheme.outline,
                                                            modifier = Modifier.size(MaterialTheme.sizes.iconMedium)
                                                        )
                                                        if (!canAddMore) {
                                                            Surface(
                                                                modifier = Modifier
                                                                    .align(Alignment.BottomCenter)
                                                                    .padding(bottom = MaterialTheme.spacing.small),
                                                                shape = RoundedCornerShape(50),
                                                                color = colorScheme.surfaceVariant.copy(alpha = 0.9f),
                                                                tonalElevation = 2.dp
                                                            ) {
                                                                Text(
                                                                    text = "Max $MAX_IMAGES",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = colorScheme.onSurfaceVariant,
                                                                    modifier = Modifier.padding(
                                                                        horizontal = MaterialTheme.spacing.small,
                                                                        vertical = 2.dp
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Crossfade(
                                    targetState = when {
                                        isLoadingImages -> "Loading images…"
                                        bitmaps.isEmpty() -> tapToAddImages
                                        else -> "${bitmaps.size} image(s) selected"
                                    },
                                    label = "image-selection"
                                ) { animatedText ->
                                    Text(
                                        text = animatedText,
                                        color = colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                ElevatedButton(
                    onClick = {
                        attemptedSave = true
                        if (validateFormData(title, content) && !isSaving) {
                            isSaving = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        updateSnippet(
                                            context = context,
                                            snippetId = snippetId,
                                            title = title,
                                            content = content,
                                            bitmaps = bitmaps
                                        )
                                    }
                                    isSaving = false
                                    snackbarHostState.showSnackbar(updatedSuccessfully)
                                    navController.popBackStack()
                                } catch (e: Exception) {
                                    isSaving = false
                                    snackbarHostState.showSnackbar(
                                        e.message ?: "Error updating snippet"
                                    )
                                }
                            }
                        } else if (!isSaving) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Please enter a title and content")
                            }
                        }
                    },
                    enabled = validateFormData(title, content) && !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .padding(horizontal = MaterialTheme.spacing.small)
                        .height(52.dp),
                    colors = ButtonDefaults.elevatedButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    elevation = ButtonDefaults.elevatedButtonElevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 2.dp
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(text = save, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }

        if (previewIndex != null) {
            val idx = previewIndex!!
            if (idx >= 0 && idx < bitmaps.size) {
                Dialog(onDismissRequest = { previewIndex = null }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MaterialTheme.spacing.medium)
                    ) {
                        Image(
                            bitmap = bitmaps[idx].asImageBitmap(),
                            contentDescription = "Preview image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EditSnippetScreenPreview() {
    SellerKeyboardTheme {
        EditSnippetScreen(
            navController = rememberNavController(),
            snippetId = 1
        )
    }
}

private suspend fun updateSnippet(
    context: Context,
    snippetId: Int,
    title: String,
    content: String,
    bitmaps: List<Bitmap>
) {
    if (bitmaps.isNotEmpty()) {
        val imageUrls = bitmaps.map { bitmap -> saveImageWithGlide(bitmap, context) }
        updateDataToSQLite(context, snippetId, title, content, imageUrls)
    } else {
        updateDataToSQLiteNoImage(context, snippetId, title, content)
    }
}

private fun saveImageWithGlide(bitmap: Bitmap, context: Context): String {
    return try {
        val imageName = "img_" + UUID.randomUUID().toString() + ".jpg"
        val file = File(context.cacheDir, imageName)
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
        }
        file.absolutePath
    } catch (e: IOException) {
        e.printStackTrace()
        ""
    }
}

private fun updateDataToSQLite(
    context: Context,
    snippetId: Int,
    title: String,
    content: String,
    imageUrls: List<String>
) {
    DatabaseHelper(context).use { db ->
        val snippetsDao = SnippetsDao(db)
        snippetsDao.updateSnippetById(
            snippetId = snippetId,
            title = title,
            content = content,
            imageUrls = imageUrls
        )
    }
}

private fun updateDataToSQLiteNoImage(
    context: Context,
    snippetId: Int,
    title: String,
    content: String
) {
    DatabaseHelper(context).use { db ->
        val snippetsDao = SnippetsDao(db)
        snippetsDao.updateSnippetByIdNoImageUrls(
            snippetId = snippetId,
            title = title,
            content = content
        )
    }
}

private fun String.toUri(): Uri {
    val parsed = Uri.parse(this)
    return if (parsed.scheme.isNullOrEmpty()) {
        Uri.fromFile(File(this))
    } else {
        parsed
    }
}

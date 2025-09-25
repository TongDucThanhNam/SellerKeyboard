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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.bumptech.glide.Glide
import com.terasumi.sellerkeyboard.NavigationViewModel
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSnippetScreen(
    navController: NavHostController,
    navigationViewModel: NavigationViewModel = viewModel()
) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    var isSaving by remember { mutableStateOf(false) }
    var attemptedSave by remember { mutableStateOf(false) }
    var isLoadingImages by remember { mutableStateOf(false) }

    // UI states for preview and undo
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var lastRemoved by remember { mutableStateOf<Pair<Int, Pair<Uri, Bitmap>?>?>(null) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    val focusManager = LocalFocusManager.current

//    val backgroundBrush = remember {
//        Brush.verticalGradient(
//            colors = listOf(
//                colorScheme.surfaceVariant.copy(alpha = 0.35f),
//                colorScheme.background
//            )
//        )
//    }

    //stringResource
    val resources = context.resources
    // Preload string resources
    val addSnippet = remember { resources.getString(R.string.add_snippet) }
    val enterLabel = remember { resources.getString(R.string.enter_label) }
    val enterContent = remember { resources.getString(R.string.enter_content) }
    val save = remember { resources.getString(R.string.save) }
    // Some optional strings may not exist in all projects; provide safe defaults
    val tapToAddImages = remember { "Bấm để thêm hình ảnh" }
    val savedSuccessfully = remember { "Saved successfully" }
    val quickTitleSuggestions = remember {
        listOf(
            "Shipping update",
            "Order ready to ship",
            "Thank you follow-up"
        )
    }
    val quickBodySuggestions = remember {
        listOf(
            "Hi there! Thanks so much for your order. Here's what happens next:",
            "We're preparing your package right now. Below is the latest status and what to expect:",
            "Need support? Reply to this message and we'll get back to you within the day."
        )
    }

    val MAX_IMAGES = 5

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        // limit to max images for UX
        val distinctUris = uris.distinct()
        val limited =
            if (distinctUris.size > MAX_IMAGES) distinctUris.take(MAX_IMAGES) else distinctUris
        imageUris = limited
        isLoadingImages = true
        // Load bitmaps on background coroutine
        scope.launch {
            val loaded = limited.map { uri ->
                withContext(Dispatchers.IO) { loadBitmap(context, uri) }
            }
            bitmaps = loaded
            isLoadingImages = false
        }
    }
    val localLifecycleOwner = LocalLifecycleOwner.current

    val imageResource = if (isSystemInDarkTheme()) {
        R.drawable.bxs_image_add // Replace with your dark mode image resource
    } else {
        R.drawable.bx_image_add // Replace with your light mode image resource
    }

    LaunchedEffect(navigationViewModel.navigationEvent) {
        navigationViewModel.navigationEvent.observe(localLifecycleOwner) { directions ->
            directions?.let {
                navController.navigate(it)
                navigationViewModel.onNavigationEventHandled()
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },

        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = addSnippet) },
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
//                .background(backgroundBrush)
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

                // Form card
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
                                focusManager.moveFocus(
                                    FocusDirection.Down
                                )
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
                                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xSmall)) {
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
                                    val showCount =
                                        if (total > visibleCount) visibleCount else total

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
                                                            // remove this image and offer undo
                                                            val removedUri =
                                                                imageUris.getOrNull(index)
                                                            val removedBmp =
                                                                bitmaps.getOrNull(index)
                                                            val newUris = imageUris.toMutableList()
                                                            val newBitmaps = bitmaps.toMutableList()
                                                            if (index < newUris.size) newUris.removeAt(
                                                                index
                                                            )
                                                            if (index < newBitmaps.size) newBitmaps.removeAt(
                                                                index
                                                            )
                                                            imageUris = newUris
                                                            bitmaps = newBitmaps
                                                            lastRemoved = Pair(
                                                                index,
                                                                removedUri?.let { uri ->
                                                                    removedBmp?.let { bmp ->
                                                                        Pair(
                                                                            uri,
                                                                            bmp
                                                                        )
                                                                    }
                                                                })

                                                            scope.launch {
                                                                val result =
                                                                    snackbarHostState.showSnackbar(
                                                                        "Image removed",
                                                                        actionLabel = "Undo"
                                                                    )
                                                                if (result == SnackbarResult.ActionPerformed) {
                                                                    // restore
                                                                    lastRemoved?.let { pair ->
                                                                        val restoreIndex =
                                                                            pair.first
                                                                        val data = pair.second
                                                                        data?.let { (uri, bmp) ->
                                                                            val urisMutable =
                                                                                imageUris.toMutableList()
                                                                            val bmMutable =
                                                                                bitmaps.toMutableList()
                                                                            val insertIndex =
                                                                                if (restoreIndex <= urisMutable.size) restoreIndex else urisMutable.size
                                                                            urisMutable.add(
                                                                                insertIndex,
                                                                                uri
                                                                            )
                                                                            bmMutable.add(
                                                                                insertIndex,
                                                                                bmp
                                                                            )
                                                                            imageUris = urisMutable
                                                                            bitmaps = bmMutable
                                                                        }
                                                                    }
                                                                    lastRemoved = null
                                                                }
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

                                    // Add tile at end
                                    item {
                                        val canAddMore =
                                            bitmaps.size < MAX_IMAGES && !isLoadingImages
                                        Surface(
                                            modifier = Modifier
                                                .size(MaterialTheme.sizes.imageThumb)
                                                .clip(MaterialTheme.shapes.medium)
                                                .border(
                                                    1.dp,
                                                    colorScheme.outline.copy(alpha = 0.2f),
                                                    MaterialTheme.shapes.medium
                                                )
                                                .clickable(enabled = canAddMore) {
                                                    imagePickerLauncher.launch(
                                                        "image/*"
                                                    )
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
                                                            modifier = Modifier.size(
                                                                MaterialTheme.sizes.iconMedium
                                                            )
                                                        )
                                                    }

                                                    bitmaps.isEmpty() -> {
                                                        Image(
                                                            painter = painterResource(id = imageResource),
                                                            contentDescription = null,
                                                            modifier = Modifier.size(64.dp)
                                                        )
                                                    }

                                                    else -> {
                                                        if (total > visibleCount) {
                                                            Box(
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Add,
                                                                    contentDescription = "Add images",
                                                                    tint = colorScheme.primary,
                                                                    modifier = Modifier.size(
                                                                        MaterialTheme.sizes.iconMedium
                                                                    )
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
                                                        } else {
                                                            Icon(
                                                                imageVector = Icons.Default.Add,
                                                                contentDescription = "Add images",
                                                                tint = if (canAddMore) colorScheme.primary else colorScheme.outline,
                                                                modifier = Modifier.size(
                                                                    MaterialTheme.sizes.iconMedium
                                                                )
                                                            )
                                                        }

                                                        if (!canAddMore) {
                                                            Surface(
                                                                modifier = Modifier
                                                                    .align(Alignment.BottomCenter)
                                                                    .padding(bottom = MaterialTheme.spacing.small),
                                                                shape = RoundedCornerShape(50),
                                                                color = colorScheme.surfaceVariant.copy(
                                                                    alpha = 0.9f
                                                                ),
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
                                    // perform IO on background thread
                                    val saved = withContext(Dispatchers.IO) {
                                        if (bitmaps.isEmpty()) {
                                            saveDataToSQLite(context, title, content, emptyList())
                                        } else {
                                            val imagePaths = bitmaps.map { bitmap ->
                                                saveImageToInternalStorage(context, bitmap)
                                            }
                                            saveDataToSQLite(context, title, content, imagePaths)
                                        }
                                        true
                                    }

                                    if (saved) {
                                        isSaving = false
                                        snackbarHostState.showSnackbar(savedSuccessfully)
                                        navController.popBackStack("main", inclusive = false)
                                    }
                                } catch (e: Exception) {
                                    isSaving = false
                                    snackbarHostState.showSnackbar(
                                        e.message ?: "Error saving snippet"
                                    )
                                }
                            }
                        } else if (!isSaving) {
                            scope.launch { snackbarHostState.showSnackbar("Please enter a title and content") }
                        }
                    },
                    enabled = validateFormData(title, content) && !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .padding(horizontal = MaterialTheme.spacing.small)
                        .height(52.dp),
                    colors = ButtonDefaults.elevatedButtonColors(containerColor = colorScheme.primary),
                    elevation = ButtonDefaults.elevatedButtonElevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 2.dp
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = colorScheme.onPrimary
                        )
                    } else {
                        Text(text = save, color = colorScheme.onPrimary)
                    }
                }
            }
        }

        // Full screen image preview dialog
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

suspend fun loadBitmap(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
    Glide.with(context)
        .asBitmap()
        .load(uri)
        .submit()
        .get()
}

fun validateFormData(title: String, content: String): Boolean {
    return title.isNotEmpty() && content.isNotEmpty()
}


private fun saveImageToInternalStorage(context: Context, bitmap: Bitmap): String {
    val imageName = "img_" + UUID.randomUUID().toString() + ".jpg"
    val file = File(context.cacheDir, imageName)
    try {
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }
    return file.absolutePath
}

private fun saveDataToSQLite(
    context: Context,
    title: String,
    content: String,
    imageUrls: List<String>
) {
    val snippet = Snippets(0, title, content, imageUrls)
    SnippetsDao(DatabaseHelper(context)).insertSnippet(snippet)
    // Removed navigation from here to avoid double pop; UI layer handles navigation and snackbar.
}

@Preview(showBackground = true)
@Composable
fun AddSnippetFragmentPreview() {
    SellerKeyboardTheme {
        AddSnippetScreen(navController = rememberNavController())
    }
}

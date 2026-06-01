package com.terasumi.sellerkeyboard.main

import SnippetsDao
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

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

    val addSnippet = stringResource(R.string.add_snippet)
    val save = stringResource(R.string.save)
    val savedSuccessfully = stringResource(R.string.snippet_saved)
    val formInvalid = stringResource(R.string.snippet_form_invalid)
    val saveError = stringResource(R.string.snippet_save_error)
    val imageRemoved = stringResource(R.string.image_removed)
    val undo = stringResource(R.string.undo)

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        val distinctUris = uris.distinct()
        val limited =
            if (distinctUris.size > SNIPPET_MAX_IMAGES) {
                distinctUris.take(SNIPPET_MAX_IMAGES)
            } else {
                distinctUris
            }
        imageUris = limited
        isLoadingImages = true
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
        R.drawable.bxs_image_add
    } else {
        R.drawable.bx_image_add
    }

    LaunchedEffect(navigationViewModel.navigationEvent) {
        navigationViewModel.navigationEvent.observe(localLifecycleOwner) { directions ->
            directions?.let {
                navController.navigate(it)
                navigationViewModel.onNavigationEventHandled()
            }
        }
    }

    val canSave = validateFormData(title, content) && !isSaving

    fun saveSnippet() {
        attemptedSave = true
        if (validateFormData(title, content) && !isSaving) {
            isSaving = true
            scope.launch {
                try {
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
                    snackbarHostState.showSnackbar(e.message ?: saveError)
                }
            }
        } else if (!isSaving) {
            scope.launch { snackbarHostState.showSnackbar(formInvalid) }
        }
    }

    fun removeImageAt(index: Int) {
        val removedUri = imageUris.getOrNull(index)
        val removedBitmap = bitmaps.getOrNull(index)
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
            if (removedUri != null && removedBitmap != null) {
                Pair(removedUri, removedBitmap)
            } else {
                null
            }
        )

        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = imageRemoved,
                actionLabel = undo
            )
            if (result == SnackbarResult.ActionPerformed) {
                lastRemoved?.let { (restoreIndex, data) ->
                    data?.let { (uri, bitmap) ->
                        val urisMutable = imageUris.toMutableList()
                        val bitmapsMutable = bitmaps.toMutableList()
                        val insertIndex = if (restoreIndex <= urisMutable.size) {
                            restoreIndex
                        } else {
                            urisMutable.size
                        }
                        urisMutable.add(insertIndex, uri)
                        bitmapsMutable.add(insertIndex, bitmap)
                        imageUris = urisMutable
                        bitmaps = bitmapsMutable
                    }
                }
            }
            lastRemoved = null
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
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            SnippetEditorBottomBar(
                saveLabel = save,
                isSaving = isSaving,
                canSave = canSave,
                onSave = { saveSnippet() }
            )
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = MaterialTheme.spacing.medium)
                    .padding(vertical = MaterialTheme.spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
            ) {
                SnippetEditorCard(
                    title = title,
                    content = content,
                    bitmaps = bitmaps,
                    attemptedSave = attemptedSave,
                    isLoadingImages = isLoadingImages,
                    imagePlaceholderRes = imageResource,
                    onTitleChange = { title = it },
                    onContentChange = { content = it },
                    onClearTitle = { title = "" },
                    onClearContent = { content = "" },
                    onAddImages = { imagePickerLauncher.launch("image/*") },
                    onPreviewImage = { previewIndex = it },
                    onRemoveImage = { removeImageAt(it) }
                )
            }

            SnippetImagePreviewDialog(
                bitmaps = bitmaps,
                previewIndex = previewIndex,
                onDismiss = { previewIndex = null }
            )
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

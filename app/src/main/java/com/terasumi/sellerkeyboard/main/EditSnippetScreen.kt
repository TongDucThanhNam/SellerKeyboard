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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    val editSnippet = stringResource(R.string.edit_snippet)
    val save = stringResource(R.string.save)
    val updatedSuccessfully = stringResource(R.string.snippet_updated)
    val formInvalid = stringResource(R.string.snippet_form_invalid)
    val updateError = stringResource(R.string.snippet_update_error)
    val imageRemoved = stringResource(R.string.image_removed)
    val undo = stringResource(R.string.undo)

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
        val limited = if (distinctUris.size > SNIPPET_MAX_IMAGES) {
            distinctUris.take(SNIPPET_MAX_IMAGES)
        } else {
            distinctUris
        }
        imageUris = limited
        isLoadingImages = true
        scope.launch {
            val loaded = limited.map { loadBitmap(context, it) }
            bitmaps = loaded
            isLoadingImages = false
        }
    }

    val canSave = validateFormData(title, content) && !isSaving

    fun saveSnippet() {
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
                        e.message ?: updateError
                    )
                }
            }
        } else if (!isSaving) {
            scope.launch {
                snackbarHostState.showSnackbar(formInvalid)
            }
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = editSnippet) },
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
    } catch (_: IOException) {
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

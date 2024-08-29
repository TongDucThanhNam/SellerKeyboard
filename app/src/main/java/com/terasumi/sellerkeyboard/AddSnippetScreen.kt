package com.terasumi.sellerkeyboard

import SnippetsDao
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.bumptech.glide.Glide
import com.terasumi.sellerkeyboard.ui.theme.SellerKeyboardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
    var title by remember { mutableStateOf(TextFieldValue("")) }
    var content by remember { mutableStateOf(TextFieldValue("")) }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        imageUris = uris
        bitmaps = runBlocking {
            uris.map { uri ->
                loadBitmap(context, uri)
            }
        }
    }
    val localLifecycleOwner = LocalLifecycleOwner.current

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
            TopAppBar(
                title = { Text(text = "Thêm tin nhắn mẫu") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Localized description"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* do something */ }) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Localized description"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Nhập từ khoá") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                trailingIcon = {
                    if (title.text.isNotEmpty()) {
                        IconButton(onClick = { title = TextFieldValue("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear"
                            )
                        }
                    }
                }
            )

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Nhập nội dung") },
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                trailingIcon = {
                    if (content.text.isNotEmpty()) {
                        IconButton(onClick = { content = TextFieldValue("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear"
                            )
                        }
                    }
                }
            )

            Box(modifier = Modifier
                .padding(bottom = 8.dp)
                .clickable {
                    imagePickerLauncher.launch("image/*")
                }
                .align(Alignment.CenterHorizontally)) {
                if (bitmaps.isNotEmpty()) {
                    Log.d("AddSnippetScreen", "Number of bitmaps: ${bitmaps.size}")

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        bitmaps.forEach { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(100.dp)
                            )
                        }
                    }
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.image_holder),
                        contentDescription = null
                    )
                }
            }

            Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                if (validateFormData(title.text, content.text)) {
                    uploadImagesAndSaveData(
                        context,
                        title.text,
                        content.text,
                        imageUris,
                        bitmaps,
                        navController
                    )
                }
            }) {
                Text(text = "Lưu")
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

private fun uploadImagesAndSaveData(
    context: Context,
    title: String,
    content: String,
    imageUris: List<Uri>,
    bitmaps: List<Bitmap>,
    navController: NavHostController
) {
    if (imageUris.isEmpty() || bitmaps.isEmpty()) {
        saveDataToSQLite(context, title, content, emptyList(), navController)
    } else {
        val imagePaths = bitmaps.map { bitmap ->
            saveImageToInternalStorage(context, bitmap)
        }
        saveDataToSQLite(context, title, content, imagePaths, navController)
    }
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
    imageUrls: List<String>,
    navController: NavHostController
) {
    val snippet = Snippets(0, title, content, imageUrls)
    SnippetsDao(DatabaseHelper(context)).insertSnippet(snippet)
    // Show a snackbar

    //Navigate back
    navController.popBackStack("main", inclusive = false)
}

@Preview(showBackground = true)
@Composable
fun AddSnippetFragmentPreview() {
    SellerKeyboardTheme {
        AddSnippetScreen(navController = rememberNavController())
    }
}
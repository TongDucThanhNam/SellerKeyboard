package com.terasumi.sellerkeyboard

import SnippetsDao
import android.annotation.SuppressLint
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.terasumi.sellerkeyboard.ui.theme.SellerKeyboardTheme
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID


@SuppressLint("MutableCollectionMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSnippetScreen(navController: NavHostController, snippetId: Int) {
    val context = LocalContext.current

    val snippet = remember { mutableStateOf<Snippets?>(null) }

    var title by remember { mutableStateOf(TextFieldValue("")) }
    var content by remember { mutableStateOf(TextFieldValue("")) }
//    var imageUri by remember { mutableStateOf<Uri?>(null) }
//    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val bitmaps = remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    DisposableEffect(Unit) {
        onDispose {
            snippet.value = null
            title = TextFieldValue("")
            content = TextFieldValue("")
            imageUris = emptyList()
            bitmaps.value = emptyList()
        }
    }


    // Step 1: Define a new state to track the loading status of bitmaps
    var isBitmapLoading by remember { mutableStateOf(false) }

    // Step 2: Use LaunchedEffect to load bitmaps only when snippetId changes
    LaunchedEffect(snippetId) {
        Log.d("EditSnippetScreen", "Snippet ID: $snippetId")
        val dbHelper = DatabaseHelper(context)
        dbHelper.use {
            val snippetsDao = SnippetsDao(dbHelper)
            snippet.value = snippetsDao.fetchSnippetById(snippetId)
        }
        isBitmapLoading = true
    }


    // Step 3: Update the bitmaps state outside of the LaunchedEffect
    LaunchedEffect(snippet.value) {
        Log.d("EditSnippetScreen", "Snippet: ${snippet.value}")
        snippet.value?.let {
            title = TextFieldValue(it.title)
            content = TextFieldValue(it.content)
            Log.d("EditSnippetScreen", "Snippet images: ${it.imageUrls}")
            bitmaps.value = listOf()

            var updatedBitmaps = listOf<Bitmap>()

            snippet.value?.imageUrls?.forEach { imageUrl ->
                Glide.with(context)
                    .asBitmap()
                    .load(imageUrl)
                    .into(object : SimpleTarget<Bitmap>() {
                        override fun onResourceReady(
                            resource: Bitmap,
                            transition: Transition<in Bitmap>?
                        ) {
                            Log.d("Fetch bimap from imageUrls", "Bitmap: $resource")
                            updatedBitmaps = updatedBitmaps + resource
                            isBitmapLoading = false
                        }
                    })
            }
            bitmaps.value = updatedBitmaps

            Log.d("EditSnippetScreen", "Bitmaps: ${bitmaps.value}")

        }
    }

    //top app bar
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        imageUris = uris
        bitmaps.value = runBlocking {
            uris.map { uri ->
                loadBitmap(context, uri)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Sửa tin nhẵn mẫu") },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
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
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Nhập nhãn") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                trailingIcon = {
                    if (title.text.isNotEmpty()) {
                        IconButton(onClick = {
                            title = TextFieldValue("")
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = null
                            )
                        }
                    }
                }
            )

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Nhập nội dung") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                trailingIcon = {
                    if (content.text.isNotEmpty()) {
                        IconButton(onClick = {
                            content = TextFieldValue("")
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = null
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
                if (bitmaps.value.isNotEmpty()) {
                    Log.d("EditSnippets", "Number of bitmaps: ${bitmaps.value.size}")

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        bitmaps.value.forEach { bitmap ->
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

            Button(
                modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                    if (validateFormData(title.text, content.text)) {
                        Log.d("EditSnippetScreen", "Title: ${title.text}, Content: ${content.text}")
                        //TODO: Upload image and save data
                        // uploadImageAndSaveData(context, title.text, content.text, imageUri, bitmap)

                        //Update snippet
                        updateSnippet(
                            context,
                            snippetId,
                            title.text,
                            content.text,
                            imageUris,
                            bitmaps.value,
                            navController
                        )
                    }
                }) {
                Text(text = "Lưu lại")
            }
        }
    }
}

// On EditSnippet Screen
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

fun updateSnippet(
    context: Context,
    snippetId: Int,
    title: String,
    content: String,
    imageUris: List<Uri>,
    bitmaps: List<Bitmap>,
    navController: NavHostController
) {
    Log.d("EditSnippetScreen", "Snippet ID: $snippetId, Title: $title, Content: $content")
    Log.d("EditSnippetScreen", "Bitmaps: $bitmaps")

    if (bitmaps.isNotEmpty()) {
        // save bitmap to internal storage
        val imageUrls = mutableListOf<String>()
        bitmaps.forEach { bitmap ->
            val imageUrl = saveImageWithGlide(bitmap, context)
            imageUrls.add(imageUrl)
        }

        updateDataToSQLite(context, snippetId, title, content, imageUrls, navController)

    } else {
        // Upload image and save data
        // uploadImageAndSaveData(context, title, content, imageUri, bitmap)
        updateDataToSQLiteNoImage(context, snippetId, title, content, navController)
    }
}

private fun saveImageWithGlide(bitmap: Bitmap, context: Context): String {
    return try {
        val imageName = "img_" + UUID.randomUUID().toString() + ".jpg"
        val file = File(context.cacheDir, imageName) // Or another suitable directory
        try {
            FileOutputStream(file).use { fos ->
                bitmap.compress(
                    Bitmap.CompressFormat.JPEG, // You can choose PNG too
                    100,
                    fos
                ) // You can adjust the quality (0-100)
            }
        } catch (e: IOException) {
            e.printStackTrace() // Handle the exception appropriately
        }
        // Now you have the file path: file.getAbsolutePath()
        Log.d("Image", "Image saved to internal storage: " + file.absolutePath)
        file.absolutePath
    } catch (e: Exception) {
        Log.e("AddSnippetFragment", "Error saving image with Glide", e)
        ""
    }
}

private fun updateDataToSQLite(
    context: Context,
    snippetId: Int,
    title: String,
    content: String,
    imageUrls: List<String>,
    navController: NavHostController
) {
    // Update snippet
    val dbHelper = DatabaseHelper(context)
//    val snippetsDao = SnippetsDao(dbHelper)
//    snippetsDao.updateSnippetById(
//        snippetId = snippetId,
//        title = title,
//        content = content,
//        imageUrls = imageUrls
//    )
    dbHelper.use {
        val snippetsDao = SnippetsDao(dbHelper)
        snippetsDao.updateSnippetById(
            snippetId = snippetId,
            title = title,
            content = content,
            imageUrls = imageUrls
        )
    }
    //Log
    Log.d("EditSnippetScreen", "Update snippet success")
    //Show SnackBar

    //Navigate back
    navController.popBackStack()
}

//update Data to SQLite no Image
private fun updateDataToSQLiteNoImage(
    context: Context,
    snippetId: Int,
    title: String,
    content: String,
    navController: NavHostController
) {
    // Update snippet
    val dbHelper = DatabaseHelper(context)
//    val snippetsDao = SnippetsDao(dbHelper)
//    snippetsDao.updateSnippetByIdNoImageUrls(
//        snippetId = snippetId,
//        title = title,
//        content = content
//    )
    dbHelper.use {
        val snippetsDao = SnippetsDao(dbHelper)
        snippetsDao.updateSnippetByIdNoImageUrls(
            snippetId = snippetId,
            title = title,
            content = content
        )
    }
    //Log
    Log.d("EditSnippetScreen", "Update snippet success")
    //Show SnackBar

    //Navigate back
    navController.popBackStack()
}
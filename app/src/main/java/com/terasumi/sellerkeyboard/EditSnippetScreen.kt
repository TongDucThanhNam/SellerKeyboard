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

@SuppressLint("MutableCollectionMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSnippetScreen(navController: NavHostController, snippetId: Int) {
    val context = LocalContext.current
    val dbHelper = DatabaseHelper(context)
    val snippetsDao = SnippetsDao(dbHelper)

    val snippet = remember { mutableStateOf<Snippets?>(null) }

    var title by remember { mutableStateOf(TextFieldValue("")) }
    var content by remember { mutableStateOf(TextFieldValue("")) }
//    var imageUri by remember { mutableStateOf<Uri?>(null) }
//    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }


    LaunchedEffect(snippetId) {
        Log.d("EditSnippetScreen", "Snippet ID: $snippetId")
        snippet.value = snippetsDao.fetchSnippetById(snippetId)
    }

    LaunchedEffect(snippet.value) {
        Log.d("EditSnippetScreen", "Snippet: ${snippet.value}")
        snippet.value?.let {
            title = TextFieldValue(it.title)
            content = TextFieldValue(it.content)
            // TODO: Load image from URL

            Log.d("EditSnippetScreen", "Snippet images: ${it.imageUrls}")
            it.imageUrls.forEach { imageUrl ->
                // Tải ảnh bất đồng bộ với Glide
                Glide.with(context)
                    .asBitmap()
                    .load(imageUrl)
                    .into(object : SimpleTarget<Bitmap>() {
                        override fun onResourceReady(
                            resource: Bitmap,
                            transition: Transition<in Bitmap>?
                        ) {
                            // Thêm Bitmap vào list
                            bitmaps = bitmaps + resource
                        }
                    })
            }
        }
    }

    //top app bar
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
                if (bitmaps.isNotEmpty()) {
                    Log.d("EditSnippets", "Number of bitmaps: ${bitmaps.size}")

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

            Button(
                modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                    if (validateFormData(title.text, content.text)) {
                        Log.d("EditSnippetScreen", "Title: ${title.text}, Content: ${content.text}")
                        //TODO: Upload image and save data
                        // uploadImageAndSaveData(context, title.text, content.text, imageUri, bitmap)

                        //Update snippet
                        updateSnippet(context, snippetId, title.text, content.text, imageUris, bitmaps, navController)
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
    if (bitmaps.isNotEmpty()) {
        // Upload image and save data
        // uploadImageAndSaveData(context, title, content, imageUri, bitmap)
        updateDataToSQLiteNoImage(context, snippetId, title, content, navController)
    } else {
        // Update snippet
        updateDataToSQLite(context, snippetId, title, content, emptyList(), navController)
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
    val snippetsDao = SnippetsDao(dbHelper)
    snippetsDao.updateSnippetById(
        snippetId = snippetId,
        title = title,
        content = content,
        imageUrls = imageUrls
    )
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
    val snippetsDao = SnippetsDao(dbHelper)
    snippetsDao.updateSnippetByIdNoImageUrls(
        snippetId = snippetId,
        title = title,
        content = content
    )
    //Log
    Log.d("EditSnippetScreen", "Update snippet success")
    //Show SnackBar

    //Navigate back
    navController.popBackStack()
}


package com.terasumi.sellerkeyboard

import SnippetsDao
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.FractionalThreshold
import androidx.wear.compose.material.rememberSwipeableState
import androidx.wear.compose.material.swipeable
import com.terasumi.sellerkeyboard.ui.theme.SellerKeyboardTheme
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val listSnippets = remember { mutableStateOf(listOf<Snippets>()) }

    //stringResource
//    val homePage = StringResources.homepage
    val homePage = remember {
        StringResources.homepage
    }

    LaunchedEffect(context) {
        listSnippets.value = fetchDataFromSQLite(context)
    }

    SellerKeyboardTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(homePage) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                    actions = {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Bật/tắt bàn phím") }, onClick = {
                                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                                showMenu = false
                            })
                            DropdownMenuItem(text = { Text("Thay đổi bàn phím") }, onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                                } catch (e: Exception) {
                                    Log.e("HomeScreen", "Error changing keyboard", e)
                                }
                                showMenu = false
                            })
                            DropdownMenuItem(text = { Text("Voice Assistance") }, onClick = {
                                context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                                showMenu = false
                            })
                            DropdownMenuItem(text = { Text("Cài đặt ngôn ngữ phụ") }, onClick = {
                                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS))
                                showMenu = false
                            })
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { navController.navigate("addSnippet") }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                }
            },
            content = { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    LazyColumn {
                        items(listSnippets.value) { snippet ->
                            ItemCard(
                                snippetId = snippet.id,
                                title = snippet.title,
                                content = snippet.content,
                                imageUrls = snippet.imageUrls,
                                navController = navController,
                                listSnippets = listSnippets
                            )
                        }
                    }
                }
            }
        )
    }
}

@SuppressLint("CoroutineCreationDuringComposition")
@OptIn(ExperimentalWearMaterialApi::class)
@Composable
fun ItemCard(
    snippetId: Int,
    title: String,
    content: String,
    imageUrls: List<String>,
    navController: NavController,
    listSnippets: MutableState<List<Snippets>>
) {
    val context = LocalContext.current
    val swipeAble = rememberSwipeableState(initialValue = 0)
    val scope = rememberCoroutineScope()
    val anchors = mapOf(0f to 0, -300f to 1)
    var isRevealed by remember { mutableStateOf(false) }
    var isDeleted by remember { mutableStateOf(false) }

    if (isDeleted) {
        Text(text = "Deleted!", color = Color.Green, modifier = Modifier.padding(16.dp))
        scope.launch {
            swipeAble.animateTo(0)
            isDeleted = false
            isRevealed = false
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = isRevealed,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        ) {
            IconButton(onClick = {
                isDeleted = true
                deleteDataFromSQLite(context, snippetId, listSnippets)
            }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.Red
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .offset { IntOffset(swipeAble.offset.value.roundToInt(), 0) }
                .swipeable(
                    state = swipeAble,
                    anchors = anchors,
                    thresholds = { _, _ -> FractionalThreshold(0.3f) },
                    orientation = Orientation.Horizontal
                )
                .clickable { navController.navigate("editSnippet/$snippetId") },
            elevation = CardDefaults.cardElevation(4.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                Column {
                    Text(text = title, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val imageFile = File(imageUrls.getOrNull(0) ?: "")
                        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Image",
                                modifier = Modifier.size(64.dp)
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.icon),
                                contentDescription = "Image",
                                modifier = Modifier.size(64.dp)
                            )
                        }

                        Text(
                            text = content,
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .align(Alignment.CenterVertically)
                                .fillMaxWidth(),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    isRevealed = swipeAble.currentValue == 1
}

private fun deleteDataFromSQLite(
    context: Context,
    snippetId: Int,
    listSnippets: MutableState<List<Snippets>>
) {
//    SnippetsDao(DatabaseHelper(context)).deleteSnippetById(snippetId)
    val dbHelper = DatabaseHelper(context)
    dbHelper.use {
        val snippetsDao = SnippetsDao(dbHelper)
        snippetsDao.deleteSnippetById(snippetId)
    }
    Log.d("HomeScreen", "Deleted snippet with ID $snippetId from SQLite")
    listSnippets.value = fetchDataFromSQLite(context)
}

fun fetchDataFromSQLite(context: Context): List<Snippets> {
//    val snippets = SnippetsDao(DatabaseHelper(context)).fetchSnippets()
    val dbHelper = DatabaseHelper(context)
    dbHelper.use {
        val snippetsDao = SnippetsDao(dbHelper)
        val snippets = snippetsDao.fetchSnippets()
        Log.d("HomeScreen", "Fetched ${snippets.size} snippets from SQLite")
        return snippets
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    SellerKeyboardTheme {
        HomeScreen(navController = rememberNavController())
    }
}

@Preview(showBackground = true)
@Composable
fun ItemCardPreview() {
    SellerKeyboardTheme {
        ItemCard(
            snippetId = 1,
            title = "Title",
            content = "Content",
            imageUrls = listOf(""),
            navController = rememberNavController(),
            listSnippets = remember { mutableStateOf(listOf()) })
    }
}
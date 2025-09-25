package com.terasumi.sellerkeyboard.main

import SnippetsDao
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.database.DatabaseHelper
import com.terasumi.sellerkeyboard.database.Snippets
import com.terasumi.sellerkeyboard.ui.theme.SellerKeyboardTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val listSnippets = remember { mutableStateOf(listOf<Snippets>()) }

    //stringResource
    val resources = context.resources
    val homePage = remember {
        resources.getString(R.string.homepage)
    }
    val turnOnKeyboard = remember {
        resources.getString(R.string.enable_keyboard)
    }

    val changeKeyboard = remember {
        resources.getString(R.string.change_keyboard)
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
                            DropdownMenuItem(text = { Text(turnOnKeyboard) }, onClick = {
                                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                                showMenu = false
                            })
                            DropdownMenuItem(text = { Text(changeKeyboard) }, onClick = {
                                try {
                                    val imeManager: InputMethodManager =
                                        context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                                    imeManager.showInputMethodPicker()

                                } catch (e: Exception) {
                                    Log.e("HomeScreen", "Error changing keyboard", e)
                                }
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
                        item {
                            // Alert banner change keyboard
                            Row(
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically,
                                content = {
                                    Text(
                                        text = stringResource(R.string.switch_to_seller_keyboard),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Button(onClick = {
                                        try {
                                            val imeManager: InputMethodManager =
                                                context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                                            imeManager.showInputMethodPicker()

                                        } catch (e: Exception) {
                                            Log.e("HomeScreen", "Error changing keyboard", e)
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Keyboard,
                                            contentDescription = "Settings"
                                        )
                                    }

                                },
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth()
                            )
                        }

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

@OptIn(ExperimentalMaterial3Api::class)
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

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                deleteDataFromSQLite(context, snippetId, listSnippets)
                true
            } else false
        },
        positionalThreshold = { it * 0.35f }
    )

    val primaryImagePath = remember(imageUrls) { imageUrls.firstOrNull().orEmpty() }
    val bitmap = remember(primaryImagePath) {
        runCatching { BitmapFactory.decodeFile(primaryImagePath) }.getOrNull()
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeBackground() }
    ) {
        ElevatedCard(
            onClick = { navController.navigate("editSnippet/$snippetId") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column {



                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Big blog image (có placeholder)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Snippet image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .padding(12.dp)
                        ,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.icon), // ảnh mặc định trong res/drawable
                        contentDescription = "Placeholder image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(MaterialTheme.shapes.medium)                            .padding(12.dp)
                            .padding(12.dp)
                        ,
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeBackground() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}



private fun deleteDataFromSQLite(
    context: Context,
    snippetId: Int,
    listSnippets: MutableState<List<Snippets>>
) {
    val dbHelper = DatabaseHelper(context)
    dbHelper.use {
        val snippetsDao = SnippetsDao(dbHelper)
        snippetsDao.deleteSnippetById(snippetId)
    }
    listSnippets.value = fetchDataFromSQLite(context)
}

fun fetchDataFromSQLite(context: Context): List<Snippets> {
    val dbHelper = DatabaseHelper(context)
    dbHelper.use {
        val snippetsDao = SnippetsDao(dbHelper)
        val snippets = snippetsDao.fetchSnippets()
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
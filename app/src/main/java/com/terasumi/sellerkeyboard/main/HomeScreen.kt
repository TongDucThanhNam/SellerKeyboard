package com.terasumi.sellerkeyboard.main

import SnippetsDao
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.terasumi.sellerkeyboard.MyApplication
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.backend.ConvexAuthRepository
import com.terasumi.sellerkeyboard.backend.RestorePlan
import com.terasumi.sellerkeyboard.backend.SnippetBackupRepository
import com.terasumi.sellerkeyboard.backend.planSnippetRestore
import com.terasumi.sellerkeyboard.database.DatabaseHelper
import com.terasumi.sellerkeyboard.database.Snippets
import com.terasumi.sellerkeyboard.service.SellerKeyboard
import com.terasumi.sellerkeyboard.settings.KeyboardSettingsRepository
import com.terasumi.sellerkeyboard.suggestion.SharedPreferencesPersonalDictionary
import com.terasumi.sellerkeyboard.seller.SharedPreferencesProductReplyStore
import com.terasumi.sellerkeyboard.profile.SharedPreferencesAppProfileStore
import com.terasumi.sellerkeyboard.ui.theme.SellerKeyboardTheme
import com.terasumi.sellerkeyboard.ui.theme.spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    convexAuthRepository: ConvexAuthRepository? = null,
    snippetBackupRepository: SnippetBackupRepository? = null,
    showCloudBackup: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val application = context.applicationContext as? MyApplication
    val authRepository = remember(showCloudBackup, application, convexAuthRepository) {
        when {
            !showCloudBackup -> convexAuthRepository
            convexAuthRepository != null -> convexAuthRepository
            else -> application?.convexAuthRepository
        }
    }
    val backupRepository = remember(showCloudBackup, application, snippetBackupRepository) {
        when {
            !showCloudBackup -> snippetBackupRepository
            snippetBackupRepository != null -> snippetBackupRepository
            else -> application?.snippetBackupRepository
        }
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardSettingsRepository = remember(context) { KeyboardSettingsRepository(context) }
    val personalDictionary = remember(context) { SharedPreferencesPersonalDictionary(context) }
    var personalWords by remember { mutableStateOf(personalDictionary.words()) }
    val productReplyStore = remember(context) { SharedPreferencesProductReplyStore(context) }
    var productReplies by remember { mutableStateOf(productReplyStore.list()) }
    val appProfileStore = remember(context) { SharedPreferencesAppProfileStore(context) }
    var appProfileBindings by remember { mutableStateOf(appProfileStore.bindings()) }
    var keyboardPreferences by remember { mutableStateOf(keyboardSettingsRepository.snapshot()) }
    var isKeyHapticEnabled by remember { mutableStateOf(keyboardPreferences.hapticEnabled) }
    val snippetsState = remember { mutableStateOf(emptyList<Snippets>()) }
    var signedInEmail by rememberSaveable {
        mutableStateOf(authRepository?.currentSession()?.email.orEmpty())
    }
    var cloudEmail by rememberSaveable {
        mutableStateOf(authRepository?.currentSession()?.email.orEmpty())
    }
    var cloudPassword by rememberSaveable { mutableStateOf("") }
    var isCloudBusy by remember { mutableStateOf(false) }
    var restorePlan by remember { mutableStateOf<RestorePlan?>(null) }

    val homePage = stringResource(R.string.homepage)
    val enableKeyboard = stringResource(R.string.enable_keyboard)
    val changeKeyboard = stringResource(R.string.change_keyboard)
    val enableVibration = stringResource(R.string.enable_key_vibration)
    val disableVibration = stringResource(R.string.disable_key_vibration)
    val cloudUnavailable = stringResource(R.string.cloud_backup_unavailable)
    val cloudRegistered = stringResource(R.string.cloud_backup_registered)
    val cloudLoggedIn = stringResource(R.string.cloud_backup_logged_in)
    val cloudLoggedOut = stringResource(R.string.cloud_backup_logged_out)
    val cloudNoSnippets = stringResource(R.string.cloud_backup_no_snippets)
    val cloudBackupSavedSuffix = stringResource(R.string.cloud_backup_saved_suffix)
    val cloudRestoreEmpty = stringResource(R.string.cloud_restore_empty)
    val cloudRestoreSuccessSuffix = stringResource(R.string.cloud_restore_success_suffix)
    val cloudGenericError = stringResource(R.string.cloud_error_generic)

    fun refreshSnippets() {
        snippetsState.value = fetchDataFromSQLite(context)
    }

    fun openInputMethodPicker() {
        try {
            val imeManager = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imeManager.showInputMethodPicker()
        } catch (e: Exception) {
            Log.e("HomeScreen", "Input method picker failed: ${e.javaClass.simpleName}")
        }
    }

    fun runCloudOperation(block: suspend () -> String?) {
        if (isCloudBusy) {
            return
        }
        isCloudBusy = true
        scope.launch {
            val message = try {
                block()
            } catch (e: Exception) {
                e.message ?: cloudGenericError
            } finally {
                isCloudBusy = false
            }
            if (!message.isNullOrBlank()) {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    LaunchedEffect(context) {
        refreshSnippets()
    }

    LaunchedEffect(authRepository) {
        val currentEmail = authRepository?.currentSession()?.email.orEmpty()
        signedInEmail = currentEmail
        if (cloudEmail.isBlank()) {
            cloudEmail = currentEmail
        }
    }

    SellerKeyboardTheme {
        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = homePage,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    actions = {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings)
                            )
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text(enableKeyboard) }, onClick = {
                                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                                showMenu = false
                            })
                            DropdownMenuItem(text = { Text(changeKeyboard) }, onClick = {
                                openInputMethodPicker()
                                showMenu = false
                            })
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isKeyHapticEnabled) {
                                            disableVibration
                                        } else {
                                            enableVibration
                                        }
                                    )
                                },
                                onClick = {
                                    isKeyHapticEnabled = !isKeyHapticEnabled
                                    keyboardSettingsRepository.hapticEnabled = isKeyHapticEnabled
                                    keyboardPreferences = keyboardPreferences.copy(
                                        hapticEnabled = isKeyHapticEnabled
                                    )
                                    showMenu = false
                                }
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { navController.navigate("addSnippet") },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_snippet)
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            content = { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        horizontal = MaterialTheme.spacing.medium,
                        vertical = MaterialTheme.spacing.medium
                    ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                ) {
                    item {
                        KeyboardSettingsCard(
                            preferences = keyboardPreferences,
                            onChange = { updated ->
                                keyboardPreferences = updated
                                isKeyHapticEnabled = updated.hapticEnabled
                                keyboardSettingsRepository.save(updated)
                            },
                            onReset = {
                                keyboardSettingsRepository.resetToDefaults()
                                keyboardPreferences = keyboardSettingsRepository.snapshot()
                                isKeyHapticEnabled = keyboardPreferences.hapticEnabled
                            },
                            onClearPersonalDictionary = {
                                personalDictionary.clear()
                                personalWords = emptySet()
                            },
                            personalWords = personalWords,
                            onAddPersonalWord = { word ->
                                personalDictionary.add(word)
                                personalWords = personalDictionary.words()
                            },
                            onRemovePersonalWord = { word ->
                                personalDictionary.remove(word)
                                personalWords = personalDictionary.words()
                            }
                        )
                    }

                    item {
                        ProductManagementCard(
                            products = productReplies,
                            onSave = { product ->
                                productReplyStore.save(product)
                                productReplies = productReplyStore.list()
                            },
                            onDelete = { id ->
                                productReplyStore.delete(id)
                                productReplies = productReplyStore.list()
                            }
                        )
                    }

                    item {
                        AppProfileManagementCard(
                            bindings = appProfileBindings,
                            onSaveAndBind = { profile, packageName ->
                                appProfileStore.saveProfile(profile)
                                appProfileStore.bind(packageName, profile.id)
                                appProfileBindings = appProfileStore.bindings()
                            }
                        )
                    }

                    item {
                        ReplyTemplateManagementCard()
                    }

                    item {
                        ReplyPackManagementCard()
                    }

                    if (showCloudBackup) {
                        item {
                            CloudBackupCard(
                                signedInEmail = signedInEmail,
                                email = cloudEmail,
                                password = cloudPassword,
                                isBusy = isCloudBusy,
                                isCloudAvailable = authRepository != null && backupRepository != null,
                                onEmailChange = { cloudEmail = it },
                                onPasswordChange = { cloudPassword = it },
                                onRegister = {
                                    runCloudOperation {
                                        val repository = authRepository
                                            ?: throw IllegalStateException(cloudUnavailable)
                                        val session = withContext(Dispatchers.IO) {
                                            repository.register(cloudEmail, cloudPassword)
                                        }
                                        signedInEmail = session.email
                                        cloudEmail = session.email
                                        cloudPassword = ""
                                        cloudRegistered
                                    }
                                },
                                onLogin = {
                                    runCloudOperation {
                                        val repository = authRepository
                                            ?: throw IllegalStateException(cloudUnavailable)
                                        val session = withContext(Dispatchers.IO) {
                                            repository.login(cloudEmail, cloudPassword)
                                        }
                                        signedInEmail = session.email
                                        cloudEmail = session.email
                                        cloudPassword = ""
                                        cloudLoggedIn
                                    }
                                },
                                onLogout = {
                                    runCloudOperation {
                                        val repository = authRepository
                                            ?: throw IllegalStateException(cloudUnavailable)
                                        withContext(Dispatchers.IO) {
                                            repository.logout()
                                        }
                                        signedInEmail = ""
                                        cloudPassword = ""
                                        cloudLoggedOut
                                    }
                                },
                                onBackupNow = {
                                    runCloudOperation {
                                        val repository = backupRepository
                                            ?: throw IllegalStateException(cloudUnavailable)
                                        val snippets = snippetsState.value
                                        if (snippets.isEmpty()) {
                                            cloudNoSnippets
                                        } else {
                                            val savedCount = withContext(Dispatchers.IO) {
                                                repository.backup(snippets)
                                            }
                                            "$savedCount $cloudBackupSavedSuffix"
                                        }
                                    }
                                },
                                onRestore = {
                                    runCloudOperation {
                                        val repository = backupRepository
                                            ?: throw IllegalStateException(cloudUnavailable)
                                        val remoteSnippets = withContext(Dispatchers.IO) {
                                            repository.listBackup()
                                        }
                                        val plan = planSnippetRestore(
                                            remoteSnippets = remoteSnippets,
                                            localSnippets = snippetsState.value
                                        )
                                        if (plan.snippetsToInsert.isEmpty()) {
                                            cloudRestoreEmpty
                                        } else {
                                            restorePlan = plan
                                            null
                                        }
                                    }
                                }
                            )
                        }
                    }

                    item {
                        SnippetSectionHeader(snippetCount = snippetsState.value.size)
                    }

                    if (snippetsState.value.isEmpty()) {
                        item {
                            EmptySnippetsState(onAddSnippet = {
                                navController.navigate("addSnippet")
                            })
                        }
                    } else {
                        items(snippetsState.value, key = { it.id }) { snippet ->
                            ItemCard(
                                snippetId = snippet.id,
                                title = snippet.title,
                                content = snippet.content,
                                imageUrls = snippet.imageUrls,
                                navController = navController,
                                onDeleteSnippet = { snippetId ->
                                    deleteDataFromSQLite(context, snippetId)
                                    refreshSnippets()
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(84.dp))
                    }
                }
            }
        )
        restorePlan?.let { pendingPlan ->
            RestoreBackupDialog(
                restorePlan = pendingPlan,
                isBusy = isCloudBusy,
                onConfirm = {
                    runCloudOperation {
                        val insertedCount = withContext(Dispatchers.IO) {
                            insertSnippetsIntoSQLite(context, pendingPlan.snippetsToInsert)
                        }
                        restorePlan = null
                        refreshSnippets()
                        "$insertedCount $cloudRestoreSuccessSuffix"
                    }
                },
                onDismiss = {
                    restorePlan = null
                }
            )
        }
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
    onDeleteSnippet: (Int) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDeleteSnippet(snippetId)
                true
            } else false
        },
        positionalThreshold = { it * 0.25f }
    )

    val primaryImagePath = remember(imageUrls) { imageUrls.firstOrNull().orEmpty() }
    val bitmap = remember(primaryImagePath) {
        if (primaryImagePath.isBlank() || !File(primaryImagePath).exists()) {
            null
        } else {
            runCatching { BitmapFactory.decodeFile(primaryImagePath) }.getOrNull()
        }
    }
    val imageCount = remember(imageUrls) { imageUrls.count { it.isNotBlank() } }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            SwipeBackground(
                dismissDirection = dismissState.dismissDirection,
                progress = dismissState.progress
            )
        },
        enableDismissFromStartToEnd = false
    ) {
        ElevatedCard(
            onClick = { navController.navigate("editSnippet/$snippetId") },
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val titleText = title.ifBlank {
                            stringResource(R.string.snippet_untitled)
                        }
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = stringResource(R.string.tap_to_edit),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = content.ifBlank { stringResource(R.string.snippet_content_empty) },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MaterialTheme.spacing.xSmall),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = MaterialTheme.spacing.small,
                                    vertical = MaterialTheme.spacing.xSmall
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(
                                        R.string.snippet_images_count,
                                        imageCount
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.tap_to_edit),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = MaterialTheme.spacing.small)
                        )
                    }
                }

                if (bitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(148.dp)
                            .padding(horizontal = MaterialTheme.spacing.medium)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.snippet_image),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.spacing.medium),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = MaterialTheme.spacing.small),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.snippet_no_image),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun deleteDataFromSQLite(
    context: Context,
    snippetId: Int
) {
    val dbHelper = DatabaseHelper(context)
    dbHelper.use {
        val snippetsDao = SnippetsDao(dbHelper)
        snippetsDao.deleteSnippetById(snippetId)
    }
}

private fun insertSnippetsIntoSQLite(
    context: Context,
    snippets: List<Snippets>
): Int {
    val dbHelper = DatabaseHelper(context)
    dbHelper.use {
        val snippetsDao = SnippetsDao(dbHelper)
        snippets.forEach { snippet ->
            snippetsDao.insertSnippet(snippet)
        }
    }
    return snippets.size
}

private fun readKeyHapticEnabled(context: Context): Boolean {
    return KeyboardSettingsRepository(context).hapticEnabled
}

fun fetchDataFromSQLite(context: Context): List<Snippets> {
    val dbHelper = DatabaseHelper(context)
    dbHelper.use {
        val snippetsDao = SnippetsDao(dbHelper)
        return snippetsDao.fetchSnippets()
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
            title = "Shipping update",
            content = "Hi there, your order is packed and will be handed over today.",
            imageUrls = listOf(""),
            navController = rememberNavController(),
            onDeleteSnippet = {}
        )
    }
}

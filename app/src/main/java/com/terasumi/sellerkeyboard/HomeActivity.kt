package com.terasumi.sellerkeyboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.terasumi.sellerkeyboard.main.AddSnippetScreen
import com.terasumi.sellerkeyboard.main.BoardingScreen
import com.terasumi.sellerkeyboard.main.EditSnippetScreen
import com.terasumi.sellerkeyboard.main.HomeScreen
import com.terasumi.sellerkeyboard.service.SellerKeyboard
import com.terasumi.sellerkeyboard.ui.theme.SellerKeyboardTheme
import androidx.core.content.ContextCompat

class HomeActivity : ComponentActivity() {
    private val isSellerKeyboardEnabledState = mutableStateOf(false)
    private val showVoicePermissionDisclosure = mutableStateOf(false)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            isSellerKeyboardEnabledState.value = isSellerKeyboardEnabled(this)
            enableEdgeToEdge() // Enable edge-to-edge display
            setContent {
                SellerKeyboardTheme {
                    MainScreen(isSellerKeyboardEnabledState.value)
                    if (showVoicePermissionDisclosure.value) {
                        VoicePermissionDisclosure(
                            onContinue = {
                                showVoicePermissionDisclosure.value = false
                                requestPermissions(
                                    arrayOf(Manifest.permission.RECORD_AUDIO),
                                    REQUEST_RECORD_AUDIO_PERMISSION
                                )
                            },
                            onDismiss = { showVoicePermissionDisclosure.value = false }
                        )
                    }
                }
            }
            handleRecordAudioPermissionIntent(intent)
        } catch (_: Exception) {
            // Keep startup failure text-free; the companion screen can be reopened safely.
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRecordAudioPermissionIntent(intent)
    }

    override fun onResume() {
        super.onResume()

        try {
            isSellerKeyboardEnabledState.value = isSellerKeyboardEnabled(this)
        } catch (_: Exception) {
            // Input-method manager failures contain no actionable user-facing state here.
        }
    }

    private fun handleRecordAudioPermissionIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_REQUEST_RECORD_AUDIO_PERMISSION, false) != true) {
            return
        }
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        ) {
            showVoicePermissionDisclosure.value = false
            return
        }
        showVoicePermissionDisclosure.value = true
    }

    companion object {
        private const val EXTRA_REQUEST_RECORD_AUDIO_PERMISSION =
            "com.terasumi.sellerkeyboard.extra.REQUEST_RECORD_AUDIO_PERMISSION"
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 1001

        fun recordAudioPermissionIntent(context: Context): Intent {
            return Intent(context, HomeActivity::class.java).apply {
                putExtra(EXTRA_REQUEST_RECORD_AUDIO_PERMISSION, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }
}

@Composable
private fun VoicePermissionDisclosure(
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allow voice input?") },
        text = {
            Text(
                "SellerKeyboard uses the microphone only after you tap Voice. " +
                    "On-device recognition is preferred; the installed speech provider may use " +
                    "the network. A transcript is shown for review and is never sent automatically."
            )
        },
        confirmButton = {
            Button(onClick = onContinue) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        }
    )
}

@Composable
fun MainScreen(
    isSellerKeyboardEnabled: Boolean,
    navigationViewModel: NavigationViewModel = viewModel()
) {
    val navController = rememberNavController() // Create a navController
//    val context = LocalContext.current
    val startDestination = if (isSellerKeyboardEnabled) "main" else "boarding"

    LaunchedEffect(isSellerKeyboardEnabled) {
        val currentRoute = navController.currentDestination?.route
        when {
            isSellerKeyboardEnabled && currentRoute == "boarding" -> {
                navController.navigate("main") {
                    popUpTo("boarding") { inclusive = true }
                }
            }

            !isSellerKeyboardEnabled && currentRoute == "main" -> {
                navController.navigate("boarding") {
                    popUpTo("main") { inclusive = true }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("addSnippet") {
            AddSnippetScreen(
                navController = navController,
                navigationViewModel = navigationViewModel
            )
        }
        composable(
            "editSnippet/{snippetId}",
            arguments = listOf(navArgument("snippetId") { type = NavType.IntType })
        ) { backStackEntry ->
            val snippetId = backStackEntry.arguments?.getInt("snippetId")
            EditSnippetScreen(navController = navController, snippetId = snippetId!!)
        }
        composable("boarding") {
            BoardingScreen()
        }
        composable("main") {
            HomeScreen(navController = navController)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    SellerKeyboardTheme {
        MainScreen(true)
    }
}

@SuppressLint("ServiceCast")
fun isSellerKeyboardEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val enabledInputMethods = imm.enabledInputMethodList

    val sellerKeyboardId = ComponentName(context, SellerKeyboard::class.java)
        .flattenToShortString()

    for (inputMethod in enabledInputMethods) {
        if (inputMethod.id == sellerKeyboardId) {
            return true
        }
    }
    return false
}

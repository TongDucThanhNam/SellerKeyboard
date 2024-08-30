package com.terasumi.sellerkeyboard

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.terasumi.sellerkeyboard.ui.theme.SellerKeyboardTheme
import kotlinx.coroutines.delay

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SellerKeyboardTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen(navigationViewModel: NavigationViewModel = viewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current

    var isSellerKeyboardEnabled by remember { mutableStateOf(isSellerKeyboardEnabled(context)) }

    StringResources.initialize(context)

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            isSellerKeyboardEnabled = isSellerKeyboardEnabled(context)
        }
    }

    val startDestination = if (isSellerKeyboardEnabled) "main" else "boarding"

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
        MainScreen()
    }
}

fun isSellerKeyboardEnabled(context: Context): Boolean {
//    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val enabledInputMethods =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)
    val sellerKeyboardId = "com.terasumi.sellerkeyboard/.SellerKeyboard"
    return enabledInputMethods?.split(":")?.any { it == sellerKeyboardId } ?: false
}
package com.terasumi.sellerkeyboard

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
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
import com.terasumi.sellerkeyboard.ui.theme.SellerKeyboardTheme

class HomeActivity : ComponentActivity() {
    private var isSellerKeyboardEnabled = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SellerKeyboardTheme {
                MainScreen(isSellerKeyboardEnabled)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isSellerKeyboardEnabled = isSellerKeyboardEnabled(this)
        setContent {
            SellerKeyboardTheme {
                MainScreen(isSellerKeyboardEnabled)
            }
        }
    }
}

@Composable
fun MainScreen(
    isSellerKeyboardEnabled: Boolean,
    navigationViewModel: NavigationViewModel = viewModel()
) {
    val navController = rememberNavController()
    val context = LocalContext.current

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
        MainScreen(true)
    }
}

fun isSellerKeyboardEnabled(context: Context): Boolean {
//    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
//    Log.d("SellerKeyboard", "Enabled input methods: ${imm.enabledInputMethodList}")
    val enabledInputMethods =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)

//    Log.d("SellerKeyboard", "Enabled input methods: $enabledInputMethods")
    val sellerKeyboardId = "com.terasumi.sellerkeyboard/.SellerKeyboard"
    val sellerKeyboardId2 = "com.terasumi.sellerkeyboard/.service.SellerKeyboard"
    return enabledInputMethods?.contains(sellerKeyboardId) == true || enabledInputMethods?.contains(
        sellerKeyboardId2
    ) == true
}
package com.terasumi.sellerkeyboard

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
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

class HomeActivity : ComponentActivity() {
    private val isSellerKeyboardEnabledState = mutableStateOf(false)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            isSellerKeyboardEnabledState.value = isSellerKeyboardEnabled(this)
            enableEdgeToEdge() // Enable edge-to-edge display
            setContent {
                SellerKeyboardTheme {
                    MainScreen(isSellerKeyboardEnabledState.value)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()

        try {
            isSellerKeyboardEnabledState.value = isSellerKeyboardEnabled(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun MainScreen(
    isSellerKeyboardEnabled: Boolean,
    navigationViewModel: NavigationViewModel = viewModel()
) {
    val navController = rememberNavController() // Create a navController
//    val context = LocalContext.current
//    Log.d("SellerKeyboard", "isSellerKeyboardEnabled: $isSellerKeyboardEnabled")

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

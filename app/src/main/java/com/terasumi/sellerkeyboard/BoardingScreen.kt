package com.terasumi.sellerkeyboard

import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity.INPUT_METHOD_SERVICE
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.terasumi.sellerkeyboard.ui.theme.SellerKeyboardTheme

//On Boarding Screen
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoardingScreen(navController: NavController) {
    Box(modifier = Modifier.fillMaxSize()) {
        val pagerState = rememberPagerState(pageCount = {
            1
        })
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            // Our page content
            OnboardingPage(page, pagerState, navController)
        }
        Row(
            Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pagerState.pageCount) { iteration ->
                val color =
                    if (pagerState.currentPage == iteration) Color.DarkGray else Color.LightGray
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(color)
                        .size(16.dp)
                )
            }
        }
    }
}

//
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingPage(page: Int, pageState: PagerState, navController: NavController) {
    when (page) {
        0 -> {
            FirstBoardingPage(pageState, navController)
        }

//        1 -> {
//            SecondBoardingPage(pageState)
//        }
//        // Add more pages as needed
//        2 -> {
//            TutorialScreen(pageState)
//        }
    }
}

//Enable keyboard
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FirstBoardingPage(pageState: PagerState, navController: NavController) {
    val context = LocalContext.current

    // Content for the first page
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Chào mừng bạn đến với ứng dụng Seller Keyboard !",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Trước tiên bạn phải bật bàn phím Seller Keyboard để sử dụng ứng dụng.",
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }

        Button(
            onClick = {
                //TODO: Implement logic to enable keyboard
                // Intent to enable Seller Keyboard
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                context.startActivity(intent)
                // Navigate to the next page

            },
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.BottomCenter)
        ) {
            Text("Bật Seller Keyboard")
        }
    }
}

//Select Keyboard
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SecondBoardingPage(pageState: PagerState) {
    val context = LocalContext.current

    // Content for the second page
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to the Second Page!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "This is the content for the second page.",
                fontSize = 16.sp
            )
        }

        Button(
            onClick = {
                //TODO: Implement logic to select keyboard
                val inputMethodManager =
                    context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showInputMethodPicker()

                // Navigate to the next page
                var currentPage = pageState.currentPage
                currentPage += 1
            },
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.BottomCenter)

        ) {
            Text("Select Keyboard")
        }
    }
}

//Tutorial Screen
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TutorialScreen(pageState: PagerState) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to the Tutorial Screen!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "This is the content for the tutorial screen.",
                fontSize = 16.sp
            )
        }

        Button(
            onClick = {
                //TODO: Implement logic to navigate to the main screen

                // Navigate to the main screen

            },
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.BottomCenter)
        ) {
            Text("Get Started")
        }
    }
}


// On Boarding Screen
@Preview(showBackground = true)
@Composable
fun BoardingPreview() {
    SellerKeyboardTheme {
        BoardingScreen(navController = rememberNavController())
    }
}


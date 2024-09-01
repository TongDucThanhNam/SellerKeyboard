package com.terasumi.sellerkeyboard.main

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.ui.theme.SellerKeyboardTheme

//On Boarding Screen
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoardingScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        val pagerState = rememberPagerState(pageCount = {
            1
        })
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            // Our page content
            OnboardingPage(page)
        }
//        Row(
//            Modifier
//                .wrapContentHeight()
//                .fillMaxWidth()
//                .align(Alignment.BottomCenter)
//                .padding(bottom = 8.dp),
//            horizontalArrangement = Arrangement.Center
//        ) {
//            repeat(pagerState.pageCount) { iteration ->
//                val color =
//                    if (pagerState.currentPage == iteration) Color.DarkGray else Color.LightGray
//                Box(
//                    modifier = Modifier
//                        .padding(2.dp)
//                        .clip(CircleShape)
//                        .background(color)
//                        .size(16.dp)
//                )
//            }
//        }
    }
}

//
@Composable
fun OnboardingPage(page: Int) {
    when (page) {
        0 -> {
            FirstBoardingPage()
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
@Composable
fun FirstBoardingPage() {
    val context = LocalContext.current

    //stringResource
    val resources = context.resources
    val enableKeyboard = remember {
        resources.getString(R.string.enable_keyboard)
    }

    val welcomeMessage = remember {
        resources.getString(R.string.welcome_message)
    }

    val enableKeyboardPrompt = remember {
        resources.getString(R.string.enable_keyboard_prompt)
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.seller))
    val progress by animateLottieCompositionAsState(
        composition, iterations = LottieConstants.IterateForever,
    )


    // Content for the first page
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 200.dp), // Adjust the value as needed

            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            LottieAnimation(
                composition = composition,
                progress = progress,
                modifier = Modifier.size(200.dp),
            )


            Text(
                text = welcomeMessage,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = enableKeyboardPrompt,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
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
                .padding(bottom = 60.dp)
                .align(Alignment.BottomCenter)
        ) {
            Text(enableKeyboard)
        }
    }
}

//
////Tutorial Screen
//@OptIn(ExperimentalFoundationApi::class)
//@Composable
//fun TutorialScreen(pageState: PagerState) {
//    Box(modifier = Modifier.fillMaxSize()) {
//        Column(
//            modifier = Modifier.fillMaxSize(),
//            verticalArrangement = Arrangement.Center,
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            Text(
//                text = "Welcome to the Tutorial Screen!",
//                fontSize = 24.sp,
//                fontWeight = FontWeight.Bold
//            )
//            Spacer(modifier = Modifier.height(16.dp))
//            Text(
//                text = "This is the content for the tutorial screen.",
//                fontSize = 16.sp
//            )
//        }
//
//        Button(
//            onClick = {
//                //TODO: Implement logic to navigate to the main screen
//
//                // Navigate to the main screen
//
//            },
//            modifier = Modifier
//                .padding(16.dp)
//                .align(Alignment.BottomCenter)
//        ) {
//            Text("Get Started")
//        }
//    }
//}


// On Boarding Screen
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun BoardingPreview() {
    SellerKeyboardTheme {
        BoardingScreen()
    }
}


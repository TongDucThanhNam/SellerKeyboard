package com.terasumi.sellerkeyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardContent() {
    //Fetch snippets from the database
    val context = LocalContext.current

    //stringResource
//    val snippetText = StringResources.snippet_list
//    val calculatorText = StringResources.calculator

    val snippetText = remember {
        StringResources.snippet_list
    }

    val calculatorText = remember {
        StringResources.calculator
    }



    val listSnippets = remember { mutableStateOf(listOf<Snippets>()) }

    LaunchedEffect(context) {
        listSnippets.value = fetchDataFromSQLite(context)
    }


    var state by remember { mutableIntStateOf(0) }
    val titles = listOf(snippetText, calculatorText)


    Column(
        modifier = Modifier.background(
            color = MaterialTheme.colorScheme.background
        )
    ) {
        //TabLayout of Keyboard
        PrimaryTabRow(selectedTabIndex = state) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = state == index,
                    onClick = { state = index },
                    text = { Text(text = title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        //Content of Keyboard
        when (state) {
            0 -> {
                //Log
//                Log.d("KeyboardContent", "Snippets: $listSnippets")
                SnippetsKeyboard(listSnippets = listSnippets.value)
            }

            1 -> {
                // Calculate
                CalculateContent()
            }

            2 -> {
                //TODO: Implement the content of the third tab
            }
        }
    }
}


@Preview(showSystemUi = true)
@Composable
fun KeyboardContentPreview() {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
//                .padding(16.dp)
        ) {
            KeyboardContent()
        }
    }
}
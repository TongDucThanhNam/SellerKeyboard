package com.terasumi.sellerkeyboard.backend

import SnippetsDao
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipeLeft
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.terasumi.sellerkeyboard.R
import com.terasumi.sellerkeyboard.database.DatabaseHelper
import com.terasumi.sellerkeyboard.database.Snippets
import com.terasumi.sellerkeyboard.database.SnippetsTable
import com.terasumi.sellerkeyboard.keyboard.KeyboardAction
import com.terasumi.sellerkeyboard.keyboard.SnippetsKeyboard
import com.terasumi.sellerkeyboard.keyboard.theme.LightKeyboardColors
import com.terasumi.sellerkeyboard.main.HomeScreen
import com.terasumi.sellerkeyboard.ui.theme.SellerKeyboardTheme
import dev.convex.android.ConvexClient
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConvexBackupConnectedSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setup() {
        assumeTrue(
            "Set instrumentation argument convexSmoke=true to run the live Convex backup smoke test.",
            InstrumentationRegistry.getArguments().getString("convexSmoke") == "true"
        )
        context = ApplicationProvider.getApplicationContext()
        clearLocalState()
    }

    @After
    fun teardown() {
        if (::context.isInitialized) {
            clearLocalState()
        }
    }

    @Test
    fun repositoryRegisterLoginBackupListAndDeleteWorksOnDevice() = runBlocking {
        val suffix = System.currentTimeMillis().toString()
        val email = "android-repo-smoke-$suffix@example.com"
        val password = "SmokePassword$suffix"
        val snippet = Snippets(
            id = 101,
            title = "Repository smoke $suffix",
            content = "Repository smoke content $suffix",
            imageUrls = emptyList()
        )
        val client = ConvexClient(context.getString(R.string.convex_url))
        val authRepository = ConvexAuthRepository(context, client)
        val backupRepository = SnippetBackupRepository(client, authRepository)

        val registered = authRepository.register(email, password)
        assertEquals(email, registered.email)
        authRepository.logout()

        val loggedIn = authRepository.login(email, password)
        assertEquals(email, loggedIn.email)
        val who = authRepository.whoAmI()
        assertEquals(email, who?.email)

        assertEquals(1, backupRepository.backup(listOf(snippet)))
        val backups = backupRepository.listBackup()
        assertTrue(backups.any { it.title == snippet.title && it.content == snippet.content })
        assertTrue(backupRepository.deleteBackupItem("local-${snippet.id}"))
        assertTrue(backupRepository.listBackup().none { it.title == snippet.title })
        authRepository.logout()
    }

    @Test
    fun registerBackupRestoreAndKeyboardSnippetInsert() {
        val suffix = System.currentTimeMillis().toString()
        val email = "android-smoke-$suffix@example.com"
        val password = "SmokePassword$suffix"
        val snippetTitle = "Smoke snippet $suffix"
        val snippetContent = "Smoke content $suffix"
        val client = ConvexClient(context.getString(R.string.convex_url))
        val authRepository = ConvexAuthRepository(context, client)
        val backupRepository = SnippetBackupRepository(client, authRepository)
        var showKeyboard by mutableStateOf(false)
        var insertedText: String? = null

        insertSnippet(snippetTitle, snippetContent)

        composeRule.setContent {
            SellerKeyboardTheme {
                if (showKeyboard) {
                    SnippetsKeyboard(
                        myColor = LightKeyboardColors,
                        isLandscape = false,
                        onAction = { action ->
                            if (action is KeyboardAction.KeyPress) {
                                insertedText = action.key
                            }
                        }
                    )
                } else {
                    HomeScreen(
                        navController = rememberNavController(),
                        convexAuthRepository = authRepository,
                        snippetBackupRepository = backupRepository,
                        showCloudBackup = true
                    )
                }
            }
        }

        waitForText(snippetTitle)
        composeRule.onNodeWithTag("cloud_email").performTextInput(email)
        composeRule.onNodeWithTag("cloud_password").performTextInput(password)
        composeRule.onNodeWithTag("cloud_register").performClick()
        waitForText("Signed in:", substring = true, timeoutMillis = 60_000)

        waitForTagEnabled("cloud_backup_now")
        composeRule.onNodeWithTag("cloud_backup_now").performClick()
        waitForText("snippet(s) backed up.", substring = true, timeoutMillis = 60_000)

        composeRule.onNodeWithText(snippetTitle).performTouchInput {
            swipeLeft()
        }
        waitUntilTextGone(snippetTitle)

        waitForTagEnabled("cloud_restore")
        composeRule.onNodeWithTag("cloud_restore").performClick()
        waitForText("Restore backup")
        waitForTagEnabled("cloud_restore_confirm")
        composeRule.onNodeWithTag("cloud_restore_confirm").performClick()
        waitForText(snippetTitle, timeoutMillis = 60_000)

        composeRule.runOnIdle {
            showKeyboard = true
        }
        waitForText(snippetTitle)
        composeRule.onNodeWithText(snippetTitle).performClick()
        composeRule.runOnIdle {
            assertEquals(snippetContent, insertedText)
        }

        composeRule.runOnIdle {
            assertTrue(fetchSnippetTitles().contains(snippetTitle))
        }
    }

    private fun insertSnippet(title: String, content: String) {
        DatabaseHelper(context).use { dbHelper ->
            SnippetsDao(dbHelper).insertSnippet(
                Snippets(
                    id = 0,
                    title = title,
                    content = content,
                    imageUrls = emptyList()
                )
            )
        }
    }

    private fun fetchSnippetTitles(): List<String> {
        return DatabaseHelper(context).use { dbHelper ->
            SnippetsDao(dbHelper).fetchSnippets().map { it.title }
        }
    }

    private fun clearLocalState() {
        context.getSharedPreferences("convex_auth", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        DatabaseHelper(context).use { dbHelper ->
            dbHelper.writableDatabase.delete(SnippetsTable.TABLE_NAME, null, null)
        }
    }

    private fun waitForText(
        text: String,
        substring: Boolean = false,
        timeoutMillis: Long = 30_000
    ) {
        try {
            composeRule.waitUntil(timeoutMillis = timeoutMillis) {
                composeRule.onAllNodesWithText(text, substring = substring)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError(
                "Timed out waiting for text \"$text\". Current semantics tree:\n" +
                    composeRule.onRoot(useUnmergedTree = true).printToString(),
                error
            )
        }
    }

    private fun waitUntilTextGone(
        text: String,
        timeoutMillis: Long = 30_000
    ) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule.onAllNodesWithText(text)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    private fun waitForTagEnabled(
        tag: String,
        timeoutMillis: Long = 30_000
    ) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            runCatching {
                composeRule.onNodeWithTag(tag).assertIsEnabled()
            }.isSuccess
        }
    }
}

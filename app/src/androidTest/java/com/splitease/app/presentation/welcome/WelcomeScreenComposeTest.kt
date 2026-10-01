package com.splitease.app.presentation.welcome

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.splitease.app.R
import com.splitease.app.presentation.theme.SplitEaseTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose smoke: Welcome screen renders branded title and primary CTAs.
 */
@RunWith(AndroidJUnit4::class)
class WelcomeScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun welcomeShowsBrandAndActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val title = context.getString(R.string.welcome_title)
        val getStarted = context.getString(R.string.action_get_started)
        val logIn = context.getString(R.string.action_log_in)

        composeRule.setContent {
            SplitEaseTheme(darkTheme = false, dynamicColor = false) {
                WelcomeScreen(onGetStarted = {}, onLogIn = {}, onOpenInviteLink = { true })
            }
        }

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(getStarted).assertIsDisplayed()
        composeRule.onNodeWithText(logIn).assertIsDisplayed()
    }
}

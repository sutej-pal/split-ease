package com.splitease.app.presentation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitease.app.presentation.theme.SplitEaseColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeListRowTrailingIconTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pencilContainerMatchesChevron() {
        composeRule.setContent {
            SePreview {
                Column(Modifier.width(360.dp)) {
                    SeListRow(
                        title = "Account settings",
                        subtitle = "Profile, currency, language",
                        modifier = Modifier.testTag("pencilRow"),
                        trailing = {
                            SeListRowTrailingIcon(
                                imageVector = Icons.Filled.Edit,
                                tint = SplitEaseColors.NavyMuted,
                                modifier = Modifier.testTag("pencil"),
                            )
                        },
                        onClick = {},
                        showDivider = false,
                        horizontalPadding = SeLayout.listInset,
                    )
                    SeListRow(
                        title = "Appearance",
                        subtitle = "System default",
                        modifier = Modifier.testTag("chevronRow"),
                        trailing = {
                            SeChevron(modifier = Modifier.testTag("chevron"))
                        },
                        onClick = {},
                        showDivider = false,
                        horizontalPadding = SeLayout.listInset,
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val pencilBounds = composeRule.onNodeWithTag("pencil", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val chevronBounds = composeRule.onNodeWithTag("chevron", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val pencilRowBounds = composeRule.onNodeWithTag("pencilRow", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val chevronRowBounds = composeRule.onNodeWithTag("chevronRow", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        assertEquals(chevronBounds.width, pencilBounds.width, 0.5f)
        assertEquals(chevronBounds.height, pencilBounds.height, 0.5f)
        assertEquals(chevronBounds.right, pencilBounds.right, 0.5f)
        assertEquals(chevronBounds.center.x, pencilBounds.center.x, 0.5f)
        assertEquals(pencilRowBounds.center.y, pencilBounds.center.y, 0.5f)
        assertEquals(chevronRowBounds.center.y, chevronBounds.center.y, 0.5f)
    }
}
